package com.wxwr.kaleidoscopeagricultureevolution.recipe;

import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class DurablePortionConversionRecipe extends CustomRecipe {
    public DurablePortionConversionRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        return !assemble(container, level.registryAccess()).isEmpty();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        NonNullList<ItemStack> inputs = findInputs(container);
        if (inputs.size() == 2) {
            return assembleCombine(inputs.get(0), inputs.get(1));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingContainer container) {
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }

    private static ItemStack assembleCombine(ItemStack first, ItemStack second) {
        if (first.getItem() != second.getItem() || first.getCount() != 1 || second.getCount() != 1) {
            return ItemStack.EMPTY;
        }
        if (!(first.getItem() instanceof DurablePortionItem portionItem)) {
            return ItemStack.EMPTY;
        }

        int portions = portionItem.getPortions(first) + portionItem.getPortions(second);
        if (portions > portionItem.getMaxPortions()) {
            return ItemStack.EMPTY;
        }
        return portionItem.withPortions(portions);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.DURABLE_PORTION_CONVERSION.get();
    }

    private static NonNullList<ItemStack> findInputs(CraftingContainer container) {
        NonNullList<ItemStack> inputs = NonNullList.create();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            inputs.add(stack);
        }
        return inputs;
    }
}
