package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.Random;

public class CrossingEngine {

    public static Genome cross(Genome parentA, Genome parentB, CropSpecies species, Random rand) {
        requireCompatible(parentA, parentB, species);
        byte[] gameteA = Meiosis.formGamete(parentA, species, rand);
        byte[] gameteB = Meiosis.formGamete(parentB, species, rand);
        return zygote(gameteA, gameteB, species);
    }

    public static Genome cross(Genome parentA, Genome parentB, CropSpecies species) {
        return cross(parentA, parentB, species, new Random());
    }

    public static Genome selfPollinate(Genome parent, CropSpecies species, Random rand) {
        if (parent == null || species == null || !sameSpecies(parent.getSpecies(), species)) {
            throw new IllegalArgumentException("Genome species does not match the requested crop species");
        }
        byte[] gameteA = Meiosis.formGamete(parent, species, rand);
        byte[] gameteB = Meiosis.formGamete(parent, species, rand);
        return zygote(gameteA, gameteB, species);
    }

    public static Genome selfPollinate(Genome parent, CropSpecies species) {
        return selfPollinate(parent, species, new Random());
    }

    static Genome zygote(byte[] gameteA, byte[] gameteB, CropSpecies species) {
        int totalLoci = species.getTotalLoci();
        byte[] zygote = new byte[totalLoci];
        for (int i = 0; i < totalLoci; i++) {
            int a = gameteA[i] & 0xF;
            int b = gameteB[i] & 0xF;
            zygote[i] = (byte) ((a << 4) | b);
        }
        return Genome.of(zygote, species);
    }

    private static void requireCompatible(Genome parentA, Genome parentB, CropSpecies species) {
        if (parentA == null || parentB == null || species == null
                || !sameSpecies(parentA.getSpecies(), species)
                || !sameSpecies(parentB.getSpecies(), species)) {
            throw new IllegalArgumentException("Both parent genomes must belong to the requested crop species");
        }
    }

    private static boolean sameSpecies(CropSpecies first, CropSpecies second) {
        return first != null && second != null && first.getId().equals(second.getId());
    }
}
