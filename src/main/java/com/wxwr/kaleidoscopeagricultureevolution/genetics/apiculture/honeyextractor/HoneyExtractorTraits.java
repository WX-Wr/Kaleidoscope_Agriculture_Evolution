package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public record HoneyExtractorTraits(double progressSpeedMultiplier,
                                   double honeyYieldMultiplier,
                                   double beeswaxYieldMultiplier) {
    private static final String TAG_PROGRESS = "ProgressSpeedMultiplier";
    private static final String TAG_HONEY = "HoneyYieldMultiplier";
    private static final String TAG_BEESWAX = "BeeswaxYieldMultiplier";

    private static final HoneyExtractorTraits NEUTRAL =
            new HoneyExtractorTraits(1.0D, 1.0D, 1.0D);

    public HoneyExtractorTraits {
        progressSpeedMultiplier = sanitize(progressSpeedMultiplier);
        honeyYieldMultiplier = sanitize(honeyYieldMultiplier);
        beeswaxYieldMultiplier = sanitize(beeswaxYieldMultiplier);
    }

    @NotNull
    public static HoneyExtractorTraits neutral() {
        return NEUTRAL;
    }

    public boolean isNeutral() {
        return equals(NEUTRAL);
    }

    @NotNull
    public static HoneyExtractorTraits fromNBT(@NotNull CompoundTag tag) {
        return new HoneyExtractorTraits(
                tag.getDouble(TAG_PROGRESS),
                tag.getDouble(TAG_HONEY),
                tag.getDouble(TAG_BEESWAX));
    }

    @NotNull
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_PROGRESS, progressSpeedMultiplier);
        tag.putDouble(TAG_HONEY, honeyYieldMultiplier);
        tag.putDouble(TAG_BEESWAX, beeswaxYieldMultiplier);
        return tag;
    }

    private static double sanitize(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Mth.clamp(value, 0.0D, 64.0D);
    }
}
