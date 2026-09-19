package com.wxwr.kaleidoscopeagricultureevolution.entity;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * 连接由耕牛驱动的农夫状态机与原版 Brain 记忆之间的桥梁。
 */
final class FarmerBrainController {
    private boolean initialized;

    static void clearConvertedVillagerState(LivingEntity entity) {
        if (!(entity instanceof Villager villager)) {
            return;
        }

        villager.releasePoi(MemoryModuleType.HOME);
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.releasePoi(MemoryModuleType.MEETING_POINT);
        clearVolatileMemories(villager.getBrain());
    }

    static void clearVolatileMemories(Brain<?> brain) {
        if (brain == null) {
            return;
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    void bootstrap(FarmerEntity farmer) {
        Brain<?> brain = farmer.getBrain();
        if (brain == null || initialized) {
            return;
        }
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        initialized = true;
    }

    void update(FarmerEntity farmer, FarmerContext context, FarmerEntity.MovementState movementState) {
        Brain<?> brain = farmer.getBrain();
        if (brain == null) {
            return;
        }

        PlowOxEntity ox = context.ox();
        if (ox != null && farmer.level() instanceof ServerLevel serverLevel) {
            brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(serverLevel.dimension(), ox.blockPosition()));
            brain.setMemory(ModMemoryModuleTypes.FARMER_BOUND_OX.get(), ox.getUUID());
            setOrErase(brain, ModMemoryModuleTypes.FARMER_BOUND_TOOL.get(),
                    context.tool() == null ? null : context.tool().getUUID());
            setOrErase(brain, ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get(), farmer.supplyChestPos());
            brain.setMemory(ModMemoryModuleTypes.FARMER_SUPPLY_STATE.get(), farmer.getChestTask().name());
            brain.setMemory(ModMemoryModuleTypes.FARMER_OX_STATE.get(), context.oxState().name());
            brain.setMemory(ModMemoryModuleTypes.FARMER_IN_TARGET_AREA.get(), context.inTargetArea());
            brain.setMemory(ModMemoryModuleTypes.FARMER_WORK_REMAINING.get(), context.remainingWork());
            brain.setMemory(ModMemoryModuleTypes.FARMER_NEEDS_STORE.get(), context.supplyNeedsStore());
            brain.setMemory(ModMemoryModuleTypes.FARMER_TARGET_POS.get(), farmer.targetAreaBlock(ox));
        } else {
            brain.eraseMemory(MemoryModuleType.JOB_SITE);
            eraseFarmerMemories(brain);
        }

        if (movementState != FarmerEntity.MovementState.IDLE && !isBrainManagedChestTask(farmer)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    void tick(FarmerEntity farmer, ServerLevel level) {
        Brain<Villager> brain = farmer.getBrain();
        brain.tick(level, farmer);
    }

    private static void eraseFarmerMemories(Brain<?> brain) {
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_BOUND_OX.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_BOUND_TOOL.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_SUPPLY_STATE.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_OX_STATE.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_IN_TARGET_AREA.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_WORK_REMAINING.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_NEEDS_STORE.get());
        brain.eraseMemory(ModMemoryModuleTypes.FARMER_TARGET_POS.get());
    }

    private static <T> void setOrErase(Brain<?> brain, MemoryModuleType<T> memory, T value) {
        if (value == null) {
            brain.eraseMemory(memory);
        } else {
            brain.setMemory(memory, value);
        }
    }

    private static boolean isBrainManagedChestTask(FarmerEntity farmer) {
        FarmerEntity.ChestTask task = farmer.getChestTask();
        return task == FarmerEntity.ChestTask.TO_FETCH || task == FarmerEntity.ChestTask.TO_STORE;
    }
}
