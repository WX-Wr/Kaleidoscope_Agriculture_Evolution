package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution.MODID;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<PlowOxEntity>> PLOW_OX =
            ENTITY_TYPES.register("plow_ox",
                    () -> EntityType.Builder.of(PlowOxEntity::new, MobCategory.CREATURE)
                            .sized(1.2F, 1.2F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(MODID + ":plow_ox"));

    public static final RegistryObject<EntityType<PlowEntity>> PLOW_ENTITY =
            ENTITY_TYPES.register("plow_entity",
                    () -> EntityType.Builder.<PlowEntity>of(PlowEntity::new, MobCategory.MISC)
                            .sized(1.2F, 0.8F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build(MODID + ":plow_entity"));

    // ROPE_COLLISION 已删除，不要保留

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}