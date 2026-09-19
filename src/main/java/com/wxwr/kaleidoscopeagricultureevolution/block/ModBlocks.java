package com.wxwr.kaleidoscopeagricultureevolution.block;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final RegistryObject<Block> PLOW = registerBlock("plow", PlowBlock::new);

    public static final RegistryObject<Block> LOUCHE = registerBlock("louche", LoucheBlock::new);

    public static final RegistryObject<Block> CROCK =
            registerBlock("crock", CrockBlock::new);

    public static final RegistryObject<Block> DRYING_BOARD = registerBlock("drying_board", DryingBoardBlock::new);

    public static final RegistryObject<Block> WOODEN_FERMENTATION = registerBlock("wooden_fermentation",
            WoodenFermentationBlock::new);

    public static final RegistryObject<Block> GLASS_FERMENTATION = registerBlock("glass_fermentation",
            GlassFermentationBlock::new);

    public static final RegistryObject<Block> HONEY_EXTRACTOR = registerBlock("honey_extractor",
            HoneyExtractorBlock::new);

    public static final RegistryObject<Block> BEE_COLONY = registerBlock("bee_colony",
            BeeColonyBlock::new);

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PLOW.get());
            event.accept(LOUCHE.get());
            event.accept(CROCK.get());
            event.accept(DRYING_BOARD.get());
            event.accept(WOODEN_FERMENTATION.get());
            event.accept(GLASS_FERMENTATION.get());
            event.accept(HONEY_EXTRACTOR.get());
            event.accept(BEE_COLONY.get());
        }
    }
}
