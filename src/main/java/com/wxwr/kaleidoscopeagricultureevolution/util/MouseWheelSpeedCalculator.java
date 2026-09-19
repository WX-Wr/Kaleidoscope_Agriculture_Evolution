package com.wxwr.kaleidoscopeagricultureevolution.util;

import net.minecraft.util.Mth;

/**
 * 鼠标滚轮驱动旋转速度的通用计算工具。
 *
 * <p>用于把滚轮输入转换成可复用的旋转速度，
 * 并提供阻尼衰减与角度插值辅助方法。
 */
public final class MouseWheelSpeedCalculator {

    private MouseWheelSpeedCalculator() {
    }

    /**
     * 将一次滚轮输入叠加到当前速度上。
     *
     * @param currentVelocity 当前速度
     * @param scrollDelta     鼠标滚轮增量
     * @param sensitivity     灵敏度系数
     * @param maxAbsVelocity  绝对值上限
     * @return 新速度
     */
    public static float addScrollImpulse(float currentVelocity, float scrollDelta,
                                         float sensitivity, float maxAbsVelocity) {
        float next = currentVelocity + scrollDelta * sensitivity;
        return Mth.clamp(next, -maxAbsVelocity, maxAbsVelocity);
    }

    /**
     * 将滚轮增量换算为原始角速度，单位为弧度/秒。
     *
     * @param scrollDelta       鼠标滚轮增量
     * @param deltaSeconds      本次输入与上次输入之间的秒数
     * @param maxAbsRawOmega    原始角速度绝对值上限
     * @return 限幅后的原始角速度
     */
    public static double rawOmegaFromScroll(double scrollDelta, double deltaSeconds, double maxAbsRawOmega) {
        double safeDeltaSeconds = Math.max(deltaSeconds, 1.0D / 60.0D);
        double rawOmega = scrollDelta / safeDeltaSeconds;
        return clamp(rawOmega, -maxAbsRawOmega, maxAbsRawOmega);
    }

    public static double rawOmegaFromScroll(double scrollDelta, double deltaSeconds,
                                            double wheelToOmegaFactor, double maxAbsRawOmega) {
        double safeDeltaSeconds = Math.max(deltaSeconds, 1.0D / 60.0D);
        double rawOmega = scrollDelta / safeDeltaSeconds * wheelToOmegaFactor;
        return clamp(rawOmega, -maxAbsRawOmega, maxAbsRawOmega);
    }

    /**
     * 指数平滑追踪目标角速度。
     */
    public static double smoothOmega(double currentOmega, double rawOmega, double smoothFactor) {
        double alpha = clamp(smoothFactor, 0.0D, 1.0D);
        return currentOmega + (rawOmega - currentOmega) * alpha;
    }

    /**
     * 对弧度/秒角速度做按秒指数衰减。
     *
     * @param omega        当前角速度，单位弧度/秒
     * @param dampingBase  60fps 基准下每帧保留比例，例如 0.92
     * @param deltaSeconds 时间步长，单位秒
     */
    public static double dampOmega(double omega, double dampingBase, double deltaSeconds) {
        double base = clamp(dampingBase, 0.0D, 1.0D);
        return omega * Math.pow(base, deltaSeconds * 60.0D);
    }

    /**
     * 以弧度/秒角速度推进角度，并返回角度制结果。
     */
    public static double integrateDegrees(double angleDegrees, double omegaRadiansPerSecond,
                                          double deltaSeconds, double multiplier) {
        double next = angleDegrees + Math.toDegrees(omegaRadiansPerSecond * multiplier * deltaSeconds);
        return wrapDegrees(next);
    }

    /**
     * 计算角度制渲染插值。
     */
    public static float renderDegrees(double angleDegrees, double omegaRadiansPerSecond,
                                      float partialTick, double multiplier) {
        double deltaSeconds = partialTick / 20.0D;
        return (float) wrapDegrees(angleDegrees
                + Math.toDegrees(omegaRadiansPerSecond * multiplier * deltaSeconds));
    }

    /**
     * 将速度向 0 衰减。
     *
     * @param velocity 当前速度
     * @param damping  阻尼系数，建议范围 {@code 0.0F ~ 1.0F}
     * @return 衰减后的速度
     */
    public static float applyDamping(float velocity, float damping) {
        return velocity * Mth.clamp(damping, 0.0F, 1.0F);
    }

    /**
     * 根据当前速度和帧间插值计算渲染角度。
     *
     * @param angle       当前累计角度
     * @param velocity    当前速度
     * @param partialTick 帧间插值
     * @return 用于渲染的角度
     */
    public static float getRenderAngle(float angle, float velocity, float partialTick) {
        return angle + velocity * partialTick;
    }

    /**
     * 判断速度是否足够接近 0。
     */
    public static boolean isStopped(float velocity) {
        return Math.abs(velocity) < 0.001F;
    }

    public static boolean isOmegaStopped(double omega) {
        return Math.abs(omega) < 0.05D;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrapDegrees(double angleDegrees) {
        double wrapped = angleDegrees % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }
}
