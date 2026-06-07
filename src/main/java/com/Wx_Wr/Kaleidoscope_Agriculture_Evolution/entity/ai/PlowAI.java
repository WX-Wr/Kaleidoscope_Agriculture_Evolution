package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Layered S-scan plowing AI with A* obstacle avoidance.
 *
 * Algorithm per layer:
 * 1. S-shaped scan to number all plowable blocks sequentially
 * 2. Connect numbered blocks in order using 2D A* (8-dir within layer)
 * 3. Mark all processed blocks as obstacles for future layers
 * 4. Connect layers using 3D A* (6-dir)
 */
public class PlowAI {

    private final Level level;
    private BlockPos cornerA;
    private BlockPos cornerB;
    private Direction plowDir;
    private int layerDirection = 1;

    private final List<BlockPos> path = new ArrayList<>();
    private int pathIndex;
    private boolean finished;
    private final Set<BlockPos> processed = new HashSet<>();

    private int currentLayerY;
    private int maxLayerY;
    private int minLayerY;

    // Pre-computed region bounds (normalized)
    private int minX, maxX, minZ, maxZ;

    // All rows generated for the current layer (for client preview)
    private final List<BlockPos[]> currentLayerRows = new ArrayList<>();

    public enum Direction {
        PLUS_X, MINUS_X, PLUS_Z, MINUS_Z
    }

    public PlowAI(Level level) {
        this.level = level;
    }

    // --- Public API ---

    public void start(BlockPos cornerA, BlockPos cornerB, Direction plowDir) {
        this.cornerA = cornerA;
        this.cornerB = cornerB;
        this.plowDir = plowDir;
        this.finished = false;
        this.pathIndex = 0;
        this.path.clear();
        this.processed.clear();
        this.currentLayerRows.clear();

        this.minX = Math.min(cornerA.getX(), cornerB.getX());
        this.maxX = Math.max(cornerA.getX(), cornerB.getX());
        this.minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        this.maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        this.minLayerY = Math.min(cornerA.getY(), cornerB.getY());
        this.maxLayerY = Math.max(cornerA.getY(), cornerB.getY());

        this.currentLayerY = cornerA.getY();
        this.layerDirection = 1; // always go upward from start

        buildFullPath();
    }

    public BlockPos getTargetPos() {
        if (finished || pathIndex >= path.size()) return null;
        return path.get(pathIndex);
    }

    public void onReachedTarget() {
        pathIndex++;
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
    }

    /**
     * Returns remaining path as a string for client sync.
     * Format: "x,y,z;x,y,z;..."
     */
    public String getPathData() {
        StringBuilder sb = new StringBuilder();
        for (int i = pathIndex; i < path.size(); i++) {
            BlockPos p = path.get(i);
            sb.append(p.getX()).append(",").append(p.getY()).append(",").append(p.getZ()).append(";");
        }
        return sb.toString();
    }

    /**
     * Returns row segments for the current layer (used for client preview).
     */
    public List<BlockPos[]> getCurrentLayerRows() {
        return currentLayerRows;
    }

    /**
     * Returns the set of all processed block positions.
     */
    public Set<BlockPos> getProcessed() {
        return Collections.unmodifiableSet(processed);
    }

