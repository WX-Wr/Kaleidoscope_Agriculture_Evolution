package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.entity.FarmerEntity;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class PlowEventHandler {

    /**
     * 空手潜行右键耕牛 → 取下犁（掉落犁物品），牛保持耕牛状态不变。
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof PlowOxEntity ox) {
            if (event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().isEmpty()) {
                boolean handled = false;
                if (ox.hasRiceSacks()) {
                    ox.setVisualState(PlowOxEntity.VisualState.DEFAULT);
                    if (!event.getLevel().isClientSide) {
                        ox.spawnAtLocation(new ItemStack(ModItems.RICE_SACK.get(), 2));
                    }
                    handled = true;
                } else if (ox.hasMountedTool()) {
                    ox.removeTool();
                    ox.setToolRemoved(true);
                    handled = true;
                } else if (ox.hasYoke()) {
                    ox.setVisualState(PlowOxEntity.VisualState.DEFAULT);
                    ox.setToolRemoved(true);
                    ox.setToolEntityUUID(null);
                    if (!event.getLevel().isClientSide) {
                        ox.spawnAtLocation(new ItemStack(ModItems.YOKE.get()));
                    }
                    handled = true;
                }
                if (handled) {
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }
            }
        }
    }

    /**
     * 阻止 FarmerEntity 和 PlowOxEntity 踩坏耕地。
     */
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof FarmerEntity || event.getEntity() instanceof PlowOxEntity) {
            event.setCanceled(true);
        }
    }
}
