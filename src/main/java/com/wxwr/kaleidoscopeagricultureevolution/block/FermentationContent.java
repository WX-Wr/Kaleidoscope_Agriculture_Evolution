package com.wxwr.kaleidoscopeagricultureevolution.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FermentationContent implements StringRepresentable {
    EMPTY("empty"),
    WATER("water"),
    VINEGAR("vinegar"),
    HONEY("honey"),
    YEAST("yeast"),
    APPLE_VINEGAR("apple_vinegar"),
    RICE("rice");

    private final String name;

    FermentationContent(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
