package com.wxwr.kaleidoscopeagricultureevolution.util;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraitResolver;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

public final class ArmorWaxing {
    public static final String TAG_WAX_LAYER = "WaxLayer";
    public static final String TAG_WAX_LAYER_MAX = "WaxLayerMax";
    public static final String TAG_WAX_PROTECTION_CHANCE = "WaxProtectionChance";

    private static final int DEFAULT_LAYER = 32;
    private static final int MIN_LAYER = 16;
    private static final int MAX_LAYER = 64;
    private static final double DEFAULT_CHANCE = 0.25D;
    private static final double MIN_CHANCE = 0.20D;
    private static final double MAX_CHANCE = 0.40D;

    private ArmorWaxing() {
    }

    public static boolean canWax(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armorItem)) {
            return false;
        }
        return isMetalMaterial(armorItem.getMaterial());
    }

    public static boolean hasWaxLayer(ItemStack stack) {
        return getWaxLayer(stack) > 0;
    }

    public static int getWaxLayer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? Math.max(0, tag.getInt(TAG_WAX_LAYER)) : 0;
    }

    public static int getMaxWaxLayer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_WAX_LAYER_MAX)) {
            return getWaxLayer(stack);
        }
        return Math.max(0, tag.getInt(TAG_WAX_LAYER_MAX));
    }

    public static double getProtectionChance(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_WAX_PROTECTION_CHANCE)) {
            return DEFAULT_CHANCE;
        }
        return Mth.clamp(tag.getDouble(TAG_WAX_PROTECTION_CHANCE), MIN_CHANCE, MAX_CHANCE);
    }

    public static boolean applyWax(ItemStack armor, ItemStack wax, Player player) {
        if (!canWax(armor) || hasWaxLayer(armor)) {
            return false;
        }

        HoneyExtractorTraits traits = HoneyExtractorTraitResolver.resolve(wax);
        int maxLayer = layerFromTraits(armor, traits);
        double chance = chanceFromTraits(traits);

        CompoundTag tag = armor.getOrCreateTag();
        tag.putInt(TAG_WAX_LAYER, maxLayer);
        tag.putInt(TAG_WAX_LAYER_MAX, maxLayer);
        tag.putDouble(TAG_WAX_PROTECTION_CHANCE, chance);

        if (player == null || !player.getAbilities().instabuild) {
            wax.shrink(1);
        }
        return true;
    }

    public static int reduceDurabilityDamage(ItemStack stack, int amount, RandomSource random) {
        if (amount <= 0 || !canWax(stack)) {
            return amount;
        }

        int layer = getWaxLayer(stack);
        if (layer <= 0) {
            return amount;
        }

        double chance = getProtectionChance(stack);
        int prevented = 0;
        for (int i = 0; i < amount && layer > 0; i++) {
            if (random.nextDouble() < chance) {
                prevented++;
                layer--;
            }
        }

        if (prevented > 0) {
            setWaxLayer(stack, layer);
        }
        return amount - prevented;
    }

    private static void setWaxLayer(ItemStack stack, int layer) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }

        if (layer > 0) {
            tag.putInt(TAG_WAX_LAYER, layer);
            return;
        }

        tag.remove(TAG_WAX_LAYER);
        tag.remove(TAG_WAX_LAYER_MAX);
        tag.remove(TAG_WAX_PROTECTION_CHANCE);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    private static int layerFromTraits(ItemStack armor, HoneyExtractorTraits traits) {
        int layer = Mth.clamp((int) Math.round(DEFAULT_LAYER * traits.beeswaxYieldMultiplier()), MIN_LAYER, MAX_LAYER);
        if (armor.getItem() instanceof ArmorItem armorItem && armorItem.getMaterial() == ArmorMaterials.GOLD) {
            layer = Mth.clamp((int) Math.round(layer * 1.5D), MIN_LAYER, MAX_LAYER);
        }
        return layer;
    }

    private static double chanceFromTraits(HoneyExtractorTraits traits) {
        double chance = DEFAULT_CHANCE + (traits.progressSpeedMultiplier() - 1.0D) * 0.10D;
        return Mth.clamp(chance, MIN_CHANCE, MAX_CHANCE);
    }

    private static boolean isMetalMaterial(ArmorMaterial material) {
        return material == ArmorMaterials.CHAIN
                || material == ArmorMaterials.IRON
                || material == ArmorMaterials.GOLD
                || material == ArmorMaterials.DIAMOND
                || material == ArmorMaterials.NETHERITE;
    }
}
