package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block.ModBlocks;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ModEntities;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;  // 改为 entity
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class PlowItem extends BlockItem {

    public PlowItem(Properties properties) {
        super(ModBlocks.PLOW.get(), properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) {
        if (entity instanceof PlowOxEntity) return false;

        if (entity instanceof Cow cow) {
            if (!player.level().isClientSide) {
                PlowOxEntity plowOx = ModEntities.PLOW_OX.get().create((ServerLevel) player.level());
                if (plowOx != null) {
                    plowOx.moveTo(cow.getX(), cow.getY(), cow.getZ(), cow.yRotO, cow.xRotO);
                    plowOx.setYRot(cow.yRotO);
                    plowOx.setXRot(cow.xRotO);
                    plowOx.yBodyRot = cow.yBodyRot;
                    plowOx.yHeadRot = cow.yHeadRot;
                    plowOx.setHealth(cow.getHealth());
                    plowOx.setAge(cow.getAge());
                    cow.discard();
                    player.level().addFreshEntity(plowOx);
                    stack.shrink(1);
                    return true;
                }
            }
            return true;
        }
        return super.onLeftClickEntity(stack, player, entity);
    }
}