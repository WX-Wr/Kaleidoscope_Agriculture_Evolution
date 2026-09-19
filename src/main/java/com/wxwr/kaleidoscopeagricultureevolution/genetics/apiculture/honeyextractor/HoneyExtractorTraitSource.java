package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface HoneyExtractorTraitSource {
    @NotNull HoneyExtractorTraits getHoneyExtractorTraits(@NotNull ItemStack stack);
}
