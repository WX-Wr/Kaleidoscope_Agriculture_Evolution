package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class DirectionArrowRenderer {

    private static final float ARROW_WORLD_SCALE = 0.5f;
    private static final float ARROW_HALF_SIZE = 0.8f;
    private static final AnimatedTexture X_ARROW =
            () -> KaleidoscopeAgricultureEvolution.rl("gui/arrow/xarrow");
    private static final AnimatedTexture Z_ARROW =
            () -> KaleidoscopeAgricultureEvolution.rl("gui/arrow/zarrow");

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

        poseStack.scale(ARROW_WORLD_SCALE, ARROW_WORLD_SCALE, ARROW_WORLD_SCALE);

        float s = ARROW_HALF_SIZE;
        TextureAtlasSprite sprite = AnimatedTextureRenderer.getSprite(isX ? X_ARROW : Z_ARROW);
        AnimatedTextureRenderer.renderQuad(poseStack, sprite,
                new AnimatedTextureRenderer.QuadVertex(-s, 0.0F, -s),
                new AnimatedTextureRenderer.QuadVertex(s, 0.0F, -s),
                new AnimatedTextureRenderer.QuadVertex(s, 0.0F, s),
                new AnimatedTextureRenderer.QuadVertex(-s, 0.0F, s), true);
        poseStack.popPose();
    }
}
