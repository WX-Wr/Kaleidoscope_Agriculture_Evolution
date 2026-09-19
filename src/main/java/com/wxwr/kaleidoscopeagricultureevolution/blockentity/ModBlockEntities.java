package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

    public static final RegistryObject<BlockEntityType<CrockBlockEntity>> CROCK =
            BLOCK_ENTITIES.register("crock", () ->
                    BlockEntityType.Builder.of(CrockBlockEntity::new,
                            ModBlocks.CROCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<DryingBoardBlockEntity>> DRYING_BOARD =
            BLOCK_ENTITIES.register("drying_board", () ->
                    BlockEntityType.Builder.of(DryingBoardBlockEntity::new,
                            ModBlocks.DRYING_BOARD.get()).build(null));

    public static final RegistryObject<BlockEntityType<FermentationContainerBlockEntity>> FERMENTATION_CONTAINER =
            BLOCK_ENTITIES.register("fermentation_container", () ->
                    BlockEntityType.Builder.of(FermentationContainerBlockEntity::new,
                            ModBlocks.WOODEN_FERMENTATION.get(),
                            ModBlocks.GLASS_FERMENTATION.get()).build(null));

    public static final RegistryObject<BlockEntityType<HoneyExtractorBlockEntity>> HONEY_EXTRACTOR =
            BLOCK_ENTITIES.register("honey_extractor", () ->
                    BlockEntityType.Builder.of(HoneyExtractorBlockEntity::new,
                            ModBlocks.HONEY_EXTRACTOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<BeeColonyBlockEntity>> BEE_COLONY =
            BLOCK_ENTITIES.register("bee_colony", () ->
                    BlockEntityType.Builder.of(BeeColonyBlockEntity::new,
                            ModBlocks.BEE_COLONY.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
