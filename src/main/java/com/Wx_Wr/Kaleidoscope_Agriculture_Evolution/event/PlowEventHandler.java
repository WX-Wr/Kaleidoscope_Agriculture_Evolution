package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.event;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block.ModBlocks;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;  // 改为 entity
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class PlowEventHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof PlowOxEntity ox) {
            if (event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().isEmpty()) {
                if (!event.getLevel().isClientSide) {
                    Cow cow = EntityType.COW.create(event.getLevel());
                    if (cow != null) {
                        cow.moveTo(ox.getX(), ox.getY(), ox.getZ(), ox.getYRot(), ox.getXRot());
                        cow.setHealth(ox.getHealth());
                        event.getLevel().addFreshEntity(cow);
                    }
                    ox.spawnAtLocation(new ItemStack(ModBlocks.PLOW.get().asItem()));
                    ox.discard();
                }
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }
}