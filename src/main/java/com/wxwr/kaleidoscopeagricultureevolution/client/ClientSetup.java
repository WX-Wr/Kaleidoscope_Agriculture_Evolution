package com.wxwr.kaleidoscopeagricultureevolution.client;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.ModBlockEntities;
import com.wxwr.kaleidoscopeagricultureevolution.client.gui.KaeConfigScreen;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.CrockBlockEntityRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.DryingBoardBlockEntityRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.FarmerRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.FermentationContainerBlockEntityRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.HoneyExtractorBlockEntityRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.LoucheRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.PlowOxRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.client.renderer.PlowRenderer;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ModEntities;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public class ClientSetup {

    @SuppressWarnings("removal")
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new KaeConfigScreen(parent)));
    }

    public static void setup() {

        EntityRenderers.register(ModEntities.PLOW_OX.get(), PlowOxRenderer::new);
        EntityRenderers.register(ModEntities.PLOW_ENTITY.get(), PlowRenderer::new);
        EntityRenderers.register(ModEntities.LOUCHE_ENTITY.get(), LoucheRenderer::new);
        EntityRenderers.register(ModEntities.FARMER.get(), FarmerRenderer::new);
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.GLASS_FERMENTATION.get(), RenderType.translucent());
        BlockEntityRenderers.register(ModBlockEntities.CROCK.get(), CrockBlockEntityRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.DRYING_BOARD.get(), DryingBoardBlockEntityRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.FERMENTATION_CONTAINER.get(), FermentationContainerBlockEntityRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.HONEY_EXTRACTOR.get(), HoneyExtractorBlockEntityRenderer::new);

    }
}
