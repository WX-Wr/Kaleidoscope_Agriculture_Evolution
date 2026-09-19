package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;

import java.util.Optional;
import java.util.Random;

public final class BeeBreedingEngine {
    private static final Random RANDOM = new Random();

    private BeeBreedingEngine() {
    }

    public static Optional<BeeGenomeData> breed(BeeGenomeData parentA, BeeGenomeData parentB) {
        return breed(parentA, parentB, RANDOM);
    }

    public static Optional<BeeGenomeData> breed(BeeGenomeData parentA, BeeGenomeData parentB, Random random) {
        if (!canBreed(parentA, parentB) || random == null) {
            return Optional.empty();
        }

        BeeSpecies species = BeeSpeciesRegistry.get(parentA.getSpeciesId());
        byte[] gameteA = formGamete(parentA, species, random);
        byte[] gameteB = formGamete(parentB, species, random);
        byte[] zygote = combineGametes(gameteA, gameteB, species);
        int generation = Math.max(parentA.getGeneration(), parentB.getGeneration()) + 1;
        return Optional.of(new BeeGenomeData(species.getId(), BeeGenomeData.CURRENT_VERSION, zygote,
            BeeGenomeData.ORIGIN_BRED, generation, false));
    }

    public static Optional<BeeGenomeData> selfBreed(BeeGenomeData parent, Random random) {
        return breed(parent, parent, random);
    }

    public static boolean canBreed(BeeGenomeData parentA, BeeGenomeData parentB) {
        return parentA != null
            && parentB != null
            && parentA.getSpeciesId().equals(parentB.getSpeciesId());
    }

    public static byte[] formGamete(BeeGenomeData parent, BeeSpecies species, Random random) {
        if (parent == null || species == null || random == null) {
            return new byte[0];
        }

        Chromosome[] chromosomes = species.getChromosomes();
        byte[] gamete = new byte[species.getTotalLoci()];
        for (int chromosomeIndex = 0; chromosomeIndex < chromosomes.length; chromosomeIndex++) {
            Chromosome chromosome = chromosomes[chromosomeIndex];
            GeneLocus[] loci = chromosome.getLoci();
            int currentHomolog = random.nextBoolean() ? 0 : 1;

            for (int locusIndex = 0; locusIndex < loci.length; locusIndex++) {
                if (locusIndex > 0 && random.nextDouble() < chromosome.crossoverProbability(locusIndex)) {
                    currentHomolog = 1 - currentHomolog;
                }

                int flatIndex = species.getFlatLocusIndex(chromosomeIndex, locusIndex);
                int allele = currentHomolog == 0
                    ? parent.getAlleleA(flatIndex)
                    : parent.getAlleleB(flatIndex);
                gamete[flatIndex] = (byte) BeeMutationRegistry.maybeMutate(loci[locusIndex], allele, species, random);
            }
        }
        return gamete;
    }

    private static byte[] combineGametes(byte[] gameteA, byte[] gameteB, BeeSpecies species) {
        byte[] zygote = new byte[species.getTotalLoci()];
        for (int i = 0; i < zygote.length; i++) {
            GeneLocus locus = species.getLocus(i);
            int alleleA = i < gameteA.length ? gameteA[i] : 0;
            int alleleB = i < gameteB.length ? gameteB[i] : 0;
            zygote[i] = (byte) ((clampAllele(alleleA, locus) << 4) | clampAllele(alleleB, locus));
        }
        return zygote;
    }

    private static int clampAllele(int allele, GeneLocus locus) {
        int max = locus != null ? locus.getMaxValue() : 4;
        return Math.max(0, Math.min(max, allele));
    }
}
