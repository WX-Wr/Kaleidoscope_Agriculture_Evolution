package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationContent;
import com.wxwr.kaleidoscopeagricultureevolution.block.GlassFermentationBlock;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemContainerStorage;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemMergePolicy;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.ContainerRecipeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class FermentationContainerBlockEntity extends BaseBlockEntity {
    public static final int NON_LIQUID_ITEM_SLOTS = 8;
    private static final String NON_LIQUID_ITEMS_TAG = "NonLiquidItems";
    private static final String APPLE_VINEGAR_FINISH_GAME_TIME_TAG = "AppleVinegarFinishGameTime";
    private static final String APPLE_VINEGAR_BATCHES_TAG = "AppleVinegarBatches";
    private static final String APPLE_VINEGAR_BOTTLES_TAG = "AppleVinegarBottles";
    private static final String GLASS_PRESERVING_FINISH_GAME_TIME_TAG = "GlassPreservingFinishGameTime";
    private static final String GLASS_PRESERVING_RECIPE_TAG = "GlassPreservingRecipe";
    private static final String LEGACY_SUGARED_TOMATO_FINISH_GAME_TIME_TAG = "SugaredTomatoFinishGameTime";
    public static final String GLASS_RECIPE_SUGARED_TOMATO = "sugared_tomato";
    public static final String GLASS_RECIPE_HONEY_GLAZED_CARROT = "honey_glazed_carrot";
    public static final String GLASS_RECIPE_HONEY_GLAZED_PUMPKIN = "honey_glazed_pumpkin";

    private final ItemContainerStorage nonLiquidStorage = new ItemContainerStorage(
            NON_LIQUID_ITEM_SLOTS, Integer.MAX_VALUE, 64, stack -> true,
            ItemMergePolicy.EXACT, this::refresh);
    private long appleVinegarFinishGameTime = -1L;
    private int appleVinegarBatches;
    private int appleVinegarBottles;
    private long glassPreservingFinishGameTime = -1L;
    private String glassPreservingRecipeId = "";

    public FermentationContainerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FERMENTATION_CONTAINER.get(), pos, state);
    }

    public NonNullList<ItemStack> getNonLiquidItems() {
        return nonLiquidStorage.getItems();
    }

    public boolean isAppleVinegarFermenting() {
        return appleVinegarFinishGameTime >= 0L;
    }

    public int getAppleVinegarBottles() {
        return appleVinegarBottles;
    }

    public int getAppleVinegarBatches() {
        return appleVinegarBatches;
    }

    public long getRemainingAppleVinegarTicks(Level level) {
        if (appleVinegarFinishGameTime < 0L) {
            return 0L;
        }
        return Math.max(0L, appleVinegarFinishGameTime - level.getGameTime());
    }

    public int countNonLiquidItem(Item item) {
        int count = 0;
        for (ItemStack stack : nonLiquidStorage.getItems()) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public int countNonLiquidItem(Predicate<ItemStack> predicate) {
        int count = 0;
        for (ItemStack stack : nonLiquidStorage.getItems()) {
            if (predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean canStartGlassPreserving(String recipeId, BlockState state) {
        return !isGlassPreserving() && matchesGlassPreservingIngredients(recipeId, state);
    }

    public boolean isGlassPreserving() {
        return glassPreservingFinishGameTime >= 0L;
    }

    public String getGlassPreservingRecipeId() {
        return glassPreservingRecipeId;
    }

    public long getRemainingGlassPreservingTicks(Level level) {
        if (glassPreservingFinishGameTime < 0L) {
            return 0L;
        }
        return Math.max(0L, glassPreservingFinishGameTime - level.getGameTime());
    }

    public void startGlassPreserving(Level level, String recipeId, int durationTicks) {
        glassPreservingRecipeId = recipeId;
        glassPreservingFinishGameTime = level.getGameTime() + durationTicks;
        refresh();
    }

    public boolean hasNonLiquidItems() {
        return !nonLiquidStorage.isEmpty();
    }

    public ItemStack addNonLiquidItem(ItemStack stack) {
        return nonLiquidStorage.insert(stack);
    }

    public int countNonLiquidPortions(Item item) {
        int count = 0;
        for (ItemStack stack : nonLiquidStorage.getItems()) {
            if (stack.is(item) && stack.getItem() instanceof DurablePortionItem portionItem) {
                count += portionItem.getPortions(stack);
            }
        }
        return count;
    }

    public void removeNonLiquidPortions(Item item, int portions) {
        int remaining = portions;
        for (int i = 0; i < nonLiquidStorage.getSlots() && remaining > 0; i++) {
            ItemStack stack = nonLiquidStorage.getItem(i);
            if (!stack.is(item) || !(stack.getItem() instanceof DurablePortionItem portionItem)) {
                continue;
            }
            int consumed = Math.min(remaining, portionItem.getPortions(stack));
            nonLiquidStorage.setItem(i, portionItem.consumePortions(stack, consumed));
            remaining -= consumed;
        }
        if (remaining != portions) {
            refresh();
        }
    }

    public void removeNonLiquidMatching(Predicate<ItemStack> predicate, int count) {
        int remaining = count;
        for (int i = 0; i < nonLiquidStorage.getSlots() && remaining > 0; i++) {
            ItemStack stack = nonLiquidStorage.getItem(i);
            if (!predicate.test(stack)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (stack.isEmpty()) {
                nonLiquidStorage.setItem(i, ItemStack.EMPTY);
            }
        }
        if (remaining != count) {
            refresh();
        }
    }

    public NonNullList<ItemStack> removeAllNonLiquidItems() {
        return nonLiquidStorage.removeAll();
    }

    public void dropNonLiquidItems(Level level, BlockPos pos) {
        for (ItemStack stack : nonLiquidStorage.getItems()) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }

    public void startAppleVinegarFermentation(Level level, int batches) {
        appleVinegarBatches = Math.max(1, Math.min(GlassFermentationBlock.MAX_FILL_LEVEL, batches));
        appleVinegarBottles = 0;
        ContainerRecipeConfig.GlassRecipeDefinition recipe = ContainerRecipeConfig.appleVinegarRecipe();
        appleVinegarFinishGameTime = level.getGameTime() + (recipe == null ? 0L : recipe.durationTicks());
        refresh();
    }

    public int takeAppleVinegarBottle() {
        if (appleVinegarBottles <= 0) {
            return 0;
        }
        appleVinegarBottles--;
        refresh();
        return appleVinegarBottles;
    }

    public void setAppleVinegarBottles(int bottles) {
        appleVinegarBottles = Math.max(0, bottles);
        appleVinegarFinishGameTime = -1L;
        appleVinegarBatches = 0;
        refresh();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FermentationContainerBlockEntity container) {
        if (level.isClientSide || !(state.getBlock() instanceof GlassFermentationBlock)) {
            return;
        }

        if (container.isAppleVinegarFermenting() && level.getGameTime() >= container.appleVinegarFinishGameTime) {
            completeAppleVinegar(level, pos, state, container);
            state = level.getBlockState(pos);
        }

        if (container.isGlassPreserving()
                && level.getGameTime() >= container.glassPreservingFinishGameTime) {
            completeGlassPreserving(level, pos, state, container);
        }
    }

    private static void completeAppleVinegar(Level level, BlockPos pos, BlockState state,
                                             FermentationContainerBlockEntity container) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = ContainerRecipeConfig.appleVinegarRecipe();
        if (recipe == null) {
            container.refresh();
            return;
        }
        boolean hasIngredients = recipe.hasIngredients(container);
        int batches = hasIngredients
                ? container.appleVinegarBatches
                : 0;
        if (hasIngredients) {
            recipe.consumeIngredients(container);
        }
        container.appleVinegarFinishGameTime = -1L;
        container.appleVinegarBatches = 0;
        container.appleVinegarBottles = batches * recipe.outputCount();

        BlockState nextState = batches <= 0
                ? state.setValue(GlassFermentationBlock.CONTENT, FermentationContent.EMPTY)
                        .setValue(GlassFermentationBlock.FILL_LEVEL, 0)
                : state.setValue(GlassFermentationBlock.CONTENT, FermentationContent.APPLE_VINEGAR)
                        .setValue(GlassFermentationBlock.FILL_LEVEL, batches);
        level.setBlock(pos, nextState, Block.UPDATE_ALL);
        container.refresh();
    }

    private static void completeGlassPreserving(Level level, BlockPos pos, BlockState state,
                                                FermentationContainerBlockEntity container) {
        String recipeId = container.glassPreservingRecipeId;
        container.glassPreservingFinishGameTime = -1L;
        container.glassPreservingRecipeId = "";
        ContainerRecipeConfig.GlassRecipeDefinition recipe = ContainerRecipeConfig.glassRecipe(recipeId);

        if (recipe == null || !container.matchesGlassPreservingIngredients(recipeId, state)) {
            container.refresh();
            return;
        }

        recipe.consumeIngredients(container);
        if (recipe.drainsLiquid()) {
            level.setBlock(pos, drainOneGlassPreservingLiquid(state), Block.UPDATE_ALL);
        }
        container.addNonLiquidItem(recipe.outputStack());
        container.refresh();
    }

    private static BlockState drainOneGlassPreservingLiquid(BlockState state) {
        int nextFillLevel = state.getValue(GlassFermentationBlock.FILL_LEVEL) - 1;
        return nextFillLevel <= 0
                ? state.setValue(GlassFermentationBlock.CONTENT, FermentationContent.EMPTY)
                        .setValue(GlassFermentationBlock.FILL_LEVEL, 0)
                : state.setValue(GlassFermentationBlock.FILL_LEVEL, nextFillLevel);
    }

    private boolean matchesGlassPreservingIngredients(String recipeId, BlockState state) {
        ContainerRecipeConfig.GlassRecipeDefinition recipe = ContainerRecipeConfig.glassRecipe(recipeId);
        return recipe != null
                && recipe.matchesState(state.getValue(GlassFermentationBlock.CONTENT), state.getValue(GlassFermentationBlock.FILL_LEVEL))
                && recipe.hasIngredients(this);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        nonLiquidStorage.load(tag, NON_LIQUID_ITEMS_TAG);
        appleVinegarFinishGameTime = tag.contains(APPLE_VINEGAR_FINISH_GAME_TIME_TAG)
                ? tag.getLong(APPLE_VINEGAR_FINISH_GAME_TIME_TAG)
                : -1L;
        appleVinegarBatches = tag.getInt(APPLE_VINEGAR_BATCHES_TAG);
        appleVinegarBottles = tag.getInt(APPLE_VINEGAR_BOTTLES_TAG);
        if (tag.contains(GLASS_PRESERVING_FINISH_GAME_TIME_TAG)) {
            glassPreservingFinishGameTime = tag.getLong(GLASS_PRESERVING_FINISH_GAME_TIME_TAG);
            glassPreservingRecipeId = tag.getString(GLASS_PRESERVING_RECIPE_TAG);
        } else if (tag.contains(LEGACY_SUGARED_TOMATO_FINISH_GAME_TIME_TAG)) {
            glassPreservingFinishGameTime = tag.getLong(LEGACY_SUGARED_TOMATO_FINISH_GAME_TIME_TAG);
            glassPreservingRecipeId = glassPreservingFinishGameTime >= 0L ? GLASS_RECIPE_SUGARED_TOMATO : "";
        } else {
            glassPreservingFinishGameTime = -1L;
            glassPreservingRecipeId = "";
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        nonLiquidStorage.save(tag, NON_LIQUID_ITEMS_TAG);
        tag.putLong(APPLE_VINEGAR_FINISH_GAME_TIME_TAG, appleVinegarFinishGameTime);
        tag.putInt(APPLE_VINEGAR_BATCHES_TAG, appleVinegarBatches);
        tag.putInt(APPLE_VINEGAR_BOTTLES_TAG, appleVinegarBottles);
        tag.putLong(GLASS_PRESERVING_FINISH_GAME_TIME_TAG, glassPreservingFinishGameTime);
        tag.putString(GLASS_PRESERVING_RECIPE_TAG, glassPreservingRecipeId);
    }

    public ItemStack removeOneNonLiquidItem() {
        for (int i = nonLiquidStorage.getSlots() - 1; i >= 0; i--) {
            ItemStack stack = nonLiquidStorage.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            return nonLiquidStorage.extract(i, 1);
        }
        return ItemStack.EMPTY;
    }
}
