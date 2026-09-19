package com.wxwr.kaleidoscopeagricultureevolution.block;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.FermentationContainerBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

final class FermentationLiquidInteractions {
    private FermentationLiquidInteractions() {
    }

    static InteractionResult tryUseLiquid(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, EnumProperty<FermentationContent> contentProperty,
                                          IntegerProperty fillLevelProperty, int maxFillLevel) {
        ItemStack held = player.getItemInHand(hand);
        FermentationContent content = state.getValue(contentProperty);
        if (player.isShiftKeyDown() && canReceiveExtractedLiquid(held, content)
                && state.getValue(fillLevelProperty) > 0) {
            return tryExtractLiquid(state, level, pos, player, hand, contentProperty, fillLevelProperty);
        }

        FermentationContent inserted = contentFromItem(held);
        if (inserted != FermentationContent.EMPTY) {
            return tryInsert(state, level, pos, player, hand, contentProperty, fillLevelProperty, maxFillLevel, inserted);
        }

        return isLiquidInteractionItem(held) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    static boolean isLiquidInteractionItem(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || contentFromItem(stack) != FermentationContent.EMPTY
                || stack.is(ModItems.PORTION_WATER_BOTTLE.get())
                || stack.is(ModItems.PORTION_HONEY_BOTTLE.get())
                || stack.is(ModItems.PORTION_VINEGAR.get())
                || stack.is(ModItems.PORTION_APPLE_VINEGAR.get())
                || isYeastItem(stack);
    }

    private static InteractionResult tryInsert(BlockState state, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, EnumProperty<FermentationContent> contentProperty,
                                               IntegerProperty fillLevelProperty, int maxFillLevel,
                                               FermentationContent inserted) {
        FermentationContent current = state.getValue(contentProperty);
        int fillLevel = state.getValue(fillLevelProperty);
        if (current != FermentationContent.EMPTY && current != inserted) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (fillLevel >= maxFillLevel) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state
                    .setValue(contentProperty, inserted)
                    .setValue(fillLevelProperty, fillLevel + 1), Block.UPDATE_ALL);
            if (inserted == FermentationContent.APPLE_VINEGAR
                    && level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container) {
                int currentBottles = container.getAppleVinegarBottles();
                container.setAppleVinegarBottles(currentBottles > 0 ? currentBottles + 1 : fillLevel + 1);
            }
            consumeLiquidIngredient(player, hand);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static InteractionResult tryExtractLiquid(BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, EnumProperty<FermentationContent> contentProperty,
                                              IntegerProperty fillLevelProperty) {
        FermentationContent content = state.getValue(contentProperty);
        if (!player.isShiftKeyDown() || !canReceiveExtractedLiquid(player.getItemInHand(hand), content)) {
            return InteractionResult.PASS;
        }

        int fillLevel = state.getValue(fillLevelProperty);
        if (fillLevel <= 0) {
            return InteractionResult.PASS;
        }

        if (content == FermentationContent.APPLE_VINEGAR) {
            return tryExtractAppleVinegar(state, level, pos, player, hand, contentProperty, fillLevelProperty);
        }

        if (!level.isClientSide) {
            int nextFillLevel = fillLevel - 1;
            BlockState nextState = nextFillLevel == 0
                    ? state.setValue(contentProperty, FermentationContent.EMPTY).setValue(fillLevelProperty, 0)
                    : state.setValue(fillLevelProperty, nextFillLevel);
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
            fillLiquidPortion(player, hand, content);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static InteractionResult tryExtractAppleVinegar(BlockState state, Level level, BlockPos pos, Player player,
                                                            InteractionHand hand,
                                                            EnumProperty<FermentationContent> contentProperty,
                                                            IntegerProperty fillLevelProperty) {
        if (!canReceiveExtractedLiquid(player.getItemInHand(hand), FermentationContent.APPLE_VINEGAR)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            int remainingBottles = 0;
            boolean usesStoredBottleCount = false;
            if (level.getBlockEntity(pos) instanceof FermentationContainerBlockEntity container
                    && container.getAppleVinegarBottles() > 0) {
                remainingBottles = container.takeAppleVinegarBottle();
                usesStoredBottleCount = true;
            } else {
                remainingBottles = Math.max(0, state.getValue(fillLevelProperty) - 1);
            }

            int nextFillLevel = usesStoredBottleCount
                    ? Math.min(GlassFermentationBlock.MAX_FILL_LEVEL,
                    (remainingBottles + GlassFermentationBlock.APPLE_VINEGAR_BOTTLES_PER_STAGE - 1)
                            / GlassFermentationBlock.APPLE_VINEGAR_BOTTLES_PER_STAGE)
                    : remainingBottles;
            BlockState nextState = nextFillLevel <= 0
                    ? state.setValue(contentProperty, FermentationContent.EMPTY).setValue(fillLevelProperty, 0)
                    : state.setValue(fillLevelProperty, nextFillLevel);
            level.setBlock(pos, nextState, Block.UPDATE_ALL);
            fillLiquidPortion(player, hand, FermentationContent.APPLE_VINEGAR);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canReceiveExtractedLiquid(ItemStack stack, FermentationContent content) {
        if (content == FermentationContent.EMPTY) {
            return false;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            return true;
        }
        if (!(stack.getItem() instanceof DurablePortionItem portionItem) || portionItem.isFull(stack)) {
            return false;
        }
        return switch (content) {
            case WATER -> stack.is(ModItems.PORTION_WATER_BOTTLE.get());
            case VINEGAR -> stack.is(ModItems.PORTION_VINEGAR.get());
            case APPLE_VINEGAR -> stack.is(ModItems.PORTION_APPLE_VINEGAR.get());
            case HONEY -> stack.is(ModItems.PORTION_HONEY_BOTTLE.get());
            case YEAST -> stack.is(ModItems.PORTION_YEAST.get());
            case RICE -> false;
            case EMPTY -> false;
        };
    }

    private static FermentationContent contentFromItem(ItemStack stack) {
        if (stack.is(ModItems.PORTION_WATER_BOTTLE.get())
                || (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER)) {
            return FermentationContent.WATER;
        }
        if (stack.is(ModItems.PORTION_HONEY_BOTTLE.get()) || stack.is(Items.HONEY_BOTTLE)) {
            return FermentationContent.HONEY;
        }
        if (stack.is(ModItems.PORTION_VINEGAR.get()) || stack.is(ModItems.VINEGAR.get())) {
            return FermentationContent.VINEGAR;
        }
        if (stack.is(ModItems.PORTION_APPLE_VINEGAR.get()) || stack.is(ModItems.APPLE_VINEGAR.get())) {
            return FermentationContent.APPLE_VINEGAR;
        }
        if (isYeastItem(stack)) {
            return FermentationContent.YEAST;
        }
        return FermentationContent.EMPTY;
    }

    private static boolean isYeastItem(ItemStack stack) {
        return stack.is(ModItems.PORTION_YEAST.get());
    }

    private static void consumeLiquidIngredient(Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, DurablePortionItem.consume(held, 1));
            return;
        }

        consumeItemsAndGive(player, hand, 1, new ItemStack(Items.GLASS_BOTTLE));
    }

    private static void fillLiquidPortion(Player player, InteractionHand hand, FermentationContent content) {
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

    private static ItemStack onePortionContent(FermentationContent content) {
        return switch (content) {
            case WATER -> ((DurablePortionItem) ModItems.PORTION_WATER_BOTTLE.get()).withPortions(1);
            case VINEGAR -> ((DurablePortionItem) ModItems.PORTION_VINEGAR.get()).withPortions(1);
            case APPLE_VINEGAR -> ((DurablePortionItem) ModItems.PORTION_APPLE_VINEGAR.get()).withPortions(1);
            case HONEY -> ((DurablePortionItem) ModItems.PORTION_HONEY_BOTTLE.get()).withPortions(1);
            case YEAST -> ((DurablePortionItem) ModItems.PORTION_YEAST.get()).withPortions(1);
            case RICE -> ItemStack.EMPTY;
            case EMPTY -> ItemStack.EMPTY;
        };
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
}
