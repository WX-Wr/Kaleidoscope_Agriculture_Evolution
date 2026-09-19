package com.wxwr.kaleidoscopeagricultureevolution.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum GlassFermentationBottleState implements StringRepresentable {
    NONE("none", FermentationContent.EMPTY, false),
    EMPTY_CLOSE("empty_close", FermentationContent.EMPTY, false),
    EMPTY_OPEN("empty_open", FermentationContent.EMPTY, true),
    WATER_CLOSE("water_close", FermentationContent.WATER, false),
    WATER_OPEN("water_open", FermentationContent.WATER, true),
    VINEGAR_CLOSE("vinegar_close", FermentationContent.VINEGAR, false),
    VINEGAR_OPEN("vinegar_open", FermentationContent.VINEGAR, true),
    HONEY_CLOSE("honey_close", FermentationContent.HONEY, false),
    HONEY_OPEN("honey_open", FermentationContent.HONEY, true),
    YEAST_SOLUTION_CLOSE("yeast_solution_close", FermentationContent.YEAST, false),
    YEAST_SOLUTION_OPEN("yeast_solution_open", FermentationContent.YEAST, true),
    APPLE_VINEGAR_CLOSE("apple_vinegar_close", FermentationContent.APPLE_VINEGAR, false),
    APPLE_VINEGAR_OPEN("apple_vinegar_open", FermentationContent.APPLE_VINEGAR, true);

    private final String name;
    private final FermentationContent content;
    private final boolean open;

    GlassFermentationBottleState(String name, FermentationContent content, boolean open) {
        this.name = name;
        this.content = content;
        this.open = open;
    }

    public boolean hasBottle() {
        return this != NONE;
    }

    public boolean isFilled() {
        return hasBottle() && content != FermentationContent.EMPTY;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isClosed() {
        return hasBottle() && !open;
    }

    public GlassFermentationBottleState open() {
        return switch (content) {
            case EMPTY -> EMPTY_OPEN;
            case WATER -> WATER_OPEN;
            case VINEGAR -> VINEGAR_OPEN;
            case HONEY -> HONEY_OPEN;
            case YEAST -> YEAST_SOLUTION_OPEN;
            case APPLE_VINEGAR -> APPLE_VINEGAR_OPEN;
            case RICE -> NONE;
        };
    }

    public GlassFermentationBottleState close() {
        return switch (content) {
            case EMPTY -> EMPTY_CLOSE;
            case WATER -> WATER_CLOSE;
            case VINEGAR -> VINEGAR_CLOSE;
            case HONEY -> HONEY_CLOSE;
            case YEAST -> YEAST_SOLUTION_CLOSE;
            case APPLE_VINEGAR -> APPLE_VINEGAR_CLOSE;
            case RICE -> NONE;
        };
    }

    public static GlassFermentationBottleState fromContent(FermentationContent content) {
        return fromContent(content, false);
    }

    public static GlassFermentationBottleState fromContent(FermentationContent content, boolean open) {
        return switch (content) {
            case WATER -> open ? WATER_OPEN : WATER_CLOSE;
            case VINEGAR -> open ? VINEGAR_OPEN : VINEGAR_CLOSE;
            case HONEY -> open ? HONEY_OPEN : HONEY_CLOSE;
            case YEAST -> open ? YEAST_SOLUTION_OPEN : YEAST_SOLUTION_CLOSE;
            case APPLE_VINEGAR -> open ? APPLE_VINEGAR_OPEN : APPLE_VINEGAR_CLOSE;
            case RICE -> open ? EMPTY_OPEN : EMPTY_CLOSE;
            case EMPTY -> open ? EMPTY_OPEN : EMPTY_CLOSE;
        };
    }

    public FermentationContent toContent() {
        return content;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}
