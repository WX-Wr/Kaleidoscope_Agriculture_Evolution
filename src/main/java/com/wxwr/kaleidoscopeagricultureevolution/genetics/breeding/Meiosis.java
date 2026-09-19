package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.Random;

public class Meiosis {
    private static final Random RANDOM = new Random();

    public static byte[] formGamete(Genome parent, CropSpecies species, Random rand) {
        Chromosome[] chromosomes = species.getChromosomes();
        int totalLoci = species.getTotalLoci();
        byte[] gamete = new byte[totalLoci];

        for (int c = 0; c < chromosomes.length; c++) {
            Chromosome chr = chromosomes[c];
            GeneLocus[] loci = chr.getLoci();
            int currentHomolog = rand.nextBoolean() ? 0 : 1;

            for (int l = 0; l < loci.length; l++) {
                if (l > 0) {
                    double crossProb = chr.crossoverProbability(l);
                    if (rand.nextDouble() < crossProb) {
                        currentHomolog = 1 - currentHomolog;
                    }
                }
                int flatIndex = species.getFlatLocusIndex(c, l);
                int allele = parent.getAllele(c, l, currentHomolog);
                allele = MutationEngine.maybeMutate(loci[l], allele, rand);
                gamete[flatIndex] = (byte) allele;
            }
        }
        return gamete;
    }

    public static byte[] formGamete(Genome parent, CropSpecies species) {
        return formGamete(parent, species, RANDOM);
    }
}
