package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 一个在 XZ 平面上具有椭圆投影的区域，沿垂直方向
 * 从 {@code minY} 拉伸到 {@code maxY}（含）。
 *
 * <p>位置 {@code (x, y, z)} 在区域内当且仅当：
 * <pre>
 *   ((x - cx) / rx)² + ((z - cz) / rz)² ≤ 1   且   minY ≤ y ≤ maxY
 * </pre>
 *
 * <p>在开始操作之前，使用 {@link #checkAreaCap()} 来强制执行
 * {@value FlatRegion#MAX_AREA} 方块的农田限制。
 */
public class CircleRegion implements FlatRegion {

    private final BlockPos center;
    private final double radiusX;
    private final double radiusZ;
    private final int minY;
    private final int maxY;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int area;
    private final int volume;

    /**
     * 创建一个圆形区域（X 和 Z 半径相等）。
     */
    public CircleRegion(BlockPos center, double radius, int minY, int maxY) {
        this(center, radius, radius, minY, maxY);
    }

    /**
     * 创建一个椭圆区域。
     *
     * @param center  XZ 平面上的中心位置
     * @param radiusX X 方向的半跨度
     * @param radiusZ Z 方向的半跨度
     * @param minY    Y 轴下界（含）
     * @param maxY    Y 轴上界（含）
     */
    public CircleRegion(BlockPos center, double radiusX, double radiusZ, int minY, int maxY) {
        if (radiusX < 0 || radiusZ < 0) {
            throw new IllegalArgumentException("半径必须为非负数");
        }
        this.center = center;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);

        int cx = center.getX();
        int cz = center.getZ();
        this.minX = (int) Math.floor(cx - radiusX);
        this.maxX = (int) Math.ceil(cx + radiusX);
        this.minZ = (int) Math.floor(cz - radiusZ);
        this.maxZ = (int) Math.ceil(cz + radiusZ);

        // 计算椭圆内部的格点数量
        this.area = countLatticePoints();
        int height = this.maxY - this.minY + 1;

        long vol = (long) this.area * height;
        if (vol > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                    "CircleRegion 体积 " + vol + " 超过了 Integer.MAX_VALUE"
            );
        }
        this.volume = (int) vol;
    }

    /**
     * 统计满足椭圆方程的整数格点 {@code (x,z)} 数量。
     * 遍历边界框——对于 ≤300 方块的上限来说效率足够。
     */
    private int countLatticePoints() {
        double cx = center.getX();
        double cz = center.getZ();
        double rx2 = radiusX * radiusX;
        double rz2 = radiusZ * radiusZ;

        // 处理退化（零半径）椭圆的情况
        if (rx2 == 0 && rz2 == 0) {
            return 1; // 仅中心点
        }
        if (rx2 == 0) {
            // 垂直线段
            int count = 0;
            for (int z = minZ; z <= maxZ; z++) {
                double nz = (z - cz) / radiusZ;
                if (nz * nz <= 1) count++;
            }
            return count;
        }
        if (rz2 == 0) {
            // 水平线段
            int count = 0;
            for (int x = minX; x <= maxX; x++) {
                double nx = (x - cx) / radiusX;
                if (nx * nx <= 1) count++;
            }
            return count;
        }

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            double nx = (x - cx) / radiusX;
            double nx2 = nx * nx;
            if (nx2 > 1) continue; // 超出 X 范围

            for (int z = minZ; z <= maxZ; z++) {
                double nz = (z - cz) / radiusZ;
                if (nx2 + nz * nz <= 1) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---- Region / FlatRegion ------------------------------------------------

    @Override
    public boolean contains(BlockPos pos) {
        if (pos.getY() < minY || pos.getY() > maxY) return false;

        double nx = (pos.getX() - center.getX()) / radiusX;
        double nz = (pos.getZ() - center.getZ()) / radiusZ;
        return nx * nx + nz * nz <= 1;
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

    // ---- 访问器 ----------------------------------------------------------

    public BlockPos getCenterPos() {
        return center;
    }

    public double getRadiusX() {
        return radiusX;
    }

    public double getRadiusZ() {
        return radiusZ;
    }

    // ---- 扩展 / 收缩 --------------------------------------------------

    /**
     * 返回一个新的 CircleRegion，两个半径均增加 {@code amount}，
     * Y 范围也相应扩展。
     *
     * <p>传入负数则缩小半径。半径被限制为不低于零。
     */
    public CircleRegion expand(int amount) {
        return new CircleRegion(
                center,
                Math.max(0, radiusX + amount),
                Math.max(0, radiusZ + amount),
                minY - amount,
                maxY + amount
        );
    }

    /**
     * 返回一个新的 CircleRegion，两个半径均减少 {@code amount}。
     * 等价于 {@code expand(-amount)}。
     */
    public CircleRegion contract(int amount) {
        return expand(-amount);
    }

    // ---- 迭代器 -----------------------------------------------------------

    @Override
    public Iterator<BlockPos> iterator() {
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

                while (y <= maxY) {
                    while (z <= maxZ) {
                        while (x <= maxX) {
                            int cx = x;
                            int cz = z;
                            x++;
                            if (contains2D(cx, cz)) {
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

            private boolean contains2D(int bx, int bz) {
                double nx = (bx - center.getX()) / radiusX;
                double nz = (bz - center.getZ()) / radiusZ;
                return nx * nx + nz * nz <= 1;
            }
        };
    }
}
