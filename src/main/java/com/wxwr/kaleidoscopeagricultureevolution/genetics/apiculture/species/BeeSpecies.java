package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BeeSpecies {
    private final ResourceLocation id;
    private final Chromosome[] chromosomes;
    private final int[] flatIndexOffsets;
    private final int totalLoci;
    private final Map<ResourceLocation, Float> productTable;
    private final List<ResourceLocation> biomeTags;
    private final List<ResourceLocation> flowerTags;
    private final float mutationBias;
    private final int baseColonySize;
    private final float[][] wildAlleleFrequencies;

    public BeeSpecies(ResourceLocation id, Chromosome[] chromosomes,
                      Map<ResourceLocation, Float> productTable,
                      List<ResourceLocation> biomeTags, List<ResourceLocation> flowerTags,
                      float mutationBias, int baseColonySize,
                      float[][] wildAlleleFrequencies) {
        this.id = id;
        this.chromosomes = chromosomes;
        this.productTable = productTable != null ? Map.copyOf(productTable) : Map.of();
        this.biomeTags = biomeTags != null ? List.copyOf(biomeTags) : List.of();
        this.flowerTags = flowerTags != null ? List.copyOf(flowerTags) : List.of();
        this.mutationBias = Math.max(0.0F, mutationBias);
        this.baseColonySize = Math.max(1, baseColonySize);

        this.flatIndexOffsets = new int[chromosomes.length];
        int offset = 0;
        for (int i = 0; i < chromosomes.length; i++) {
            flatIndexOffsets[i] = offset;
            offset += chromosomes[i].getLocusCount();
        }
        this.totalLoci = offset;
        this.wildAlleleFrequencies = normalizeWildFrequencies(wildAlleleFrequencies, totalLoci);
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getDisplayTranslationKey() {
        return "bee_species." + id.getNamespace() + "." + id.getPath();
    }

    public Chromosome[] getChromosomes() {
        return chromosomes;
    }

    public int getTotalLoci() {
        return totalLoci;
    }

    public Map<ResourceLocation, Float> getProductTable() {
        return Collections.unmodifiableMap(productTable);
    }

    public List<ResourceLocation> getBiomeTags() {
        return Collections.unmodifiableList(biomeTags);
    }

    public List<ResourceLocation> getFlowerTags() {
        return Collections.unmodifiableList(flowerTags);
    }

    public float getMutationBias() {
        return mutationBias;
    }

    public int getBaseColonySize() {
        return baseColonySize;
    }

    public int getFlatLocusIndex(int chromosomeIndex, int locusIndex) {
        if (chromosomeIndex < 0 || chromosomeIndex >= chromosomes.length) return -1;
        if (locusIndex < 0 || locusIndex >= chromosomes[chromosomeIndex].getLocusCount()) return -1;
        return flatIndexOffsets[chromosomeIndex] + locusIndex;
    }

    public int getChromosomeFromFlat(int flatIndex) {
        for (int c = chromosomes.length - 1; c >= 0; c--) {
            if (flatIndex >= flatIndexOffsets[c]) return c;
        }
        return 0;
    }

    public int getLocusFromFlat(int flatIndex) {
        int chr = getChromosomeFromFlat(flatIndex);
        return flatIndex - flatIndexOffsets[chr];
    }

    public GeneLocus getLocus(int flatIndex) {
        int chr = getChromosomeFromFlat(flatIndex);
        int locus = flatIndex - flatIndexOffsets[chr];
        return chromosomes[chr].getLoci()[locus];
    }

    public float[] getWildAlleleFrequencies(int flatIndex) {
        if (flatIndex < 0 || flatIndex >= wildAlleleFrequencies.length) return null;
        return wildAlleleFrequencies[flatIndex];
    }

    private static float[][] normalizeWildFrequencies(float[][] source, int totalLoci) {
        float[][] normalized = new float[totalLoci][];
        for (int i = 0; i < totalLoci; i++) {
            normalized[i] = source != null && i < source.length && source[i] != null
                ? source[i].clone()
                : new float[]{1.0F};
        }
        return normalized;
    }
}
