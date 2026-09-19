package com.wxwr.kaleidoscopeagricultureevolution.recipe;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.GenomeSerializer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class GeneticTagRemovalRecipe extends CustomRecipe {
    public GeneticTagRemovalRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        return !assemble(container, level.registryAccess()).isEmpty();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        NonNullList<ItemStack> inputs = findInputs(container);
        if (inputs.size() != 1) {
            return ItemStack.EMPTY;
        }

        ItemStack input = inputs.get(0);
        if (!input.hasTag() || input.getTag() == null || !GenomeSerializer.hasGenome(input.getTag())) {
            return ItemStack.EMPTY;
        }

        ItemStack output = input.copyWithCount(1);
        CompoundTag tag = output.getTag();
        if (tag != null) {
            tag.remove(GenomeSerializer.TAG_KEY);
            if (tag.isEmpty()) {
                output.setTag(null);
            }
        }
        return output;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.GENETIC_TAG_REMOVAL.get();
    }

    private static NonNullList<ItemStack> findInputs(CraftingContainer container) {
        NonNullList<ItemStack> inputs = NonNullList.create();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        return inputs;
    }
}
