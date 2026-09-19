package com.wxwr.kaleidoscopeagricultureevolution.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum FermentationTapState implements StringRepresentable {
    NONE("none"),
    CLOSE("close"),
    OPEN("open");

    private final String name;

    FermentationTapState(String name) {
        this.name = name;
    }

    public FermentationTapState toggleOpenClosed() {
        return this == OPEN ? CLOSE : OPEN;
    }

    public FermentationTapState next() {
        return switch (this) {
            case NONE -> CLOSE;
            case CLOSE -> OPEN;
            case OPEN -> NONE;
        };
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
