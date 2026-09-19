package com.wxwr.kaleidoscopeagricultureevolution.genetics.genome;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding.HeterosisCalculator;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding.PhenotypeBuilder;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.Objects;

public class Phenotype {
    private static final float GROWTH_GRADE_SCALE = 2.0f;
    private static final float YIELD_GRADE_SCALE = 3.5f;
    private static final float GROWTH_ALLELE_SCALE = 0.12f;
    private static final float YIELD_ALLELE_SCALE = 0.12f;
    private static final float FRUIT_SIZE_ALLELE_SCALE = 0.08f;
    private static final float SEED_SET_ALLELE_SCALE = 0.16f;

    public final float growthSpeedMult;
    public final float yieldMult;
    public final float seedMult;

    private Phenotype(PhenotypeBuilder b) {
        this.growthSpeedMult = b.growthSpeedMult;
        this.yieldMult = b.yieldMult;
        this.seedMult = b.seedMult;
    }

    public static Phenotype evaluate(Genome genome, CropSpecies species) {
        PhenotypeBuilder b = new PhenotypeBuilder();

        int growthIdx = species.getFlatLocusIndex(0, 0);
        b.growthSpeedMult = 1.0f + GROWTH_ALLELE_SCALE * genome.getQuantitativeSum(growthIdx);

        int yieldIdx = species.getFlatLocusIndex(1, 0);
        int fruitSizeIdx = species.getFlatLocusIndex(1, 1);
        int seedSetIdx = species.getFlatLocusIndex(1, 2);

        b.yieldMult = 1.0f + YIELD_ALLELE_SCALE * genome.getQuantitativeSum(yieldIdx);
        float fruitSizeScale = 1.0f + FRUIT_SIZE_ALLELE_SCALE * genome.getQuantitativeSum(fruitSizeIdx);
        b.yieldMult *= fruitSizeScale;
        b.seedMult = 1.0f + SEED_SET_ALLELE_SCALE * genome.getQuantitativeSum(seedSetIdx);

        // 杂种优势：来自杂合产量/生长位点的杂交活力
        double heterosisYield = HeterosisCalculator.calculateYieldHeterosis(genome, species);
        b.yieldMult *= (float) heterosisYield;
        double heterosisGrowth = HeterosisCalculator.calculateGrowthHeterosis(genome, species);
        b.growthSpeedMult *= (float) heterosisGrowth;

        return new Phenotype(b);
    }

    public static Phenotype ofGenome(Genome genome, CropSpecies species) {
        return PhenotypeCache.get(genome, species);
    }

    public char growthGrade() {
        float g = growthSpeedMult / GROWTH_GRADE_SCALE;
        return grade(g);
    }

    public char yieldGrade() {
        float y = yieldMult / YIELD_GRADE_SCALE;
        return grade(y);
    }

    private static char grade(float relative) {
        if (relative >= 0.9f) return 'S';
        if (relative >= 0.7f) return 'A';
        if (relative >= 0.55f) return 'B';
        if (relative >= 0.4f) return 'C';
        return 'D';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Phenotype that)) return false;
        return Float.compare(that.growthSpeedMult, growthSpeedMult) == 0 &&
            Float.compare(that.yieldMult, yieldMult) == 0 &&
            Float.compare(that.seedMult, seedMult) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(growthSpeedMult, yieldMult, seedMult);
    }

    @Override
    public String toString() {
        return String.format("Phenotype[growth=%c yield=%c]",
            growthGrade(), yieldGrade());
    }
}
