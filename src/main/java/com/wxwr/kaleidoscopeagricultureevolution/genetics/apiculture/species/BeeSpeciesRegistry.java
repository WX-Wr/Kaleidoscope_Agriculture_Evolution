package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BeeSpeciesRegistry {
    public static final ResourceLocation TROPICAL_SPECIES =
        KaleidoscopeAgricultureEvolution.rl("tropical_bee");
    public static final ResourceLocation TEMPERATE_SPECIES =
        KaleidoscopeAgricultureEvolution.rl("temperate_bee");
    public static final ResourceLocation COLD_SPECIES =
        KaleidoscopeAgricultureEvolution.rl("cold_bee");
    public static final ResourceLocation DEFAULT_SPECIES = TEMPERATE_SPECIES;

    private static final ResourceLocation LEGACY_MEADOW_SPECIES =
        KaleidoscopeAgricultureEvolution.rl("meadow_bee");
    private static final String SPECIES_RESOURCE_ROOT =
        "/data/" + KaleidoscopeAgricultureEvolution.MODID + "/bee_species/";
    private static final String[] BUILTIN_SPECIES_FILES = {
        "tropical_bee.json",
        "temperate_bee.json",
        "cold_bee.json"
    };

    private static final Map<ResourceLocation, BeeSpecies> SPECIES = new LinkedHashMap<>();

    private BeeSpeciesRegistry() {
    }

    public static void registerAll() {
        if (!SPECIES.isEmpty()) {
            return;
        }
        reload(loadBundledSpecies());
    }

    public static void reload(Collection<BeeSpecies> species) {
        if (species == null || species.isEmpty()) {
            if (SPECIES.isEmpty()) {
                reload(loadBundledSpecies());
            }
            return;
        }
        SPECIES.clear();
        for (BeeSpecies entry : species) {
            register(entry);
        }
    }

    public static void register(BeeSpecies species) {
        if (species != null) {
            SPECIES.put(species.getId(), species);
        }
    }

    public static BeeSpecies get(ResourceLocation id) {
        ensureBuiltins();
        return SPECIES.get(normalizeSpeciesId(id));
    }

    public static BeeSpecies getDefaultSpecies() {
        ensureBuiltins();
        return SPECIES.get(DEFAULT_SPECIES);
    }

    public static ResourceLocation normalizeSpeciesId(ResourceLocation id) {
        ensureBuiltins();
        if (LEGACY_MEADOW_SPECIES.equals(id)) {
            return TEMPERATE_SPECIES;
        }
        return SPECIES.containsKey(id) ? id : DEFAULT_SPECIES;
    }

    public static Collection<BeeSpecies> getAll() {
        ensureBuiltins();
        return Collections.unmodifiableCollection(SPECIES.values());
    }

    static float[][] defaultWildAlleleFrequencies(ResourceLocation speciesId, Chromosome[] chromosomes) {
        if (TROPICAL_SPECIES.equals(speciesId)) {
            return tropicalWildFrequencies(chromosomes);
        }
        if (TEMPERATE_SPECIES.equals(speciesId)) {
            return temperateWildFrequencies(chromosomes);
        }
        if (COLD_SPECIES.equals(speciesId)) {
            return coldWildFrequencies(chromosomes);
        }
        return uniformWildFrequencies(chromosomes);
    }

    private static void ensureBuiltins() {
        if (SPECIES.isEmpty()) {
            registerAll();
        }
    }

    private static List<BeeSpecies> loadBundledSpecies() {
        List<BeeSpecies> species = new ArrayList<>();
        for (String fileName : BUILTIN_SPECIES_FILES) {
            ResourceLocation fallbackId = KaleidoscopeAgricultureEvolution.rl(fileName.replace(".json", ""));
            String resourcePath = SPECIES_RESOURCE_ROOT + fileName;
            try (InputStream stream = BeeSpeciesRegistry.class.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new IOException("Missing bundled bee species resource: " + resourcePath);
                }
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    species.add(BeeSpeciesDefinition.fromJson(fallbackId, root).toSpecies());
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load bee species resource: " + resourcePath, exception);
            }
        }
        return List.copyOf(species);
    }

    private static float[][] temperateWildFrequencies(Chromosome[] chromosomes) {
        return fillFrequencies(chromosomes, new float[]{0.30F, 0.30F, 0.22F, 0.13F, 0.05F});
    }

    private static float[][] tropicalWildFrequencies(Chromosome[] chromosomes) {
        float[][] freqs = temperateWildFrequencies(chromosomes);
        freqs[flat(chromosomes, 1, 0)] = new float[]{0.20F, 0.28F, 0.25F, 0.18F, 0.09F};
        freqs[flat(chromosomes, 3, 0)] = new float[]{0.22F, 0.28F, 0.24F, 0.18F, 0.08F};
        freqs[flat(chromosomes, 5, 1)] = new float[]{0.18F, 0.28F, 0.27F, 0.19F, 0.08F};
        return freqs;
    }

    private static float[][] coldWildFrequencies(Chromosome[] chromosomes) {
        float[][] freqs = temperateWildFrequencies(chromosomes);
        freqs[flat(chromosomes, 2, 0)] = new float[]{0.18F, 0.25F, 0.28F, 0.20F, 0.09F};
        freqs[flat(chromosomes, 2, 1)] = new float[]{0.18F, 0.24F, 0.28F, 0.21F, 0.09F};
        freqs[flat(chromosomes, 4, 0)] = new float[]{0.16F, 0.24F, 0.28F, 0.22F, 0.10F};
        return freqs;
    }

    private static float[][] fillFrequencies(Chromosome[] chromosomes, float[] weights) {
        int totalLoci = totalLoci(chromosomes);
        float[][] freqs = new float[totalLoci][];
        for (int i = 0; i < totalLoci; i++) {
            freqs[i] = weights.clone();
        }
        return freqs;
    }

    private static float[][] uniformWildFrequencies(Chromosome[] chromosomes) {
        int totalLoci = totalLoci(chromosomes);
        float[][] freqs = new float[totalLoci][];
        for (int i = 0; i < totalLoci; i++) {
            freqs[i] = new float[]{1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
        }
        return freqs;
    }

    private static int flat(Chromosome[] chromosomes, int chromosomeIndex, int locusIndex) {
        int offset = 0;
        for (int i = 0; i < chromosomeIndex; i++) {
            offset += chromosomes[i].getLocusCount();
        }
        return offset + locusIndex;
    }

    private static int totalLoci(Chromosome[] chromosomes) {
        int total = 0;
        for (Chromosome chromosome : chromosomes) {
            total += chromosome.getLocusCount();
        }
        return total;
    }
}
