package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 对轴对齐矩形范围进行纯几何操作。
 *
 * <p>每个方法都是静态且无副作用的。范围以
 * {@code BlockPos[2]} 表示，其中元素 0 是最小角点，
 * 元素 1 是最大角点（含）。
 *
 * <p>这些操作为鞭子的矩形编辑模式提供底层支持，
 * 而不绑定到任何特定物品或 NBT 格式。
 */
public final class CuboidGeometry {

    private CuboidGeometry() {}

    // ========================================================================
    //  规范化
    // ========================================================================

    /**
     * 规范化两个角点，使得 {@code result[0]} 为最小点，
     * {@code result[1]} 为最大点。
     */
    public static BlockPos[] normalize(BlockPos a, BlockPos b) {
        return new BlockPos[]{
                new BlockPos(Math.min(a.getX(), b.getX()),
                             Math.min(a.getY(), b.getY()),
                             Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()),
                             Math.max(a.getY(), b.getY()),
                             Math.max(a.getZ(), b.getZ()))
        };
    }

    // ========================================================================
    //  交集
    // ========================================================================

    /**
     * 计算两个矩形范围的交集。
     *
     * @return 相交的范围，如果两个范围不重叠则返回 {@code null}。
     */
    public static BlockPos[] intersection(BlockPos[] a, BlockPos[] b) {
        BlockPos[] na = normalize(a[0], a[1]);
        BlockPos[] nb = normalize(b[0], b[1]);

        int ix1 = Math.max(na[0].getX(), nb[0].getX());
        int iy1 = Math.max(na[0].getY(), nb[0].getY());
        int iz1 = Math.max(na[0].getZ(), nb[0].getZ());
        int ix2 = Math.min(na[1].getX(), nb[1].getX());
        int iy2 = Math.min(na[1].getY(), nb[1].getY());
        int iz2 = Math.min(na[1].getZ(), nb[1].getZ());

        if (ix1 > ix2 || iy1 > iy2 || iz1 > iz2) return null;
        return new BlockPos[]{new BlockPos(ix1, iy1, iz1), new BlockPos(ix2, iy2, iz2)};
    }

    // ========================================================================
    //  差集
    // ========================================================================

    /**
     * 从较大范围中减去交集范围，返回最多 6 个
     * 互不重叠的矩形片段，它们共同表示
     * {@code range \ intersection}。
     *
     * <p>如果 {@code intersection} 实际上不是 {@code range} 的子集，
     * 结果在几何上仍然是正确的——片段会
     * 被裁剪到范围的边界内。
     */
    public static List<BlockPos[]> subtract(BlockPos[] range, BlockPos[] intersection) {
        List<BlockPos[]> result = new ArrayList<>();
        BlockPos[] nr = normalize(range[0], range[1]);
        BlockPos[] ni = normalize(intersection[0], intersection[1]);

        int ax1 = nr[0].getX(), ay1 = nr[0].getY(), az1 = nr[0].getZ();
        int ax2 = nr[1].getX(), ay2 = nr[1].getY(), az2 = nr[1].getZ();
        int ix1 = ni[0].getX(), iy1 = ni[0].getY(), iz1 = ni[0].getZ();
        int ix2 = ni[1].getX(), iy2 = ni[1].getY(), iz2 = ni[1].getZ();

        // X 轴切片
        if (ax1 < ix1)
            result.add(new BlockPos[]{new BlockPos(ax1, ay1, az1), new BlockPos(ix1 - 1, ay2, az2)});
        if (ax2 > ix2)
            result.add(new BlockPos[]{new BlockPos(ix2 + 1, ay1, az1), new BlockPos(ax2, ay2, az2)});

        int mx1 = Math.max(ax1, ix1), mx2 = Math.min(ax2, ix2);

        // Y 轴切片（约束到 X 重叠范围）
        if (ay1 < iy1)
            result.add(new BlockPos[]{new BlockPos(mx1, ay1, az1), new BlockPos(mx2, iy1 - 1, az2)});
        if (ay2 > iy2)
            result.add(new BlockPos[]{new BlockPos(mx1, iy2 + 1, az1), new BlockPos(mx2, ay2, az2)});

        int my1 = Math.max(ay1, iy1), my2 = Math.min(ay2, iy2);

        // Z 轴切片（约束到 X 和 Y 重叠范围）
        if (az1 < iz1)
            result.add(new BlockPos[]{new BlockPos(mx1, my1, az1), new BlockPos(mx2, my2, iz1 - 1)});
        if (az2 > iz2)
            result.add(new BlockPos[]{new BlockPos(mx1, my1, iz2 + 1), new BlockPos(mx2, my2, az2)});

        return result;
    }

    // ========================================================================
    //  合并
    // ========================================================================

    /**
     * 合并一个范围列表，使得没有两个范围重叠或共享内部面。
     * 这消除了当同一组中的两个矩形相交时出现的
     * 可见"接缝"边界。
     *
     * <p>算法（运行直到稳定）：
     * <ol>
     *   <li>贪心合并任意两个共享完整面且在其他两个轴上
     *       对齐的范围。</li>
     *   <li>对于剩余的重叠对，从一个范围中减去交集
     *       以消除重叠。</li>
     * </ol>
     */
    public static List<BlockPos[]> merge(List<BlockPos[]> ranges) {
        if (ranges.size() <= 1) return new ArrayList<>(ranges);
        List<BlockPos[]> working = new ArrayList<>(ranges);

        boolean changed;
        do {
            changed = false;

            // 阶段 1：合并共享完整面的范围
            for (int i = 0; i < working.size() && !changed; i++) {
                for (int j = i + 1; j < working.size() && !changed; j++) {
                    BlockPos[] merged = tryMergeTwo(working.get(i), working.get(j));
                    if (merged != null) {
                        working.remove(j);
                        working.remove(i);
                        working.add(merged);
                        changed = true;
                    }
                }
            }

            if (changed) continue;

            // 阶段 2：通过差集消除重叠
            for (int i = 0; i < working.size() && !changed; i++) {
                for (int j = i + 1; j < working.size() && !changed; j++) {
                    BlockPos[] isec = intersection(working.get(i), working.get(j));
                    if (isec != null) {
                        List<BlockPos[]> fragments = subtract(working.get(j), isec);
                        working.remove(j);
                        working.addAll(fragments);
                        changed = true;
                    }
                }
            }
        } while (changed);

        return working;
    }

    // ========================================================================
    //  尝试合并两个
    // ========================================================================

    /**
     * 尝试将两个范围合并为一个更大的轴对齐矩形。
     *
     * <p>当两个范围共享一个完整面时，它们可以被合并——
     * 即它们在两个轴上对齐，在第三个轴上相邻或重叠。
     * 这涵盖了：
     * <ul>
     *   <li>面对面接触的并排矩形</li>
     *   <li>重叠的矩形，它们一起构成一个完美的更大矩形</li>
     *   <li>一个矩形完全包含在另一个矩形内</li>
     * </ul>
     *
     * @return 合并后的范围，如果无法合并则返回 {@code null}
     */
    public static BlockPos[] tryMergeTwo(BlockPos[] a, BlockPos[] b) {
        BlockPos[] na = normalize(a[0], a[1]);
        BlockPos[] nb = normalize(b[0], b[1]);

        int ax1 = na[0].getX(), ay1 = na[0].getY(), az1 = na[0].getZ();
        int ax2 = na[1].getX(), ay2 = na[1].getY(), az2 = na[1].getZ();
        int bx1 = nb[0].getX(), by1 = nb[0].getY(), bz1 = nb[0].getZ();
        int bx2 = nb[1].getX(), by2 = nb[1].getY(), bz2 = nb[1].getZ();

        // X 轴合并：Y 和 Z 范围相同，X 轴相邻或重叠
        if (ay1 == by1 && ay2 == by2 && az1 == bz1 && az2 == bz2) {
            if (ax2 >= bx1 - 1 && ax1 <= bx2 + 1) {
                return new BlockPos[]{
                        new BlockPos(Math.min(ax1, bx1), ay1, az1),
                        new BlockPos(Math.max(ax2, bx2), ay2, az2)
                };
            }
        }

        // Y 轴合并：X 和 Z 范围相同，Y 轴相邻或重叠
        if (ax1 == bx1 && ax2 == bx2 && az1 == bz1 && az2 == bz2) {
            if (ay2 >= by1 - 1 && ay1 <= by2 + 1) {
                return new BlockPos[]{
                        new BlockPos(ax1, Math.min(ay1, by1), az1),
                        new BlockPos(ax2, Math.max(ay2, by2), az2)
                };
            }
        }

        // Z 轴合并：X 和 Y 范围相同，Z 轴相邻或重叠
        if (ax1 == bx1 && ax2 == bx2 && ay1 == by1 && ay2 == by2) {
            if (az2 >= bz1 - 1 && az1 <= bz2 + 1) {
                return new BlockPos[]{
                        new BlockPos(ax1, ay1, Math.min(az1, bz1)),
                        new BlockPos(ax2, ay2, Math.max(az2, bz2))
                };
            }
        }

        return null;
    }
}