    /**
     * Scan a Y-layer for plowable blocks in S-shaped order.
     * @param y the Y-level to scan
     * @param recordPreview if true, populates {@link #currentLayerRows} for client rendering
     * @return ordered list of plowable BlockPos in this layer
     */
    private List<BlockPos> scanLayerBlocks(int y, boolean recordPreview) {
        List<BlockPos> numbered = new ArrayList<>();

        if (recordPreview) {
            currentLayerRows.clear();
        }

        // Determine scan parameters from plowDir and corner positions
        boolean rowAlongX;
        int rowCount, rowStart, rowStep, scanStartBase, scanEndBase;

        switch (plowDir) {
            case PLUS_X, MINUS_X -> {
                rowAlongX = true;
                rowCount = maxZ - minZ + 1;
                rowStart = cornerA.getZ() == minZ ? minZ : maxZ;
                rowStep = rowStart == minZ ? 1 : -1;
            }
            default -> { // PLUS_Z, MINUS_Z
                rowAlongX = false;
                rowCount = maxX - minX + 1;
                rowStart = cornerA.getX() == minX ? minX : maxX;
                rowStep = rowStart == minX ? 1 : -1;
            }
        }

        if (rowAlongX) {
            scanStartBase = cornerA.getX() == maxX ? maxX : minX;
            scanEndBase   = cornerA.getX() == maxX ? minX : maxX;
        } else {
            scanStartBase = cornerA.getZ() == maxZ ? maxZ : minZ;
            scanEndBase   = cornerA.getZ() == maxZ ? minZ : maxZ;
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
                if (PlowableChecker.isPlowable(level, pos)) {
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

    private BlockPos findClosestInList(BlockPos ref, List<BlockPos> candidates) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : candidates) {
            double d = p.distSqr(ref);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // --- Path building ---

    private void buildFullPath() {
        int y = currentLayerY;
        while (y <= maxLayerY && y >= minLayerY) {
            processLayer(y);
            if (path.isEmpty()) break; // nothing plowable left
            y += layerDirection;
        }
        if (path.isEmpty()) {
            finished = true;
        }
    }

    private void processLayer(int y) {
        // 1. S-scan to number plowable blocks
        List<BlockPos> numbered = sScanLayer(y);
        if (numbered.isEmpty()) return;

        // Record the first block as this layer's start
        BlockPos layerStart = numbered.get(0);

        // If this is NOT the first layer, connect from previous path end to this layer
        if (!path.isEmpty()) {
            BlockPos prevEnd = path.get(path.size() - 1);
            BlockPos bestEntry = findClosestPlowable(prevEnd, y);
            if (bestEntry != null && !bestEntry.equals(layerStart)) {
                List<BlockPos> link3D = AStarPathfinder.findPath3D(level, prevEnd, bestEntry, processed,
                        minX, maxX, minLayerY, maxLayerY, minZ, maxZ);
                if (link3D != null) {
                    path.addAll(link3D.subList(1, link3D.size()));
                }
                // Split numbered into two segments and reverse the prefix,
                // so the transition from S-end to the next segment is as short
                // as possible instead of jumping across the entire field.
                int idx = numbered.indexOf(bestEntry);
                if (idx > 0) {
                    List<BlockPos> reordered = new ArrayList<>();
                    // Forward segment: [bestEntry, bestEntry+1, ..., end]
                    for (int i = idx; i < numbered.size(); i++) {
                        reordered.add(numbered.get(i));
                    }
                    // Reversed prefix: [bestEntry-1, bestEntry-2, ..., 0]
                    for (int i = idx - 1; i >= 0; i--) {
                        reordered.add(numbered.get(i));
                    }
                    numbered = reordered;
                }
            }
        }

        // 2. Build within-layer path using 2D A*
        Set<BlockPos> layerObstacles = new HashSet<>(processed);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos bp = new BlockPos(x, y, z);
                if (!numbered.contains(bp) && PlowableChecker.isObstacle(level, bp, processed)) {
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

            // Try successive targets until we find a reachable one
            while (targetIdx < numbered.size()) {
                segment = AStarPathfinder.findPath2D(level, numbered.get(curIdx), numbered.get(targetIdx),
                        layerObstacles, y, minX, maxX, minZ, maxZ);
                if (segment != null) break;
                // Mark unreachable block as obstacle so the pathfinder routes around it
                layerObstacles.add(numbered.get(targetIdx));
                targetIdx++;
            }

            if (segment == null) {
                // No more reachable blocks in this layer
                break;
            }

            // Append segment (skip first element to avoid duplicate)
            layerPath.addAll(segment.subList(1, segment.size()));
            curIdx = targetIdx;
        }

        // 3. Append to global path
        if (path.isEmpty()) {
            path.addAll(layerPath);
        } else {
            // path already contains prevEnd from inter-layer link, so append remaining
            // Skip first element of layerPath if it equals the last element of path
            if (!path.isEmpty() && path.get(path.size() - 1).equals(layerPath.get(0))) {
                path.addAll(layerPath.subList(1, layerPath.size()));
            } else {
                path.addAll(layerPath);
            }
        }

        // 4. Mark all numbered blocks as processed
        for (BlockPos np : numbered) {
            processed.add(np);
        }

        // Record this layer as the current
        currentLayerY = y;
    }

    /**
     * S-shaped scan within a layer at Y=y.
     * Returns the list of plowable blocks in scan order.
     */
    private List<BlockPos> sScanLayer(int y) {
        return scanLayerBlocks(y, true);
    }

    /**
     * Find the closest plowable block at Y=y to the reference position.
     */
    private BlockPos findClosestPlowable(BlockPos ref, int y) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (processed.contains(pos)) continue;
                if (!PlowableChecker.isPlowable(level, pos)) continue;
                double dist = pos.distSqr(ref);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos;
                }
            }
        }
        return best;
    }

}
