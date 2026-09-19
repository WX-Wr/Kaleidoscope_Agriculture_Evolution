package com.wxwr.kaleidoscopeagricultureevolution.rope.math;

import net.minecraft.world.phys.Vec3;

/**
 * 用于绳子渲染的真实悬链线（双曲余弦）曲线计算。
 *
 * <p>真实悬挂的绳子在均匀重力下遵循
 * {@code y = a · cosh(x / a)}。我们将曲线居中，使两个
 * 端点位于 {@code start} 和 {@code end} 之间的直线上，
 * 中点在该直线下方下垂。
 *
 * <p>悬链线参数 <i>a</i> 由现有的 {@code sagFactor} 推导得出，
 * 使中点垂度约为 {@code sagFactor · horizontalDist}
 * （与默认值 0.12 对应的旧抛物线近似一致）。
 */
public class CatenaryMath {

    /**
     * 计算 {@code start} 和 {@code end} 之间悬链线曲线上的点。
     *
     * @param start     绳子起点（在耕牛身上）
     * @param end       绳子终点（在犁上）
     * @param sagFactor 控制绳子的垂度（0 = 直线；默认 0.12；越大垂度越深）
     * @param segments  分段数（返回的数组有 {@code segments + 1} 个点）
     * @return 悬链线曲线上均匀分布的点
     */
    public static Vec3[] computeCurvePoints(Vec3 start, Vec3 end, double sagFactor, int segments) {
        Vec3[] points = new Vec3[segments + 1];

        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double totalLength = Math.sqrt(horizontalDist * horizontalDist + dy * dy);

        if (totalLength < 0.001) {
            points[0] = start;
            for (int i = 1; i <= segments; i++) {
                points[i] = end;
            }
            return points;
        }

        // ---- 悬链线参数 ----------------------------------------------
        // 对于跨度为 L 的悬链线 y = a·cosh(x/a)，中点垂度为：
        //   sag = a · (cosh(L/(2a)) − 1)
        //
        // 对于较小的垂度（cosh ε ≈ 1 + ε²/2），可简化为 L²/(8a)，
        // 因此设置 a ≈ L / (8·sagFactor) 可获得与旧抛物线大致相同
        // 的视觉垂度。我们将 a 限制在合理的最小值，使 cosh 不会爆炸，
        // 即使对于极端大的 sagFactor 值也是如此。
        //
        // （sagFactor 在 ClientEventHandler 中硬编码为 0.12，但保留为
        // 参数以保持数学逻辑的自包含性。）

        double halfSpan = horizontalDist / 2.0;
        double a;
        if (sagFactor <= 0.0) {
            // 直线 — 不会用到 cosh
            a = Double.POSITIVE_INFINITY;
        } else {
            // a ≈ L / (8·sagFactor)；下限为 0.5 以保证 cosh 安全
            a = Math.max(horizontalDist / (8.0 * sagFactor), 0.5);
        }

        // cosh(L/(2a)) — 使两端点相对于直线弦 y=0 的常量。预先计算一次。
        double coshEndpoint = (a == Double.POSITIVE_INFINITY) ? 1.0 : Math.cosh(halfSpan / a);

        // ---- 生成点 -------------------------------------------------
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;          // [0 … 1] 沿弦
            double x = start.x + dx * t;
            double z = start.z + dz * t;
            double baseY = start.y + dy * t;           // 直线 Y

            double sag;
            if (a == Double.POSITIVE_INFINITY) {
                sag = 0.0;
            } else {
                // x' ∈ [−halfSpan … +halfSpan]，在中点为 0
                double xPrime = horizontalDist * (t - 0.5);
                // sag(x') = a · [cosh(halfSpan/a) − cosh(x'/a)]
                // 中点 (x'=0)：sag_max = a · [cosh(halfSpan/a) − 1]  → 向下垂
                // 端点 (x'=±halfSpan)：sag = 0  → 在弦上
                sag = a * (coshEndpoint - Math.cosh(xPrime / a));
            }

            double y = baseY - sag;
            points[i] = new Vec3(x, y, z);
        }

        return points;
    }
}