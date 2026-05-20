package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.client.renderer;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class DirectionArrowRenderer {

    private static final ResourceLocation X_ARROW =
            new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "textures/gui/xarrow.png");
    private static final ResourceLocation Z_ARROW =
            new ResourceLocation(Kaleidoscope_Agriculture_Evolution.MODID, "textures/gui/zarrow.png");

    public static void renderArrow(PoseStack poseStack, Camera camera, BlockPos pos, boolean isX, int direction) {
        Vec3 cam = camera.getPosition();
        float x = (float)(pos.getX() + 0.5 - cam.x);
        float y = (float)(pos.getY() + 0.01 - cam.y);
        float z = (float)(pos.getZ() + 0.5 - cam.z);

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // 负方向时绕 Y 轴旋转 180 度
        if (direction < 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
        }

        poseStack.scale(0.5f, 0.5f, 0.5f);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, isX ? X_ARROW : Z_ARROW);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float s = 0.4f;
        buffer.vertex(mat, -s, 0, -s).uv(0, 0).endVertex();
        buffer.vertex(mat,  s, 0, -s).uv(1, 0).endVertex();
        buffer.vertex(mat,  s, 0,  s).uv(1, 1).endVertex();
        buffer.vertex(mat, -s, 0,  s).uv(0, 1).endVertex();

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}