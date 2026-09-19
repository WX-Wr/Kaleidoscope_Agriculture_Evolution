package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.util.ArmorWaxing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class ArmorWaxingEventHandler {
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (tryWax(event.getLevel(), player, mainHand, offHand)
                || tryWax(event.getLevel(), player, offHand, mainHand)) {
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        }
    }

    private static boolean tryWax(Level level, Player player, ItemStack wax, ItemStack armor) {
        if (!wax.is(ModItems.BEESWAX.get())
                || armor.isEmpty()
                || !ArmorWaxing.canWax(armor)
                || ArmorWaxing.hasWaxLayer(armor)) {
            return false;
        }

        if (!level.isClientSide) {
            ArmorWaxing.applyWax(armor, wax, player);
        }
        return true;
    }
}
