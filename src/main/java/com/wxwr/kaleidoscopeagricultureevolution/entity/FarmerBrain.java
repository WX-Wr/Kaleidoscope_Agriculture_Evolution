package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Set;

final class FarmerBrain {
    private FarmerBrain() {
    }

    static ImmutableList<MemoryModuleType<?>> getMemoryTypes() {
        return ImmutableList.of(
                MemoryModuleType.HOME,
                MemoryModuleType.JOB_SITE,
                MemoryModuleType.POTENTIAL_JOB_SITE,
                MemoryModuleType.MEETING_POINT,
                MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.VISIBLE_VILLAGER_BABIES,
                MemoryModuleType.NEAREST_PLAYERS,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
                MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
                MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET,
                MemoryModuleType.INTERACTION_TARGET,
                MemoryModuleType.BREED_TARGET,
                MemoryModuleType.PATH,
                MemoryModuleType.DOORS_TO_CLOSE,
                MemoryModuleType.NEAREST_BED,
                MemoryModuleType.HURT_BY,
                MemoryModuleType.HURT_BY_ENTITY,
                MemoryModuleType.NEAREST_HOSTILE,
                MemoryModuleType.SECONDARY_JOB_SITE,
                MemoryModuleType.HIDING_PLACE,
                MemoryModuleType.HEARD_BELL_TIME,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.LAST_SLEPT,
                MemoryModuleType.LAST_WOKEN,
                MemoryModuleType.LAST_WORKED_AT_POI,
                MemoryModuleType.GOLEM_DETECTED_RECENTLY,
                ModMemoryModuleTypes.FARMER_BOUND_OX.get(),
                ModMemoryModuleTypes.FARMER_BOUND_TOOL.get(),
                ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get(),
                ModMemoryModuleTypes.FARMER_SUPPLY_STATE.get(),
                ModMemoryModuleTypes.FARMER_OX_STATE.get(),
                ModMemoryModuleTypes.FARMER_IN_TARGET_AREA.get(),
                ModMemoryModuleTypes.FARMER_WORK_REMAINING.get(),
                ModMemoryModuleTypes.FARMER_NEEDS_STORE.get(),
                ModMemoryModuleTypes.FARMER_TARGET_POS.get()
        );
    }

    static ImmutableList<SensorType<? extends Sensor<? super Villager>>> getSensorTypes() {
        return ImmutableList.of(
                SensorType.NEAREST_LIVING_ENTITIES,
                SensorType.NEAREST_PLAYERS,
                SensorType.NEAREST_ITEMS,
                SensorType.NEAREST_BED,
                SensorType.HURT_BY,
                SensorType.VILLAGER_HOSTILES,
                SensorType.VILLAGER_BABIES,
                SensorType.SECONDARY_POIS,
                SensorType.GOLEM_DETECTED
        );
    }

