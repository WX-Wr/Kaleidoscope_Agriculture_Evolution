package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import org.jetbrains.annotations.NotNull;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wxwr.kaleidoscopeagricultureevolution.block.HoneyExtractorBlock;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity.SlotContent;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity.SlotSnapshot;
import com.wxwr.kaleidoscopeagricultureevolution.client.HoneyExtractorClientModels;
import com.wxwr.kaleidoscopeagricultureevolution.util.HoneyExtractorMath;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class HoneyExtractorBlockEntityRenderer implements BlockEntityRenderer<HoneyExtractorBlockEntity> {
    private static final double CRANK_MODEL_Y_OFFSET = 18.0D / 16.0D;
    private static final double CRANK_PIVOT_X = 8.0D / 16.0D;
    private static final double CRANK_PIVOT_Y = (1.5D / 16.0D) + CRANK_MODEL_Y_OFFSET;
    private static final double CRANK_PIVOT_Z = 8.0D / 16.0D;
    private static final double[] SLOT_Z_OFFSETS = {0.0D, 5.0D / 16.0D};
    private static final float HONEY_SURFACE_MIN_X = 3.1F / 16.0F;
    private static final float HONEY_SURFACE_MAX_X = 12.9F / 16.0F;
    private static final float HONEY_SURFACE_MIN_Z = 3.1F / 16.0F;
    private static final float HONEY_SURFACE_MAX_Z = 12.9F / 16.0F;

    public HoneyExtractorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull HoneyExtractorBlockEntity extractor, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = extractor.getBlockState();
        if (!(state.getBlock() instanceof HoneyExtractorBlock)) {
            return;
        }

        Direction facing = state.getValue(HoneyExtractorBlock.FACING);
        float facingYaw = HoneyExtractorMath.visualYaw(facing);
        float crankAngle = extractor.getRenderedCrankAngle(partialTick);
        float basketAngle = extractor.getRenderedBasketAngle(partialTick);

        renderCrank(state, facing, facingYaw, crankAngle, poseStack, buffer, packedLight, packedOverlay);
        renderBasket(state, facingYaw, basketAngle, poseStack, buffer, packedLight, packedOverlay);
        renderContents(extractor, state, facingYaw, basketAngle, poseStack, buffer, packedLight, packedOverlay);
        renderHoneySurface(extractor, poseStack, buffer, packedLight);
    }

    private static void renderCrank(BlockState state, Direction facing, float facingYaw, float crankAngle,
                                    PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, int packedOverlay) {
        poseStack.pushPose();
        rotateAround(poseStack, CRANK_PIVOT_X, CRANK_PIVOT_Y, CRANK_PIVOT_Z, Axis.YP.rotationDegrees(facingYaw));
        if (facing == Direction.NORTH) {
            rotateAround(poseStack, CRANK_PIVOT_X, CRANK_PIVOT_Y, CRANK_PIVOT_Z, Axis.YP.rotationDegrees(180.0F));
        }
        rotateAround(poseStack, CRANK_PIVOT_X, CRANK_PIVOT_Y, CRANK_PIVOT_Z, Axis.ZP.rotationDegrees(crankAngle));
        poseStack.translate(0.0D, CRANK_MODEL_Y_OFFSET, 0.0D);
        renderModel(HoneyExtractorClientModels.STICK, state, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderBasket(BlockState state, float facingYaw, float basketAngle,
                                     PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, int packedOverlay) {
        poseStack.pushPose();
        rotateAroundBlockCenter(poseStack, Axis.YP.rotationDegrees(facingYaw));
        rotateAroundBlockCenter(poseStack, Axis.YP.rotationDegrees(basketAngle));
        renderModel(HoneyExtractorClientModels.EXTRACTOR, state, poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderContents(HoneyExtractorBlockEntity extractor, BlockState state,
                                       float facingYaw, float basketAngle,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, int packedOverlay) {
        poseStack.pushPose();
        rotateAroundBlockCenter(poseStack, Axis.YP.rotationDegrees(facingYaw));
        rotateAroundBlockCenter(poseStack, Axis.YP.rotationDegrees(basketAngle));

        for (int i = 0; i < extractor.getSlotCount(); i++) {
            SlotSnapshot slot = extractor.getSlotSnapshot(i);
            if (slot.content() == SlotContent.EMPTY) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, SLOT_Z_OFFSETS[i]);
            renderSlot(slot, state, poseStack, buffer, packedLight, packedOverlay);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void renderSlot(SlotSnapshot slot, BlockState state, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (slot.content() == SlotContent.HONEYCOMB) {
            if (slot.honeyVisualAlpha() > 0.001F) {
                renderModel(HoneyExtractorClientModels.HONEY_1, state, poseStack, buffer, packedLight, packedOverlay,
                        slot.honeyVisualAlpha());
            }
            if (slot.beeswaxVisualAlpha() > 0.001F) {
                renderModel(HoneyExtractorClientModels.BEESWAX_1, state, poseStack, buffer, packedLight, packedOverlay,
                        slot.beeswaxVisualAlpha());
            }
            return;
        }

        if (slot.content() == SlotContent.BEESWAX && slot.beeswaxVisualAlpha() > 0.001F) {
            renderModel(HoneyExtractorClientModels.BEESWAX_1, state, poseStack, buffer, packedLight, packedOverlay,
                    slot.beeswaxVisualAlpha());
        }
    }

    private static void renderHoneySurface(HoneyExtractorBlockEntity extractor, PoseStack poseStack,
                                           MultiBufferSource buffer, int packedLight) {
        if (extractor.getHoneyAmount() <= 0.0D) {
            return;
        }

        float y = HoneyExtractorMath.surfaceY(extractor.getHoneyAmount());
        LiquidContentRenderer.renderFlatSurface(LiquidAppearanceHelper.LiquidVisual.HONEY,
                HONEY_SURFACE_MIN_X, y, HONEY_SURFACE_MIN_Z,
                HONEY_SURFACE_MAX_X, HONEY_SURFACE_MAX_Z,
                true, poseStack, buffer, packedLight);
    }

    private static void rotateAroundBlockCenter(PoseStack poseStack, org.joml.Quaternionf rotation) {
        rotateAround(poseStack, 0.5D, 0.5D, 0.5D, rotation);
    }

    private static void rotateAround(PoseStack poseStack, double x, double y, double z,
                                     org.joml.Quaternionf rotation) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }

    private static void renderModel(ResourceLocation modelLocation, BlockState state, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight, int packedOverlay) {
        renderModel(modelLocation, state, poseStack, buffer, packedLight, packedOverlay, 1.0F);
    }

    private static void renderModel(ResourceLocation modelLocation, BlockState state, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight, int packedOverlay, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BakedModel model = minecraft.getModelManager().getModel(modelLocation);
        float clampedAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
        VertexConsumer consumer = new AlphaVertexConsumer(
                buffer.getBuffer(clampedAlpha < 0.999F ? RenderType.translucent() : RenderType.cutoutMipped()),
                clampedAlpha);
        blockRenderer.getModelRenderer().renderModel(poseStack.last(), consumer, state, model,
                1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = Mth.clamp(alpha, 0.0F, 1.0F);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, Mth.clamp(Math.round(alpha * this.alpha), 0, 255));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, Mth.clamp(Math.round(alpha * this.alpha), 0, 255));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
