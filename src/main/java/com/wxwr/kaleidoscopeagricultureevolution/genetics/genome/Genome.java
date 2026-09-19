package com.wxwr.kaleidoscopeagricultureevolution.genetics.genome;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.Arrays;

public class Genome {
    private final byte[] data;
    private final CropSpecies species;

    private Genome(byte[] data, CropSpecies species) {
        this.data = data;
        this.species = species;
    }

    public static Genome create(CropSpecies species) {
        int totalLoci = species.getTotalLoci();
        return new Genome(new byte[totalLoci], species);
    }

    public static Genome of(byte[] data, CropSpecies species) {
        return new Genome(normalizeData(Arrays.copyOf(data, data.length), species), species);
    }

    public int getAllele(int chromosomeIndex, int locusIndex, int homolog) {
        int flatIndex = species.getFlatLocusIndex(chromosomeIndex, locusIndex);
        if (flatIndex < 0 || flatIndex >= data.length) return 0;
        byte b = data[flatIndex];
        return homolog == 0 ? (b >> 4) & 0xF : b & 0xF;
    }

    public int getAlleleA(int flatIndex) {
        return (data[flatIndex] >> 4) & 0xF;
    }

    public int getAlleleB(int flatIndex) {
        return data[flatIndex] & 0xF;
    }

    public boolean isHeterozygous(int flatIndex) {
        return getAlleleA(flatIndex) != getAlleleB(flatIndex);
    }

    public boolean isHomozygous(int flatIndex) {
        return getAlleleA(flatIndex) == getAlleleB(flatIndex);
    }

    public boolean hasDominantAllele(int flatIndex, int dominantAllele) {
        return getAlleleA(flatIndex) == dominantAllele || getAlleleB(flatIndex) == dominantAllele;
    }

    public boolean isHomozygousRecessive(int flatIndex, int recessiveAllele) {
        return getAlleleA(flatIndex) == recessiveAllele && getAlleleB(flatIndex) == recessiveAllele;
    }

    public int getQuantitativeSum(int... flatIndices) {
        int sum = 0;
        for (int idx : flatIndices) {
            sum += getAlleleA(idx) + getAlleleB(idx);
        }
        return sum;
    }

    public int getQuantitativeAverage(int... flatIndices) {
        if (flatIndices.length == 0) return 0;
        return getQuantitativeSum(flatIndices) / (flatIndices.length * 2);
    }

    public double getHeterozygosity(int... flatIndices) {
        if (flatIndices.length == 0) return 0;
        int het = 0;
        for (int idx : flatIndices) {
            if (isHeterozygous(idx)) het++;
        }
        return (double) het / flatIndices.length;
    }

    public byte[] getData() { return Arrays.copyOf(data, data.length); }
    byte[] getDataInternal() { return data; }
    public CropSpecies getSpecies() { return species; }
    public int getLocusCount() { return data.length; }

    public Genome copy() {
        return new Genome(Arrays.copyOf(data, data.length), species);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Genome other)) return false;
        return Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Genome[");
        Chromosome[] chromosomes = species.getChromosomes();
        for (int c = 0; c < chromosomes.length; c++) {
            if (c > 0) sb.append(" | ");
            Chromosome chr = chromosomes[c];
            sb.append(chr.getName()).append(":");
            for (int l = 0; l < chr.getLocusCount(); l++) {
                int flat = species.getFlatLocusIndex(c, l);
                sb.append(' ').append(getAlleleA(flat)).append('/').append(getAlleleB(flat));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static class Builder {
        private final byte[] data;
        private final CropSpecies species;

        public Builder(CropSpecies species) {
            this.species = species;
            this.data = new byte[species.getTotalLoci()];
        }

        public Builder setAlleles(int chromosomeIndex, int locusIndex, int alleleA, int alleleB) {
            int flatIndex = species.getFlatLocusIndex(chromosomeIndex, locusIndex);
            return setAlleles(flatIndex, alleleA, alleleB);
        }

        public Builder setAlleles(int flatIndex, int alleleA, int alleleB) {
            GeneLocus locus = species.getLocus(flatIndex);
            int a = clampAllele(locus, alleleA);
            int b = clampAllele(locus, alleleB);
            data[flatIndex] = (byte) ((a << 4) | b);
            return this;
        }

        public Builder setHomozygous(int chromosomeIndex, int locusIndex, int allele) {
            return setAlleles(chromosomeIndex, locusIndex, allele, allele);
        }

        public Genome build() {
            return new Genome(Arrays.copyOf(data, data.length), species);
        }
    }

    private static byte[] normalizeData(byte[] source, CropSpecies species) {
        byte[] normalized = new byte[species.getTotalLoci()];
        System.arraycopy(source, 0, normalized, 0, Math.min(source.length, normalized.length));
        for (int i = 0; i < normalized.length; i++) {
            GeneLocus locus = species.getLocus(i);
            int a = clampAllele(locus, (normalized[i] >> 4) & 0xF);
            int b = clampAllele(locus, normalized[i] & 0xF);
            normalized[i] = (byte) ((a << 4) | b);
        }
        return normalized;
    }

    private static int clampAllele(GeneLocus locus, int allele) {
        return Math.max(0, Math.min(locus.getMaxValue(), allele));
    }
}
