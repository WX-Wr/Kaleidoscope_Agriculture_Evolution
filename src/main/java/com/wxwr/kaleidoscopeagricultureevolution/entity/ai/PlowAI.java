package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import com.wxwr.kaleidoscopeagricultureevolution.region.CuboidRegion;
import com.wxwr.kaleidoscopeagricultureevolution.region.FlatRegion;
import com.wxwr.kaleidoscopeagricultureevolution.region.Region;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 分层 S 形扫描犁地 AI，带 A* 障碍物规避。
 *
 * 每层算法：
 * 1. S 形扫描，按顺序为所有可犁方块编号
 * 2. 使用 2D A*（层内 4 方向）按顺序连接已编号的方块
 * 3. 将所有已处理的方块标记为后续层的障碍物
 * 4. 使用 3D A*（6 方向）连接各层
 */
public class PlowAI {

    private static final int MAX_PATH_VOLUME = FlatRegion.MAX_AREA * 16;
    private static final int MAX_PLOW_TARGETS = 2048;
    private static final int MAX_SERIALIZED_PATH_POINTS = 2048;

    private final Level level;
    private CuboidRegion region;
    private Region originalRegion;  // 原始区域（圆形等非矩形区域用 contains() 过滤）
    private Direction plowDir;
    private WorkAction workAction = WorkAction.TILL;
    private WorkPathRule pathRule = new WorkPathRule("dry_field", 0, false, 1, -1, 0, 0);
    private int layerDirection = 1;

    private final List<BlockPos> path = new ArrayList<>();
    private int pathIndex;
    private boolean finished;
    private final Set<BlockPos> processed = new HashSet<>();
    private String cachedPathData = "";
    private int cachedPathIndex = -1;
    private int cachedPathSize = -1;

    private int currentLayerY;

    // 当前层生成的所有行（用于客户端预览）
    private final List<BlockPos[]> currentLayerRows = new ArrayList<>();

    public enum Direction {
        PLUS_X, MINUS_X, PLUS_Z, MINUS_Z
    }

    public PlowAI(Level level) {
        this.level = level;
    }

    // --- 公开 API ---

    /**
     * 在区域内从指定角点开始犁地。
     *
     * @param region   要犁的区域（矩形或圆形 — 圆形使用其外接矩形）
     * @param startPos 耕牛起始的角点位置
     * @param plowDir  犁地方向
     */
    public boolean start(Region region, BlockPos startPos, Direction plowDir) {
        return start(region, startPos, plowDir, WorkAction.TILL,
                new WorkPathRule("dry_field", 0, false, 1, -1, 0, 0));
    }

    public boolean start(Region region, BlockPos startPos, Direction plowDir,
                         WorkAction workAction, WorkPathRule pathRule) {
        if (!isRegionWithinWorkLimit(region)) {
            reset();
            return false;
        }

        // 保存原始区域用于 contains() 过滤
        this.originalRegion = region;
        // 用外接矩形作为扫描边界
        if (region instanceof CuboidRegion cr) {
            this.region = cr;
        } else {
            this.region = new CuboidRegion(region.getMinimumPoint(), region.getMaximumPoint());
        }
        this.plowDir = plowDir;
        this.workAction = workAction;
        this.pathRule = pathRule;
        this.finished = false;
        this.pathIndex = 0;
        this.path.clear();
        this.processed.clear();
        this.currentLayerRows.clear();
        invalidatePathData();

        this.currentLayerY = startPos.getY();
        this.layerDirection = 1; // 始终从起点向上

        buildFullPath();
        return !finished;
    }

    /** @deprecated 请改用 {@link #start(Region, BlockPos, Direction)}。 */
    @Deprecated
    public boolean start(BlockPos cornerA, BlockPos cornerB, Direction plowDir) {
        try {
            return start(new CuboidRegion(cornerA, cornerB), cornerA, plowDir);
        } catch (ArithmeticException e) {
            reset();
            return false;
        }
    }

    public BlockPos getTargetPos() {
        if (finished || pathIndex >= path.size()) return null;
        return path.get(pathIndex);
    }

