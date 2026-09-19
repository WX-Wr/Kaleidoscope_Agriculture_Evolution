package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class LoucheItem extends BlockItem {

    public LoucheItem(Properties properties) {
        super(ModBlocks.LOUCHE.get(), properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) {
        if (entity instanceof PlowOxEntity ox) {
            return attachToOx(stack, player, ox);
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (target instanceof PlowOxEntity ox) {
            return attachToOx(stack, player, ox) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    private boolean attachToOx(ItemStack stack, Player player, PlowOxEntity ox) {
        if (!ox.hasYoke()) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.kaleidoscope_agriculture_evolution.no_yoke_attached"), true);
            }
            return true;
        }
        if (!ox.canAttachTool()) {
            return false;
        }
        if (!player.level().isClientSide) {
            passNbtToOx(stack, ox);
            ox.attachTool(PlowOxEntity.ToolType.LOUCHE);
            if (!player.isCreative()) stack.shrink(1);
        }
        return true;
    }

    private static void passNbtToOx(ItemStack stack, PlowOxEntity ox) {
        if (stack.hasTag() && stack.getTag().contains("LoucheInv")) {
            ox.getPersistentData().put("PendingLoucheInv", stack.getTag().getCompound("LoucheInv"));
        }
    }
}
