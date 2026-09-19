package com.wxwr.kaleidoscopeagricultureevolution.region;

/**
 * 一个具有均匀二维覆盖范围的区域，沿垂直方向拉伸。
 *
 * <p>所有 XZ 列共享相同的形状；该区域是二维覆盖范围
 * 与 Y 区间 [{@link #getMinY()}, {@link #getMaxY()}] 的笛卡尔积。
 *
 * <p>实现类：{@link CuboidRegion}、{@link Polygonal2DRegion}、
 * {@link CircleRegion}。
 */
public interface FlatRegion extends Region {

    /** XZ 平面上的最大农田面积（以方块计）。 */
    int MAX_AREA = 1000;

    /** Y 轴下界（含）。 */
    int getMinY();

    /** Y 轴上界（含）。 */
    int getMaxY();

    /**
     * XZ 覆盖范围内的方块位置数量。
     *
     * <p>对于 {@link CuboidRegion}，此为 {@code width × length}。
     * 对于 {@link Polygonal2DRegion}，此为通过皮克定理计算的
     * 精确格点数量。
     * 对于 {@link CircleRegion}，此为椭圆内部
     * 整数格点的数量。
     */
    int getArea();

    /** 垂直方向的跨度（以方块计）。 */
    @Override
    default int getHeight() {
        return getMaxY() - getMinY() + 1;
    }

    /**
     * 验证 XZ 面积是否不超过 {@value #MAX_AREA} 方块。
     *
     * @throws IllegalArgumentException 如果面积超过上限
     */
    default void checkAreaCap() {
        int area = getArea();
        if (area > MAX_AREA) {
            throw new IllegalArgumentException(
                    "农田面积 " + area + " 超过了最大限制 " + MAX_AREA + " 方块"
            );
        }
    }
}
