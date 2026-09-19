package com.wxwr.kaleidoscopeagricultureevolution.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;

import java.util.HashSet;
import java.util.Set;

/**
 * 处理农夫在绑定的耕牛旁进行的直接田间工作。
 */
final class FarmerWorkManager {

    void tick(FarmerEntity farmer, FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) {
            return;
        }
        if (ox.getCurrentWorkAction() != WorkAction.FERTILIZE) {
            tryFeedOx(farmer, ox, context);
        }
        if (ox.getCurrentWorkAction() == WorkAction.SOW) {
            tryRefillLouche(farmer, context);
        } else if (ox.getCurrentWorkAction() == WorkAction.FERTILIZE) {
            tryRefillFertilizer(farmer, context);
        }
    }

    private static void tryFeedOx(FarmerEntity farmer, PlowOxEntity ox, FarmerContext context) {
        if (ox.getBoostRemaining() != 0) {
            return;
        }
        if (!context.isFieldWorkState()) {
            return;
        }

        SimpleContainer inventory = farmer.getInventory();
        int wheatSlot = -1;
        int stewSlot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.is(Items.WHEAT) && wheatSlot < 0) {
                    wheatSlot = i;
                }
                if (isSaturationStew(stack) && stewSlot < 0) {
                    stewSlot = i;
                }
            }
        }

        boolean hasWheat = wheatSlot >= 0;
        boolean hasStew = stewSlot >= 0;
        if (!hasWheat && !hasStew) {
            return;
        }

        int remainingBlocks = context.remainingWork();
        if (hasWheat && hasStew) {
            if (remainingBlocks > 320) {
                feedStew(ox, inventory, stewSlot, true);
            } else {
                feedWheat(ox, inventory, wheatSlot);
            }
            return;
        }

        if (hasStew) {
            feedStew(ox, inventory, stewSlot, false);
        } else {
            feedWheat(ox, inventory, wheatSlot);
        }
    }

    private static void feedStew(PlowOxEntity ox, SimpleContainer inventory, int slot, boolean showParticles) {
        ox.setBoostRemaining(-1);
        ox.addEffect(new MobEffectInstance(MobEffects.SATURATION, -1, 0, false, showParticles));
        inventory.getItem(slot).shrink(1);
    }

    private static void feedWheat(PlowOxEntity ox, SimpleContainer inventory, int slot) {
        ox.setBoostRemaining(64);
        inventory.getItem(slot).shrink(1);
    }

    private static void tryRefillLouche(FarmerEntity farmer, FarmerContext context) {
        LoucheEntity louche = context.louche();
        if (louche == null) {
            return;
        }
        PlowOxEntity ox = context.ox();
        if (ox == null || ox.getCurrentWorkAction() != WorkAction.SOW) {
            return;
        }
        WorkFieldType fieldType = ox.getCurrentWorkFieldType();

        if (louche.getSeedCount() >= louche.getMaxSeedCount()) {
            return;
        }
        if (!context.isFieldWorkState()) {
            return;
        }

        SimpleContainer inventory = farmer.getInventory();
        Item neededSeed = louche.getSeedItem();
        if (neededSeed != null) {
            if (tryLoadFromInventory(louche, inventory, neededSeed, fieldType)) {
                return;
            }
            if (!trySwitchToAvailableSlot(louche, inventory, fieldType)) {
                return;
            }
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && LoucheEntity.isSeedAllowedForField(stack, fieldType)) {
                int loaded = louche.tryLoadSeed(stack);
                if (loaded > 0) {
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    break;
                }
            }
        }
    }

    private static void tryRefillFertilizer(FarmerEntity farmer, FarmerContext context) {
        LoucheEntity louche = context.louche();
        if (louche == null || louche.isModeSeed()) return;

        SimpleContainer inventory = farmer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && (stack.is(Items.ROTTEN_FLESH) || stack.is(Items.BONE_MEAL))) {
                if (louche.tryLoadFertilizer(stack) > 0 && stack.isEmpty()) {
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private static boolean tryLoadFromInventory(LoucheEntity louche, SimpleContainer inventory,
                                                Item neededSeed, WorkFieldType fieldType) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == neededSeed
                    && LoucheEntity.isSeedAllowedForField(stack, fieldType)) {
                int loaded = louche.tryLoadSeed(stack);
                if (loaded > 0) {
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean trySwitchToAvailableSlot(LoucheEntity louche, SimpleContainer inventory,
                                                    WorkFieldType fieldType) {
        Set<Item> available = new HashSet<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && LoucheEntity.isSeedAllowedForField(stack, fieldType)) {
                available.add(stack.getItem());
            }
        }
        if (available.isEmpty()) {
            return false;
        }

        int currentIdx = louche.getSeedIndex();
        int slotCount = louche.getSeedSlotCount();
        for (int i = 0; i < slotCount; i++) {
            int idx = (currentIdx + 1 + i) % slotCount;
            Item slotItem = louche.getSeedSlotItem(idx);
            if (slotItem != null && available.contains(slotItem)) {
                louche.switchToSeedSlot(idx);
                return true;
            }
        }
        return false;
    }

    private static boolean isSaturationStew(ItemStack stack) {
        return stack.is(Items.SUSPICIOUS_STEW) && PlowOxEntity.hasSaturationEffect(stack);
    }
}
