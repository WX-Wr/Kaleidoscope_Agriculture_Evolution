package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 动画 AABB 轮廓线的单例生命周期管理器。
 *
 * <p>受 Create 模组的 {@code Outliner}（Catnip）启发。调用者使用不透明的
 * {@code Object} 键来跨帧标识其轮廓线。只要调用者每帧持续调用
 * {@link #chaseAABB(Object, AABB, OutlineStyle)}（或 {@link #showAABB}），
 * 轮廓线就会保持存在。一旦不再刷新，轮廓线将在 8 刻内淡出并被移除。
 *
 * <h3>用法</h3>
 * <pre>{@code
 *   // 在每帧更新中：
 *   Outliner.getInstance().chaseAABB(mySlot, currentBox, OutlineStyle.PREVIEW);
 *
 *   // 在世界渲染事件中（每帧一次）：
 *   Outliner.getInstance().tickOutlines();
 *   Outliner.getInstance().renderOutlines(poseStack, camera, partialTick);
 * }</pre>
 */
public final class Outliner {

    private static final Outliner INSTANCE = new Outliner();

    /** 淡出轮廓线完全消失所需的刻数。 */
    static final int FADE_TICKS = 8;

    private final Map<Object, OutlineEntry> outlines =
            Collections.synchronizedMap(new HashMap<>());

    private Outliner() {}

    public static Outliner getInstance() {
        return INSTANCE;
    }

    // ========================================================================
    //  公共 API
    // ========================================================================

    /**
     * 平滑追踪到给定的 AABB。
     *
     * <p>在轮廓线应当可见的期间，必须<b>每帧</b>调用。设置逻辑目标并将
     * 淡出计时器重置为 1（完全存活）。
     *
     * @param slot  不透明键（例如 {@code new Object()} 或字符串常量）
     * @param bb    世界空间中的期望目标 AABB（可为 null 以隐藏）
     * @param style 视觉参数
     */
    public void chaseAABB(Object slot, @Nullable AABB bb, OutlineStyle style) {
        if (bb == null) {
            // 目标为 null → 开始淡出
            OutlineEntry entry = outlines.get(slot);
            if (entry != null && entry.ticksTillRemoval > 0) {
                entry.ticksTillRemoval = 0;
            }
            return;
        }

        OutlineEntry entry = outlines.computeIfAbsent(slot, k -> new OutlineEntry(new AABBOutline(style)));
        entry.ticksTillRemoval = 1;              // 保持存活
        entry.outline.setStyle(style);
        entry.outline.setTarget(bb);
    }

    /**
     * 立即跳转到某个 AABB（无追踪动画）。用于首次出现的帧。
     */
    public void showAABB(Object slot, @Nullable AABB bb, OutlineStyle style) {
        if (bb == null) {
            OutlineEntry entry = outlines.get(slot);
            if (entry != null && entry.ticksTillRemoval > 0) {
                entry.ticksTillRemoval = 0;
            }
            return;
        }

        OutlineEntry entry = outlines.computeIfAbsent(slot, k -> new OutlineEntry(new AABBOutline(style)));
        entry.ticksTillRemoval = 1;
        entry.outline.setStyle(style);
        entry.outline.jumpTo(bb);
    }

    /** 保持现有轮廓线额外存活一 tick，不改变其目标。 */
    public void keep(Object slot) {
        OutlineEntry entry = outlines.get(slot);
        if (entry != null && entry.ticksTillRemoval > -FADE_TICKS) {
            entry.ticksTillRemoval = 1;
        }
    }

    /** 立即移除轮廓线（不淡出）。 */
    public void remove(Object slot) {
        outlines.remove(slot);
    }

    // ========================================================================
    //  帧更新 — 在世界渲染事件中每帧调用一次
    // ========================================================================

    public void tickOutlines() {
        synchronized (outlines) {
            Iterator<Map.Entry<Object, OutlineEntry>> it = outlines.entrySet().iterator();
            while (it.hasNext()) {
                OutlineEntry entry = it.next().getValue();
                entry.ticksTillRemoval--;

                // 先更新追踪位置（在 TTL 递减之前）
                entry.outline.tick();

                // 已死亡：移除
                if (entry.ticksTillRemoval < -FADE_TICKS) {
                    it.remove();
                }
            }
        }
    }

    // ========================================================================
    //  渲染 — 每帧调用一次（可变 fps）
    // ========================================================================

    public void renderOutlines(PoseStack poseStack, Camera camera, float partialTick) {
        synchronized (outlines) {
            for (OutlineEntry entry : outlines.values()) {
                float fadeAlpha = computeFadeAlpha(entry.ticksTillRemoval);
                if (fadeAlpha < 1f / 128f) continue;

                entry.outline.render(poseStack, camera, partialTick, fadeAlpha);
            }
        }
    }

    // ========================================================================
    //  淡出辅助方法
    // ========================================================================

    /**
     * 将 TTL 计数器转换为透明度系数。
     *
     * <p>存活（{@code > 0}）→ 1.0。淡出（{@code 0 … -FADE_TICKS}）
     * 遵循三次缓出曲线（与 Create 一致）。
     */
    static float computeFadeAlpha(int ticksTillRemoval) {
        if (ticksTillRemoval > 0) return 1f;
        if (ticksTillRemoval <= -FADE_TICKS) return 0f;

        float raw = 1f + (float) ticksTillRemoval / FADE_TICKS; // 1.0 → 0.0
        return raw * raw * raw; // 三次缓出
    }

    // ========================================================================
    //  内部条目
    // ========================================================================

    static class OutlineEntry {
        final AABBOutline outline;
        int ticksTillRemoval = 1;

        OutlineEntry(AABBOutline outline) {
            this.outline = outline;
        }
    }
}
