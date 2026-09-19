package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.DryingBoardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class DryingBoardBlockEntityRenderer implements BlockEntityRenderer<DryingBoardBlockEntity> {
    private static final float LAYER_MIN_X = 1.0F / 16.0F;
    private static final float LAYER_Y = (1.05F / 16.0F) + 0.0005F;
    private static final float LAYER_MIN_Z = 1.0F / 16.0F;
    private static final float LAYER_MAX_X = 15.0F / 16.0F;
    private static final float LAYER_MAX_Z = 15.0F / 16.0F;

    private static final ResourceLocation[] LAYER_TEXTURES = {
            null,
            KaleidoscopeAgricultureEvolution.rl("block/farm_tools/atop_water"),
            KaleidoscopeAgricultureEvolution.rl("block/farm_tools/atop_salt1"),
            KaleidoscopeAgricultureEvolution.rl("block/farm_tools/atop_salt2"),
            KaleidoscopeAgricultureEvolution.rl("block/farm_tools/atop_salt3"),
            KaleidoscopeAgricultureEvolution.rl("block/farm_tools/atop_salt4")
    };

    public DryingBoardBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull DryingBoardBlockEntity dryingBoard, float partialTick,
                       @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        int layerStage = dryingBoard.getLayerStage();
        if (layerStage <= DryingBoardBlockEntity.LAYER_EMPTY || layerStage >= LAYER_TEXTURES.length) {
            return;
        }

        LiquidContentRenderer.renderFlatSurface(LAYER_TEXTURES[layerStage],
                LAYER_MIN_X, LAYER_Y, LAYER_MIN_Z, LAYER_MAX_X, LAYER_MAX_Z,
                true, poseStack, buffer, packedLight);
    }
}
