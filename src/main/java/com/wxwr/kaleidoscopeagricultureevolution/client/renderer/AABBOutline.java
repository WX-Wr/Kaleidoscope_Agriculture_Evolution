package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * 单个动画轴对齐包围盒轮廓线。
 *
 * <p>受 Create 模组的 {@code ChasingAABBOutline} 启发。双层插值
 * 产生平滑的指数衰减追踪效果：
 * <ol>
 *   <li><b>逻辑刻</b> — {@link #tick()} 将 {@code currentBB}
 *       向 {@code targetBB} 移动剩余距离的 20%
 *       （针对渲染帧率调优）。</li>
 *   <li><b>渲染帧</b> — {@link #render(PoseStack, Camera, float, float)}
 *       在 {@code prevBB} 和 {@code currentBB} 之间使用
 *       {@code partialTick} 进行线性插值，实现流畅的 60+ fps 显示。</li>
 * </ol>
 */
public class AABBOutline {

    @Nullable private AABB prevBB;
    @Nullable private AABB currentBB;
    @Nullable private AABB targetBB;

    private OutlineStyle style;

    public AABBOutline(OutlineStyle style) {
        this.style = style;
    }

    // ========================================================================
    //  公共 API
    // ========================================================================

    /** 设置新的目标 AABB。轮廓线将平滑追踪到该目标。 */
    public void setTarget(@Nullable AABB target) {
        this.targetBB = target;
    }

    /**
     * 立即跳转到目标 AABB（无插值）。用于首次出现的帧，
     * 避免轮廓线从旧位置滑入。
     */
    public void jumpTo(@Nullable AABB target) {
        this.targetBB = target;
        this.prevBB = target;
        this.currentBB = target;
    }

    /** 更新视觉样式（颜色、线宽、面透明度等）。 */
    public void setStyle(OutlineStyle style) {
        this.style = style;
    }

    public OutlineStyle getStyle() {
        return style;
    }

    @Nullable
    public AABB getTargetBB() {
        return targetBB;
    }

    // ========================================================================
    //  刻更新 — 每帧推进逻辑
    // ========================================================================

    /**
     * 将 {@code currentBB} 向 {@code targetBB} 移动一半距离。
     *
     * <p>每渲染帧调用（约 60 fps）。20% 指数衰减在约 15 帧（约 0.25 秒）
     * 内达到目标的约 97%。
     */
    public void tick() {
        if (targetBB == null) return;

        // 首帧：直接定位（避免从原点滑入）
        if (currentBB == null) {
            prevBB = targetBB;
            currentBB = targetBB;
            return;
        }

        prevBB = currentBB;
        currentBB = interpolateAABB(currentBB, targetBB, 0.05f);
    }

    // ========================================================================
    //  渲染 — 使用部分刻插值绘制
    // ========================================================================

    /**
     * 渲染此轮廓线。
     *
     * @param fadeAlpha 全局透明度系数（1 = 完全不透明，
     *                  0 = 不可见）。在样式颜色透明度和面透明度之上
     *                  进行乘法叠加。
     */
    public void render(PoseStack poseStack, Camera camera, float partialTick, float fadeAlpha) {
        if (fadeAlpha < 0.01f) return;

        // 确定渲染时的 AABB
        AABB renderBB;
        if (currentBB == null) {
            renderBB = targetBB;
        } else if (prevBB != null) {
            renderBB = interpolateAABB(prevBB, currentBB, partialTick);
        } else {
            renderBB = currentBB;
        }

        if (renderBB == null) return;

        // 将样式透明度与淡出透明度混合
        float faceAlpha = style.faceAlpha() * fadeAlpha;
        float edgeAlpha = style.alpha() * fadeAlpha;
        int fadedColor = packColor(
                style.red(), style.green(), style.blue(), edgeAlpha);

        OutlineStyle fadedStyle = new OutlineStyle(
                fadedColor, style.lineWidth(), faceAlpha,
                style.highlightedFace(), style.disableCull(), style.fadeLineWidth());

        // 使用浮点坐标变体以保留亚方块精度的追踪移动
        OutlineRenderer.renderCuboidFloat(poseStack, camera,
                (float) renderBB.minX, (float) renderBB.minY, (float) renderBB.minZ,
                (float) renderBB.maxX, (float) renderBB.maxY, (float) renderBB.maxZ,
                fadedStyle);
    }

    // ========================================================================
    //  内部辅助方法
    // ========================================================================

    private static AABB interpolateAABB(AABB a, AABB b, float t) {
        return new AABB(
                Mth.lerp(t, a.minX, b.minX),
                Mth.lerp(t, a.minY, b.minY),
                Mth.lerp(t, a.minZ, b.minZ),
                Mth.lerp(t, a.maxX, b.maxX),
                Mth.lerp(t, a.maxY, b.maxY),
                Mth.lerp(t, a.maxZ, b.maxZ));
    }

    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (Mth.clamp(r, 0, 1) * 255);
        int ig = (int) (Mth.clamp(g, 0, 1) * 255);
        int ib = (int) (Mth.clamp(b, 0, 1) * 255);
        int ia = (int) (Mth.clamp(a, 0, 1) * 255);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }
}