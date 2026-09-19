package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import net.minecraft.core.Direction;
import javax.annotation.Nullable;

/**
 * 轮廓线渲染的不可变参数集。
 *
 * <p>受 Create 模组的 {@code OutlineParams} 启发，将单次轮廓线调用的所有
 * 视觉属性捆绑到一个对象中，使渲染器方法保持可读性。
 *
 * @param color           ARGB 边框颜色（例如 {@code 0xFF6886C5}）
 * @param lineWidth       以世界单位为单位的边框粗细（默认 {@code 1/32f ≈ 0.03125}）
 * @param faceAlpha       六个半透明面的透明度（0 = 不渲染面）
 * @param highlightedFace 以双倍透明度渲染的面，或 {@code null}
 * @param disableCull     为 {@code true} 时即使面朝后也进行渲染
 * @param fadeLineWidth   为 {@code true} 时线宽随全局透明度缩放
 */
public record OutlineStyle(
        int color,
        float lineWidth,
        float faceAlpha,
        @Nullable Direction highlightedFace,
        boolean disableCull,
        boolean fadeLineWidth
) {

    // ---- 便捷工厂方法 --------------------------------------------

    /** 编辑预览用的线框 + 半透明面填充。 */
    public static final OutlineStyle PREVIEW =
            new OutlineStyle(0xFF_FFC800, 1 / 32f, 0.12f, null, false, false);

    /** 已保存区域的线框 + 半透明面填充。 */
    public static final OutlineStyle SAVED =
            new OutlineStyle(0xFF_FFC800, 1 / 24f, 0.12f, null, false, false);

    /** 方向选择时的工作区域边框（金色 + 半透明面）。 */
    public static final OutlineStyle WORK_AREA =
            new OutlineStyle(0xFF_FFC800, 1 / 32f, 0.12f, null, false, false);

    // ---- 派生辅助方法 --------------------------------------------------

    /** 红色分量 0..1。 */
    public float red()   { return ((color >> 16) & 0xFF) / 255f; }
    /** 绿色分量 0..1。 */
    public float green() { return ((color >> 8)  & 0xFF) / 255f; }
    /** 蓝色分量 0..1。 */
    public float blue()  { return (color         & 0xFF) / 255f; }
    /** 透明度分量 0..1。 */
    public float alpha() { return ((color >> 24) & 0xFF) / 255f; }

    /**
     * 返回此样式的副本，仅更改高亮面。
     */
    public OutlineStyle withHighlightedFace(@Nullable Direction face) {
        return new OutlineStyle(color, lineWidth, faceAlpha, face, disableCull, fadeLineWidth);
    }

    /**
     * 返回此样式的副本，仅更改颜色。
     */
    public OutlineStyle withColor(int newColor) {
        return new OutlineStyle(newColor, lineWidth, faceAlpha, highlightedFace, disableCull, fadeLineWidth);
    }

    /**
     * 返回此样式的副本，仅更改面透明度。
     */
    public OutlineStyle withFaceAlpha(float newFaceAlpha) {
        return new OutlineStyle(color, lineWidth, newFaceAlpha, highlightedFace, disableCull, fadeLineWidth);
    }
}