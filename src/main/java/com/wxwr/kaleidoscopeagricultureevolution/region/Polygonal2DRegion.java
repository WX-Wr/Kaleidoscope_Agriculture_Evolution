package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 由 XZ 平面上的二维多边形定义的区域，沿垂直方向
 * 从 {@code minY} 拉伸到 {@code maxY}（含）。
 *
 * <p>多边形顶点以有序的 {@link BlockPos2D} 列表形式给出。
 * 边假定连接连续顶点；最后一个顶点连接回第一个。
 * 自相交的多边形会产生未定义的结果。
 *
 * <p>点包含性使用射线投射算法。体积由
 * 精确的格点数量通过<b>皮克定理</b>推导得出。
 */
public class Polygonal2DRegion implements FlatRegion {

    private final List<BlockPos2D> points;
    private final int minY;
    private final int maxY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int area;   // XZ 覆盖范围内的精确格点数量
    private final int volume;

    /**
     * 创建一个多边形区域。
     *
     * @param points 按顺序排列的多边形顶点（有效多边形至少需要 3 个）
     * @param minY   Y 轴下界（含）
     * @param maxY   Y 轴上界（含）
     */
    public Polygonal2DRegion(List<BlockPos2D> points, int minY, int maxY) {
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        int height = this.maxY - this.minY + 1;

        if (points.size() < 3) {
            // 退化情况——零面积多边形
            this.minX = this.maxX = this.minZ = this.maxZ = 0;
            this.area = 0;
            this.volume = 0;
        } else {
            int mnX = Integer.MAX_VALUE;
            int mxX = Integer.MIN_VALUE;
            int mnZ = Integer.MAX_VALUE;
            int mxZ = Integer.MIN_VALUE;
            for (BlockPos2D pt : points) {
                if (pt.x() < mnX) mnX = pt.x();
                if (pt.x() > mxX) mxX = pt.x();
                if (pt.z() < mnZ) mnZ = pt.z();
                if (pt.z() > mxZ) mxZ = pt.z();
            }
            this.minX = mnX;
            this.maxX = mxX;
            this.minZ = mnZ;
            this.maxZ = mxZ;

            this.area = computeLatticePoints(points);

            long vol = (long) this.area * height;
            if (vol > Integer.MAX_VALUE) {
                throw new ArithmeticException(
                        "Polygonal2DRegion 体积 " + vol + " 超过了 Integer.MAX_VALUE"
                );
            }
            this.volume = (int) vol;
        }
    }

    // ---- 皮克定理 -----------------------------------------------------

    /**
     * 使用<b>皮克定理</b>计算多边形内部和边界上的
     * 整数格点精确数量：
     *
     * <pre>
     *   面积 = I + B/2 - 1
     *   →  总数 = I + B = (2·面积 + B) / 2 + 1
     * </pre>
     *
     * 其中"面积"是连续多边形的面积，B 是
     * 恰好位于边界上的格点数量。
     */
    private static int computeLatticePoints(List<BlockPos2D> pts) {
        int n = pts.size();

        // 1. 有符号面积的两倍（鞋带公式）
        long twiceArea = 0;
        for (int i = 0; i < n; i++) {
            BlockPos2D a = pts.get(i);
            BlockPos2D b = pts.get((i + 1) % n);
            twiceArea += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        twiceArea = Math.abs(twiceArea);

        // 2. 边界格点（边增量的 gcd）
        long boundary = 0;
        for (int i = 0; i < n; i++) {
            BlockPos2D a = pts.get(i);
            BlockPos2D b = pts.get((i + 1) % n);
            boundary += BlockPos2D.gcd(b.x() - a.x(), b.z() - a.z());
        }

        // 3. 皮克定理：I + B = (2A + B) / 2 + 1
        long total = (twiceArea + boundary) / 2 + 1;

        if (total > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                    "Polygonal2DRegion 格点数量 " + total + " 超过了 Integer.MAX_VALUE"
            );
        }
        return (int) total;
    }

    // ---- 多边形辅助方法 ----------------------------------------------------

