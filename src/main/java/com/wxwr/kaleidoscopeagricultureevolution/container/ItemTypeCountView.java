package com.wxwr.kaleidoscopeagricultureevolution.container;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 只读的物品类型计数，用于不与存储交互的显示。
 */
public interface ItemTypeCountView {
    List<ItemTypeCount> getItemTypeCounts();

    record ItemTypeCount(Item item, int count) {
        public ItemStack toDisplayStack() {
            return new ItemStack(item, count);
        }
    }
}
