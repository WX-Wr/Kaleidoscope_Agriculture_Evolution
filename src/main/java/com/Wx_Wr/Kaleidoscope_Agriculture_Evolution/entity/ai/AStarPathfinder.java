package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * A* pathfinder for plow routing.
 *
 * <p>Three modes:
 * <ul>
 *   <li><b>2D Flat</b> — within a single Y layer, 4-directional movement.
 *       No vertical stepping. Used for S-scan connections within a layer.</li>
 *   <li><b>3D</b> — 6-directional movement across layers.
 *       Used for inter-layer bridging.</li>
 *   <li><b>2D With Step</b> — vanilla-style 8-directional movement with step-up/fall.
 *       Available as {@link #findPathWithStep} for general pathfinding.</li>
 * </ul>
 *
 * <p>All modes apply a turn penalty ({@link #TURN_PENALTY}) so the path
 * prefers continuing in the same direction rather than zigzagging.
 */
public class AStarPathfinder {

    private static final int MAX_ITERATIONS = 20000;

    /** Extra g-cost added when the movement direction changes.
     *  Breaks f-score ties in favour of straight lines. */
    private static final int TURN_PENALTY = 1;

    // --- 2D Flat (same Y, 4 cardinal, no vertical movement) ---

    private static final int[][] DIRS_2D = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /**
     * Find a strictly flat 2D path — all nodes stay at the same Y.
     */
    public static List<BlockPos> findPath2D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles, int y,
                                             int minX, int maxX, int minZ, int maxZ) {
        BlockPos flatStart = new BlockPos(start.getX(), y, start.getZ());
        BlockPos flatGoal  = new BlockPos(goal.getX(), y, goal.getZ());

        if (flatStart.equals(flatGoal)) return List.of(flatStart);

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
                if (!isPassableAt(level, neighbor, obstacles)) continue;

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

    // --- 2D With Step (vanilla-style, 8-dir, with jump/fall) ---

    private static final double STEP_HEIGHT = 1.125;
    private static final int MAX_FALL = 3;

    private static final int[][] DIAG_DIRS = {
            {-1, -1, -1, 0, 0, -1},
            { 1, -1,  1, 0, 0, -1},
            {-1,  1, -1, 0, 0,  1},
            { 1,  1,  1, 0, 0,  1}
    };

    /**
     * Find a 2D-ish path with vanilla-style step-up/down.
     */
    public static List<BlockPos> findPathWithStep(Level level, BlockPos start, BlockPos goal,
                                                   Set<BlockPos> obstacles,
                                                   int minX, int maxX, int minZ, int maxZ) {
        if (start.equals(goal)) return List.of(start);

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

            // Cardinal
            for (int[] d : DIRS_2D) {
                int nx = cur.getX() + d[0];
                int nz = cur.getZ() + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                expandNeighbor(level, cur, nx, nz, obstacles, closed, gScore, cameFrom, goal, open);
            }

            // Diagonal
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
        // 1. Same Y
        BlockPos sameY = new BlockPos(nx, cur.getY(), nz);
        if (canStandAt(level, sameY, obstacles)) {
            addNeighbor(cur, sameY, closed, gScore, cameFrom, goal, open);
            return;
        }

        // 2. Step up
        BlockPos upOne = new BlockPos(nx, cur.getY() + 1, nz);
        if (canStandAt(level, upOne, obstacles)) {
            double floorCur = getFloorY(level, cur);
            double floorUp = getFloorY(level, upOne);
            if (floorUp - floorCur <= STEP_HEIGHT) {
                addNeighbor(cur, upOne, closed, gScore, cameFrom, goal, open);
                return;
            }
        }

        // 3. Step down
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

    // --- 3D (6 cardinal directions) ---

    public static List<BlockPos> findPath3D(Level level, BlockPos start, BlockPos goal,
                                             Set<BlockPos> obstacles,
                                             int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (start.equals(goal)) return List.of(start);

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
                        if (obstacles.contains(neighbor)) continue;
                        if (PlowableChecker.isObstacle(level, neighbor, obstacles)) continue;

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

    // --- Shared helpers ---

    /**
     * Returns {@link #TURN_PENALTY} if moving from {@code cur} to {@code neighbor}
     * changes direction compared to the previous step, otherwise 0.
     * <p>
     * No penalty when {@code cur} has no parent (start node).
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

        // Same direction vector → no penalty
        if (prevDx == curDx && prevDy == curDy && prevDz == curDz) return 0;

        return TURN_PENALTY;
    }

    private static boolean isPassableAt(Level level, BlockPos pos, Set<BlockPos> obstacles) {
        if (obstacles.contains(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        return isPlant(state);
    }

    private static boolean isPlant(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.REPLACEABLE)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS)
                || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || state.is(net.minecraft.tags.BlockTags.CROPS);
    }

    /** Add neighbour with step-cost = 1 + turn penalty. */
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