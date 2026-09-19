package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

public class HeterosisCalculator {

    public static double calculateYieldHeterosis(Genome genome, CropSpecies species) {
        int yieldLocus = species.getFlatLocusIndex(1, 0);
        int fruitSizeLocus = species.getFlatLocusIndex(1, 1);
        int seedSetLocus = species.getFlatLocusIndex(1, 2);

        double heterozygosity = genome.getHeterozygosity(yieldLocus, fruitSizeLocus, seedSetLocus);
        return 1.0 + 0.15 * heterozygosity;
    }

    public static double calculateGrowthHeterosis(Genome genome, CropSpecies species) {
        int growthLocus = species.getFlatLocusIndex(0, 0);
        double heterozygosity = genome.getHeterozygosity(growthLocus);
        return 1.0 + 0.10 * heterozygosity;
    }
}
