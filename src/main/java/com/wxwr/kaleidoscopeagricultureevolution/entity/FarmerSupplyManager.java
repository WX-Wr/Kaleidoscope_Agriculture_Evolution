package com.wxwr.kaleidoscopeagricultureevolution.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueReason;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueState;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkItemRequirement;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRule;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkSeedGroup;

/**
 * 农夫补给行为管理器，灵感来自 TouhouLittleMaid 在实体状态、
 * 活动分派和具体行为之间的划分。
 */
final class FarmerSupplyManager {
    private static final int CHEST_SEARCH_RADIUS = 32;
    private static final double CHEST_REACH_DIST = 2.0;

    @Nullable
    private BlockPos chestPos;
    private boolean workDone;
    private boolean warnRecoveryActive;
    @Nullable
    private ChestInteractionTask activeChestTask;

    boolean isTravelling(FarmerEntity farmer) {
        FarmerEntity.ChestTask task = farmer.getChestTask();
        return this.isInteracting()
                || task == FarmerEntity.ChestTask.TO_FETCH
                || task == FarmerEntity.ChestTask.TO_STORE
                || (warnRecoveryActive && task == FarmerEntity.ChestTask.FETCHED);
    }

    boolean needsStore(FarmerEntity farmer) {
        FarmerEntity.ChestTask task = farmer.getChestTask();
        return this.isInteracting()
                || task == FarmerEntity.ChestTask.FETCHED
                || task == FarmerEntity.ChestTask.TO_STORE
                || hasStoreableItems(farmer);
    }

    boolean isWorkDone() {
        return workDone;
    }

    @Nullable
    BlockPos chestPos() {
        return this.chestPos;
    }

    void clearWorkDone() {
        this.workDone = false;
    }

    boolean isWarnRecoveryReturning(FarmerEntity farmer) {
        return warnRecoveryActive && farmer.getChestTask() == FarmerEntity.ChestTask.FETCHED;
    }

    boolean hasStoreableItems(FarmerEntity farmer) {
        if (containsStoreableItems(farmer.getInventory())) {
            return true;
        }

        PlowOxEntity ox = farmer.getBoundOx();
        if (ox == null) {
            return false;
        }

        AbstractDraggableEntity tool = ox.getToolEntity();
        return tool instanceof LoucheEntity louche && louche.isAlive() && !louche.isModeEmpty();
    }

    void update(FarmerEntity farmer, FarmerEntity.MovementState state, FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) {
            return;
        }

        if (warnRecoveryActive
                && farmer.getChestTask() == FarmerEntity.ChestTask.FETCHED
                && context.inTargetArea()) {
            ox.resumeWorkAfterSupply();
            farmer.setChestTask(FarmerEntity.ChestTask.NONE);
            warnRecoveryActive = false;
            return;
        }

        if (farmer.getChestTask() == FarmerEntity.ChestTask.NONE && this.activeChestTask == null) {
            PlowOxEntity.OxState oxState = context.oxState();
            if (oxState == PlowOxEntity.OxState.MOVE_TO_CORNER
                    || oxState == PlowOxEntity.OxState.SELECT_CORNER
                    || oxState == PlowOxEntity.OxState.SELECT_DIRECTION
                    || oxState == PlowOxEntity.OxState.MOVE_TO_START
                    || oxState == PlowOxEntity.OxState.START_WORKING) {
                this.chestPos = findChest(farmer);
                if (this.chestPos != null) {
                    farmer.setChestTask(FarmerEntity.ChestTask.TO_FETCH);
                } else {
                }
                this.workDone = true;
            }
        }

        // 补给一旦取回，就保留它们，直到耕牛真正空闲。
        // SELECT_CORNER / SELECT_DIRECTION 仍是准备工作周期的一部分。
        if (state == FarmerEntity.MovementState.IDLE && farmer.getChestTask() == FarmerEntity.ChestTask.FETCHED) {
            farmer.setChestTask(FarmerEntity.ChestTask.TO_STORE);
        }

