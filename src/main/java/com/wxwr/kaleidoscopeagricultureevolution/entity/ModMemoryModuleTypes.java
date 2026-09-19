package com.wxwr.kaleidoscopeagricultureevolution.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Optional;
import java.util.UUID;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

public final class ModMemoryModuleTypes {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES =
            DeferredRegister.create(ForgeRegistries.MEMORY_MODULE_TYPES, MODID);

    public static final RegistryObject<MemoryModuleType<UUID>> FARMER_BOUND_OX =
            MEMORY_MODULE_TYPES.register("farmer_bound_ox", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<UUID>> FARMER_BOUND_TOOL =
            MEMORY_MODULE_TYPES.register("farmer_bound_tool", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<BlockPos>> FARMER_SUPPLY_CHEST =
            MEMORY_MODULE_TYPES.register("farmer_supply_chest", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<String>> FARMER_SUPPLY_STATE =
            MEMORY_MODULE_TYPES.register("farmer_supply_state", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<String>> FARMER_OX_STATE =
            MEMORY_MODULE_TYPES.register("farmer_ox_state", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<Boolean>> FARMER_IN_TARGET_AREA =
            MEMORY_MODULE_TYPES.register("farmer_in_target_area", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<Integer>> FARMER_WORK_REMAINING =
            MEMORY_MODULE_TYPES.register("farmer_work_remaining", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<Boolean>> FARMER_NEEDS_STORE =
            MEMORY_MODULE_TYPES.register("farmer_needs_store", () -> new MemoryModuleType<>(Optional.empty()));
    public static final RegistryObject<MemoryModuleType<BlockPos>> FARMER_TARGET_POS =
            MEMORY_MODULE_TYPES.register("farmer_target_pos", () -> new MemoryModuleType<>(Optional.empty()));

    private ModMemoryModuleTypes() {
    }

    public static void register(IEventBus bus) {
        MEMORY_MODULE_TYPES.register(bus);
    }
}
