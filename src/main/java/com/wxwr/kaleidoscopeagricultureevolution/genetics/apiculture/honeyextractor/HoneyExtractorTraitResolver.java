package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class HoneyExtractorTraitResolver {
    public static final String TAG_TRAITS = "HoneyExtractorTraits";

    private HoneyExtractorTraitResolver() {
    }

    @NotNull
    public static HoneyExtractorTraits resolve(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return HoneyExtractorTraits.neutral();
        }
        if (stack.getItem() instanceof HoneyExtractorTraitSource source) {
            return source.getHoneyExtractorTraits(stack);
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TRAITS, Tag.TAG_COMPOUND)) {
            return HoneyExtractorTraits.fromNBT(tag.getCompound(TAG_TRAITS));
        }
        return HoneyExtractorTraits.neutral();
    }

    public static void writeToStack(@NotNull ItemStack stack, @NotNull HoneyExtractorTraits traits) {
        if (traits.isNeutral()) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(TAG_TRAITS);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        stack.getOrCreateTag().put(TAG_TRAITS, traits.toNBT());
    }
}
