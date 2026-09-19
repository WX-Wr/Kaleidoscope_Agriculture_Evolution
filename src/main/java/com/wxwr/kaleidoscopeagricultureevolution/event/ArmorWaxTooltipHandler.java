package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.util.ArmorWaxing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class ArmorWaxTooltipHandler {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int layer = ArmorWaxing.getWaxLayer(stack);
        if (layer <= 0) {
            return;
        }

        int maxLayer = ArmorWaxing.getMaxWaxLayer(stack);
        int chancePercent = (int) Math.round(ArmorWaxing.getProtectionChance(stack) * 100.0D);
        event.getToolTip().add(Component.translatable(
                "tooltip.kaleidoscope_agriculture_evolution.wax_layer", layer, maxLayer)
            .withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable(
                "tooltip.kaleidoscope_agriculture_evolution.wax_protection", chancePercent)
            .withStyle(ChatFormatting.GRAY));
    }
}