        if (state == FarmerEntity.MovementState.IDLE
                && (farmer.getChestTask() == FarmerEntity.ChestTask.TO_FETCH || isActiveFetchTask())) {
            cancel(farmer);
            farmer.setChestTask(FarmerEntity.ChestTask.NONE);
        }
    }

    private boolean isActiveFetchTask() {
        return this.activeChestTask != null && "fetch".equals(this.activeChestTask.getTaskType());
    }

    boolean tick(FarmerEntity farmer, FarmerContext context) {
        if (context.ox() == null) {
            cancel(farmer);
            return false;
        }

        if (this.activeChestTask != null && this.activeChestTask.isActive()) {
            if (this.activeChestTask.tick()) {
                FarmerEntity.ChestTask completedTask = farmer.getChestTask();
                farmer.setChestInteracting(false);
                this.activeChestTask = null;
                if (farmer.getWorkIssueState() == WorkIssueState.ERROR) {
                    return false;
                }
                if (completedTask == FarmerEntity.ChestTask.TO_STORE) {
                    if (hasStoreableItems(farmer)) {
                        farmer.setChestTask(FarmerEntity.ChestTask.TO_STORE);
                    } else {
                        farmer.setChestTask(FarmerEntity.ChestTask.NONE);
                        this.chestPos = null;
                    }
                } else {
                    farmer.setChestTask(FarmerEntity.ChestTask.FETCHED);
                }
                return false;
            }
            return true;
        }

        switch (farmer.getChestTask()) {
            case TO_FETCH -> {
                if (this.chestPos == null) {
                    farmer.setChestTask(FarmerEntity.ChestTask.NONE);
                    return false;
                }
                return true;
            }
            case TO_STORE -> {
                if (this.chestPos == null) {
                    farmer.setChestTask(FarmerEntity.ChestTask.NONE);
                    return false;
                }
                return true;
            }
            case FETCHED, NONE -> {
                return false;
            }
        }
        return false;
    }

    void retrieveLoucheInventory(FarmerEntity farmer, FarmerContext context) {
        retrieveLoucheInventoryFromContext(farmer, context);
    }

    private static void retrieveLoucheInventoryFromContext(FarmerEntity farmer, FarmerContext context) {
        LoucheEntity louche = context.louche();
        if (louche == null) {
            return;
        }

        java.util.List<ItemStack> retrieved = louche.retrieveAllItems();
        if (retrieved.isEmpty()) {
            return;
        }

        SimpleContainer inventory = farmer.getInventory();
        for (ItemStack stack : retrieved) {
            if (!stack.isEmpty()) {
                ItemStack remainder = inventory.addItem(stack);
                if (!remainder.isEmpty()) {
                    farmer.spawnAtLocation(remainder);
                }
            }
        }
    }

    private static boolean containsStoreableItems(SimpleContainer inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isFeedOrSeed(inventory.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    void cancel(FarmerEntity farmer) {
        if (this.activeChestTask != null) {
            this.activeChestTask.cancel();
            this.activeChestTask = null;
        }
        warnRecoveryActive = false;
        farmer.setChestInteracting(false);
    }

    void save(CompoundTag tag, FarmerEntity farmer) {
        if (this.chestPos != null) {
            tag.putLong("ChestPos", this.chestPos.asLong());
        }
        tag.putString("ChestTask", farmer.getChestTask().name());
        tag.putBoolean("WorkDone", this.workDone);
        tag.putBoolean("WarnRecoveryActive", this.warnRecoveryActive);
        if (this.activeChestTask != null && this.activeChestTask.isActive()) {
            CompoundTag taskTag = new CompoundTag();
            this.activeChestTask.save(taskTag);
            tag.put("ActiveChestTask", taskTag);
            tag.putString("ActiveChestTaskType", this.activeChestTask.getTaskType());
        }
    }

    void load(CompoundTag tag, FarmerEntity farmer) {
        if (tag.contains("ChestPos")) {
            this.chestPos = BlockPos.of(tag.getLong("ChestPos"));
        }
        if (tag.contains("ChestTask")) {
            farmer.setChestTask(FarmerEntity.readChestTask(tag.getString("ChestTask")));
        }
        if (tag.contains("WorkDone")) {
            this.workDone = tag.getBoolean("WorkDone");
        }
        if (tag.contains("WarnRecoveryActive")) {
            this.warnRecoveryActive = tag.getBoolean("WarnRecoveryActive");
        }
        if (tag.contains("ActiveChestTask") && this.chestPos != null) {
            CompoundTag taskTag = tag.getCompound("ActiveChestTask");
            String type = tag.getString("ActiveChestTaskType");
            BlockPos targetChest = this.chestPos;
            this.activeChestTask = "fetch".equals(type)
                    ? new ChestInteractionTask(targetChest, farmer.level(), "fetch",
                            () -> doFetchFromChest(farmer, targetChest, FarmerContext.capture(farmer, this)))
                    : new ChestInteractionTask(targetChest, farmer.level(), "store",
                            () -> doStoreToChest(farmer, targetChest, FarmerContext.capture(farmer, this)));
            ChestInteractionTask.load(taskTag, this.activeChestTask);
            this.activeChestTask.resumeLidAfterLoad();
            if (this.activeChestTask.getTimer() > 0) {
                farmer.setChestInteracting(true);
            }
        }
    }

    boolean isInteracting() {
        return this.activeChestTask != null && this.activeChestTask.isActive();
    }

    boolean isAtChest(FarmerEntity farmer) {
        if (this.chestPos == null) {
            return false;
        }
        return distanceToChest(farmer, this.chestPos) < CHEST_REACH_DIST;
    }

    boolean tryStartFetchInteraction(FarmerEntity farmer) {
        if (this.chestPos == null || farmer.getChestTask() != FarmerEntity.ChestTask.TO_FETCH) {
            return false;
        }
        if (ChestBlockEntity.getOpenCount(farmer.level(), this.chestPos) > 0) {
            return false;
        }
        BlockPos targetChest = this.chestPos;
        this.activeChestTask = new ChestInteractionTask(targetChest, farmer.level(), "fetch",
                () -> doFetchFromChest(farmer, targetChest, FarmerContext.capture(farmer, this)));
        this.activeChestTask.start();
        farmer.setChestInteracting(true);
        return true;
    }

    boolean tryStartWarnRecovery(FarmerEntity farmer) {
        if (farmer.getWorkIssueState() != WorkIssueState.WARN || warnRecoveryActive) {
            return false;
        }
        PlowOxEntity ox = farmer.getBoundOx();
        if (ox == null) {
            return false;
        }
        BlockPos targetChest = this.chestPos != null ? this.chestPos : findChest(farmer);
        if (targetChest == null || !hasOptionalSupplyInChest(farmer, targetChest, ox)) {
            return false;
        }
        this.chestPos = targetChest;
        if (!ox.pauseWorkForSupply() && ox.getOxState() != PlowOxEntity.OxState.WAITING) {
            return false;
        }
        warnRecoveryActive = true;
        farmer.setChestTask(FarmerEntity.ChestTask.TO_FETCH);
        return true;
    }

    private static boolean hasOptionalSupplyInChest(FarmerEntity farmer, BlockPos chestPos, PlowOxEntity ox) {
        BlockEntity blockEntity = farmer.level().getBlockEntity(chestPos);
        if (blockEntity == null) return false;
        var optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty()) return false;

        WorkRule rule = WorkRuleRegistry.findRule(ox.getCurrentWorkFieldType(), ox.getCurrentWorkAction()).orElse(null);
        if (rule == null || rule.optional().isEmpty()) return false;

        IItemHandler handler = optional.get();
        for (WorkItemRequirement requirement : rule.optional()) {
            WorkSeedGroup seedGroup = requirement.isSeedGroupRequirement()
                    ? WorkRuleRegistry.findSeedGroup(requirement.seedGroup()).orElse(null)
                    : null;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (matchesRequirement(handler.getStackInSlot(i), requirement, seedGroup,
                        ox.getCurrentWorkFieldType(), ox.getCurrentWorkAction())) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean tryStartStoreInteraction(FarmerEntity farmer) {
        if (this.chestPos == null || farmer.getChestTask() != FarmerEntity.ChestTask.TO_STORE) {
            return false;
        }
        if (ChestBlockEntity.getOpenCount(farmer.level(), this.chestPos) > 0) {
            return false;
        }
        BlockPos targetChest = this.chestPos;
        this.activeChestTask = new ChestInteractionTask(targetChest, farmer.level(), "store",
                () -> doStoreToChest(farmer, targetChest, FarmerContext.capture(farmer, this)));
        this.activeChestTask.start();
        farmer.setChestInteracting(true);
        return true;
    }

    private static double distanceToChest(FarmerEntity farmer, BlockPos target) {
        double dx = target.getX() + 0.5 - farmer.getX();
        double dy = target.getY() - farmer.getY();
        double dz = target.getZ() + 0.5 - farmer.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Nullable
    private static BlockPos findChest(FarmerEntity farmer) {
        AABB range = farmer.getBoundingBox().inflate(CHEST_SEARCH_RADIUS);
        Level level = farmer.level();
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, range)) {
            if (!frame.getItem().is(Items.WHEAT)) {
                continue;
            }
            Direction facing = frame.getDirection();
            BlockPos behind = frame.blockPosition().relative(facing.getOpposite());
            BlockEntity blockEntity = level.getBlockEntity(behind);
            if (blockEntity == null) {
                continue;
            }
            if (blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().isPresent()) {
                return behind.immutable();
            }
        }
        return null;
    }

    private static void doFetchFromChest(FarmerEntity farmer, BlockPos chestPos, FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) {
            return;
        }
        BlockEntity blockEntity = farmer.level().getBlockEntity(chestPos);
        if (blockEntity == null) {
            return;
        }
        var optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty()) {
            return;
        }
        IItemHandler handler = optional.get();
        SimpleContainer inventory = farmer.getInventory();
        WorkFieldType fieldType = ox.getCurrentWorkFieldType();
        WorkAction action = ox.getCurrentWorkAction();
        WorkRule rule = WorkRuleRegistry.findRule(fieldType, action).orElse(null);
        if (rule == null) return;

        for (WorkItemRequirement requirement : rule.required()) {
            if (!fetchRequirement(handler, inventory, requirement, fieldType, action)) {
                farmer.enterWorkIssue(WorkIssueState.ERROR, WorkIssueReason.MISSING_REQUIRED_SUPPLY);
                return;
            }
        }
        boolean missingOptional = false;
        for (WorkItemRequirement requirement : rule.optional()) {
            if (!fetchRequirement(handler, inventory, requirement, fieldType, action)) {
                missingOptional = true;
            }
        }
        farmer.enterWorkIssue(
                missingOptional ? WorkIssueState.WARN : WorkIssueState.NORMAL,
                missingOptional ? WorkIssueReason.MISSING_OPTIONAL_SUPPLY : WorkIssueReason.NONE);
    }

    private static boolean fetchRequirement(IItemHandler handler, SimpleContainer inventory,
                                            WorkItemRequirement requirement,
                                            WorkFieldType fieldType, WorkAction action) {
        int remaining = requirement.max();
        WorkSeedGroup seedGroup = requirement.isSeedGroupRequirement()
                ? WorkRuleRegistry.findSeedGroup(requirement.seedGroup()).orElse(null)
                : null;
        if (requirement.isSeedGroupRequirement() && seedGroup == null) return false;

        if (hasRequirement(inventory, requirement, seedGroup, fieldType, action)) {
            return true;
        }

        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!matchesRequirement(stack, requirement, seedGroup, fieldType, action)) continue;

            int toTake = Math.min(remaining, stack.getCount());
            ItemStack extracted = handler.extractItem(i, toTake, false);
            if (extracted.isEmpty()) continue;
            ItemStack remainder = inventory.addItem(extracted);
            int inserted = extracted.getCount() - remainder.getCount();
            remaining -= inserted;
            if (!remainder.isEmpty()) {
                handler.insertItem(i, remainder, false);
            }
            if (inserted == 0) break;
        }
        return hasRequirement(inventory, requirement, seedGroup, fieldType, action);
    }

    private static boolean hasRequirement(SimpleContainer inventory, WorkItemRequirement requirement,
                                          @Nullable WorkSeedGroup seedGroup,
                                          WorkFieldType fieldType, WorkAction action) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (matchesRequirement(inventory.getItem(i), requirement, seedGroup, fieldType, action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRequirement(ItemStack stack, WorkItemRequirement requirement,
                                              @Nullable WorkSeedGroup seedGroup,
                                              WorkFieldType fieldType, WorkAction action) {
        if (stack.isEmpty()) return false;
        if (!requirement.isSeedGroupRequirement()) return requirement.matches(stack);
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || seedGroup == null || seedGroup.isExcluded(itemId)) return false;
        return seedGroup.isExplicitlyIncluded(itemId)
                || (seedGroup.includeBushBlockSeeds()
                && LoucheEntity.isSeedAllowedForField(stack, fieldType));
    }

    private static void doStoreToChest(FarmerEntity farmer, BlockPos chestPos, FarmerContext context) {
        BlockEntity blockEntity = farmer.level().getBlockEntity(chestPos);
        if (blockEntity == null) {
            return;
        }
        var optional = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
        if (optional.isEmpty()) {
            return;
        }
        IItemHandler handler = optional.get();

        retrieveLoucheInventoryFromContext(farmer, context);

        SimpleContainer inventory = farmer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isFeedOrSeed(stack)) {
                for (int j = 0; j < handler.getSlots() && !stack.isEmpty(); j++) {
                    stack = handler.insertItem(j, stack, false);
                }
                inventory.setItem(i, stack);
            }
        }
    }

    private static boolean isFeedOrSeed(ItemStack stack) {
        return stack.is(Items.WHEAT) || isStew(stack)
                || stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE_MEAL)
                || LoucheEntity.isSeedItem(stack.getItem());
    }

    private static boolean isStew(ItemStack stack) {
        return stack.is(Items.SUSPICIOUS_STEW) && PlowOxEntity.hasSaturationEffect(stack);
    }
}
