package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.entity.LoucheEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 耧车输送带种子渲染 — 在 belt 的 locator 位置渲染滑落的种子
 *
 * <p><b>状态：待定。</b>本类目前没有任何调用点，<b>尚未决定是否接入</b>，请勿删除。
 * 若要启用，在 {@code LoucheRenderer.render()} 的 {@code super.render()} 之后调用：
 * <pre>{@code BeltSeedRenderer.render(entity, partialTicks, poseStack, buffer, light);}</pre>
 */
public final class BeltSeedRenderer {

    private static final int CYCLE_TICKS = 60;
    private static final Minecraft mc = Minecraft.getInstance();

    // locator 模型坐标（像素，相对 root），parent pivot + locator offset
    private static final double[][] LOCS = {
        { 8, 7.275, -10.125}, { 8, 4.375, -7.225}, { 8, 3.275, -4.725}, { 8, 3.275, -3.725}, // belt1 右
        { 0, 7.275, -10.125}, { 0, 4.375, -7.225}, { 0, 3.275, -4.725}, { 0, 3.275, -3.725}, // belt2 中
        {-8, 7.275, -10.125}, {-8, 4.375, -7.225}, {-8, 3.275, -4.725}, {-8, 3.275, -3.725}, // belt3 左
    };

    private BeltSeedRenderer() {}

    public static void render(LoucheEntity entity, float partialTicks,
                              PoseStack poseStack, MultiBufferSource buffer, int light) {
        Item seed = entity.getSeedItem();
        if (seed == null) return;
        ItemStack stack = new ItemStack(seed, 1);

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        double ex = Mth.lerp(partialTicks, entity.xOld, entity.getX()) - camPos.x;
        double ey = Mth.lerp(partialTicks, entity.yOld, entity.getY()) - camPos.y;
        double ez = Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - camPos.z;
        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());

        // 动画进度：用 entity.tickCount 驱动
        float progress = ((entity.tickCount + partialTicks) % CYCLE_TICKS) / (float) CYCLE_TICKS;

        RenderSystem.disableDepthTest();

        for (int belt = 0; belt < 3; belt++) {
            int base = belt * 4;
            renderSegment(stack, poseStack, buffer, light,
                    ex, ey, ez, yaw, progress, base, entity);
        }

        RenderSystem.enableDepthTest();
    }

    private static void renderSegment(ItemStack stack, PoseStack poseStack,
                                       MultiBufferSource buffer, int light,
                                       double ex, double ey, double ez, float yaw,
                                       float cycleProgress, int base, LoucheEntity entity) {
        double[] p1 = LOCS[base];
        double[] p2 = LOCS[base + 1];
        double[] p3 = LOCS[base + 2];
        double[] p4 = LOCS[base + 3];

        double[] from, to;
        float segT;
        if (cycleProgress < 1f / 3f) {
            segT = cycleProgress * 3f; from = p1; to = p2;
        } else if (cycleProgress < 2f / 3f) {
            segT = (cycleProgress - 1f / 3f) * 3f; from = p2; to = p3;
        } else {
            segT = (cycleProgress - 2f / 3f) * 3f; from = p3; to = p4;
        }

        double mx = -(from[0] + (to[0] - from[0]) * segT) / 16.0;
        double my =  (from[1] + (to[1] - from[1]) * segT) / 16.0;
        double mz =  (from[2] + (to[2] - from[2]) * segT) / 16.0;

        double dxSeg = to[0] - from[0];
        double dySeg = to[1] - from[1];
        double dzSeg = to[2] - from[2];
        double segLen = Math.sqrt(dxSeg * dxSeg + dySeg * dySeg + dzSeg * dzSeg);
        float pitch = segLen > 0.001 ? (float) Math.toDegrees(Math.asin(-dySeg / segLen)) : 0f;
        float segYaw = (float) Math.toDegrees(Math.atan2(-dxSeg, dzSeg));

        poseStack.pushPose();
        poseStack.translate(ex, ey, ez);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.translate(mx, my, mz);
        poseStack.mulPose(Axis.YP.rotationDegrees(segYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.scale(0.5f, 0.5f, 0.5f);

        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }
}
