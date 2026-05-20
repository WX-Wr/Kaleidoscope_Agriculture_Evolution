package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class WhipItem extends Item {

    public WhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        CompoundTag tag = stack.getOrCreateTag();

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                boolean editing = !tag.getBoolean("editing");
                if (!editing) {
                    // 退出编辑模式：直接保存当前的 corner1 和 corner2
                    if (tag.contains("corner1") && tag.contains("corner2")) {
                        saveRange(tag);
                    }
                    tag.remove("corner1");
                    tag.remove("corner2");
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_exit"), true);
                } else {
                    // 进入编辑模式
                    tag.putLong("corner1", player.blockPosition().asLong());
                    tag.remove("corner2");
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_enter"), true);
                }
                tag.putBoolean("editing", editing);
            } else {
                if (tag.getBoolean("editing")) {
                    tag.putLong("corner2", player.blockPosition().asLong());
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_corner"), true);
                }
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || level.isClientSide) return InteractionResult.PASS;

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.getBoolean("editing") && !player.isShiftKeyDown()) {
            tag.putLong("corner2", context.getClickedPos().asLong());
            player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_corner"), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private void saveRange(CompoundTag tag) {
        ListTag ranges = tag.getList("ranges", Tag.TAG_COMPOUND);
        CompoundTag range = new CompoundTag();
        range.putLong("c1", tag.getLong("corner1"));
        range.putLong("c2", tag.getLong("corner2"));
        ranges.add(range);
        tag.put("ranges", ranges);
    }

    public static List<BlockPos[]> getRanges(ItemStack stack) {
        List<BlockPos[]> result = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        ListTag ranges = tag.getList("ranges", Tag.TAG_COMPOUND);
        for (int i = 0; i < ranges.size(); i++) {
            CompoundTag range = ranges.getCompound(i);
            result.add(new BlockPos[]{
                    BlockPos.of(range.getLong("c1")),
                    BlockPos.of(range.getLong("c2"))
            });
        }
        return result;
    }
}