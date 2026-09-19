package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

/**
 * 世界中的三维方块位置区域。
 *
 * <p>提供边界查询、包含性检查、体积计算，
 * 以及对所有包含位置的迭代。实现类必须是
 * 不可变的，或至少在读取操作上是线程安全的。
 *
 * <p>具体形状：
 * <ul>
 *   <li>{@link CuboidRegion} — 轴对齐矩形</li>
 *   <li>{@link Polygonal2DRegion} — 二维多边形垂直拉伸</li>
 *   <li>{@link CircleRegion} — 椭圆投影垂直拉伸</li>
 * </ul>
 */
public interface Region extends Iterable<BlockPos> {

    /**
     * 如果给定位置在此区域内，则返回 true。
     */
    boolean contains(BlockPos position);

    /**
     * 返回最小角点（所有坐标均为下界）。
     */
    BlockPos getMinimumPoint();

    /**
     * 返回最大角点（所有坐标均为上界）。
     */
    BlockPos getMaximumPoint();

    /**
     * 返回此区域中的方块位置总数。
     */
    int getVolume();

    // ---- 派生查询（默认实现） ----

    /**
     * 返回边界框的中心位置（向零舍入）。
     */
    default BlockPos getCenter() {
        BlockPos min = getMinimumPoint();
        BlockPos max = getMaximumPoint();
        return new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
    }

    /** 返回 X 轴方向的跨度（以方块计）。 */
    default int getWidth() {
        return getMaximumPoint().getX() - getMinimumPoint().getX() + 1;
    }

    /** 返回 Y 轴方向的跨度（以方块计）。 */
    default int getHeight() {
        return getMaximumPoint().getY() - getMinimumPoint().getY() + 1;
    }

    /** 返回 Z 轴方向的跨度（以方块计）。 */
    default int getLength() {
        return getMaximumPoint().getZ() - getMinimumPoint().getZ() + 1;
    }
}