    /**
     * 射线投射法点包含性测试（XZ 平面）。
     *
     * <p>从测试点向 +X 方向发出一条水平射线。与射线相交的
     * 多边形边的数量决定了包含性：奇数 → 内部，
     * 偶数 → 外部。恰好位于边或顶点上的点被视为
     * 内部。
     */
    public static boolean contains2D(List<BlockPos2D> points, int x, int z) {
        int n = points.size();
        if (n < 3) return false;

        boolean inside = false;
        for (int i = 0; i < n; i++) {
            BlockPos2D a = points.get(i);
            BlockPos2D b = points.get((i + 1) % n);

            // 检查点是否恰好位于顶点上
            if ((a.x() == x && a.z() == z) || (b.x() == x && b.z() == z)) {
                return true;
            }

            // 边是否跨越了 testZ 处的水平射线？
            if ((a.z() > z) != (b.z() > z)) {
                // 计算边与射线相交的 X 坐标
                double intersectionX = (double) (b.x() - a.x()) * (z - a.z())
                                     / (b.z() - a.z()) + a.x();

                // 如果交点恰好位于 testX，则点在边上
                if (Math.abs(intersectionX - x) < 1e-10) {
                    return true;
                }

                if (intersectionX > x) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    // ---- Region / FlatRegion ------------------------------------------------

    @Override
    public boolean contains(BlockPos pos) {
        // 快速 Y 轴检查
        if (pos.getY() < minY || pos.getY() > maxY) return false;
        // 快速边界框排除
        if (pos.getX() < minX || pos.getX() > maxX
         || pos.getZ() < minZ || pos.getZ() > maxZ) return false;

        return contains2D(points, pos.getX(), pos.getZ());
    }

    @Override
    public BlockPos getMinimumPoint() {
        return new BlockPos(minX, minY, minZ);
    }

    @Override
    public BlockPos getMaximumPoint() {
        return new BlockPos(maxX, maxY, maxZ);
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public int getArea() {
        return area;
    }

    // ---- 扩展 / 收缩 --------------------------------------------------

    /**
     * 返回扩展后的边界框的 {@link CuboidRegion}。
     *
     * <p>原始多边形形状<em>不</em>会保留——结果是
     * 在所有六个轴方向上扩大了 {@code amount} 个方块的
     * 轴对齐边界框。这是保守（安全）的扩展：
     * 原始多边形内的每个位置也都在结果内。
     *
     * <p>传入负数则收缩边界框。
     */
    public CuboidRegion expand(int amount) {
        return new CuboidRegion(
                new BlockPos(minX - amount, minY - amount, minZ - amount),
                new BlockPos(maxX + amount, maxY + amount, maxZ + amount)
        );
    }

    /**
     * 返回收缩后的边界框的 {@link CuboidRegion}。
     * 等价于 {@code expand(-amount)}。
     */
    public CuboidRegion contract(int amount) {
        return expand(-amount);
    }

    // ---- 迭代器 -----------------------------------------------------------

    @Override
    public Iterator<BlockPos> iterator() {
        if (points.size() < 3) return Collections.emptyIterator();

        return new Iterator<>() {
            private int x = minX;
            private int y = minY;
            private int z = minZ;

            @Override
            public boolean hasNext() {
                return y <= maxY;
            }

            @Override
            public BlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();

                // 前进到下一个包含的位置
                while (y <= maxY) {
                    while (z <= maxZ) {
                        while (x <= maxX) {
                            int cx = x;
                            int cz = z;
                            x++;
                            if (contains2D(points, cx, cz)) {
                                return new BlockPos(cx, y, cz);
                            }
                        }
                        x = minX;
                        z++;
                    }
                    z = minZ;
                    y++;
                }
                throw new NoSuchElementException();
            }
        };
    }

    // ---- 访问器 ----------------------------------------------------------

    /** 返回多边形顶点的不可修改视图。 */
    public List<BlockPos2D> getPoints() {
        return points;
    }

    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    /** 顶点数量。 */
    public int vertexCount() {
        return points.size();
    }
}
