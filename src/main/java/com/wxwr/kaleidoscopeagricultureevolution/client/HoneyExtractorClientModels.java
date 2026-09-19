package com.wxwr.kaleidoscopeagricultureevolution.client;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoneyExtractorClientModels {
    public static final ResourceLocation STICK =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/honey_extractor_stick");
    public static final ResourceLocation EXTRACTOR =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/extractor");
    public static final ResourceLocation HONEY_1 =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/honey1");
    public static final ResourceLocation HONEY_2 =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/honey2");
    public static final ResourceLocation BEESWAX_1 =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/beeswax1");
    public static final ResourceLocation BEESWAX_2 =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/beeswax2");
    public static final ResourceLocation BEESWAX_HONEY =
            KaleidoscopeAgricultureEvolution.rl("block/honey_extractor/beeswax_honey");

    private HoneyExtractorClientModels() {
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(STICK);
        event.register(EXTRACTOR);
        event.register(HONEY_1);
        event.register(HONEY_2);
        event.register(BEESWAX_1);
        event.register(BEESWAX_2);
        event.register(BEESWAX_HONEY);
    }
}
