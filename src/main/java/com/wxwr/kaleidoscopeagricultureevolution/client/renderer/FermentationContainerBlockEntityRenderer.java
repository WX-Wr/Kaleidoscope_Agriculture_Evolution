package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationContent;
import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationTapState;
import com.wxwr.kaleidoscopeagricultureevolution.block.GlassFermentationBottleState;
import com.wxwr.kaleidoscopeagricultureevolution.block.GlassFermentationBlock;
import com.wxwr.kaleidoscopeagricultureevolution.block.WoodenFermentationBlock;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.FermentationContainerBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.container.ContainerWhitelist;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class FermentationContainerBlockEntityRenderer implements BlockEntityRenderer<FermentationContainerBlockEntity> {
    private static final float[][] GLASS_INGREDIENT_POSITIONS = {
            {0.50F, 0.105F, 0.50F, 20.0F},
            {0.50F, 0.130F, 0.50F, 92.0F},
            {0.50F, 0.155F, 0.50F, 308.0F},
            {0.50F, 0.180F, 0.50F, 236.0F},
            {0.50F, 0.205F, 0.50F, 164.0F},
            {0.50F, 0.230F, 0.50F, 92.0F},
    };
    private final ItemRenderer itemRenderer;

    public FermentationContainerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(@NotNull FermentationContainerBlockEntity container, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = container.getBlockState();
        if (state.getBlock() instanceof WoodenFermentationBlock) {
            if (state.getValue(WoodenFermentationBlock.LIDDED)) {
                return;
            }
            renderLiquid(LiquidAppearanceHelper.ContainerType.WOODEN_FERMENTATION,
                    state.getValue(WoodenFermentationBlock.CONTENT),
                    state.getValue(WoodenFermentationBlock.FILL_LEVEL),
                    poseStack, buffer, packedLight);
            return;
        }

        if (state.getBlock() instanceof GlassFermentationBlock) {
            renderGlassIngredients(container, poseStack, buffer, packedLight, packedOverlay);
            renderGlassTapStream(state, poseStack, buffer, packedLight);
            renderLiquid(LiquidAppearanceHelper.ContainerType.GLASS_FERMENTATION,
                    state.getValue(GlassFermentationBlock.CONTENT),
                    state.getValue(GlassFermentationBlock.FILL_LEVEL),
                    poseStack, buffer, packedLight);
        }
    }

    private static void renderLiquid(LiquidAppearanceHelper.ContainerType container, FermentationContent content,
                                     int fillLevel, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        LiquidContentRenderer.render(container, LiquidAppearanceHelper.fromFermentationContent(content),
                fillLevel, content != FermentationContent.RICE, poseStack, buffer, packedLight);
    }

    private void renderGlassIngredients(FermentationContainerBlockEntity container, PoseStack poseStack,
                                        MultiBufferSource buffer, int packedLight, int packedOverlay) {
        int rendered = 0;
        int appleModels = ContainerWhitelist.isGlassAppleVinegarIngredient(new ItemStack(Items.APPLE))
                ? groupedRenderCount(container.countNonLiquidItem(Items.APPLE), 3)
                : 0;
        for (int i = 0; i < appleModels && rendered < GLASS_INGREDIENT_POSITIONS.length; i++) {
            renderGlassIngredient(new ItemStack(Items.APPLE), rendered, container, poseStack, buffer,
                    packedLight, packedOverlay);
            rendered++;
        }

        for (ItemStack stack : container.getNonLiquidItems()) {
            if (stack.isEmpty() || stack.is(Items.APPLE) || !isGlassIngredientRenderable(stack)) {
                continue;
            }
            int count = Math.max(1, stack.getCount());
            for (int i = 0; i < count && rendered < GLASS_INGREDIENT_POSITIONS.length; i++) {
                renderGlassIngredient(stack, rendered, container, poseStack, buffer, packedLight, packedOverlay);
                rendered++;
            }
            if (rendered >= GLASS_INGREDIENT_POSITIONS.length) {
                return;
            }
        }
    }

    private static int groupedRenderCount(int itemCount, int itemsPerModel) {
        if (itemCount <= 0) {
            return 0;
        }
        return (itemCount + itemsPerModel - 1) / itemsPerModel;
    }

    private void renderGlassIngredient(ItemStack stack, int index, FermentationContainerBlockEntity container,
                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                       int packedOverlay) {
        float[] position = GLASS_INGREDIENT_POSITIONS[index];
        poseStack.pushPose();
        poseStack.translate(position[0], position[1], position[2]);
        poseStack.mulPose(Axis.YP.rotationDegrees(position[3]));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        itemRenderer.renderStatic(stack.copyWithCount(1), ItemDisplayContext.GROUND,
                packedLight, packedOverlay, poseStack, buffer, container.getLevel(), index);
        poseStack.popPose();
    }

    private static boolean isGlassIngredientRenderable(ItemStack stack) {
        return ContainerWhitelist.isGlassIngredientRenderable(stack);
    }

    private static void renderGlassTapStream(BlockState state, PoseStack poseStack,
                                             MultiBufferSource buffer, int packedLight) {
        if (state.getValue(GlassFermentationBlock.TAP) != FermentationTapState.OPEN) {
            return;
        }

        GlassFermentationBottleState bottle = state.getValue(GlassFermentationBlock.BOTTLE);
        if (!bottle.isFilled()) {
            return;
        }

        poseStack.pushPose();
        rotateToFacing(state.getValue(GlassFermentationBlock.FACING), poseStack);
        LiquidContentRenderer.renderGlassTapStream(
                LiquidAppearanceHelper.fromFermentationContent(bottle.toContent()), poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private static void rotateToFacing(Direction facing, PoseStack poseStack) {
        float rotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
    }
}
