package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

public final class AnimatedTextureRenderer {
    private AnimatedTextureRenderer() {
    }

    public static TextureAtlasSprite getSprite(AnimatedTexture texture) {
        return Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture.spriteLocation());
    }

    public static void renderQuad(PoseStack poseStack, TextureAtlasSprite sprite,
                                  QuadVertex first, QuadVertex second,
                                  QuadVertex third, QuadVertex fourth, boolean flipV) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float firstV = flipV ? sprite.getV0() : sprite.getV1();
        float secondV = flipV ? sprite.getV1() : sprite.getV0();
        builder.vertex(matrix, first.x(), first.y(), first.z()).uv(sprite.getU0(), firstV).endVertex();
        builder.vertex(matrix, second.x(), second.y(), second.z()).uv(sprite.getU1(), firstV).endVertex();
        builder.vertex(matrix, third.x(), third.y(), third.z()).uv(sprite.getU1(), secondV).endVertex();
        builder.vertex(matrix, fourth.x(), fourth.y(), fourth.z()).uv(sprite.getU0(), secondV).endVertex();
        BufferUploader.drawWithShader(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public record QuadVertex(float x, float y, float z) {
    }
}