    public void onReachedTarget() {
        pathIndex++;
        invalidatePathData();
        if (pathIndex >= path.size()) {
            finished = true;
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void reset() {
        this.finished = true;
        this.path.clear();
        this.pathIndex = 0;
        this.processed.clear();
        this.currentLayerRows.clear();
        invalidatePathData();
    }

    /**
     * 从角点重建完整路径后，向前跳过以便剩余路径与 {@code pathData}（保存前序列化）匹配。
     * <p>
     * 剩余路径从末尾开始与完整路径进行比对；
     * 一旦找到匹配，{@link #pathIndex} 将相应设置。
     * 如果未找到匹配，则保留完整路径不变。
     */
    public void skipToRemaining(String pathData) {
        if (pathData == null || pathData.isEmpty()) return;
        List<BlockPos> remaining = parsePathData(pathData);
        if (remaining.isEmpty()) return;

        // 尝试将剩余路径与完整路径的尾部进行匹配。
        // 从完整路径的末尾向前遍历。
        int matchIdx = -1;
        for (int i = path.size() - remaining.size(); i >= 0; i--) {
            boolean match = true;
            for (int j = 0; j < remaining.size(); j++) {
                if (!path.get(i + j).equals(remaining.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matchIdx = i;
                break;
            }
        }

        if (matchIdx >= 0) {
            // 将匹配点之前的所有方块标记为已处理
            for (int i = 0; i < matchIdx; i++) {
                processed.add(path.get(i));
            }
            pathIndex = matchIdx;
            invalidatePathData();
        }
    }

    /**
     * 将路径数据字符串解析为 BlockPos 列表。
     */
    private static List<BlockPos> parsePathData(String data) {
        List<BlockPos> points = new ArrayList<>();
        if (data.isEmpty()) return points;
        for (String entry : data.split(";")) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(",");
            if (parts.length != 3) continue;
            try {
                points.add(new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                ));
            } catch (NumberFormatException ignored) {}
        }
        return points;
    }

    /**
     * 将剩余路径以字符串形式返回，用于客户端同步。
     * 格式："x,y,z;x,y,z;..."
     */
    public String getPathData() {
        if (cachedPathIndex == pathIndex && cachedPathSize == path.size()) {
            return cachedPathData;
        }

        StringBuilder sb = new StringBuilder();
        int end = Math.min(path.size(), pathIndex + MAX_SERIALIZED_PATH_POINTS);
        for (int i = pathIndex; i < end; i++) {
            BlockPos p = path.get(i);
            sb.append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append(";");
        }
        cachedPathData = sb.toString();
        cachedPathIndex = pathIndex;
        cachedPathSize = path.size();
        return cachedPathData;
    }

    public int getRemainingPathCount() {
        return Math.max(0, path.size() - pathIndex);
    }

    /**
     * 返回当前层的行段（用于客户端预览）。
     */
    public List<BlockPos[]> getCurrentLayerRows() {
        return currentLayerRows;
    }

    /**
     * 返回所有已处理方块位置的集合。
     */
    public Set<BlockPos> getProcessed() {
        return Collections.unmodifiableSet(processed);
    }

    /**
     * 按 S 形顺序扫描某个 Y 层的可犁方块。
     */
    private List<BlockPos> scanLayerBlocks(int y, boolean recordPreview) {
        List<BlockPos> numbered = new ArrayList<>();

        if (recordPreview) {
            currentLayerRows.clear();
        }

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        boolean rowAlongX;
        int rowCount, rowStart, rowStep, scanStartBase, scanEndBase;

        BlockPos pos1 = region.getPos1();

        switch (plowDir) {
            case PLUS_X, MINUS_X -> {
                rowAlongX = true;
                rowCount = maxZ - minZ + 1;
                rowStart = pos1.getZ() == minZ ? minZ : maxZ;
                rowStep = rowStart == minZ ? 1 : -1;
            }
            default -> {
                rowAlongX = false;
                rowCount = maxX - minX + 1;
                rowStart = pos1.getX() == minX ? minX : maxX;
                rowStep = rowStart == minX ? 1 : -1;
            }
        }

        if (rowAlongX) {
            scanStartBase = pos1.getX() == maxX ? maxX : minX;
            scanEndBase   = pos1.getX() == maxX ? minX : maxX;
        } else {
            scanStartBase = pos1.getZ() == maxZ ? maxZ : minZ;
            scanEndBase   = pos1.getZ() == maxZ ? minZ : maxZ;
        }

        for (int row = 0; row < rowCount; row++) {
            int rowPos = rowStart + row * rowStep;
            boolean reversed = (row % 2 == 1);

            int scanStart = reversed ? scanEndBase : scanStartBase;
            int scanEnd   = reversed ? scanStartBase : scanEndBase;
            int scanStep  = (scanStart <= scanEnd) ? 1 : -1;

            List<BlockPos> rowBlocks = new ArrayList<>();
            for (int col = scanStart; (scanStep > 0 ? col <= scanEnd : col >= scanEnd); col += scanStep) {
                BlockPos pos;
                if (rowAlongX) {
                    pos = new BlockPos(col, y, rowPos);
                } else {
                    pos = new BlockPos(rowPos, y, col);
                }

                if (processed.contains(pos)) continue;
                if (!originalRegion.contains(pos)) continue;
                if (PlowableChecker.isWorkable(level, pos, workAction, pathRule)) {
                    rowBlocks.add(pos);
                }
            }

            if (recordPreview && !rowBlocks.isEmpty()) {
                currentLayerRows.add(new BlockPos[]{rowBlocks.get(0), rowBlocks.get(rowBlocks.size() - 1)});
            }

            numbered.addAll(rowBlocks);
        }

        return numbered;
    }

    // --- 路径构建（内部实现未变） ---

    private void buildFullPath() {
        int minY = region.getMinimumPoint().getY();
        int maxY = region.getMaximumPoint().getY();
        int y = currentLayerY;
        while (y <= maxY && y >= minY) {
            processLayer(y);
            if (finished) return;
            y += layerDirection;
        }
        if (path.isEmpty()) {
            finished = true;
        }
        invalidatePathData();
    }

    private void processLayer(int y) {
        List<BlockPos> numbered = sScanLayer(y);
        if (numbered.isEmpty()) return;
        if ((long) processed.size() + numbered.size() > MAX_PLOW_TARGETS) {
            reset();
            return;
        }

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        BlockPos layerStart = numbered.get(0);

        if (!path.isEmpty()) {
            BlockPos prevEnd = path.get(path.size() - 1);
            BlockPos bestEntry = findClosestPlowable(prevEnd, y);
            if (bestEntry != null && !bestEntry.equals(layerStart)) {
                List<BlockPos> link3D = AStarPathfinder.findPath3D(
                        level, prevEnd, bestEntry, processed, region, workAction, pathRule);
                if (link3D != null) {
                    path.addAll(link3D.subList(1, link3D.size()));
                }
                int idx = numbered.indexOf(bestEntry);
                if (idx > 0) {
                    List<BlockPos> reordered = new ArrayList<>();
                    for (int i = idx; i < numbered.size(); i++) {
                        reordered.add(numbered.get(i));
                    }
                    for (int i = idx - 1; i >= 0; i--) {
                        reordered.add(numbered.get(i));
                    }
                    numbered = reordered;
                }
            }
        }

        Set<BlockPos> numberedSet = new HashSet<>(numbered);
        Set<BlockPos> layerObstacles = new HashSet<>(processed);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos bp = new BlockPos(x, y, z);
                // 区域外的方块视为障碍物，防止 A* 路径穿出区域
                if (!originalRegion.contains(bp)) {
                    layerObstacles.add(bp);
                } else if (!numberedSet.contains(bp)
                        && PlowableChecker.isObstacle(level, bp, processed, workAction, pathRule)) {
                    layerObstacles.add(bp);
                }
            }
        }

        List<BlockPos> layerPath = new ArrayList<>();
        layerPath.add(numbered.get(0));

        int curIdx = 0;
        while (curIdx < numbered.size() - 1) {
            int targetIdx = curIdx + 1;
            List<BlockPos> segment = null;

            while (targetIdx < numbered.size()) {
                segment = AStarPathfinder.findPath2D(
                        level,
                        numbered.get(curIdx),
                        numbered.get(targetIdx),
                        layerObstacles,
                        y,
                        region,
                        workAction,
                        pathRule);
                if (segment != null) break;
                layerObstacles.add(numbered.get(targetIdx));
                targetIdx++;
            }

            if (segment == null) {
                break;
            }

            layerPath.addAll(segment.subList(1, segment.size()));
            curIdx = targetIdx;
        }

        if (path.isEmpty()) {
            path.addAll(layerPath);
        } else {
            if (!path.isEmpty() && path.get(path.size() - 1).equals(layerPath.get(0))) {
                path.addAll(layerPath.subList(1, layerPath.size()));
            } else {
                path.addAll(layerPath);
            }
        }

        for (BlockPos np : numbered) {
            processed.add(np);
        }

        currentLayerY = y;
        invalidatePathData();
    }

    private List<BlockPos> sScanLayer(int y) {
        return scanLayerBlocks(y, true);
    }

    private BlockPos findClosestPlowable(BlockPos ref, int y) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (processed.contains(pos)) continue;
                if (!originalRegion.contains(pos)) continue;
                if (!PlowableChecker.isWorkable(level, pos, workAction, pathRule)) continue;
                double dist = pos.distSqr(ref);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos;
                }
            }
        }
        return best;
    }

    private boolean isRegionWithinWorkLimit(Region region) {
        if (region.getVolume() > MAX_PATH_VOLUME) return false;
        if (region instanceof FlatRegion flatRegion && flatRegion.getArea() > FlatRegion.MAX_AREA) return false;
        return region.getHeight() <= 16;
    }

    private void invalidatePathData() {
        cachedPathIndex = -1;
        cachedPathSize = -1;
        cachedPathData = "";
    }
}
