package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class RangeHighlightRenderer {

    public static void render(PoseStack poseStack, MultiBufferSource buffer, Camera camera,
                              BlockPos corner1, BlockPos corner2) {
        if (corner1 == null || corner2 == null) return;

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX()) + 1;
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY()) + 1;
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ()) + 1;

        Vec3 cam = camera.getPosition();
        VertexConsumer vertex = buffer.getBuffer(RenderType.lines());

        Matrix4f mat = poseStack.last().pose();

        RenderSystem.lineWidth(10.0f);

        drawLine(vertex, mat, cam, minX, minY, minZ, maxX, minY, minZ);
        drawLine(vertex, mat, cam, minX, minY, minZ, minX, maxY, minZ);
        drawLine(vertex, mat, cam, maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(vertex, mat, cam, minX, minY, maxZ, maxX, minY, maxZ);
        drawLine(vertex, mat, cam, minX, maxY, minZ, minX, maxY, maxZ);
        drawLine(vertex, mat, cam, minX, maxY, maxZ, maxX, maxY, maxZ);
        drawLine(vertex, mat, cam, maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(vertex, mat, cam, minX, minY, minZ, minX, minY, maxZ);
        drawLine(vertex, mat, cam, minX, maxY, minZ, maxX, maxY, minZ);
        drawLine(vertex, mat, cam, minX, minY, maxZ, minX, maxY, maxZ);
        drawLine(vertex, mat, cam, maxX, maxY, minZ, maxX, maxY, maxZ);
        drawLine(vertex, mat, cam, maxX, minY, maxZ, maxX, maxY, maxZ);

        RenderSystem.lineWidth(1.0f);
    }

    private static void drawLine(VertexConsumer vertex, Matrix4f mat, Vec3 cam,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2) {
        vertex.vertex(mat, (float)(x1 - cam.x), (float)(y1 - cam.y), (float)(z1 - cam.z))
                .color(255, 215, 0, 255).normal(0, 1, 0).endVertex();
        vertex.vertex(mat, (float)(x2 - cam.x), (float)(y2 - cam.y), (float)(z2 - cam.z))
                .color(255, 215, 0, 255).normal(0, 1, 0).endVertex();
    }
}