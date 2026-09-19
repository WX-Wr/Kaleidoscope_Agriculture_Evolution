package com.wxwr.kaleidoscopeagricultureevolution.block;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.CrockBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.ModBlockEntities;
import com.wxwr.kaleidoscopeagricultureevolution.container.ContainerWhitelist;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.recipe.CrockRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CrockBlock extends BaseEntityBlock {
    public static final EnumProperty<Content> CONTENT = EnumProperty.create("content", Content.class);
    public static final BooleanProperty LIDDED = BooleanProperty.create("lidded");
    public static final BooleanProperty WATER_SEALED = BooleanProperty.create("water_sealed");
    public static final IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 3);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1.6D, 0.0D, 1.6D, 14.4D, 16.0D, 14.4D);

    public CrockBlock() {
        super(Properties.of()
                .strength(2.0F)
                .sound(SoundType.DECORATED_POT)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONTENT, Content.EMPTY)
                .setValue(LIDDED, true)
                .setValue(WATER_SEALED, false)
                .setValue(FILL_LEVEL, 0)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENT, FILL_LEVEL, LIDDED, WATER_SEALED, FACING);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull net.minecraft.world.level.BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state,
                                                 @NotNull net.minecraft.world.level.BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CrockBlockEntity(pos, state);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
                dropCrockItems(level, pos, crock.getRawItems());
                dropCrockItems(level, pos, crock.getNonRawItems());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                            @NotNull BlockState state,
                                                                            @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CROCK.get(),
                CrockBlockEntity::tick);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        Content content = state.getValue(CONTENT);
        boolean lidded = state.getValue(LIDDED);
        boolean waterSealed = state.getValue(WATER_SEALED);

        if (waterSealed) {
            if (isCompletelyEmptyHanded(player) && !player.isShiftKeyDown()) {
                return showPicklingStatus(state, level, pos, player);
            }
            if (player.isShiftKeyDown() && held.is(Items.GLASS_BOTTLE)) {
                return tryExtractLiquidPortion(state, level, pos, player, hand);
            }
            return isCrockInteractionItem(held) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }

        if (isCompletelyEmptyHanded(player)) {
            if (player.isShiftKeyDown() && !lidded) {
                return tryExtractNonLiquidMaterial(level, pos, player);
            }
            if (!player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(LIDDED, !lidded), Block.UPDATE_ALL);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (lidded && held.is(ModItems.PORTION_WATER_BOTTLE.get())) {
            if (!level.isClientSide) {
                BlockState nextState = state.setValue(WATER_SEALED, true);
                if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
                    CrockRecipes.Preview preview = CrockRecipes.preview(crock,
                            content, state.getValue(FILL_LEVEL));
                    if (preview.changed()) {
                        crock.startPickling(level.getGameTime() + preview.durationTicks(), preview.products());
                    } else {
                        crock.clearPicklingState();
                    }
                }
                level.setBlock(pos, nextState, Block.UPDATE_ALL);
                consumeContentIngredient(player, hand, new ItemStack(Items.GLASS_BOTTLE));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (player.isShiftKeyDown() && !lidded) {
            if (canHoldLiquidPortion(held, content) && content != Content.EMPTY && state.getValue(FILL_LEVEL) > 0) {
                return tryExtractLiquidPortion(state, level, pos, player, hand);
            }
            return isCrockInteractionItem(held) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }

        if (isRawIngredient(held)) {
            if (lidded) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return tryInsertRawIngredient(level, pos, player, hand, held);
        }

        if (lidded) {
            return isCrockInteractionItem(held) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }

        if (isNonRawIngredient(held)) {
            return tryInsertNonRawIngredient(level, pos, player, hand, held);
        }

        if (content == Content.EMPTY || state.getValue(FILL_LEVEL) < 3) {
            InteractionResult result = tryInsertContent(state, level, pos, player, hand, held);
            if (result != InteractionResult.PASS) {
                return result;
            }
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult tryInsertRawIngredient(Level level, BlockPos pos, Player player,
                                                            InteractionHand hand, ItemStack held) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
            ItemStack remaining = crock.addRawItem(held.copyWithCount(1));
            if (remaining.isEmpty()) {
                crock.clearPicklingState();
                if (!player.getAbilities().instabuild) {
                    player.getItemInHand(hand).shrink(1);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }

    public static InteractionResult tryExtractLiquidPortion(BlockState state, Level level, BlockPos pos, Player player,
                                                            InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (state.getValue(WATER_SEALED)) {
            if (!canHoldLiquidPortion(held, Content.WATER)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
                    crock.clearPicklingState();
                }
                level.setBlock(pos, state.setValue(WATER_SEALED, false), Block.UPDATE_ALL);
                fillLiquidPortion(player, hand, Content.WATER);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getValue(LIDDED)) {
            return InteractionResult.PASS;
        }

        Content content = state.getValue(CONTENT);
        if (content == Content.EMPTY || state.getValue(FILL_LEVEL) <= 0) {
            return InteractionResult.PASS;
        }
        if (!canHoldLiquidPortion(held, content)) {
            return InteractionResult.PASS;
        }
        return tryExtractContent(state, level, pos, player, hand, content);
    }

    private static InteractionResult tryExtractNonLiquidMaterial(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
            ItemStack removed = crock.removeRawItem();
            if (removed.isEmpty()) {
                removed = crock.removeNonRawItem();
            }
            if (!removed.isEmpty()) {
                crock.clearPicklingState();
                giveItem(player, removed);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }

    private static InteractionResult tryInsertNonRawIngredient(Level level, BlockPos pos, Player player,
                                                               InteractionHand hand, ItemStack held) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
            ItemStack inserted = normalizeNonRawIngredient(held);
            ItemStack remaining = crock.addNonRawItem(inserted);
            if (remaining.isEmpty()) {
                crock.clearPicklingState();
                if (!player.getAbilities().instabuild) {
                    if (held.getItem() instanceof DurablePortionItem) {
                        player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
                    } else {
                        player.getItemInHand(hand).shrink(1);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }

    private static ItemStack normalizeNonRawIngredient(ItemStack held) {
        if (held.is(ModItems.SALT.get())) {
            return ((DurablePortionItem) ModItems.PORTION_SALT.get()).withPortions(1);
        }
        if (held.getItem() instanceof DurablePortionItem portionItem) {
            return portionItem.withPortions(1);
        }
        return held.copyWithCount(1);
    }

    /**
     * <b>状态：待定。</b>“清空非生料并把物品还给玩家”的完整实现，但<b>没有任何调用点</b>
     * （未接入 {@code use()} 流程）。它连带使用的 {@code giveItems}、{@code itemForContent}、
     * {@code CrockBlockEntity#hasNonRawItems}、{@code #removeAllNonRawItems} 也仅服务于本方法。
     * 在决定是否启用之前，这些都不要删除。
     */
    private static InteractionResult clearNonRawIngredients(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean changed = false;
        if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock && crock.hasNonRawItems()) {
            giveItems(player, crock.removeAllNonRawItems());
            changed = true;
        }

        int fillLevel = state.getValue(FILL_LEVEL);
        Content content = state.getValue(CONTENT);
        if (content != Content.EMPTY && fillLevel > 0) {
            for (int i = 0; i < fillLevel; i++) {
                giveItem(player, itemForContent(content));
            }
            level.setBlock(pos, state.setValue(CONTENT, Content.EMPTY).setValue(FILL_LEVEL, 0), Block.UPDATE_ALL);
            changed = true;
        }

        return changed ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private static InteractionResult tryInsertContent(BlockState state, Level level, BlockPos pos, Player player,
                                                      InteractionHand hand, ItemStack held) {
        Content inserted = contentFromItem(held);
        if (inserted == Content.EMPTY) {
            return isCrockInteractionItem(held) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }

        Content current = state.getValue(CONTENT);
        int fillLevel = state.getValue(FILL_LEVEL);
        if (current != Content.EMPTY && current != inserted) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (fillLevel >= 3) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
                crock.clearPicklingState();
            }
            level.setBlock(pos, state.setValue(CONTENT, inserted).setValue(FILL_LEVEL, fillLevel + 1), Block.UPDATE_ALL);
            consumeContentIngredient(player, hand, new ItemStack(Items.GLASS_BOTTLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult tryExtractContent(BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, Content content) {
        int fillLevel = state.getValue(FILL_LEVEL);
        if (fillLevel <= 0) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CrockBlockEntity crock) {
                crock.clearPicklingState();
            }
            int nextFillLevel = fillLevel - 1;
            BlockState nextState = nextFillLevel == 0
                    ? state.setValue(CONTENT, Content.EMPTY).setValue(FILL_LEVEL, 0)
                    : state.setValue(FILL_LEVEL, nextFillLevel);
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
            fillLiquidPortion(player, hand, content);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult showPicklingStatus(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CrockBlockEntity crock)) {
            return InteractionResult.CONSUME;
        }

        NonNullList<ItemStack> products = productsForStatus(crock, level);
        if (products.stream().allMatch(ItemStack::isEmpty)) {
            CrockRecipes.Preview preview = CrockRecipes.preview(crock,
                    state.getValue(CONTENT), state.getValue(FILL_LEVEL));
            products = preview.products();
        }

        if (products.stream().allMatch(ItemStack::isEmpty)) {
            player.displayClientMessage(Component.translatable(
                    "message.kaleidoscope_agriculture_evolution.pickling_status_none"), false);
            return InteractionResult.SUCCESS;
        }

        long remainingTicks = crock.isPicklingComplete() ? 0L : crock.getRemainingPicklingTicks(level);
        player.displayClientMessage(Component.translatable(
                "message.kaleidoscope_agriculture_evolution.pickling_status",
                formatProducts(products), formatRemainingTime(remainingTicks)), false);
        return InteractionResult.SUCCESS;
    }

    private static NonNullList<ItemStack> productsForStatus(CrockBlockEntity crock, Level level) {
        if (crock.hasActivePickling(level)) {
            return crock.getPendingProducts();
        }
        if (crock.isPicklingComplete()) {
            return crock.getPendingProducts().stream().anyMatch(stack -> !stack.isEmpty())
                    ? crock.getPendingProducts()
                    : crock.getRawItems();
        }
        return NonNullList.create();
    }

    private static Component formatProducts(NonNullList<ItemStack> products) {
        Component result = Component.empty();
        boolean first = true;
        for (ItemStack stack : products) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!first) {
                result = result.copy().append(Component.literal(", "));
            }
            result = result.copy()
                    .append(stack.getHoverName())
                    .append(Component.literal(" x" + stack.getCount()));
            first = false;
        }
        return result;
    }

    private static Component formatRemainingTime(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds <= 0L) {
            return Component.translatable("message.kaleidoscope_agriculture_evolution.time_done");
        }
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return Component.translatable("message.kaleidoscope_agriculture_evolution.time_seconds",
                    remainingSeconds);
        }
        return Component.translatable("message.kaleidoscope_agriculture_evolution.time_minutes_seconds",
                minutes, remainingSeconds);
    }

    private static boolean canHoldLiquidPortion(ItemStack stack, Content content) {
        if (stack.is(Items.GLASS_BOTTLE)) {
            return true;
        }
        if (!(stack.getItem() instanceof DurablePortionItem portionItem) || portionItem.isFull(stack)) {
            return false;
        }
        return switch (content) {
            case WATER -> stack.is(ModItems.PORTION_WATER_BOTTLE.get());
            case VINEGAR -> stack.is(ModItems.PORTION_VINEGAR.get());
            case HONEY -> stack.is(ModItems.PORTION_HONEY_BOTTLE.get());
            case EMPTY -> false;
        };
    }

    private static Content contentFromItem(ItemStack stack) {
        if (stack.is(ModItems.PORTION_WATER_BOTTLE.get())
                || (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER)) {
            return Content.WATER;
        }
        if (stack.is(ModItems.PORTION_HONEY_BOTTLE.get()) || stack.is(Items.HONEY_BOTTLE)) {
            return Content.HONEY;
        }
        if (stack.is(ModItems.PORTION_VINEGAR.get()) || stack.is(ModItems.VINEGAR.get())) {
            return Content.VINEGAR;
        }
        return Content.EMPTY;
    }

    private static boolean isCrockInteractionItem(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE)
                || contentFromItem(stack) != Content.EMPTY
                || isRawIngredient(stack)
                || isNonRawIngredient(stack);
    }

    public static boolean isRawIngredient(ItemStack stack) {
        return ContainerWhitelist.isCrockRawIngredient(stack);
    }

    public static boolean isNonRawIngredient(ItemStack stack) {
        return ContainerWhitelist.isCrockNonRawIngredient(stack)
                || stack.is(ModItems.SALT.get());
    }

    private static boolean isCompletelyEmptyHanded(Player player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    private static ItemStack itemForContent(Content content) {
        return switch (content) {
            case WATER -> waterBottle();
            case VINEGAR -> new ItemStack(ModItems.VINEGAR.get());
            case HONEY -> new ItemStack(Items.HONEY_BOTTLE);
            case EMPTY -> ItemStack.EMPTY;
        };
    }

    private static ItemStack onePortionContent(Content content) {
        return switch (content) {
            case WATER -> ((DurablePortionItem) ModItems.PORTION_WATER_BOTTLE.get()).withPortions(1);
            case VINEGAR -> ((DurablePortionItem) ModItems.PORTION_VINEGAR.get()).withPortions(1);
            case HONEY -> ((DurablePortionItem) ModItems.PORTION_HONEY_BOTTLE.get()).withPortions(1);
            case EMPTY -> ItemStack.EMPTY;
        };
    }

    private static ItemStack waterBottle() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
    }

    private static void consumeItemsAndGive(Player player, InteractionHand hand, int count, ItemStack replacement) {
        if (player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        held.shrink(count);
        if (held.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!replacement.isEmpty()) {
            if (!player.getInventory().add(replacement)) {
                player.drop(replacement, false);
            }
        }
    }

    private static void consumeContentIngredient(Player player, InteractionHand hand, ItemStack defaultReplacement) {
        if (player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
            return;
        }

        consumeItemsAndGive(player, hand, 1, defaultReplacement);
    }

    private static void fillLiquidPortion(Player player, InteractionHand hand, Content content) {
        if (player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, DurablePortionItem.add(held, 1));
            return;
        }

        consumeItemsAndGive(player, hand, 1, onePortionContent(content));
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (player.getAbilities().instabuild || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void giveItems(Player player, NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            giveItem(player, stack);
        }
    }

    private static void dropCrockItems(Level level, BlockPos pos, NonNullList<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }

    public enum Content implements StringRepresentable {
        EMPTY("empty"),
        WATER("water"),
        VINEGAR("vinegar"),
        HONEY("honey");

        private final String name;

        Content(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}
