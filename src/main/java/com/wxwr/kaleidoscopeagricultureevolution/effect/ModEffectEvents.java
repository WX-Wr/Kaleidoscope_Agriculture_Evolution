package com.wxwr.kaleidoscopeagricultureevolution.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class ModEffectEvents {
    private static final Set<String> SOUR_FRESH_REPROCESSING = ConcurrentHashMap.newKeySet();

    private ModEffectEvents() {
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance added = event.getEffectInstance();

        if (added.getEffect() == ModEffects.SOUR_FRESH.get()
                && event.getOldEffectInstance() == null) {
            reduceHarmfulEffects(entity);
        }

        if (added.getEffect().getCategory() == MobEffectCategory.HARMFUL
                && entity.hasEffect(ModEffects.SOUR_FRESH.get())) {
            reduceNewHarmfulEffect(entity, added);
        }

        if (added.getEffect() == ModEffects.FULLNESS.get()) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.ABSORPTION, added.getDuration(), 0, false, true, true));
            entity.addEffect(new MobEffectInstance(
                    MobEffects.SATURATION, 1, 0, false, false, false));
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity().hasEffect(ModEffects.CRISP.get())) {
            event.setCanceled(true);
        }
    }

    private static void reduceHarmfulEffects(LivingEntity entity) {
        for (MobEffectInstance instance : entity.getActiveEffects().toArray(MobEffectInstance[]::new)) {
            if (instance.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }

            replaceWithReducedEffect(entity, instance);
        }
    }

    private static void reduceNewHarmfulEffect(LivingEntity entity, MobEffectInstance instance) {
        String key = entity.getUUID() + ":" + instance.getEffect();
        if (!SOUR_FRESH_REPROCESSING.add(key)) {
            return;
        }
        try {
            replaceWithReducedEffect(entity, instance);
        } finally {
            SOUR_FRESH_REPROCESSING.remove(key);
        }
    }

    private static void replaceWithReducedEffect(LivingEntity entity, MobEffectInstance instance) {
        int duration = instance.getDuration();
        int reducedDuration = duration < 0 ? duration : Math.max(1, duration / 2);
        int reducedAmplifier = Math.max(0, instance.getAmplifier() - 1);

        entity.removeEffect(instance.getEffect());
        entity.addEffect(new MobEffectInstance(
                instance.getEffect(),
                reducedDuration,
                reducedAmplifier,
                instance.isAmbient(),
                instance.isVisible(),
                instance.showIcon()));
    }
}
