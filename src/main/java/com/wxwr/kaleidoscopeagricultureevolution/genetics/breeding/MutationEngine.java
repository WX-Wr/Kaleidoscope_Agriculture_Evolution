package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneType;

import java.util.Random;

public class MutationEngine {
    private static float mutationRateMultiplier = 1.0f;

    public static void setMutationRateMultiplier(float mult) {
        mutationRateMultiplier = mult;
    }

    public static int maybeMutate(GeneLocus locus, int allele, Random rand) {
        float rate = locus.getMutationRate() * mutationRateMultiplier;
        if (rand.nextFloat() >= rate) return allele;

        if (locus.getType() == GeneType.MENDELIAN) {
            return mutateMendelian(locus, allele, rand);
        } else {
            return mutateQuantitative(locus, allele, rand);
        }
    }

    private static int mutateMendelian(GeneLocus locus, int currentAllele, Random rand) {
        int alleleCount = locus.getAlleleCount();
        if (alleleCount <= 1) return currentAllele;
        int newAllele;
        do {
            newAllele = rand.nextInt(alleleCount);
        } while (newAllele == currentAllele);
        return newAllele;
    }

    private static int mutateQuantitative(GeneLocus locus, int currentValue, Random rand) {
        int delta = rand.nextFloat() < 0.7f ? 1 : -1;
        int newValue = currentValue + delta;
        return Math.max(0, Math.min(locus.getMaxValue(), newValue));
    }
}
