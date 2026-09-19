package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class PlowPathRenderer {

    public static void renderPath(PoseStack poseStack, Camera camera, List<BlockPos> points) {
        if (points.isEmpty()) return;

        Vec3 cam = camera.getPosition();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < points.size(); i++) {
            BlockPos p = points.get(i);
            float x = (float)(p.getX() + 0.5 - cam.x);
            float y = (float)(p.getY() + 0.02 - cam.y);
            float z = (float)(p.getZ() + 0.5 - cam.z);

            // 颜色渐变：沿路径从绿色渐变到黄色
            float t = points.size() > 1 ? (float) i / (points.size() - 1) : 0f;
            float r = 0.2f + t * 0.8f;  // 0.2 → 1.0
            float g = 1.0f;
            float b = 0.2f * (1f - t);  // 0.2 → 0

            buffer.vertex(mat, x, y, z).color(r, g, b, 0.7f).endVertex();
        }

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 在耕牛上方以浮动文字的形式渲染剩余路径方块数和饱腹状态。
     */
    public static void renderRemainingCount(PoseStack poseStack, Camera camera,
                                            MultiBufferSource.BufferSource bufferSource,
                                            Entity ox,
                                            int remainingCount) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();

        double x = ox.getX() - cam.x;
        double y = ox.getY() + ox.getBbHeight() + 1.0 - cam.y; // 耕牛头顶上方 1 格
        double z = ox.getZ() - cam.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        // 强制深度写入：即使深度测试始终通过，也要写入深度缓冲
        // 这样后续渲染的云层在文字像素处会因深度测试失败而被遮挡
        RenderSystem.depthFunc(519); // GL_ALWAYS — 始终通过深度测试
        RenderSystem.depthMask(true);
        Matrix4f matrix4f = poseStack.last().pose();

        // 第一行：饱腹状态
        if (ox instanceof PlowOxEntity plowOx && plowOx.getBoostRemaining() != 0) {
            String source = plowOx.getBoostRemaining() == -1 ? "谜之炖菜" : "小麦";
            String boostText = "§a饱腹（" + source + "）";
            float boostHalf = mc.font.width(boostText) / 2.0f;
            mc.font.drawInBatch(boostText, -boostHalf, 0, 0xFFFFFFFF, false, matrix4f,
                    bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        }

        // 第二行：剩余工作量
        String countText = "剩余: " + remainingCount;
        float halfWidth = mc.font.width(countText) / 2.0f;
        mc.font.drawInBatch(countText, -halfWidth, 12, 0xFFFFFF00, false, matrix4f,
                bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
        // 立即 flush
        bufferSource.endBatch();
        // 恢复默认深度状态
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.depthMask(true);

        poseStack.popPose();
    }
}
