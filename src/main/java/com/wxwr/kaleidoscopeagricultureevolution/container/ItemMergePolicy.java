package com.wxwr.kaleidoscopeagricultureevolution.container;

import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.world.item.ItemStack;

/**
 * 定义传入的物品堆如何合并到已占用的槽位中。
 */
@FunctionalInterface
public interface ItemMergePolicy {
    MergeResult merge(ItemStack existing, ItemStack incoming, int slotLimit);

    record MergeResult(ItemStack remaining, boolean changed) {
        public static MergeResult unchanged(ItemStack incoming) {
            return new MergeResult(incoming, false);
        }
    }

    ItemMergePolicy EXACT = (existing, incoming, slotLimit) -> {
        if (existing.isEmpty() || incoming.isEmpty()
                || !ItemStack.isSameItemSameTags(existing, incoming)) {
            return MergeResult.unchanged(incoming);
        }

        int room = Math.min(existing.getMaxStackSize(), slotLimit) - existing.getCount();
        if (room <= 0) {
            return MergeResult.unchanged(incoming);
        }

        int moved = Math.min(room, incoming.getCount());
        existing.grow(moved);
        ItemStack remaining = incoming.copy();
        remaining.shrink(moved);
        return new MergeResult(remaining, true);
    };

    /**
     * 为份额物品保留现有的缸（crock）行为，同时对普通物品堆
     * 使用精确的物品/NBT 匹配。
     */
    ItemMergePolicy EXACT_OR_PORTIONS = (existing, incoming, slotLimit) -> {
        if (!existing.isEmpty() && !incoming.isEmpty()
                && existing.getItem() == incoming.getItem()
                && existing.getItem() instanceof DurablePortionItem portionItem) {
            int existingPortions = portionItem.getPortions(existing);
            int incomingPortions = portionItem.getPortions(incoming);
            int room = portionItem.getMaxPortions() - existingPortions;
            if (room <= 0) {
                return MergeResult.unchanged(incoming);
            }

            int moved = Math.min(room, incomingPortions);
            existing.setTag(portionItem.withPortions(existingPortions + moved).getTag());
            ItemStack remaining = moved >= incomingPortions
                    ? ItemStack.EMPTY
                    : portionItem.withPortions(incomingPortions - moved);
            return new MergeResult(remaining, true);
        }
        return EXACT.merge(existing, incoming, slotLimit);
    };
}
