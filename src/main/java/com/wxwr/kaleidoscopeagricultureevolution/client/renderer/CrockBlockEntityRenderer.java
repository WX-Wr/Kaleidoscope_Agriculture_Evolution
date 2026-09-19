package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.block.CrockBlock;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.CrockBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class CrockBlockEntityRenderer implements BlockEntityRenderer<CrockBlockEntity> {
    private static final int MAX_RENDERED_ITEMS = 8;
    private static final float ITEM_RING_OFFSET = 0.035f;
    private static final float ITEM_RANDOM_OFFSET = 0.008f;
    private static final float COMPLETE_BANNER_Y = 1.35f;
    private static final float COMPLETE_BANNER_SIZE = 0.65f;
    private static final AnimatedTexture PICKLING_COMPLETE_TEXTURE =
            () -> KaleidoscopeAgricultureEvolution.rl("gui/accomplished/accomplished");

    private final ItemRenderer itemRenderer;
    public CrockBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(@NotNull CrockBlockEntity crock, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = crock.getBlockState();
        if (shouldRenderCompleteBanner(crock)) {
            renderCompleteBanner(state, poseStack, buffer);
        }

        long seed = crock.getBlockPos().asLong();
        int rendered = renderItemList(crock, crock.getRawItems(), 0, seed, poseStack, buffer, packedLight);
        if (rendered < MAX_RENDERED_ITEMS) {
            renderItemList(crock, crock.getNonRawItems(), rendered, seed, poseStack, buffer, packedLight);
        }
        flushBuffer(buffer);

        if (state.hasProperty(CrockBlock.CONTENT)
                && state.hasProperty(CrockBlock.FILL_LEVEL)) {
            LiquidContentRenderer.render(LiquidAppearanceHelper.ContainerType.CROCK,
                    LiquidAppearanceHelper.fromCrockContent(state.getValue(CrockBlock.CONTENT)),
                    state.getValue(CrockBlock.FILL_LEVEL), true, poseStack, buffer, packedLight);
        }
        flushBuffer(buffer);
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull CrockBlockEntity crock) {
        return shouldRenderCompleteBanner(crock);
    }

    private static void flushBuffer(MultiBufferSource buffer) {
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }
    }

    private static boolean shouldRenderCompleteBanner(CrockBlockEntity crock) {
        if (crock.isPicklingComplete()) {
            return true;
        }
        Level level = crock.getLevel();
        return level != null && (crock.hasElapsedPicklingTimer(level)
                || crock.hasPendingProducts() && !crock.hasActivePickling(level));
    }

    private int renderItemList(CrockBlockEntity crock, NonNullList<ItemStack> items, int rendered,
                               long seed, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }

            int count = Math.min(stack.getCount(), MAX_RENDERED_ITEMS - rendered);
            for (int i = 0; i < count; i++) {
                renderRawItem(crock, stack, rendered, seed, poseStack, buffer, packedLight);
                rendered++;
                if (rendered >= MAX_RENDERED_ITEMS) {
                    return rendered;
                }
            }
        }
        return rendered;
    }

    private void renderRawItem(CrockBlockEntity crock, ItemStack stack, int index, long seed,
                               PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int local = index % 4;
        float x = (local % 2 == 0 ? -ITEM_RING_OFFSET : ITEM_RING_OFFSET)
                + centeredRandom(seed, index, 1) * ITEM_RANDOM_OFFSET;
        float z = (local / 2 == 0 ? -ITEM_RING_OFFSET : ITEM_RING_OFFSET)
                + centeredRandom(seed, index, 2) * ITEM_RANDOM_OFFSET;
        float y = 0.18f + (index / 4) * 0.022f + stableRandom(seed, index, 3) * 0.01f;
        float yRot = stableRandom(seed, index, 4) * 360.0f;

        ItemStack renderStack = stack.copyWithCount(1);
        poseStack.pushPose();
        poseStack.translate(0.5f + x, y, 0.5f + z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        poseStack.mulPose(Axis.XN.rotationDegrees(90.0f));
        poseStack.scale(0.32f, 0.32f, 0.32f);
        itemRenderer.renderStatic(renderStack, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, crock.getLevel(), index);
        poseStack.popPose();
    }

    private void renderCompleteBanner(BlockState state, PoseStack poseStack, MultiBufferSource buffer) {
        flushBuffer(buffer);
        poseStack.pushPose();
        poseStack.translate(0.5f, COMPLETE_BANNER_Y, 0.5f);
        Direction direction = state.hasProperty(CrockBlock.FACING) ? state.getValue(CrockBlock.FACING) : Direction.NORTH;
        poseStack.mulPose(Axis.YP.rotationDegrees(bannerYaw(direction)));
        if (direction.getAxis() == Direction.Axis.Z) {
            poseStack.scale(-1.0F, 1.0F, 1.0F);
        }
        poseStack.scale(-COMPLETE_BANNER_SIZE, -COMPLETE_BANNER_SIZE, COMPLETE_BANNER_SIZE);

        AnimatedTextureRenderer.renderQuad(poseStack,
                AnimatedTextureRenderer.getSprite(PICKLING_COMPLETE_TEXTURE),
                new AnimatedTextureRenderer.QuadVertex(-0.5F, 0.5F, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(0.5F, 0.5F, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(0.5F, -0.5F, 0.0F),
                new AnimatedTextureRenderer.QuadVertex(-0.5F, -0.5F, 0.0F), false);
        poseStack.popPose();
    }

    private static float bannerYaw(Direction direction) {
        return switch (direction) {
            case NORTH -> 0.0f;
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            default -> 0.0f;
        };
    }

    private static float centeredRandom(long seed, int index, int salt) {
        return stableRandom(seed, index, salt) - 0.5f;
    }

    private static float stableRandom(long seed, int index, int salt) {
        long mixed = seed ^ (index * 0x9E3779B97F4A7C15L) ^ (salt * 0xBF58476D1CE4E5B9L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return ((mixed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }
}
