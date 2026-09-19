package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import com.wxwr.kaleidoscopeagricultureevolution.region.Region;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * A* 寻路器，用于犁地路线规划。
 *
 * <p>三种模式：
 * <ul>
 *   <li><b>2D 平面</b> — 在单个 Y 层内，4 方向移动。
 *       无垂直跨越。用于层内 S 形扫描连接。</li>
 *   <li><b>3D</b> — 跨层 6 方向移动。
 *       用于层间桥接。</li>
 *   <li><b>2D 带跨越</b> — 原版风格 8 方向移动，带上下台阶。
 *       可通过 {@link #findPathWithStep} 用于通用寻路。</li>
 * </ul>
 *
 * <p>所有模式均施加转向惩罚（{@link #TURN_PENALTY}），使路径倾向于
 * 保持同一方向前进，而非锯齿形移动。
 */
public class AStarPathfinder {

    private static final int MAX_ITERATIONS = 20000;

    /** 移动方向改变时额外增加的 g 开销。
     *  用于打破 f-score 平局，优先选择直线移动。 */
    private static final int TURN_PENALTY = 1;

    // --- 2D 平面（同 Y，4 个基本方向，无垂直移动） ---

    private static final int[][] DIRS_2D = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * 查找严格平面的 2D 路径 — 所有节点保持在相同 Y 坐标。
     */
    public static List<BlockPos> findPath2D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles, int y, Region region) {
        return findPath2D(level, start, goal, obstacles, y, region,
                WorkAction.TILL, defaultPathRule());
    }

    public static List<BlockPos> findPath2D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles, int y, Region region,
                                             WorkAction action, WorkPathRule pathRule) {
        BlockPos flatStart = new BlockPos(start.getX(), y, start.getZ());
        BlockPos flatGoal  = new BlockPos(goal.getX(), y, goal.getZ());

        if (flatStart.equals(flatGoal)) return List.of(flatStart);

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScore.put(flatStart, 0);
        open.add(new Node(flatStart, heuristic(flatStart, flatGoal)));

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node current = open.poll();
            BlockPos cur = current.pos;

            if (cur.equals(flatGoal)) {
                return reconstructPath(cameFrom, cur);
            }

            if (!closed.add(cur)) continue;

            for (int[] d : DIRS_2D) {
                int nx = cur.getX() + d[0];
                int nz = cur.getZ() + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;

                BlockPos neighbor = new BlockPos(nx, y, nz);
                if (closed.contains(neighbor)) continue;
                if (obstacles.contains(neighbor)) continue;
                if (!isPassableAt(level, neighbor, obstacles, pathRule)) continue;

                int stepCost = 1 + turnPenalty(cameFrom, cur, neighbor);
                int newG = gScore.get(cur) + stepCost;
                if (newG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    gScore.put(neighbor, newG);
                    cameFrom.put(neighbor, cur);
                    int f = newG + heuristic(neighbor, flatGoal);
                    open.add(new Node(neighbor, f));
                }
            }
        }

        return null;
    }

    // --- 2D 带台阶（原版风格，8 方向，可跳跃/下落） ---

    private static final double STEP_HEIGHT = 1.125;
    private static final int MAX_FALL = 3;

    private static final int[][] DIAG_DIRS = {
            {-1, -1, -1, 0, 0, -1},
            { 1, -1,  1, 0, 0, -1},
            {-1,  1, -1, 0, 0,  1},
            { 1,  1,  1, 0, 0,  1}
    };

    /**
     * 查找带原版风格上下台阶的类 2D 路径。
     *
     * <p><b>状态：待定。</b>目前<b>没有调用点</b>——犁地只用 4 向（{@code findPath2D}）
     * 与 6 向（{@code findPath3D}）版本。另外本方法面向 8 向移动，但启发式用的是曼哈顿
     * 距离（不可采纳，结果非最优），启用前需改为对角距离。
     */
    public static List<BlockPos> findPathWithStep(Level level, BlockPos start, BlockPos goal,
                                                   Set<BlockPos> obstacles, Region region) {
        if (start.equals(goal)) return List.of(start);

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScore.put(start, 0);
        open.add(new Node(start, heuristic(start, goal)));

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node current = open.poll();
            BlockPos cur = current.pos;

            if (cur.equals(goal)) {
                return reconstructPath(cameFrom, cur);
            }

            if (!closed.add(cur)) continue;

            // 正方向（东西南北）
            for (int[] d : DIRS_2D) {
                int nx = cur.getX() + d[0];
                int nz = cur.getZ() + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                expandNeighbor(level, cur, nx, nz, obstacles, closed, gScore, cameFrom, goal, open);
            }

            // 对角线方向
            for (int[] dd : DIAG_DIRS) {
                int nx = cur.getX() + dd[0];
                int nz = cur.getZ() + dd[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                BlockPos card1 = new BlockPos(cur.getX() + dd[2], cur.getY(), cur.getZ() + dd[3]);
                BlockPos card2 = new BlockPos(cur.getX() + dd[4], cur.getY(), cur.getZ() + dd[5]);
                if (!isPassableAt(level, card1, obstacles) || !isPassableAt(level, card2, obstacles))
                    continue;
                expandNeighbor(level, cur, nx, nz, obstacles, closed, gScore, cameFrom, goal, open);
            }
        }

        return null;
    }

    private static void expandNeighbor(Level level, BlockPos cur, int nx, int nz,
                                        Set<BlockPos> obstacles, Set<BlockPos> closed,
                                        Map<BlockPos, Integer> gScore, Map<BlockPos, BlockPos> cameFrom,
                                        BlockPos goal, PriorityQueue<Node> open) {
        // 1. 同 Y 坐标
        BlockPos sameY = new BlockPos(nx, cur.getY(), nz);
        if (canStandAt(level, sameY, obstacles)) {
            addNeighbor(cur, sameY, closed, gScore, cameFrom, goal, open);
            return;
        }

        // 2. 向上跨步
        BlockPos upOne = new BlockPos(nx, cur.getY() + 1, nz);
        if (canStandAt(level, upOne, obstacles)) {
            double floorCur = getFloorY(level, cur);
            double floorUp = getFloorY(level, upOne);
            if (floorUp - floorCur <= STEP_HEIGHT) {
                addNeighbor(cur, upOne, closed, gScore, cameFrom, goal, open);
                return;
            }
        }

        // 3. 向下跨步
        if (level.getBlockState(sameY).isAir() || isPlant(level.getBlockState(sameY))) {
            for (int fall = 1; fall <= MAX_FALL; fall++) {
                BlockPos down = new BlockPos(nx, cur.getY() - fall, nz);
                if (canStandAt(level, down, obstacles)) {
                    addNeighbor(cur, down, closed, gScore, cameFrom, goal, open);
                    return;
                }
                BlockState at = level.getBlockState(down);
                if (!at.isAir() && !isPlant(at)) break;
            }
        }
    }

    private static boolean canStandAt(Level level, BlockPos pos, Set<BlockPos> obstacles) {
        if (obstacles.contains(pos)) return false;
        if (!isPassableAt(level, pos, obstacles)) return false;
        BlockState below = level.getBlockState(pos.below());
        return !below.getCollisionShape(level, pos.below()).isEmpty();
    }

    private static double getFloorY(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (state.isAir()) return pos.getY();
        var shape = state.getCollisionShape(level, below);
        if (shape.isEmpty()) return below.getY();
        return below.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
    }

    // --- 3D（6 个基本方向） ---

    public static List<BlockPos> findPath3D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles, Region region) {
        return findPath3D(level, start, goal, obstacles, region,
                WorkAction.TILL, defaultPathRule());
    }

    public static List<BlockPos> findPath3D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles, Region region,
                                             WorkAction action, WorkPathRule pathRule) {
        if (start.equals(goal)) return List.of(start);

        int minX = region.getMinimumPoint().getX();
        int maxX = region.getMaximumPoint().getX();
        int minY = region.getMinimumPoint().getY();
        int maxY = region.getMaximumPoint().getY();
        int minZ = region.getMinimumPoint().getZ();
        int maxZ = region.getMaximumPoint().getZ();

        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        gScore.put(start, 0);
        open.add(new Node(start, heuristic(start, goal)));

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < MAX_ITERATIONS) {
            Node current = open.poll();
            BlockPos cur = current.pos;

            if (cur.equals(goal)) {
                return reconstructPath(cameFrom, cur);
            }

            if (!closed.add(cur)) continue;

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0) != 1) continue;
                        int nx = cur.getX() + dx;
                        int ny = cur.getY() + dy;
                        int nz = cur.getZ() + dz;
                        if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) continue;
                        BlockPos neighbor = new BlockPos(nx, ny, nz);
                        if (closed.contains(neighbor)) continue;
                        if (PlowableChecker.isObstacle(level, neighbor, obstacles, action, pathRule)) continue;

                        int stepCost = 1 + turnPenalty(cameFrom, cur, neighbor);
                        int newG = gScore.get(cur) + stepCost;
                        if (newG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                            gScore.put(neighbor, newG);
                            cameFrom.put(neighbor, cur);
                            int f = newG + heuristic(neighbor, goal);
                            open.add(new Node(neighbor, f));
                        }
                    }
                }
            }
        }

        return null;
    }

    // --- 共享辅助方法 ---

    /**
     * 如果从 {@code cur} 移动到 {@code neighbor} 相较前一步改变了方向，
     * 则返回 {@link #TURN_PENALTY}，否则返回 0。
     * <p>
     * 当 {@code cur} 无父节点（起始节点）时不施加惩罚。
     */
    private static int turnPenalty(Map<BlockPos, BlockPos> cameFrom, BlockPos cur, BlockPos neighbor) {
        BlockPos prev = cameFrom.get(cur);
        if (prev == null) return 0;

        int prevDx = cur.getX() - prev.getX();
        int prevDy = cur.getY() - prev.getY();
        int prevDz = cur.getZ() - prev.getZ();

        int curDx = neighbor.getX() - cur.getX();
        int curDy = neighbor.getY() - cur.getY();
        int curDz = neighbor.getZ() - cur.getZ();

        // 相同方向向量 → 无惩罚
        if (prevDx == curDx && prevDy == curDy && prevDz == curDz) return 0;

        return TURN_PENALTY;
    }

    private static boolean isPassableAt(Level level, BlockPos pos, Set<BlockPos> obstacles) {
        if (obstacles.contains(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        return isPlant(state);
    }

    private static boolean isPassableAt(Level level, BlockPos pos, Set<BlockPos> obstacles,
                                        WorkPathRule pathRule) {
        if (obstacles.contains(pos)) return false;
        return PlowableChecker.isPathPassable(level, pos, pathRule);
    }

    private static WorkPathRule defaultPathRule() {
        return new WorkPathRule("dry_field", 0, false, 1, -1, 0, 0);
    }

    private static boolean isPlant(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.REPLACEABLE)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS)
                || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || state.is(net.minecraft.tags.BlockTags.CROPS);
    }

    /** 添加相邻节点，步进开销 = 1 + 转向惩罚。 */
    private static void addNeighbor(BlockPos cur, BlockPos neighbor,
                                     Set<BlockPos> closed, Map<BlockPos, Integer> gScore,
                                     Map<BlockPos, BlockPos> cameFrom,
                                     BlockPos goal, PriorityQueue<Node> open) {
        if (closed.contains(neighbor)) return;
        int stepCost = 1 + turnPenalty(cameFrom, cur, neighbor);
        int newG = gScore.get(cur) + stepCost;
        if (newG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
            gScore.put(neighbor, newG);
            cameFrom.put(neighbor, cur);
            int f = newG + heuristic(neighbor, goal);
            open.add(new Node(neighbor, f));
        }
    }

    private static int heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos current) {
        List<BlockPos> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private record Node(BlockPos pos, int f) implements Comparable<Node> {
        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.f, o.f);
        }
    }
}
