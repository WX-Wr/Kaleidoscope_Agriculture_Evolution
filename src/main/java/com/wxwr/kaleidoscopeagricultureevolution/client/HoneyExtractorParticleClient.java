package com.wxwr.kaleidoscopeagricultureevolution.client;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.client.particle.HoneyExtractorParticle;
import com.wxwr.kaleidoscopeagricultureevolution.particle.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = KaleidoscopeAgricultureEvolution.MODID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HoneyExtractorParticleClient {
    private HoneyExtractorParticleClient() {
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.HONEY_EXTRACTOR.get(), HoneyExtractorParticle.Provider::new);
    }
}
