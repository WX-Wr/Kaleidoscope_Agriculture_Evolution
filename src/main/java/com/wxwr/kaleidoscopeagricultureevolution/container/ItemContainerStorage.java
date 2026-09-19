package com.wxwr.kaleidoscopeagricultureevolution.container;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 一个与 GUI 无关、基于槽位的物品容器。
 *
 * <p>容器如何向玩家暴露由所有者决定。这个类仅持有物品状态，
 * 以及插入、合并、提取和序列化物品堆的规则。</p>
 */
public final class ItemContainerStorage {
    private final NonNullList<ItemStack> items;
    private final int maxTotalCount;
    private final int maxCountPerSlot;
    private final Predicate<ItemStack> filter;
    private final ItemMergePolicy mergePolicy;
    private final Runnable changeListener;

    public ItemContainerStorage(int slotCount, int maxTotalCount, int maxCountPerSlot,
                                Predicate<ItemStack> filter, ItemMergePolicy mergePolicy,
                                Runnable changeListener) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        if (maxTotalCount <= 0) {
            throw new IllegalArgumentException("maxTotalCount must be positive");
        }
        if (maxCountPerSlot <= 0) {
            throw new IllegalArgumentException("maxCountPerSlot must be positive");
        }
        this.items = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        this.maxTotalCount = maxTotalCount;
        this.maxCountPerSlot = maxCountPerSlot;
        this.filter = filter == null ? stack -> true : filter;
        this.mergePolicy = mergePolicy == null ? ItemMergePolicy.EXACT : mergePolicy;
        this.changeListener = changeListener == null ? () -> {
        } : changeListener;
    }

    public int getSlots() {
        return items.size();
    }

    public int getOccupiedSlotCount() {
        int occupied = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    /**
     * 返回底层列表，供需要槽位顺序的配方和渲染器集成使用。
     * 调用者在修改槽位时必须使用 {@link #setItem(int, ItemStack)}，
     * 以便通知所有者。
     */
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return getOccupiedSlotCount() == 0;
    }

    public int getTotalCount() {
        return getCount(stack -> true);
    }

    public int getCount(Item item) {
        return getCount(stack -> stack.is(item));
    }

    public int getCount(Predicate<ItemStack> predicate) {
        int count = 0;
        for (ItemStack stack : items) {
            if (predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public ItemStack insert(ItemStack incoming) {
        return insert(incoming, false);
    }

    public ItemStack insert(ItemStack incoming, boolean simulate) {
        if (incoming.isEmpty() || !filter.test(incoming)) {
            return incoming.copy();
        }

        NonNullList<ItemStack> target = simulate ? copyItems(items) : items;
        int available = maxTotalCount - count(target, stack -> true);
        if (available <= 0) {
            return incoming.copy();
        }

        ItemStack overflow = ItemStack.EMPTY;
        ItemStack remaining = incoming.copy();
        if (remaining.getCount() > available) {
            overflow = remaining.copyWithCount(remaining.getCount() - available);
            remaining.setCount(available);
        }

        boolean changed = false;
        for (int i = 0; i < target.size() && !remaining.isEmpty(); i++) {
            ItemMergePolicy.MergeResult result = mergePolicy.merge(target.get(i), remaining, maxCountPerSlot);
            if (result.changed()) {
                changed = true;
                remaining = result.remaining();
            }
        }

        for (int i = 0; i < target.size() && !remaining.isEmpty(); i++) {
            if (!target.get(i).isEmpty()) {
                continue;
            }
            int count = Math.min(remaining.getCount(), getSlotLimit(remaining));
            target.set(i, remaining.copyWithCount(count));
            remaining.shrink(count);
            changed = true;
        }

        if (changed && !simulate) {
            changeListener.run();
        }
        return mergeRemainders(remaining, overflow);
    }

    public ItemStack extract(int slot, int amount) {
        return extract(slot, amount, false);
    }

    public ItemStack extract(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= items.size() || amount <= 0 || items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack current = items.get(slot);
        ItemStack result = current.copyWithCount(Math.min(amount, current.getCount()));
        if (!simulate) {
            current.shrink(result.getCount());
            if (current.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
            changeListener.run();
        }
        return result;
    }

    public ItemStack extractOneFromLast() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (!items.get(i).isEmpty()) {
                return extract(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    public int removeMatching(Predicate<ItemStack> predicate, int amount) {
        int remaining = Math.max(0, amount);
        int removed = 0;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack current = items.get(i);
            if (current.isEmpty() || !predicate.test(current)) {
                continue;
            }
            int taken = Math.min(remaining, current.getCount());
            current.shrink(taken);
            remaining -= taken;
            removed += taken;
            if (current.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
            }
        }
        if (removed > 0) {
            changeListener.run();
        }
        return removed;
    }

    public ItemStack removeFirstMatching(Predicate<ItemStack> predicate) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty() && predicate.test(items.get(i))) {
                return extract(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> removeAll() {
        NonNullList<ItemStack> removed = copyItems(items);
        boolean changed = !isEmpty();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (changed) {
            changeListener.run();
        }
        return removed;
    }

    public void clear() {
        boolean changed = !isEmpty();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (changed) {
            changeListener.run();
        }
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        changeListener.run();
    }

    public List<ItemStack> getNonEmptySnapshot() {
        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                snapshot.add(stack.copy());
            }
        }
        return snapshot;
    }

    public List<ItemTypeCountView.ItemTypeCount> getItemTypeCounts() {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        List<ItemTypeCountView.ItemTypeCount> result = new ArrayList<>();
        counts.forEach((item, count) -> result.add(new ItemTypeCountView.ItemTypeCount(item, count)));
        return result;
    }

    public List<ItemStack> drainNonEmpty() {
        NonNullList<ItemStack> removed = removeAll();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : removed) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    public void save(CompoundTag parent, String key) {
        CompoundTag itemsTag = new CompoundTag();
        ContainerHelper.saveAllItems(itemsTag, items);
        parent.put(key, itemsTag);
    }

    public void load(CompoundTag parent, String key) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        if (parent.contains(key)) {
            ContainerHelper.loadAllItems(parent.getCompound(key), items);
        }
    }

    private int getSlotLimit(ItemStack stack) {
        return Math.min(maxCountPerSlot, Math.min(stack.getMaxStackSize(), 64));
    }

    private static ItemStack mergeRemainders(ItemStack remaining, ItemStack overflow) {
        if (remaining.isEmpty()) {
            return overflow;
        }
        if (overflow.isEmpty()) {
            return remaining;
        }
        ItemStack result = remaining.copy();
        result.grow(overflow.getCount());
        return result;
    }

    private static int count(NonNullList<ItemStack> source, Predicate<ItemStack> predicate) {
        int count = 0;
        for (ItemStack stack : source) {
            if (predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static NonNullList<ItemStack> copyItems(NonNullList<ItemStack> source) {
        NonNullList<ItemStack> copy = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) {
            copy.set(i, source.get(i).copy());
        }
        return copy;
    }
}
