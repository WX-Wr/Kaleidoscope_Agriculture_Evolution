package com.wxwr.kaleidoscopeagricultureevolution.recipe;

import com.wxwr.kaleidoscopeagricultureevolution.block.CrockBlock;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.CrockBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class CrockRecipes {
    private static volatile List<CrockRecipe> RECIPES = List.of();

    private CrockRecipes() {
    }

    public static void reloadFromConfig() {
        RECIPES = ContainerRecipeConfig.crockRecipes().stream()
                .map(CrockRecipes::fromDefinition)
                .toList();
    }

    public static Result process(CrockBlockEntity crock, CrockBlock.Content content,
                                 int fillLevel) {
        if (RECIPES.isEmpty()) {
            reloadFromConfig();
        }
        Processed processed = process(crock.getRawItems(), crock.getNonRawItems(), content, fillLevel);
        if (processed.changed()) {
            crock.refresh();
        }
        return new Result(processed.changed(), processed.content(), processed.fillLevel());
    }

    public static Preview preview(CrockBlockEntity crock, CrockBlock.Content content,
                                  int fillLevel) {
        if (RECIPES.isEmpty()) {
            reloadFromConfig();
        }
        Processed processed = process(copyItems(crock.getRawItems()), copyItems(crock.getNonRawItems()),
                content, fillLevel);
        return new Preview(processed.changed(), processed.products(), processed.durationTicks());
    }

    private static Processed process(NonNullList<ItemStack> rawItems, NonNullList<ItemStack> nonRawItems,
                                     CrockBlock.Content content, int fillLevel) {
        boolean changed = false;
        CrockBlock.Content currentContent = content;
        int currentFillLevel = fillLevel;
        int durationTicks = 0;
        NonNullList<ItemStack> products = NonNullList.create();
        boolean progressed;

        do {
            progressed = false;
            for (int slot = 0; slot < rawItems.size() && !progressed; slot++) {
                ItemStack raw = rawItems.get(slot);
                if (raw.isEmpty()) {
                    continue;
                }
                for (CrockRecipe recipe : RECIPES) {
                    int extraRawSlot = findExtraRawSlot(rawItems, slot, recipe);
                    if (!recipe.matches(raw) || extraRawSlot == REQUIRED_RAW_NOT_FOUND
                            || !canConsume(nonRawItems, currentContent, currentFillLevel, recipe)) {
                        continue;
                    }
                    if (tryTransformRawItem(rawItems, slot, extraRawSlot, recipe)) {
                        ItemStack output = new ItemStack(recipe.output().get(), recipe.outputCount());
                        addProduct(products, output);
                        consumePortion(nonRawItems, recipe.portionIngredient(), recipe.portionCount());
                        if (recipe.liquid() != CrockBlock.Content.EMPTY) {
                            currentFillLevel--;
                            if (currentFillLevel <= 0) {
                                currentContent = CrockBlock.Content.EMPTY;
                                currentFillLevel = 0;
                            }
                        }
                        durationTicks = Math.max(durationTicks, recipe.durationTicks());
                        changed = true;
                        progressed = true;
                    }
                    break;
                }
            }
        } while (progressed);

        return new Processed(changed, currentContent, currentFillLevel, products, durationTicks);
    }

    private static boolean canConsume(NonNullList<ItemStack> nonRawItems,
                                      CrockBlock.Content content, int fillLevel,
                                      CrockRecipe recipe) {
        if (recipe.liquid() != CrockBlock.Content.EMPTY
                && (content != recipe.liquid() || fillLevel <= 0)) {
            return false;
        }
        return hasPortions(nonRawItems, recipe.portionIngredient(), recipe.portionCount());
    }

    private static final int NO_EXTRA_RAW_REQUIRED = -1;
    private static final int REQUIRED_RAW_NOT_FOUND = -2;

    private static int findExtraRawSlot(NonNullList<ItemStack> rawItems, int rawSlot, CrockRecipe recipe) {
        if (!recipe.requiresExtraRaw()) {
            return NO_EXTRA_RAW_REQUIRED;
        }

        for (int i = 0; i < rawItems.size(); i++) {
            if (i != rawSlot && recipe.matchesExtraRaw(rawItems.get(i))) {
                return i;
            }
        }
        return REQUIRED_RAW_NOT_FOUND;
    }

    private static boolean tryTransformRawItem(NonNullList<ItemStack> rawItems, int rawSlot, int extraRawSlot,
                                               CrockRecipe recipe) {
        ItemStack output = new ItemStack(recipe.output().get(), recipe.outputCount());
        if (!canFitRawOutput(rawItems, rawSlot, extraRawSlot, output)) {
            return false;
        }

        removeRawItem(rawItems, rawSlot);
        if (extraRawSlot >= 0) {
            removeRawItem(rawItems, extraRawSlot);
        }
        return addItem(rawItems, output).isEmpty();
    }

    private static void removeRawItem(NonNullList<ItemStack> rawItems, int rawSlot) {
        ItemStack raw = rawItems.get(rawSlot);
        raw.shrink(1);
        if (raw.isEmpty()) {
            rawItems.set(rawSlot, ItemStack.EMPTY);
        }
    }

    private static boolean canFitRawOutput(NonNullList<ItemStack> rawItems, int consumedRawSlot, int extraRawSlot,
                                           ItemStack output) {
        int remaining = output.getCount();
        for (int i = 0; i < rawItems.size(); i++) {
            ItemStack current = rawItems.get(i);
            if (i == consumedRawSlot) {
                current = current.copy();
                current.shrink(1);
            } else if (i == extraRawSlot) {
                current = current.copy();
                current.shrink(1);
            }
            if (current.isEmpty()) {
                remaining -= Math.min(remaining, output.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(current, output)) {
                remaining -= Math.min(remaining, current.getMaxStackSize() - current.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPortions(NonNullList<ItemStack> nonRawItems, Supplier<Item> item, int portions) {
        int found = 0;
        for (ItemStack stack : nonRawItems) {
            if (stack.is(item.get()) && stack.getItem() instanceof DurablePortionItem portionItem) {
                found += portionItem.getPortions(stack);
                if (found >= portions) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void consumePortion(NonNullList<ItemStack> nonRawItems, Supplier<Item> item, int portions) {
        int remaining = portions;
        for (int i = 0; i < nonRawItems.size() && remaining > 0; i++) {
            ItemStack stack = nonRawItems.get(i);
            if (!stack.is(item.get()) || !(stack.getItem() instanceof DurablePortionItem portionItem)) {
                continue;
            }
            int consumed = Math.min(remaining, portionItem.getPortions(stack));
            nonRawItems.set(i, portionItem.consumePortions(stack, consumed));
            remaining -= consumed;
        }
    }

    private static CrockRecipe fromDefinition(ContainerRecipeConfig.CrockRecipeDefinition definition) {
        Predicate<ItemStack> raw = definition::matchesRaw;
        Predicate<ItemStack> extraRaw = definition.requiresExtraRaw() ? definition::matchesExtraRaw : null;
        Supplier<Item> output = () -> {
            Item item = ForgeRegistries.ITEMS.getValue(definition.output());
            return item == null ? net.minecraft.world.item.Items.AIR : item;
        };
        Supplier<Item> portionIngredient = () -> {
            Item item = ForgeRegistries.ITEMS.getValue(definition.portionItem());
            return item == null ? net.minecraft.world.item.Items.AIR : item;
        };
        return new CrockRecipe(raw, extraRaw, output, definition.outputCount(), definition.liquid(),
                portionIngredient, definition.portionCount(), definition.durationTicks());
    }

    private static ItemStack addItem(NonNullList<ItemStack> items, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();
        for (ItemStack current : items) {
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, remaining)) {
                int moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    current.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, remaining.split(Math.min(remaining.getCount(), remaining.getMaxStackSize())));
            }
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    private static NonNullList<ItemStack> copyItems(NonNullList<ItemStack> items) {
        NonNullList<ItemStack> copy = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) {
            copy.set(i, items.get(i).copy());
        }
        return copy;
    }

    private static void addProduct(NonNullList<ItemStack> products, ItemStack stack) {
        for (ItemStack current : products) {
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, stack)) {
                current.grow(stack.getCount());
                return;
            }
        }
        products.add(stack.copy());
    }

    private static int minutes(int minutes) {
        return seconds(minutes * 60);
    }

    private static int seconds(int seconds) {
        return seconds * 20;
    }

    public record Result(boolean changed, CrockBlock.Content content, int fillLevel) {
    }

    public record Preview(boolean changed, NonNullList<ItemStack> products, int durationTicks) {
    }

    private record Processed(boolean changed, CrockBlock.Content content, int fillLevel,
                             NonNullList<ItemStack> products, int durationTicks) {
    }

    private record CrockRecipe(Predicate<ItemStack> raw, Predicate<ItemStack> extraRaw, Supplier<Item> output, int outputCount,
                               CrockBlock.Content liquid, Supplier<Item> portionIngredient,
                               int portionCount,
                               int durationTicks) {
        private boolean matches(ItemStack stack) {
            return raw.test(stack);
        }

        private boolean requiresExtraRaw() {
            return extraRaw != null;
        }

        private boolean matchesExtraRaw(ItemStack stack) {
            return requiresExtraRaw() && extraRaw.test(stack);
        }
    }
}
