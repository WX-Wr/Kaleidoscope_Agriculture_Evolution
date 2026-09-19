package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BeeColonyState {
    private static final String TAG_BEE_COUNT = "BeeCount";
    private static final String TAG_HEALTH = "Health";
    private static final String TAG_HONEY_STORAGE = "HoneyStorage";
    private static final String TAG_POLLEN_STORAGE = "PollenStorage";
    private static final String TAG_STABILITY = "Stability";
    private static final String TAG_DISEASE_LEVEL = "DiseaseLevel";
    private static final String TAG_MATING_PROGRESS = "MatingProgress";
    private static final String TAG_ACTIVE_SPECIES = "ActiveSpeciesId";

    private static final double MAX_HEALTH = 100.0D;
    private static final double MAX_STORAGE = 1000.0D;
    private static final double HONEY_PER_EXTRA_HONEYCOMB = 100.0D;
    private static final double HEALTH_STEP = 1.5D;
    private static final double STABILITY_STEP = 0.08D;
    private static final double BASE_HONEY_GAIN = 0.04D;
    private static final double BASE_POLLEN_GAIN = 0.03D;
    private static final double DISEASE_DRIFT = 0.01D;
    private static final double MATING_STEP = 0.012D;

    private int beeCount;
    private double health = MAX_HEALTH;
    private double honeyStorage;
    private double pollenStorage;
    private double stability = 0.5D;
    private double diseaseLevel;
    private double matingProgress;
    private ResourceLocation activeSpeciesId = BeePhenotype.neutral().speciesId();

    public int getBeeCount() {
        return beeCount;
    }

    public double getHealth() {
        return health;
    }

    public double getHoneyStorage() {
        return honeyStorage;
    }

    /**
     * Adds storage-backed bonus honeycombs to a vanilla drop and consumes only
     * the storage used for those bonus items. The vanilla drop remains intact.
     */
    public int consumeHoneyForHoneycombDrop(int vanillaCount) {
        int baseCount = Math.max(0, vanillaCount);
        int bonusCount = (int) Math.floor(honeyStorage / HONEY_PER_EXTRA_HONEYCOMB);
        if (bonusCount <= 0) {
            return baseCount;
        }

        honeyStorage = Mth.clamp(
            honeyStorage - bonusCount * HONEY_PER_EXTRA_HONEYCOMB,
            0.0D, MAX_STORAGE);
        return baseCount + bonusCount;
    }

    public double getPollenStorage() {
        return pollenStorage;
    }

    public double getStability() {
        return stability;
    }

    public double getDiseaseLevel() {
        return diseaseLevel;
    }

    public double getMatingProgress() {
        return matingProgress;
    }

    @NotNull
    public ResourceLocation getActiveSpeciesId() {
        return activeSpeciesId;
    }

    public void sync(@Nullable BeePhenotype phenotype, int beeCount) {
        BeePhenotype safe = phenotype != null ? phenotype : BeePhenotype.neutral();
        this.beeCount = Math.max(0, beeCount);
        this.activeSpeciesId = safe.speciesId();
    }

    public boolean tick(@Nullable BeePhenotype phenotype, int beeCount, @Nullable RandomSource random) {
        return tick(phenotype, beeCount, random, BeeColonyEnvironment.neutral(phenotype));
    }

    public boolean tick(@Nullable BeePhenotype phenotype, int beeCount, @Nullable RandomSource random,
                        @Nullable BeeColonyEnvironment environment) {
        BeePhenotype safe = phenotype != null ? phenotype : BeePhenotype.neutral();
        int clampedBeeCount = Math.max(0, beeCount);
        BeeColonyEnvironment env = environment != null ? environment : BeeColonyEnvironment.neutral(safe);

        boolean changed = clampedBeeCount != this.beeCount
            || !safe.speciesId().equals(activeSpeciesId);
        sync(safe, clampedBeeCount);

        double previousHealth = health;
        double previousHoney = honeyStorage;
        double previousPollen = pollenStorage;
        double previousStability = stability;
        double previousDisease = diseaseLevel;
        double previousMating = matingProgress;

        double targetStability = Mth.clamp(
            safe.stability() * 0.85D + Math.min(1.0D, clampedBeeCount / 8.0D) * 0.15D
                + env.stabilityModifier(),
            0.0D, 1.0D);
        stability = approach(stability, targetStability, STABILITY_STEP);

        double diseaseTarget = Mth.clamp(
            diseaseLevel
                + (0.02D - safe.diseaseResistance() * 0.03D)
                + Math.max(0, clampedBeeCount - 6) * 0.004D
                - stability * 0.01D,
            0.0D, 1.0D);
        diseaseTarget = Mth.clamp(diseaseTarget + env.diseasePressure(), 0.0D, 1.0D);
        if (random != null && clampedBeeCount > 0) {
            diseaseTarget = Mth.clamp(diseaseTarget + (random.nextDouble() - 0.5D) * DISEASE_DRIFT, 0.0D, 1.0D);
        }
        diseaseLevel = approach(diseaseLevel, diseaseTarget, 0.05D);

        double healthTarget = Mth.clamp(
            MAX_HEALTH - diseaseLevel * 40.0D + stability * 18.0D + safe.diseaseResistance() * 6.0D,
            0.0D, MAX_HEALTH);
        health = approach(health, healthTarget, HEALTH_STEP);

        double honeyGain = clampedBeeCount
            * safe.foragingEfficiency()
            * safe.honeyYieldMult()
            * BASE_HONEY_GAIN
            * env.productionMultiplier();
        double pollenGain = clampedBeeCount
            * safe.pollinationEfficiency()
            * safe.pollenYieldMult()
            * BASE_POLLEN_GAIN
            * env.pollinationMultiplier();
        if (random != null && clampedBeeCount > 0) {
            double jitter = 0.9D + random.nextDouble() * 0.2D;
            honeyGain *= jitter;
            pollenGain *= jitter;
        }
        honeyStorage = Mth.clamp(honeyStorage + honeyGain, 0.0D, MAX_STORAGE);
        pollenStorage = Mth.clamp(pollenStorage + pollenGain, 0.0D, MAX_STORAGE);

        double matingTarget = clampedBeeCount >= 2
            ? Math.min(1.0D, matingProgress + safe.pollinationEfficiency() * env.pollinationMultiplier() * MATING_STEP)
            : Math.max(0.0D, matingProgress - MATING_STEP * 0.5D);
        if (random != null && clampedBeeCount >= 2) {
            matingTarget = Mth.clamp(matingTarget + random.nextDouble() * 0.01D, 0.0D, 1.0D);
        }
        matingProgress = Mth.clamp(approach(matingProgress, matingTarget, 0.08D), 0.0D, 1.0D);

        return changed
            || !approximatelyEquals(previousHealth, health)
            || !approximatelyEquals(previousHoney, honeyStorage)
            || !approximatelyEquals(previousPollen, pollenStorage)
            || !approximatelyEquals(previousStability, stability)
            || !approximatelyEquals(previousDisease, diseaseLevel)
            || !approximatelyEquals(previousMating, matingProgress);
    }

    @NotNull
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_BEE_COUNT, beeCount);
        tag.putDouble(TAG_HEALTH, health);
        tag.putDouble(TAG_HONEY_STORAGE, honeyStorage);
        tag.putDouble(TAG_POLLEN_STORAGE, pollenStorage);
        tag.putDouble(TAG_STABILITY, stability);
        tag.putDouble(TAG_DISEASE_LEVEL, diseaseLevel);
        tag.putDouble(TAG_MATING_PROGRESS, matingProgress);
        tag.putString(TAG_ACTIVE_SPECIES, activeSpeciesId.toString());
        return tag;
    }

    public void fromNBT(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        beeCount = Math.max(0, tag.getInt(TAG_BEE_COUNT));
        health = Mth.clamp(tag.getDouble(TAG_HEALTH), 0.0D, MAX_HEALTH);
        honeyStorage = Mth.clamp(tag.getDouble(TAG_HONEY_STORAGE), 0.0D, MAX_STORAGE);
        pollenStorage = Mth.clamp(tag.getDouble(TAG_POLLEN_STORAGE), 0.0D, MAX_STORAGE);
        stability = Mth.clamp(tag.getDouble(TAG_STABILITY), 0.0D, 1.0D);
        diseaseLevel = Mth.clamp(tag.getDouble(TAG_DISEASE_LEVEL), 0.0D, 1.0D);
        matingProgress = Mth.clamp(tag.getDouble(TAG_MATING_PROGRESS), 0.0D, 1.0D);
        ResourceLocation species = ResourceLocation.tryParse(tag.getString(TAG_ACTIVE_SPECIES));
        if (species != null) {
            activeSpeciesId = species;
        }
    }

    private static double approach(double current, double target, double maxStep) {
        if (!Double.isFinite(current)) {
            current = 0.0D;
        }
        if (!Double.isFinite(target)) {
            target = current;
        }
        double delta = Mth.clamp(target - current, -maxStep, maxStep);
        return current + delta;
    }

    private static boolean approximatelyEquals(double a, double b) {
        return Math.abs(a - b) < 1.0E-6D;
    }
}
