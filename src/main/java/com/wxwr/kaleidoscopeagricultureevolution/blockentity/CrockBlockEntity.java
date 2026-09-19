package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import com.wxwr.kaleidoscopeagricultureevolution.block.CrockBlock;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemContainerStorage;
import com.wxwr.kaleidoscopeagricultureevolution.container.ItemMergePolicy;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.CrockRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.NonNullList;
import org.jetbrains.annotations.NotNull;

public class CrockBlockEntity extends BaseBlockEntity {
    public static final int RAW_ITEM_SLOTS = 8;
    public static final int NON_RAW_ITEM_SLOTS = 4;
    private static final String RAW_ITEMS_TAG = "RawItems";
    private static final String NON_RAW_ITEMS_TAG = "NonRawItems";
    private static final String PICKLING_COMPLETE_TAG = "PicklingComplete";
    private static final String PICKLING_FINISH_GAME_TIME_TAG = "PicklingFinishGameTime";
    private static final String PENDING_PRODUCTS_TAG = "PendingProducts";

    private final ItemContainerStorage rawStorage = new ItemContainerStorage(
            RAW_ITEM_SLOTS, Integer.MAX_VALUE, 64, stack -> true,
            ItemMergePolicy.EXACT, this::refresh);
    private final ItemContainerStorage nonRawStorage = new ItemContainerStorage(
            NON_RAW_ITEM_SLOTS, Integer.MAX_VALUE, 64, stack -> true,
            ItemMergePolicy.EXACT_OR_PORTIONS, this::refresh);
    private final NonNullList<ItemStack> pendingProducts = NonNullList.withSize(RAW_ITEM_SLOTS, ItemStack.EMPTY);
    private boolean picklingComplete;
    private long picklingFinishGameTime = -1L;

