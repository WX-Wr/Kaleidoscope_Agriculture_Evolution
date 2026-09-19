package com.wxwr.kaleidoscopeagricultureevolution.genetics.item;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.GenomeSerializer;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Phenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SeedTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!Config.isGeneticsEnabled()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        if (CropSpeciesRegistry.fromSeed(item) == null) return;

        // 只读：绝不创建空 tag
        CompoundTag tag = stack.getTag();
        if (!GenomeSerializer.hasGenome(tag)) {
            event.getToolTip().add(
                Component.translatable("tooltip.kaleidoscope_agriculture_evolution.genetics.wild")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }

        Genome genome = GenomeSerializer.deserialize(tag);
        if (genome == null) return;

        Phenotype phenotype = Phenotype.ofGenome(genome, genome.getSpecies());

        event.getToolTip().add(Component.empty());
        event.getToolTip().add(
            Component.translatable("tooltip.kaleidoscope_agriculture_evolution.genetics.header")
                .withStyle(ChatFormatting.GOLD));

        addGradeLine(event, "tooltip.kaleidoscope_agriculture_evolution.genetics.growth", phenotype.growthGrade(),
            ChatFormatting.GREEN);
        addGradeLine(event, "tooltip.kaleidoscope_agriculture_evolution.genetics.yield", phenotype.yieldGrade(),
            ChatFormatting.GOLD);

        if (event.getFlags().isAdvanced()) {
            event.getToolTip().add(Component.literal(
                genome.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addGradeLine(ItemTooltipEvent event, String key, char grade,
                                     ChatFormatting color) {
        ChatFormatting gradeColor = grade == 'S' ? ChatFormatting.GOLD :
            grade == 'A' ? ChatFormatting.GREEN :
            grade == 'B' ? ChatFormatting.YELLOW :
            grade == 'C' ? ChatFormatting.GRAY :
            ChatFormatting.DARK_GRAY;

        event.getToolTip().add(
            Component.translatable(key)
                .withStyle(color)
                .append(Component.literal(" " + grade).withStyle(gradeColor)));
    }
}
