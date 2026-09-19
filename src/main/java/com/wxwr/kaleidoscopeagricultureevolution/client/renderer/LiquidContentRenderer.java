package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.LiquidAppearanceHelper.ContainerType;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.LiquidAppearanceHelper.LiquidVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class LiquidContentRenderer {
    private static final int LIQUID_ALPHA = 160;
    private static final Uv TOP = new Uv(0.0F, 0.0F, 3.5F, 3.5F);
    private static final Uv BOTTOM = new Uv(3.5F, 0.0F, 7.0F, 3.5F);
    private static final Uv NORTH = new Uv(7.0F, 0.0F, 10.5F, 3.5F);
    private static final Uv WEST = new Uv(0.0F, 3.5F, 3.5F, 7.0F);
    private static final Uv EAST = new Uv(3.5F, 3.5F, 7.0F, 7.0F);
    private static final Uv SOUTH = new Uv(0.0F, 7.0F, 3.5F, 10.5F);

    private LiquidContentRenderer() {
    }

    public static void render(ContainerType container, @Nullable LiquidVisual liquid, int fillLevel,
                              boolean transparent, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (liquid == null || fillLevel <= 0) {
            return;
        }

        renderBox(liquid, boxFor(container, fillLevel), transparent, poseStack, buffer, packedLight);
    }

    public static void renderGlassTapStream(@Nullable LiquidVisual liquid, PoseStack poseStack,
                                            MultiBufferSource buffer, int packedLight) {
        if (liquid == null) {
            return;
        }

        renderBox(liquid, box(7.6F, 0.3F, -0.9F, 8.4F, 3.75F, -0.1F), true, poseStack, buffer, packedLight);
    }

    public static void renderFlatSurface(LiquidVisual liquid, float minX, float y, float minZ,
                                         float maxX, float maxZ, boolean transparent,
                                         PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderFlatSurface(liquid.texture(), minX, y, minZ, maxX, maxZ, transparent, poseStack, buffer, packedLight);
    }

    public static void renderFlatSurface(ResourceLocation texture, float minX, float y, float minZ,
                                         float maxX, float maxZ, boolean transparent,
                                         PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        renderFlatSurface(texture, minX, y, minZ, maxX, maxZ, transparent, poseStack, buffer, packedLight, 255);
    }

    private static void renderFlatSurface(ResourceLocation texture, float minX, float y, float minZ,
                                          float maxX, float maxZ, boolean transparent,
                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight, int alpha) {
        TextureAtlasSprite sprite = sprite(texture);
        VertexConsumer consumer = buffer.getBuffer(transparent ? RenderType.translucent() : RenderType.solid());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Box box = new Box(minX, y, minZ, maxX, y, maxZ);

        renderTop(consumer, pose, normal, sprite, box, packedLight, alpha);
    }

    private static void renderBox(LiquidVisual liquid, Box box, boolean transparent, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight) {
        renderBox(liquid.texture(), box, transparent, poseStack, buffer, packedLight,
                transparent ? LIQUID_ALPHA : 255);
    }

    private static void renderBox(ResourceLocation texture, Box box, boolean transparent, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int alpha) {
        TextureAtlasSprite sprite = sprite(texture);
        VertexConsumer consumer = buffer.getBuffer(transparent ? RenderType.translucent() : RenderType.solid());
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        renderTop(consumer, pose, normal, sprite, box, packedLight, alpha);
        if (!box.isFlat()) {
            renderBottom(consumer, pose, normal, sprite, box, packedLight, alpha);
            renderNorth(consumer, pose, normal, sprite, box, packedLight, alpha);
            renderSouth(consumer, pose, normal, sprite, box, packedLight, alpha);
            renderWest(consumer, pose, normal, sprite, box, packedLight, alpha);
            renderEast(consumer, pose, normal, sprite, box, packedLight, alpha);
        } else {
            renderTopBackFace(consumer, pose, normal, sprite, box, packedLight, alpha);
        }
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
    }

    private static Box boxFor(ContainerType container, int fillLevel) {
        int stage = Math.max(1, fillLevel);
        return switch (container) {
            case CROCK -> switch (Math.min(3, stage)) {
                case 1 -> flatLiquidBox(3.2F, 4.6F, 3.2F, 12.8F, 12.8F);
                case 2 -> flatLiquidBox(3.2F, 7.6F, 3.2F, 12.8F, 12.8F);
                default -> flatLiquidBox(3.2F, 10.6F, 3.2F, 12.8F, 12.8F);
            };
            case WOODEN_FERMENTATION -> switch (Math.min(4, stage)) {
                case 1 -> flatLiquidBox(0.0F, 7.0F, 0.0F, 16.0F, 16.0F);
                case 2 -> flatLiquidBox(0.0F, 11.0F, 0.0F, 16.0F, 16.0F);
                case 3 -> flatLiquidBox(0.0F, 15.0F, 0.0F, 16.0F, 16.0F);
                default -> flatLiquidBox(0.0F, 19.0F, 0.0F, 16.0F, 16.0F);
            };
            case GLASS_FERMENTATION -> switch (Math.min(3, stage)) {
                case 1 -> box(4.25F, 0.5F, 4.25F, 11.75F, 4.0F, 11.75F);
                case 2 -> box(4.25F, 0.5F, 4.25F, 11.75F, 7.5F, 11.75F);
                default -> box(4.25F, 0.5F, 4.25F, 11.75F, 12.7F, 11.75F);
            };
        };
    }

    private static Box flatLiquidBox(float x1, float y, float z1, float x2, float z2) {
        return box(x1, y, z1, x2, y, z2);
    }

    private static Box box(float x1, float y1, float z1, float x2, float y2, float z2) {
        return new Box(x1 / 16.0F, y1 / 16.0F, z1 / 16.0F, x2 / 16.0F, y2 / 16.0F, z2 / 16.0F);
    }

    private static void renderTop(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                  Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.minZ, TOP.minU, TOP.minV, 0, 1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.maxZ, TOP.minU, TOP.maxV, 0, 1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.maxZ, TOP.maxU, TOP.maxV, 0, 1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.minZ, TOP.maxU, TOP.minV, 0, 1, 0, packedLight, alpha);
    }

    private static void renderTopBackFace(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                                          TextureAtlasSprite sprite, Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.minZ, TOP.maxU, TOP.minV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.maxZ, TOP.maxU, TOP.maxV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.maxZ, TOP.minU, TOP.maxV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.minZ, TOP.minU, TOP.minV, 0, -1, 0, packedLight, alpha);
    }

    private static void renderBottom(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                     Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.maxZ, BOTTOM.minU, BOTTOM.maxV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.minZ, BOTTOM.minU, BOTTOM.minV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.minZ, BOTTOM.maxU, BOTTOM.minV, 0, -1, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.maxZ, BOTTOM.maxU, BOTTOM.maxV, 0, -1, 0, packedLight, alpha);
    }

    private static void renderNorth(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                    Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.minZ, NORTH.maxU, NORTH.maxV, 0, 0, -1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.minZ, NORTH.maxU, NORTH.minV, 0, 0, -1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.minZ, NORTH.minU, NORTH.minV, 0, 0, -1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.minZ, NORTH.minU, NORTH.maxV, 0, 0, -1, packedLight, alpha);
    }

    private static void renderSouth(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                    Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.maxZ, SOUTH.minU, SOUTH.maxV, 0, 0, 1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.maxZ, SOUTH.minU, SOUTH.minV, 0, 0, 1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.maxZ, SOUTH.maxU, SOUTH.minV, 0, 0, 1, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.maxZ, SOUTH.maxU, SOUTH.maxV, 0, 0, 1, packedLight, alpha);
    }

    private static void renderWest(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                   Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.minZ, WEST.maxU, WEST.maxV, -1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.minZ, WEST.maxU, WEST.minV, -1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.maxY, box.maxZ, WEST.minU, WEST.minV, -1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.minX, box.minY, box.maxZ, WEST.minU, WEST.maxV, -1, 0, 0, packedLight, alpha);
    }

    private static void renderEast(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                                   Box box, int packedLight, int alpha) {
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.maxZ, EAST.maxU, EAST.maxV, 1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.maxZ, EAST.maxU, EAST.minV, 1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.maxY, box.minZ, EAST.minU, EAST.minV, 1, 0, 0, packedLight, alpha);
        vertex(consumer, pose, normal, sprite, box.maxX, box.minY, box.minZ, EAST.minU, EAST.maxV, 1, 0, 0, packedLight, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, TextureAtlasSprite sprite,
                               float x, float y, float z, float u, float v,
                               float normalX, float normalY, float normalZ, int packedLight, int alpha) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, alpha)
                .uv(sprite.getU(u), sprite.getV(v))
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, normalX, normalY, normalZ)
                .endVertex();
    }

    private record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        private boolean isFlat() {
            return minY == maxY;
        }
    }

    private record Uv(float minU, float minV, float maxU, float maxV) {
    }
}
