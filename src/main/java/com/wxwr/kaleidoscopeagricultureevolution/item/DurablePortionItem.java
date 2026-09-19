package com.wxwr.kaleidoscopeagricultureevolution.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class DurablePortionItem extends Item {
    private static final String PORTIONS_TAG = "Portions";

    private final Supplier<ItemStack> baseItem;
    private final Supplier<ItemStack> exhaustedRemainder;
    private final int maxPortions;

    protected DurablePortionItem(Properties properties, int maxPortions, Supplier<ItemStack> baseItem,
                                 Supplier<ItemStack> exhaustedRemainder) {
        super(properties);
        this.maxPortions = maxPortions;
        this.baseItem = baseItem;
        this.exhaustedRemainder = exhaustedRemainder;
    }

    public ItemStack consumePortions(ItemStack stack, int portions) {
        if (stack.isEmpty() || portions <= 0) {
            return stack;
        }

        ItemStack result = stack.copy();
        int nextPortions = getPortions(result) - portions;
        if (nextPortions <= 0) {
            return getExhaustedRemainder();
        }

        setPortions(result, nextPortions);
        return result;
    }

    public ItemStack addPortions(ItemStack stack, int portions) {
        if (stack.isEmpty() || portions <= 0) {
            return stack;
        }

        ItemStack result = stack.copy();
        setPortions(result, Math.min(maxPortions, getPortions(result) + portions));
        return result;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(getPortions(stack) * 13.0F / maxPortions);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFFD84A;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.kaleidoscope_agriculture_evolution.portions",
                getPortions(stack), maxPortions).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return false;
    }

    public int getMaxPortions() {
        return maxPortions;
    }

    public int getPortions(ItemStack stack) {
        if (!stack.hasTag() || stack.getTag() == null || !stack.getTag().contains(PORTIONS_TAG)) {
            return maxPortions;
        }
        return stack.getTag().getInt(PORTIONS_TAG);
    }

    public boolean isFull(ItemStack stack) {
        return getPortions(stack) >= maxPortions;
    }

    public ItemStack fullStack() {
        return withPortions(maxPortions);
    }

    public ItemStack withPortions(int portions) {
        ItemStack stack = new ItemStack(this);
        setPortions(stack, Math.max(1, Math.min(maxPortions, portions)));
        return stack;
    }

    private static void setPortions(ItemStack stack, int portions) {
        stack.getOrCreateTag().putInt(PORTIONS_TAG, portions);
    }

    public ItemStack getExhaustedRemainder() {
        ItemStack remainder = exhaustedRemainder.get();
        return remainder.isEmpty()
                ? ItemStack.EMPTY
                : new ItemStack(remainder.getItem(), remainder.getCount());
    }

    public ItemStack getBaseItem() {
        return baseItem.get().copy();
    }

    public boolean matchesBaseItem(ItemStack stack) {
        ItemStack baseStack = baseItem.get();
        return !baseStack.isEmpty() && ItemStack.isSameItemSameTags(stack, baseStack);
    }

    /**
     * 以「基础物品」为键的反查索引，懒加载构建一次（整个注册表只扫一遍）。
     *
     * <p>替代原先「每次交互都遍历整个 {@link ForgeRegistries#ITEMS}」的做法。
     * 由于基座物品可能带 NBT（例如水瓶需要 {@code Potion} 标签），索引只按 Item 粗筛，
     * 命中后仍需用 {@link #matchesBaseItem(ItemStack)} 精判。
     */
    private static volatile Map<Item, List<DurablePortionItem>> baseItemIndex;

    /** 找出能把给定物品「装满」的份装物品；没有则返回 {@code null}。 */
    @Nullable
    public static DurablePortionItem findForBaseItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        List<DurablePortionItem> candidates = baseItemIndex().get(stack.getItem());
        if (candidates == null) {
            return null;
        }
        for (DurablePortionItem candidate : candidates) {
            if (candidate.matchesBaseItem(stack)) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<Item, List<DurablePortionItem>> baseItemIndex() {
        Map<Item, List<DurablePortionItem>> cached = baseItemIndex;
        if (cached != null) {
            return cached;
        }

        Map<Item, List<DurablePortionItem>> built = new HashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof DurablePortionItem portion) {
                ItemStack base = portion.getBaseItem();
                if (!base.isEmpty()) {
                    built.computeIfAbsent(base.getItem(), key -> new ArrayList<>()).add(portion);
                }
            }
        }
        baseItemIndex = built;
        return built;
    }

    public boolean hasExhaustedRemainder() {
        return !exhaustedRemainder.get().isEmpty();
    }

    public static ItemStack consume(ItemStack stack, int portions) {
        if (stack.getItem() instanceof DurablePortionItem portionItem) {
            return portionItem.consumePortions(stack, portions);
        }
        return stack;
    }

    public static ItemStack add(ItemStack stack, int portions) {
        if (stack.getItem() instanceof DurablePortionItem portionItem) {
            return portionItem.addPortions(stack, portions);
        }
        return stack;
    }

    public static RegistryObject<Item> register(String itemId, int durability) {
        return register(itemId, durability, () -> ItemStack.EMPTY, () -> ItemStack.EMPTY);
    }

    public static RegistryObject<Item> registerBottled(String itemId, int durability) {
        return register(itemId, durability, () -> ItemStack.EMPTY, () -> new ItemStack(Items.GLASS_BOTTLE));
    }

    public static RegistryObject<Item> registerBucketed(String itemId, int durability) {
        return register(itemId, durability, () -> ItemStack.EMPTY, () -> new ItemStack(Items.BUCKET));
    }

    public static RegistryObject<Item> register(String itemId, int durability, Supplier<ItemStack> exhaustedRemainder) {
        return register(itemId, durability, () -> ItemStack.EMPTY, exhaustedRemainder);
    }

    public static RegistryObject<Item> register(String itemId, int durability, Supplier<ItemStack> baseItem,
                                                Supplier<ItemStack> exhaustedRemainder) {
        if (durability <= 0) {
            throw new IllegalArgumentException("Durability must be positive for item: " + itemId);
        }

        return ModItems.ITEMS.register(itemId, () ->
                new Simple(new Item.Properties().stacksTo(1), durability, baseItem, exhaustedRemainder));
    }

    public static RegistryObject<Item> registerBottled(String itemId, int durability, Supplier<ItemStack> baseItem) {
        return register(itemId, durability, baseItem, () -> new ItemStack(Items.GLASS_BOTTLE));
    }

    public static RegistryObject<Item> registerBucketed(String itemId, int durability, Supplier<ItemStack> baseItem) {
        return register(itemId, durability, baseItem, () -> new ItemStack(Items.BUCKET));
    }

    private static final class Simple extends DurablePortionItem {
        private Simple(Properties properties, int maxPortions, Supplier<ItemStack> baseItem,
                       Supplier<ItemStack> exhaustedRemainder) {
            super(properties, maxPortions, baseItem, exhaustedRemainder);
        }
    }
}
