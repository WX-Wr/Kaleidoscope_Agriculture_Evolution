package com.wxwr.kaleidoscopeagricultureevolution.block;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.ModBlockEntities;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HoneyExtractorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);

    public HoneyExtractorBlock() {
        super(Properties.of()
                .strength(2.5F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new HoneyExtractorBlockEntity(pos, state);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor) {
            if (extractor.getBeeswaxCount() > 0) {
                Block.popResource(level, pos, new ItemStack(ModItems.BEESWAX.get(), extractor.getBeeswaxCount()));
            }
            for (int i = 0; i < extractor.getHoneycombCount(); i++) {
                Block.popResource(level, pos, new ItemStack(Items.HONEYCOMB));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                            @NotNull BlockState state,
                                                                            @NotNull BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.HONEY_EXTRACTOR.get(), HoneyExtractorBlockEntity::tick);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand,
                                          @NotNull BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor)) {
            return InteractionResult.PASS;
        }

        if (extractor.isInteracting(player)) {
            // 摇蜜过程中不再响应持续右键，避免客户端重复触发使用动画。
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (extractor.beginInteraction(player)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (removeBeeswax(level, pos, player) == InteractionResult.SUCCESS) {
                return InteractionResult.SUCCESS;
            }
            return removeHoneycomb(level, pos, player);
        }
        if (held.is(Items.GLASS_BOTTLE)) {
            return bottleHoney(level, pos, player, hand);
        }
        if (held.is(Items.HONEYCOMB)) {
            return addHoneycomb(level, pos, player, held);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult addHoneycomb(Level level, BlockPos pos, Player player, ItemStack held) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor
                && extractor.addHoneycomb(held, player.getAbilities().instabuild)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    private static InteractionResult bottleHoney(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor)
                || !extractor.canBottleHoney()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && extractor.bottleHoney()) {
            consumeItemsAndGive(player, hand, 1, new ItemStack(Items.HONEY_BOTTLE));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult removeHoneycomb(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor) {
            ItemStack removed = extractor.removeHoneycomb();
            if (!removed.isEmpty()) {
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }

    private static InteractionResult removeBeeswax(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HoneyExtractorBlockEntity extractor) {
            ItemStack removed = extractor.removeBeeswax();
            if (!removed.isEmpty()) {
                if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    private static void consumeItemsAndGive(Player player, InteractionHand hand, int count, ItemStack replacement) {
        if (player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        held.shrink(count);
        if (held.isEmpty()) {
            player.setItemInHand(hand, replacement);
        } else if (!replacement.isEmpty() && !player.getInventory().add(replacement)) {
            player.drop(replacement, false);
        }
    }
}
