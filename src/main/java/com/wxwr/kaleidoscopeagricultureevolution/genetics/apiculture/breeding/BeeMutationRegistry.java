package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class BeeMutationRegistry {
    private static final Map<String, Float> LOCUS_RATE_MULTIPLIERS = new HashMap<>();
    private static float mutationRateMultiplier = 1.0F;

    private BeeMutationRegistry() {
    }

    public static void setMutationRateMultiplier(float multiplier) {
        mutationRateMultiplier = Math.max(0.0F, multiplier);
    }

    public static float getMutationRateMultiplier() {
        return mutationRateMultiplier;
    }

    public static void registerLocusRateMultiplier(String locusId, float multiplier) {
        if (locusId == null || locusId.isBlank()) {
            return;
        }
        LOCUS_RATE_MULTIPLIERS.put(locusId, Math.max(0.0F, multiplier));
    }

    public static void clearLocusRateMultipliers() {
        LOCUS_RATE_MULTIPLIERS.clear();
    }

    public static float getEffectiveMutationRate(GeneLocus locus, BeeSpecies species) {
        if (locus == null) {
            return 0.0F;
        }
        float speciesBias = species != null ? species.getMutationBias() : 1.0F;
        float locusMultiplier = LOCUS_RATE_MULTIPLIERS.getOrDefault(locus.getId(), 1.0F);
        return Math.max(0.0F, locus.getMutationRate()
            * mutationRateMultiplier
            * Math.max(0.0F, speciesBias)
            * locusMultiplier);
    }

    public static int maybeMutate(GeneLocus locus, int allele, BeeSpecies species, Random random) {
        if (locus == null || random == null) {
            return allele;
        }

        float rate = getEffectiveMutationRate(locus, species);
        if (random.nextFloat() >= rate) {
            return clampAllele(locus, allele);
        }

        if (locus.getType() == GeneType.MENDELIAN) {
            return mutateMendelian(locus, allele, random);
        }
        return mutateQuantitative(locus, allele, random);
    }

    private static int mutateMendelian(GeneLocus locus, int currentAllele, Random random) {
        int alleleCount = locus.getAlleleCount();
        if (alleleCount <= 1) {
            return clampAllele(locus, currentAllele);
        }

        int newAllele;
        do {
            newAllele = random.nextInt(alleleCount);
        } while (newAllele == currentAllele);
        return newAllele;
    }

    private static int mutateQuantitative(GeneLocus locus, int currentValue, Random random) {
        int delta = random.nextFloat() < 0.7F ? 1 : -1;
        return clampAllele(locus, currentValue + delta);
    }

    private static int clampAllele(GeneLocus locus, int allele) {
        int max = locus != null ? locus.getMaxValue() : 4;
        return Math.max(0, Math.min(max, allele));
    }
}
