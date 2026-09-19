package com.wxwr.kaleidoscopeagricultureevolution.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);

    public static final RegistryObject<RecipeSerializer<DurablePortionConversionRecipe>> DURABLE_PORTION_CONVERSION =
            RECIPE_SERIALIZERS.register("durable_portion_conversion",
                    () -> new SimpleCraftingRecipeSerializer<>(DurablePortionConversionRecipe::new));

    public static final RegistryObject<RecipeSerializer<GeneticTagRemovalRecipe>> GENETIC_TAG_REMOVAL =
            RECIPE_SERIALIZERS.register("genetic_tag_removal",
                    () -> new SimpleCraftingRecipeSerializer<>(GeneticTagRemovalRecipe::new));

    public static void register(IEventBus bus) {
        RECIPE_SERIALIZERS.register(bus);
    }
}
