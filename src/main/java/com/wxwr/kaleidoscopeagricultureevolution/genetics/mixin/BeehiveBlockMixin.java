package com.wxwr.kaleidoscopeagricultureevolution.genetics.mixin;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraitResolver;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin {
    @Redirect(
        method = "dropHoneycomb",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/BeehiveBlock;popResource(Lnet/minecraft/world/level/Level;"
                + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V"))
    private static void kae$imprintColonyTraits(Level level, BlockPos pos, ItemStack stack) {
        if (!level.isClientSide() && Config.isGeneticsEnabled() && stack.is(Items.HONEYCOMB)
            && level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            BeehiveColonyCapability.Storage colony = BeehiveColonyCapability.get(hive);
            if (colony != null) {
                colony.synchronizeOccupants(hive);
                stack.setCount(colony.getState().consumeHoneyForHoneycombDrop(stack.getCount()));
                if (colony.getBeeCount() > 0) {
                    HoneyExtractorTraits traits = colony.getPhenotype().toHoneyExtractorTraits();
                    HoneyExtractorTraitResolver.writeToStack(stack, traits);
                }
                hive.setChanged();
            }
        }

        Block.popResource(level, pos, stack);
    }
}
