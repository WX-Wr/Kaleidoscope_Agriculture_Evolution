package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
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
        RenderSystem.disableDepthTest();

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < points.size(); i++) {
            BlockPos p = points.get(i);
            float x = (float)(p.getX() + 0.5 - cam.x);
            float y = (float)(p.getY() + 0.02 - cam.y);
            float z = (float)(p.getZ() + 0.5 - cam.z);

            // Color gradient: green → yellow along the path
            float t = points.size() > 1 ? (float) i / (points.size() - 1) : 0f;
            float r = 0.2f + t * 0.8f;  // 0.2 → 1.0
            float g = 1.0f;
            float b = 0.2f * (1f - t);  // 0.2 → 0

            buffer.vertex(mat, x, y, z).color(r, g, b, 0.7f).endVertex();
        }

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Debug rendering: draws the sequence number (index) of each path point
     * floating above the block, billboarded toward the camera.
     */
    public static void renderPathIndices(PoseStack poseStack, Camera camera,
                                          MultiBufferSource.BufferSource bufferSource,
                                          List<BlockPos> points) {
        if (points.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();

        for (int i = 0; i < points.size(); i++) {
            BlockPos p = points.get(i);
            double x = p.getX() + 0.5 - cam.x;
            double y = p.getY() + 1.35 - cam.y; // float above the block surface
            double z = p.getZ() + 0.5 - cam.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            Matrix4f matrix4f = poseStack.last().pose();
            String text = String.valueOf(i);
            float halfWidth = mc.font.width(text) / 2.0f;

            // Yellow text, see-through (no depth test), full-bright
            mc.font.drawInBatch(text, -halfWidth, 0, 0xFFFFFF00, false, matrix4f,
                    bufferSource, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);

            poseStack.popPose();
        }
    }
}
