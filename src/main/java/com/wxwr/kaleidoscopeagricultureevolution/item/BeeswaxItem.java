package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.util.ArmorWaxing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BeeswaxItem extends HoneycombItem {
    public BeeswaxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack wax = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack armor = player.getItemInHand(otherHand);
        if (!armor.isEmpty() && ArmorWaxing.canWax(armor) && !ArmorWaxing.hasWaxLayer(armor)) {
            if (!level.isClientSide) {
                ArmorWaxing.applyWax(armor, wax, player);
            }
            return InteractionResultHolder.sidedSuccess(wax, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack wax, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) {
            return false;
        }

        ItemStack armor = slot.getItem();
        if (armor.isEmpty() || !ArmorWaxing.canWax(armor) || ArmorWaxing.hasWaxLayer(armor)) {
            return false;
        }

        ArmorWaxing.applyWax(armor, wax, player);
        slot.setChanged();
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack wax, ItemStack armor, Slot slot, ClickAction action,
                                           Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY
                || armor.isEmpty()
                || !ArmorWaxing.canWax(armor)
                || ArmorWaxing.hasWaxLayer(armor)) {
            return false;
        }

        ArmorWaxing.applyWax(armor, wax, player);
        carriedAccess.set(armor);
        slot.setChanged();
        return true;
    }
}
