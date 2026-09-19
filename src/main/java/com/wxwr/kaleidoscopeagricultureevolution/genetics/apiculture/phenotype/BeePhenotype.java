package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import net.minecraft.resources.ResourceLocation;

public record BeePhenotype(ResourceLocation speciesId,
                           double precipitationTolerance,
                           double nocturnalActivity,
                           double flowerAffinity,
                           double foragingEfficiency,
                           double pollinationEfficiency,
                           double honeyYieldMult,
                           double waxYieldMult,
                           double pollenYieldMult,
                           double lifespanMult,
                           double diseaseResistance,
                           double aggression,
                           double stability,
                           double specialProductChance) {
    private static final ResourceLocation HONEY_BOTTLE =
        KaleidoscopeAgricultureEvolution.rl("minecraft", "honey_bottle");
    private static final ResourceLocation BEESWAX =
        KaleidoscopeAgricultureEvolution.rl("beeswax");

    public BeePhenotype {
        precipitationTolerance = clamp01(precipitationTolerance);
        nocturnalActivity = clamp01(nocturnalActivity);
        flowerAffinity = clamp01(flowerAffinity);
        foragingEfficiency = clampMultiplier(foragingEfficiency);
        pollinationEfficiency = clampMultiplier(pollinationEfficiency);
        honeyYieldMult = clampMultiplier(honeyYieldMult);
        waxYieldMult = clampMultiplier(waxYieldMult);
        pollenYieldMult = clampMultiplier(pollenYieldMult);
        lifespanMult = clampMultiplier(lifespanMult);
        diseaseResistance = clamp01(diseaseResistance);
        aggression = clamp01(aggression);
        stability = clamp01(stability);
        specialProductChance = clamp(specialProductChance, 0.0D, 1.0D);
    }

    public static BeePhenotype ofGenome(BeeGenomeData genome) {
        if (genome == null) {
            return neutral();
        }
        return evaluate(genome, BeeSpeciesRegistry.get(genome.getSpeciesId()));
    }

    public static BeePhenotype evaluate(BeeGenomeData genome, BeeSpecies species) {
        if (genome == null || species == null) {
            return neutral();
        }

        double bloodlineStability = score(genome, species, 0, 1);
        double honeyYield = score(genome, species, 1, 0);
        double beeswaxYield = score(genome, species, 1, 1);
        double lifespan = score(genome, species, 2, 0);
        double disease = score(genome, species, 2, 1);
        double pollination = score(genome, species, 3, 0);
        double flowerAffinity = score(genome, species, 3, 1);
        double precipitation = score(genome, species, 4, 0);
        double nocturnal = score(genome, species, 4, 1);
        double aggression = score(genome, species, 5, 0);
        double colonyActivity = score(genome, species, 5, 1);
        double homozygosityBias = score(genome, species, 6, 0);
        double mutationTolerance = score(genome, species, 6, 1);
        double specialProduct = score(genome, species, 7, 0);
        double rareDrop = score(genome, species, 7, 1);
        double homozygosity = homozygosity(genome);

        double stability = clamp01(
            bloodlineStability * 0.35D
                + homozygosityBias * 0.30D
                + homozygosity * 0.25D
                + mutationTolerance * 0.10D);

        double stableOutputFactor = 0.95D + stability * 0.10D;
        double activeForaging = clamp01(colonyActivity * 0.70D + flowerAffinity * 0.30D);

        return new BeePhenotype(
            species.getId(),
            precipitation,
            nocturnal,
            flowerAffinity,
            lerp(0.70D, 1.65D, activeForaging),
            lerp(0.65D, 1.75D, pollination) * (0.90D + flowerAffinity * 0.20D),
            lerp(0.75D, 1.75D, honeyYield) * productWeight(species, HONEY_BOTTLE) * stableOutputFactor,
            lerp(0.75D, 1.75D, beeswaxYield) * productWeight(species, BEESWAX) * stableOutputFactor,
            lerp(0.75D, 1.70D, clamp01(pollination * 0.55D + flowerAffinity * 0.30D + colonyActivity * 0.15D)),
            lerp(0.70D, 1.80D, lifespan),
            disease,
            aggression,
            stability,
            clamp(0.005D + specialProduct * specialProduct * 0.08D + rareDrop * 0.02D + stability * 0.01D,
                0.0D, 0.15D)
        );
    }

    public static BeePhenotype neutral() {
        return new BeePhenotype(BeeSpeciesRegistry.DEFAULT_SPECIES,
            0.5D, 0.0D, 0.5D,
            1.0D, 1.0D, 1.0D, 1.0D, 1.0D,
            1.0D, 0.5D, 0.0D, 0.5D, 0.01D);
    }

    public HoneyExtractorTraits toHoneyExtractorTraits() {
        return new HoneyExtractorTraits(foragingEfficiency, honeyYieldMult, waxYieldMult);
    }

    public char foragingGrade() {
        return grade(foragingEfficiency, 1.55D);
    }

    public char pollinationGrade() {
        return grade(pollinationEfficiency, 1.65D);
    }

    public char honeyGrade() {
        return grade(honeyYieldMult, 1.70D);
    }

    public char waxGrade() {
        return grade(waxYieldMult, 1.70D);
    }

    public char stabilityGrade() {
        return grade(stability, 1.0D);
    }

    private static double score(BeeGenomeData genome, BeeSpecies species, int chromosome, int locus) {
        int flat = species.getFlatLocusIndex(chromosome, locus);
        if (flat < 0 || flat >= genome.getLocusCount()) {
            return 0.0D;
        }
        int maxValue = Math.max(1, species.getLocus(flat).getMaxValue());
        return clamp01((genome.getAlleleA(flat) + genome.getAlleleB(flat)) / (maxValue * 2.0D));
    }

    private static double homozygosity(BeeGenomeData genome) {
        int loci = genome.getLocusCount();
        if (loci <= 0) {
            return 0.0D;
        }
        int homozygous = 0;
        for (int i = 0; i < loci; i++) {
            if (genome.isHomozygous(i)) {
                homozygous++;
            }
        }
        return (double) homozygous / loci;
    }

    private static double productWeight(BeeSpecies species, ResourceLocation productId) {
        Float weight = species.getProductTable().get(productId);
        return weight != null ? weight : 1.0D;
    }

    private static char grade(double value, double scale) {
        double relative = value / scale;
        if (relative >= 0.9D) return 'S';
        if (relative >= 0.7D) return 'A';
        if (relative >= 0.55D) return 'B';
        if (relative >= 0.4D) return 'C';
        return 'D';
    }

    private static double lerp(double min, double max, double t) {
        return min + (max - min) * clamp01(t);
    }

    private static double clampMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return clamp(value, 0.0D, 64.0D);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