    public CrockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CROCK.get(), pos, state);
    }

    public NonNullList<ItemStack> getRawItems() {
        return rawStorage.getItems();
    }

    public NonNullList<ItemStack> getNonRawItems() {
        return nonRawStorage.getItems();
    }

    public boolean hasRawItems() {
        return !rawStorage.isEmpty();
    }

    public boolean hasNonRawItems() {
        return !nonRawStorage.isEmpty();
    }

    public boolean isPicklingComplete() {
        return picklingComplete;
    }

    public void setPicklingComplete(boolean picklingComplete) {
        if (this.picklingComplete == picklingComplete) {
            return;
        }
        this.picklingComplete = picklingComplete;
        refresh();
    }

    public boolean hasActivePickling(Level level) {
        return picklingFinishGameTime >= 0L && !picklingComplete
                && getRemainingPicklingTicks(level) > 0L;
    }

    public boolean hasElapsedPicklingTimer(Level level) {
        return picklingFinishGameTime >= 0L && !picklingComplete
                && getRemainingPicklingTicks(level) <= 0L;
    }

    public long getRemainingPicklingTicks(Level level) {
        if (picklingFinishGameTime < 0L) {
            return 0L;
        }
        return Math.max(0L, picklingFinishGameTime - level.getGameTime());
    }

    public NonNullList<ItemStack> getPendingProducts() {
        return pendingProducts;
    }

    public boolean hasPendingProducts() {
        return pendingProducts.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public void startPickling(long finishGameTime, NonNullList<ItemStack> products) {
        this.picklingComplete = false;
        this.picklingFinishGameTime = finishGameTime;
        clearPendingProducts();
        for (int i = 0; i < products.size() && i < pendingProducts.size(); i++) {
            ItemStack product = products.get(i);
            if (!product.isEmpty()) {
                this.pendingProducts.set(i, product.copy());
            }
        }
        refresh();
    }

    public void clearPicklingState() {
        this.picklingComplete = false;
        this.picklingFinishGameTime = -1L;
        clearPendingProducts();
        refresh();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrockBlockEntity crock) {
        if (level.isClientSide || crock.picklingComplete || crock.picklingFinishGameTime < 0L
                || level.getGameTime() < crock.picklingFinishGameTime) {
            return;
        }

        CrockRecipes.Result result = CrockRecipes.process(crock,
                state.getValue(CrockBlock.CONTENT), state.getValue(CrockBlock.FILL_LEVEL));
        crock.picklingFinishGameTime = -1L;
        if (result.changed()) {
            crock.picklingComplete = true;
            CrockBlock.Content nextContent = result.content();
            int nextFillLevel = result.fillLevel();
            YeastByproduct byproduct = tryCreateYeastByproduct(crock, nextContent, nextFillLevel, level, pos);
            nextContent = byproduct.content();
            nextFillLevel = byproduct.fillLevel();
            BlockState nextState = state
                    .setValue(CrockBlock.CONTENT, nextContent)
                    .setValue(CrockBlock.FILL_LEVEL, nextFillLevel);
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
        }
        crock.refresh();
    }

    private static YeastByproduct tryCreateYeastByproduct(CrockBlockEntity crock,
                                                           CrockBlock.Content content,
                                                           int fillLevel, Level level, BlockPos pos) {
        if (content != CrockBlock.Content.WATER
                && content != CrockBlock.Content.EMPTY) {
            return new YeastByproduct(content, fillLevel);
        }
        if (level.random.nextFloat() >= 0.10F) {
            return new YeastByproduct(content, fillLevel);
        }

        if (content == CrockBlock.Content.WATER && fillLevel > 0) {
            addByproduct(crock, ((DurablePortionItem) ModItems.PORTION_YEAST.get()).withPortions(fillLevel), level, pos);
            return new YeastByproduct(CrockBlock.Content.EMPTY, 0);
        }

        int portions = 1 + level.random.nextInt(3);
        addByproduct(crock, ((DurablePortionItem) ModItems.PORTION_YEAST_POWDER.get()).withPortions(portions), level, pos);
        return new YeastByproduct(content, fillLevel);
    }

    private static void addByproduct(CrockBlockEntity crock, ItemStack stack, Level level, BlockPos pos) {
        ItemStack remaining = crock.addNonRawItem(stack);
        if (!remaining.isEmpty()) {
            Block.popResource(level, pos, remaining);
        }
    }

    private record YeastByproduct(CrockBlock.Content content, int fillLevel) {
    }

    public ItemStack addRawItem(ItemStack stack) {
        return rawStorage.insert(stack);
    }

    public ItemStack addNonRawItem(ItemStack stack) {
        return nonRawStorage.insert(stack);
    }

    public void setRawItem(int slot, ItemStack stack) {
        rawStorage.setItem(slot, stack);
    }

    public NonNullList<ItemStack> removeAllRawItems() {
        return rawStorage.removeAll();
    }

    public NonNullList<ItemStack> removeAllNonRawItems() {
        return nonRawStorage.removeAll();
    }

    public ItemStack removeRawItem() {
        return rawStorage.extractOneFromLast();
    }

    public ItemStack removeNonRawItem() {
        return nonRawStorage.extractOneFromLast();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        rawStorage.load(tag, RAW_ITEMS_TAG);
        nonRawStorage.load(tag, NON_RAW_ITEMS_TAG);
        picklingComplete = tag.getBoolean(PICKLING_COMPLETE_TAG);
        picklingFinishGameTime = tag.getLong(PICKLING_FINISH_GAME_TIME_TAG);
        clearPendingProducts();
        if (tag.contains(PENDING_PRODUCTS_TAG)) {
            ContainerHelper.loadAllItems(tag.getCompound(PENDING_PRODUCTS_TAG), pendingProducts);
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        rawStorage.save(tag, RAW_ITEMS_TAG);
        nonRawStorage.save(tag, NON_RAW_ITEMS_TAG);
        tag.putBoolean(PICKLING_COMPLETE_TAG, picklingComplete);
        tag.putLong(PICKLING_FINISH_GAME_TIME_TAG, picklingFinishGameTime);

        CompoundTag pendingProductsTag = new CompoundTag();
        ContainerHelper.saveAllItems(pendingProductsTag, pendingProducts);
        tag.put(PENDING_PRODUCTS_TAG, pendingProductsTag);
    }

    private void clearPendingProducts() {
        for (int i = 0; i < pendingProducts.size(); i++) {
            pendingProducts.set(i, ItemStack.EMPTY);
        }
    }
}
