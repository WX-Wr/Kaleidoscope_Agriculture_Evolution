package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class DurablePortionCraftingHandler {
    private DurablePortionCraftingHandler() {
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (!(crafted.getItem() instanceof DurablePortionItem craftedItem)) {
            return;
        }

        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        Container inventory = event.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (first.isEmpty()) {
                first = stack;
            } else if (second.isEmpty()) {
                second = stack;
            } else {
                return;
            }
        }

        if (!isDurablePortionCombine(crafted, craftedItem, first, second)) {
            return;
        }

        ItemStack remainder = craftedItem.getExhaustedRemainder();
        if (!remainder.isEmpty()) {
            giveItem(event.getEntity(), remainder);
        }
    }

    private static boolean isDurablePortionCombine(ItemStack crafted, DurablePortionItem craftedItem,
                                                   ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty() || first.getItem() != crafted.getItem()
                || second.getItem() != crafted.getItem() || first.getCount() != 1 || second.getCount() != 1) {
            return false;
        }

        int portions = craftedItem.getPortions(first) + craftedItem.getPortions(second);
        return portions <= craftedItem.getMaxPortions() && craftedItem.getPortions(crafted) == portions
                && craftedItem.hasExhaustedRemainder();
    }

    private static void giveItem(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
