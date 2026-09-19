package com.wxwr.kaleidoscopeagricultureevolution.genetics;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.breeding.BeeMutationRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.compatibility.BeeFlowerCompatibility;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination.BeePollinationHandler;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination.BeePollinationBlacklistReloadListener;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesReloadListener;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeeGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding.MutationEngine;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.item.SeedTooltipHandler;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.BuiltinSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.world.GeneticsWorldHandler;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class GeneticsSetup {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(GeneticsSetup::onCommonSetup);
        modEventBus.addListener(GeneticsSetup::onConfigReload);
        modEventBus.addListener(CropGenomeCapability::registerCapability);
        modEventBus.addListener(BeeGenomeCapability::registerCapability);
        modEventBus.addListener(BeehiveColonyCapability::registerCapability);
        MinecraftForge.EVENT_BUS.addListener(GeneticsSetup::onAddReloadListeners);

        MinecraftForge.EVENT_BUS.register(CropGenomeCapability.class);
        MinecraftForge.EVENT_BUS.register(BeeGenomeCapability.class);
        MinecraftForge.EVENT_BUS.register(BeehiveColonyCapability.class);
        MinecraftForge.EVENT_BUS.register(BeePollinationHandler.class);
        MinecraftForge.EVENT_BUS.register(GeneticsWorldHandler.class);
        MinecraftForge.EVENT_BUS.register(SeedTooltipHandler.class);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            modEventBus.register(
                com.wxwr.kaleidoscopeagricultureevolution.genetics.client.GeneticsClientSetup.class));

        MutationEngine.setMutationRateMultiplier((float) Config.getMutationRate());
        BeeMutationRegistry.setMutationRateMultiplier((float) Config.getMutationRate());
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BuiltinSpecies.registerAll();
            BeeSpeciesRegistry.registerAll();
            BeeFlowerCompatibility.registerDefaults();
        });
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(BeeSpeciesReloadListener.INSTANCE);
        event.addListener(BeePollinationBlacklistReloadListener.INSTANCE);
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(
            com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID)) {
            MutationEngine.setMutationRateMultiplier((float) Config.getMutationRate());
            BeeMutationRegistry.setMutationRateMultiplier((float) Config.getMutationRate());
        }
    }
}
