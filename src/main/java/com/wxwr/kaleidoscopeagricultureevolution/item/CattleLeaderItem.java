package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.entity.ModEntities;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CattleLeaderItem extends Item {

    public CattleLeaderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Cow cow) || target instanceof PlowOxEntity) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        PlowOxEntity plowOx = ModEntities.PLOW_OX.get().create((ServerLevel) level);
        if (plowOx == null) {
            return InteractionResult.PASS;
        }

        plowOx.moveTo(cow.getX(), cow.getY(), cow.getZ(), cow.getYRot(), cow.getXRot());
        plowOx.setYRot(cow.getYRot());
        plowOx.setXRot(cow.getXRot());
        plowOx.yBodyRot = cow.yBodyRot;
        plowOx.yHeadRot = cow.yHeadRot;
        plowOx.setHealth(cow.getHealth());
        plowOx.setAge(cow.getAge());
        if (cow.hasCustomName()) {
            plowOx.setCustomName(cow.getCustomName());
            plowOx.setCustomNameVisible(cow.isCustomNameVisible());
        }
        plowOx.setVisualState(PlowOxEntity.VisualState.DEFAULT);
        plowOx.setOxState(PlowOxEntity.OxState.IDLE);
        plowOx.setToolRemoved(true);

        cow.discard();
        level.addFreshEntity(plowOx);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