    static void registerBrainGoals(Brain<Villager> brain) {
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(2, new FarmerSyncMemoryTask()),
                Pair.of(3, new FarmerSupplyUpdateTask()),
                Pair.of(4, new FarmerSupplyTickTask()),
                Pair.of(99, new FarmerUpdateActivityTask())
        ));
        brain.addActivity(Activity.IDLE, ImmutableList.of(
                Pair.of(4, new FarmerPrepareStoreTask()),
                Pair.of(5, new FarmerStoreSupplyTask()),
                Pair.of(20, new FarmerFinishTask()),
                Pair.of(99, new DoNothing(30, 60))
        ));
        brain.addActivity(Activity.WORK, ImmutableList.of(
                Pair.of(5, new FarmerFetchSupplyTask()),
                Pair.of(6, new FarmerStoreSupplyTask()),
                Pair.of(20, new FarmerMoveToTargetTask()),
                Pair.of(21, new FarmerWorkTask()),
                Pair.of(99, new DoNothing(30, 60))
        ));
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    private static final class FarmerUpdateActivityTask extends Behavior<Villager> {
        private FarmerUpdateActivityTask() {
            super(ImmutableMap.of());
        }

        @Override
        protected void start(ServerLevel level, Villager villager, long gameTime) {
            syncActivity(villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            syncActivity(villager);
        }

        private static void syncActivity(Villager villager) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            Activity activity = farmer.getMovementState() == FarmerEntity.MovementState.IDLE
                    ? Activity.IDLE
                    : Activity.WORK;
            farmer.getBrain().setActiveActivityIfPossible(activity);
        }
    }

    private static final class FarmerSupplyUpdateTask extends Behavior<Villager> {
        private FarmerSupplyUpdateTask() {
            super(ImmutableMap.of());
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            FarmerContext context = FarmerContext.capture(farmer, farmer.supplyManager());
            farmer.supplyManager().update(farmer, farmer.getMovementState(), context);
        }
    }

    private static final class FarmerSyncMemoryTask extends Behavior<Villager> {
        private FarmerSyncMemoryTask() {
            super(ImmutableMap.of());
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            FarmerContext context = FarmerContext.capture(farmer, farmer.supplyManager());
            farmer.syncBrainContext(context, farmer.getMovementState());
        }
    }

    private static final class FarmerSupplyTickTask extends Behavior<Villager> {
        private FarmerSupplyTickTask() {
            super(ImmutableMap.of());
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            FarmerContext context = FarmerContext.capture(farmer, farmer.supplyManager());
            if (farmer.supplyManager().tick(farmer, context)) {
            }
        }
    }

    private static final class FarmerFetchSupplyTask extends Behavior<Villager> {
        private static final float CHEST_WALK_SPEED = 0.5F;

        private FarmerFetchSupplyTask() {
            super(ImmutableMap.of(
                    ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get(), MemoryStatus.VALUE_PRESENT,
                    ModMemoryModuleTypes.FARMER_SUPPLY_STATE.get(), MemoryStatus.VALUE_PRESENT,
                    MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
            ), 1200);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity farmer
                    && farmer.getChestTask() == FarmerEntity.ChestTask.TO_FETCH
                    && farmer.supplyChestPos() != null;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            BlockPos chestPos = farmer.supplyChestPos();
            if (chestPos == null || farmer.isSupplyInteracting()) {
                return;
            }
            if (farmer.isAtSupplyChest()) {
                farmer.getNavigation().stop();
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                farmer.tryStartFetchInteraction();
                return;
            }
            BehaviorUtils.setWalkAndLookTargetMemories(farmer, chestPos, CHEST_WALK_SPEED, 1);
        }

        @Override
        protected void stop(ServerLevel level, Villager villager, long gameTime) {
            if (villager instanceof FarmerEntity farmer
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_FETCH) {
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        }
    }

    private static final class FarmerPrepareStoreTask extends Behavior<Villager> {
        private FarmerPrepareStoreTask() {
            super(ImmutableMap.of(
                    ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get(), MemoryStatus.VALUE_PRESENT,
                    ModMemoryModuleTypes.FARMER_NEEDS_STORE.get(), MemoryStatus.VALUE_PRESENT
            ));
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return false;
            }
            if (farmer.getChestTask() != FarmerEntity.ChestTask.NONE) {
                return false;
            }
            if (farmer.supplyChestPos() == null) {
                return false;
            }
            if (!farmer.needsStore()) {
                return false;
            }
            return true;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            if (farmer.getChestTask() != FarmerEntity.ChestTask.NONE || !farmer.needsStore()) {
                return;
            }
            farmer.setChestTask(FarmerEntity.ChestTask.TO_STORE);
        }
    }

    private static final class FarmerStoreSupplyTask extends Behavior<Villager> {
        private static final float CHEST_WALK_SPEED = 0.5F;

        private FarmerStoreSupplyTask() {
            super(ImmutableMap.of(
                    ModMemoryModuleTypes.FARMER_SUPPLY_CHEST.get(), MemoryStatus.VALUE_PRESENT,
                    ModMemoryModuleTypes.FARMER_SUPPLY_STATE.get(), MemoryStatus.VALUE_PRESENT,
                    MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
            ), 1200);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return false;
            }
            if (farmer.getChestTask() != FarmerEntity.ChestTask.TO_STORE) {
                return false;
            }
            if (farmer.supplyChestPos() == null) {
                return false;
            }
            return true;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (!(villager instanceof FarmerEntity farmer)) {
                return;
            }
            BlockPos chestPos = farmer.supplyChestPos();
            if (chestPos == null || farmer.isSupplyInteracting()) {
                return;
            }
            if (farmer.isAtSupplyChest()) {
                farmer.getNavigation().stop();
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                farmer.tryStartStoreInteraction();
                return;
            }
            BehaviorUtils.setWalkAndLookTargetMemories(farmer, chestPos, CHEST_WALK_SPEED, 1);
        }

        @Override
        protected void stop(ServerLevel level, Villager villager, long gameTime) {
            if (villager instanceof FarmerEntity farmer
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_STORE) {
                farmer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        }
    }

    private static final class FarmerMoveToTargetTask extends Behavior<Villager> {
        private FarmerMoveToTargetTask() {
            super(ImmutableMap.of(ModMemoryModuleTypes.FARMER_BOUND_OX.get(), MemoryStatus.VALUE_PRESENT), 1200);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity farmer
                    && farmer.getMovementState() == FarmerEntity.MovementState.MOVING_TO_TARGET
                    && !farmer.isSupplyInteracting()
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_FETCH
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_STORE
                    && farmer.getBoundOx() != null;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (villager instanceof FarmerEntity farmer) {
                PlowOxEntity ox = farmer.getBoundOx();
                if (ox != null) {
                    farmer.getNavigation().stop();
                    farmer.tickMoveToTarget(ox);
                }
            }
        }
    }

    private static final class FarmerWorkTask extends Behavior<Villager> {
        private FarmerWorkTask() {
            super(ImmutableMap.of(ModMemoryModuleTypes.FARMER_BOUND_OX.get(), MemoryStatus.VALUE_PRESENT), 1200);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity farmer
                    && farmer.getMovementState() == FarmerEntity.MovementState.WORKING
                    && !farmer.isSupplyInteracting()
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_FETCH
                    && farmer.getChestTask() != FarmerEntity.ChestTask.TO_STORE
                    && farmer.getBoundOx() != null;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (villager instanceof FarmerEntity farmer) {
                farmer.getNavigation().stop();
                farmer.tickWorking(FarmerContext.capture(farmer, farmer.supplyManager()));
            }
        }
    }

    private static final class FarmerFinishTask extends Behavior<Villager> {
        private FarmerFinishTask() {
            super(ImmutableMap.of(ModMemoryModuleTypes.FARMER_BOUND_OX.get(), MemoryStatus.VALUE_PRESENT), 1200);
        }

        @Override
        protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
            return villager instanceof FarmerEntity farmer
                    && farmer.getMovementState() == FarmerEntity.MovementState.IDLE
                    && farmer.getChestTask() == FarmerEntity.ChestTask.NONE
                    && farmer.getBoundOx() != null;
        }

        @Override
        protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
            return checkExtraStartConditions(level, villager);
        }

        @Override
        protected void tick(ServerLevel level, Villager villager, long gameTime) {
            if (villager instanceof FarmerEntity farmer) {
                farmer.tickIdleFinish(FarmerContext.capture(farmer, farmer.supplyManager()));
            }
        }
    }
}
