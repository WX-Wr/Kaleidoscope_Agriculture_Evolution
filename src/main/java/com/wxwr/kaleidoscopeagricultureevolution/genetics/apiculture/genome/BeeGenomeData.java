package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.Arrays;

public class BeeGenomeData {
    public static final int CURRENT_VERSION = 1;
    public static final int DEFAULT_LOCUS_COUNT = BeeSpeciesRegistry.getDefaultSpecies().getTotalLoci();
    public static final ResourceLocation TROPICAL_SPECIES =
        BeeSpeciesRegistry.TROPICAL_SPECIES;
    public static final ResourceLocation TEMPERATE_SPECIES =
        BeeSpeciesRegistry.TEMPERATE_SPECIES;
    public static final ResourceLocation COLD_SPECIES =
        BeeSpeciesRegistry.COLD_SPECIES;
    public static final ResourceLocation DEFAULT_SPECIES =
        BeeSpeciesRegistry.DEFAULT_SPECIES;
    public static final String ORIGIN_WILD = "wild";
    public static final String ORIGIN_BRED = "bred";
    public static final String ORIGIN_MUTATED = "mutated";

    private static final String TAG_SPECIES = "Species";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_GENOME = "Genome";
    private static final String TAG_ORIGIN = "Origin";
    private static final String TAG_GENERATION = "Generation";
    private static final String TAG_ANALYZED = "Analyzed";
    private static final ResourceLocation LEGACY_MEADOW_SPECIES =
        KaleidoscopeAgricultureEvolution.rl("meadow_bee");

    private final ResourceLocation speciesId;
    private final int version;
    private final byte[] genome;
    private final String origin;
    private final int generation;
    private final boolean analyzed;

    public BeeGenomeData(ResourceLocation speciesId, int version, byte[] genome,
                         String origin, int generation, boolean analyzed) {
        BeeSpecies species = BeeSpeciesRegistry.get(normalizeSpecies(speciesId));
        this.speciesId = species.getId();
        this.version = version > 0 ? version : CURRENT_VERSION;
        this.genome = normalizeGenome(genome, species);
        this.origin = normalizeOrigin(origin);
        this.generation = Math.max(0, generation);
        this.analyzed = analyzed;
    }

    public static BeeGenomeData createWild(long seed) {
        BeeSpecies species = BeeSpeciesRegistry.getDefaultSpecies();
        return createWild(seed, species.getId());
    }

    public static BeeGenomeData createWild(long seed, ResourceLocation speciesId) {
        BeeSpecies species = BeeSpeciesRegistry.get(normalizeSpecies(speciesId));
        RandomSource random = RandomSource.create(seed);
        byte[] data = new byte[species.getTotalLoci()];
        for (int i = 0; i < data.length; i++) {
            float[] weights = species.getWildAlleleFrequencies(i);
            int alleleA = weightedRandomAllele(random, weights);
            int alleleB = weightedRandomAllele(random, weights);
            data[i] = packAlleles(alleleA, alleleB);
        }
        return new BeeGenomeData(species.getId(), CURRENT_VERSION, data, ORIGIN_WILD, 0, false);
    }

