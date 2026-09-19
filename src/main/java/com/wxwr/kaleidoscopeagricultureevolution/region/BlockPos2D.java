package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

/**
 * XZ 平面上的不可变二维位置，用于多边形顶点。
 *
 * @param x X 坐标
 * @param z Z 坐标
 */
public record BlockPos2D(int x, int z) {

    /** 从三维 BlockPos 创建，丢弃 Y 分量。 */
    public static BlockPos2D fromBlockPos(BlockPos pos) {
        return new BlockPos2D(pos.getX(), pos.getZ());
    }

    /** 返回按给定偏移量平移后的新 BlockPos2D。 */
    public BlockPos2D offset(int dx, int dz) {
        return new BlockPos2D(x + dx, z + dz);
    }

    /** 返回到另一个 BlockPos2D 的欧几里得距离的平方。 */
    public long distSqr(BlockPos2D other) {
        long dx = (long) x - other.x;
        long dz = (long) z - other.z;
        return dx * dx + dz * dz;
    }

    /**
     * 最大公约数（欧几里得算法）。
     * 由 {@link Polygonal2DRegion} 用于皮克定理的边界点计数。
     */
    public static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }
}
