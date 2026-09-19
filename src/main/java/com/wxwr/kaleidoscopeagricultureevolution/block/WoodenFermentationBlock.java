package com.wxwr.kaleidoscopeagricultureevolution.block;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.FermentationContainerBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WoodenFermentationBlock extends BaseEntityBlock {
    public static final EnumProperty<FermentationContent> CONTENT = EnumProperty.create("content", FermentationContent.class);
    public static final IntegerProperty FILL_LEVEL = IntegerProperty.create("fill_level", 0, 4);
    public static final BooleanProperty LIDDED = BooleanProperty.create("lidded");
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public WoodenFermentationBlock() {
        super(Properties.of()
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(CONTENT, FermentationContent.EMPTY)
                .setValue(FILL_LEVEL, 0)
                .setValue(LIDDED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONTENT, FILL_LEVEL, LIDDED);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hit) {
        if (player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(LIDDED, !state.getValue(LIDDED)), Block.UPDATE_ALL);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getValue(LIDDED)) {
            return FermentationLiquidInteractions.isLiquidInteractionItem(player.getItemInHand(hand))
                    ? InteractionResult.sidedSuccess(level.isClientSide)
                    : InteractionResult.PASS;
        }

        InteractionResult riceResult = tryInsertRice(state, level, pos, player, hand);
        if (riceResult != InteractionResult.PASS) {
            return riceResult;
        }

        InteractionResult nonLiquidResult = tryInsertNonLiquidItem(level, pos, player, hand);
        if (nonLiquidResult != InteractionResult.PASS) {
            return nonLiquidResult;
        }

        return FermentationLiquidInteractions.tryUseLiquid(state, level, pos, player, hand,
                CONTENT, FILL_LEVEL, 4);
    }

    private static InteractionResult tryInsertRice(BlockState state, Level level, BlockPos pos, Player player,
                                                   InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !isRiceIngredient(held)) {
            return InteractionResult.PASS;
        }

        FermentationContent content = state.getValue(CONTENT);
        int fillLevel = state.getValue(FILL_LEVEL);
        boolean riceSack = held.is(ModItems.RICE_SACK.get());
        if (content != FermentationContent.EMPTY && content != FermentationContent.RICE) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (fillLevel >= 4) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state
                    .setValue(CONTENT, FermentationContent.RICE)
                    .setValue(FILL_LEVEL, fillLevel + 1), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                if (riceSack) {
                    consumeItemsAndGive(player, hand, 1, new ItemStack(ModItems.SACK.get()));
                } else {
                    held.shrink(1);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult tryInsertNonLiquidItem(Level level, BlockPos pos, Player player,
                                                            InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !isNonLiquidIngredient(held)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            ItemStack inserted = ((DurablePortionItem) ModItems.PORTION_YEAST_POWDER.get()).withPortions(1);
            ItemStack remaining = container.addNonLiquidItem(inserted);
            if (remaining.isEmpty() && !player.getAbilities().instabuild) {
                player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean isNonLiquidIngredient(ItemStack stack) {
        return stack.is(ModItems.PORTION_YEAST_POWDER.get());
    }

    private static boolean isRiceIngredient(ItemStack stack) {
        if (stack.is(ModItems.RICE_SACK.get())) {
            return true;
        }
        return isRegisteredItem(stack, "farmersdelight", "rice")
                || isRegisteredItem(stack, "kaleidoscope_cookery", "rice");
    }

    private static boolean isRegisteredItem(ItemStack stack, String namespace, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(KaleidoscopeAgricultureEvolution.rl(namespace, path));
        return item != null && stack.is(item);
    }

    public static InteractionResult tryExtractLiquid(BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand) {
        if (state.getValue(LIDDED)) {
            return InteractionResult.PASS;
        }
        return FermentationLiquidInteractions.tryExtractLiquid(state, level, pos, player, hand,
                CONTENT, FILL_LEVEL);
    }

    public static InteractionResult tryExtractNonLiquidItem(BlockState state, Level level, BlockPos pos,
                                                            Player player, InteractionHand hand) {
        if (state.getValue(LIDDED) || !player.isShiftKeyDown() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container)
                || !container.hasNonLiquidItems()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            ItemStack removed = container.removeOneNonLiquidItem();
            if (removed.isEmpty()) {
                return InteractionResult.PASS;
            }
            giveItem(player, removed);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult tryExtractRiceSack(BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand) {
        if (state.getValue(LIDDED)
                || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).is(ModItems.SACK.get())
                || state.getValue(CONTENT) != FermentationContent.RICE
                || state.getValue(FILL_LEVEL) <= 0) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int nextFillLevel = state.getValue(FILL_LEVEL) - 1;
            BlockState nextState = nextFillLevel <= 0
                    ? state.setValue(CONTENT, FermentationContent.EMPTY).setValue(FILL_LEVEL, 0)
                    : state.setValue(FILL_LEVEL, nextFillLevel);
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                consumeItemsAndGive(player, hand, 1, new ItemStack(ModItems.RICE_SACK.get()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void consumeItemsAndGive(Player player, InteractionHand hand, int count, ItemStack replacement) {
        ItemStack held = player.getItemInHand(hand);
        held.shrink(count);
        if (held.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!replacement.isEmpty() && !player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container) {
            container.dropNonLiquidItems(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new FermentationContainerBlockEntity(pos, state);
    }
}
