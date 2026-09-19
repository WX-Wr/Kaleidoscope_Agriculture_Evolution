package com.wxwr.kaleidoscopeagricultureevolution.work;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record WorkSeedEntry(
        ResourceLocation item,
        @Nullable ResourceLocation crop,
        boolean waterCrop,
        boolean required) {
}
