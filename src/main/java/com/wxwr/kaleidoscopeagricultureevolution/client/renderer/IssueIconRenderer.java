package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

public final class IssueIconRenderer {
    private static final AnimatedTexture ERROR_ICON =
            () -> KaleidoscopeAgricultureEvolution.rl("gui/error/error");
    private static final AnimatedTexture WARN_ICON =
            () -> KaleidoscopeAgricultureEvolution.rl("gui/warn/warn");

    private static final float HALF_SIZE = 0.5F;

    private IssueIconRenderer() {
    }

    public static void renderError(Entity entity, Quaternionf cameraOrientation,
                                   PoseStack poseStack, MultiBufferSource buffer,
                                   float yOffset, float size) {
        render(entity, ERROR_ICON, cameraOrientation, poseStack, buffer, yOffset, size);
    }

    public static void renderWarn(Entity entity, Quaternionf cameraOrientation,
                                  PoseStack poseStack, MultiBufferSource buffer,
                                  float yOffset, float size) {
        render(entity, WARN_ICON, cameraOrientation, poseStack, buffer, yOffset, size);
    }

    private static void render(Entity entity, AnimatedTexture texture, Quaternionf cameraOrientation,
                               PoseStack poseStack, MultiBufferSource buffer,
                               float yOffset, float size) {
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() + yOffset, 0.0F);
        poseStack.mulPose(cameraOrientation);
        poseStack.scale(-size, -size, size);

        TextureAtlasSprite sprite = AnimatedTextureRenderer.getSprite(texture);
        AnimatedTextureRenderer.renderQuad(poseStack, sprite,
                new AnimatedTextureRenderer.QuadVertex(-HALF_SIZE, HALF_SIZE, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(HALF_SIZE, HALF_SIZE, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(HALF_SIZE, -HALF_SIZE, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(-HALF_SIZE, -HALF_SIZE, 0.0F), false);
        poseStack.popPose();
    }
}
