package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class PlowPathRenderer {

    public static void renderPath(PoseStack poseStack, Camera camera, List<BlockPos[]> rows) {
        if (rows.isEmpty()) return;

        Vec3 cam = camera.getPosition();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        int colorIndex = 0;
        for (BlockPos[] row : rows) {
            BlockPos start = row[0];
            BlockPos end = row[1];

            float x1 = (float)(start.getX() + 0.5 - cam.x);
            float y1 = (float)(start.getY() + 0.02 - cam.y);
            float z1 = (float)(start.getZ() + 0.5 - cam.z);

            float x2 = (float)(end.getX() + 0.5 - cam.x);
            float y2 = (float)(end.getY() + 0.02 - cam.y);
            float z2 = (float)(end.getZ() + 0.5 - cam.z);

            // 交替颜色：绿色 / 黄色
            float r, g, b;
            if (colorIndex % 2 == 0) {
                r = 0.2f; g = 1.0f; b = 0.2f;
            } else {
                r = 1.0f; g = 1.0f; b = 0.2f;
            }
            float a = 0.7f;

            buffer.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            buffer.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();

            colorIndex++;
        }

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}