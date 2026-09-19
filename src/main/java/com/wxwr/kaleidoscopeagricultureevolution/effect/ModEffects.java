package com.wxwr.kaleidoscopeagricultureevolution.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);

    public static final RegistryObject<MobEffect> FIREBURST =
            EFFECTS.register("fireburst", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.FIREBURST, 0xE84A16));

    public static final RegistryObject<MobEffect> SOUR_FRESH =
            EFFECTS.register("sour_fresh", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.SOUR_FRESH, 0x62B36F));

    public static final RegistryObject<MobEffect> SWEET_SOUR =
            EFFECTS.register("sweet_sour", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.SWEET_SOUR, 0xEBA83A));

    public static final RegistryObject<MobEffect> FULLNESS =
            EFFECTS.register("fullness", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.FULLNESS, 0xD8B24C));

    public static final RegistryObject<MobEffect> CRISP =
            EFFECTS.register("crisp", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.CRISP, 0x64C7D9));

    public static final RegistryObject<MobEffect> FRUIT_ACID =
            EFFECTS.register("fruit_acid", () -> new AgricultureMobEffect(
                    AgricultureMobEffect.Behavior.FRUIT_ACID, 0xA35CC7));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
