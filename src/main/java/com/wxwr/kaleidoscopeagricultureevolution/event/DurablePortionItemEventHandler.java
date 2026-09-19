package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class DurablePortionItemEventHandler {
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        ItemStack held = event.getItemStack();
        ItemStack converted = convert(held);
        if (converted.isEmpty()) {
            return;
        }

        Level level = event.getLevel();
        if (!level.isClientSide) {
            replaceHeldItem(player, event.getHand(), held, converted);
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    private static ItemStack convert(ItemStack stack) {
        if (stack.getItem() instanceof DurablePortionItem portionItem) {
            return portionItem.isFull(stack) ? portionItem.getBaseItem() : ItemStack.EMPTY;
        }

        // 原先这里遍历整个 ForgeRegistries.ITEMS；改为查 DurablePortionItem 的懒加载反查索引
        DurablePortionItem portionItem = DurablePortionItem.findForBaseItem(stack);
        return portionItem != null ? portionItem.fullStack() : ItemStack.EMPTY;
    }

    private static void replaceHeldItem(Player player, net.minecraft.world.InteractionHand hand,
                                        ItemStack held, ItemStack converted) {
        if (held.getItem() instanceof DurablePortionItem) {
            player.setItemInHand(hand, converted);
            return;
        }

        if (held.getCount() == 1) {
            player.setItemInHand(hand, converted);
            return;
        }

        held.shrink(1);
        if (!player.getInventory().add(converted)) {
            player.drop(converted, false);
        }
    }
}