    public static BeeGenomeData fromNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        ResourceLocation species = ResourceLocation.tryParse(tag.getString(TAG_SPECIES));
        int version = tag.getInt(TAG_VERSION);
        byte[] genome = tag.getByteArray(TAG_GENOME);
        String origin = tag.getString(TAG_ORIGIN);
        int generation = tag.getInt(TAG_GENERATION);
        boolean analyzed = tag.getBoolean(TAG_ANALYZED);
        return new BeeGenomeData(species, version, genome, origin, generation, analyzed);
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SPECIES, speciesId.toString());
        tag.putInt(TAG_VERSION, version);
        tag.putByteArray(TAG_GENOME, genome);
        tag.putString(TAG_ORIGIN, origin);
        tag.putInt(TAG_GENERATION, generation);
        tag.putBoolean(TAG_ANALYZED, analyzed);
        return tag;
    }

    public ResourceLocation getSpeciesId() {
        return speciesId;
    }

    public String getSpeciesTranslationKey() {
        return "bee_species." + speciesId.getNamespace() + "." + speciesId.getPath();
    }

    public int getVersion() {
        return version;
    }

    public byte[] getGenome() {
        return Arrays.copyOf(genome, genome.length);
    }

    public int getLocusCount() {
        return genome.length;
    }

    public int getAlleleA(int locusIndex) {
        if (locusIndex < 0 || locusIndex >= genome.length) {
            return 0;
        }
        return (genome[locusIndex] >> 4) & 0xF;
    }

    public int getAlleleB(int locusIndex) {
        if (locusIndex < 0 || locusIndex >= genome.length) {
            return 0;
        }
        return genome[locusIndex] & 0xF;
    }

    public boolean isHeterozygous(int locusIndex) {
        return getAlleleA(locusIndex) != getAlleleB(locusIndex);
    }

    public boolean isHomozygous(int locusIndex) {
        return getAlleleA(locusIndex) == getAlleleB(locusIndex);
    }

    public String getOrigin() {
        return origin;
    }

    public String getOriginTranslationKey() {
        return "bee_origin.kaleidoscope_agriculture_evolution." + origin;
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public BeeGenomeData withAnalyzed(boolean analyzed) {
        return new BeeGenomeData(speciesId, version, genome, origin, generation, analyzed);
    }

    public BeeGenomeData withGeneration(int generation) {
        return new BeeGenomeData(speciesId, version, genome, origin, generation, analyzed);
    }

    private static byte[] normalizeGenome(byte[] source, BeeSpecies species) {
        byte[] normalized = new byte[species.getTotalLoci()];
        if (source != null) {
            System.arraycopy(source, 0, normalized, 0, Math.min(source.length, normalized.length));
        }

        for (int i = 0; i < normalized.length; i++) {
            GeneLocus locus = species.getLocus(i);
            int alleleA = clampAllele((normalized[i] >> 4) & 0xF, locus);
            int alleleB = clampAllele(normalized[i] & 0xF, locus);
            normalized[i] = packAlleles(alleleA, alleleB, locus);
        }
        return normalized;
    }

    private static byte packAlleles(int alleleA, int alleleB) {
        return (byte) ((clampAllele(alleleA) << 4) | clampAllele(alleleB));
    }

    private static byte packAlleles(int alleleA, int alleleB, GeneLocus locus) {
        return (byte) ((clampAllele(alleleA, locus) << 4) | clampAllele(alleleB, locus));
    }

    private static int clampAllele(int allele) {
        return Math.max(0, Math.min(4, allele));
    }

    private static int clampAllele(int allele, GeneLocus locus) {
        int max = locus != null ? locus.getMaxValue() : 4;
        return Math.max(0, Math.min(max, allele));
    }

    private static String normalizeOrigin(String origin) {
        if (ORIGIN_BRED.equals(origin) || ORIGIN_MUTATED.equals(origin)) {
            return origin;
        }
        return ORIGIN_WILD;
    }

    private static ResourceLocation normalizeSpecies(ResourceLocation speciesId) {
        if (LEGACY_MEADOW_SPECIES.equals(speciesId)) {
            return TEMPERATE_SPECIES;
        }
        return BeeSpeciesRegistry.normalizeSpeciesId(speciesId);
    }

    private static int weightedRandomAllele(RandomSource random, float[] weights) {
        if (weights == null || weights.length == 0) {
            return 0;
        }
        float total = 0;
        for (float weight : weights) {
            total += Math.max(0.0F, weight);
        }
        if (total <= 0.0F) {
            return 0;
        }

        float roll = random.nextFloat() * total;
        float cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += Math.max(0.0F, weights[i]);
            if (roll <= cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
