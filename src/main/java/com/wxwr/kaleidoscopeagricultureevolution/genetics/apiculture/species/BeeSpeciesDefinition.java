package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BeeSpeciesDefinition(ResourceLocation id,
                                   Chromosome[] chromosomes,
                                   Map<ResourceLocation, Float> productTable,
                                   List<ResourceLocation> biomeTags,
                                   List<ResourceLocation> flowerTags,
                                   float mutationBias,
                                   int baseColonySize,
                                   float[][] wildAlleleFrequencies) {
    public BeeSpeciesDefinition {
        chromosomes = chromosomes != null ? chromosomes.clone() : new Chromosome[0];
        productTable = productTable != null ? Map.copyOf(productTable) : Map.of();
        biomeTags = biomeTags != null ? List.copyOf(biomeTags) : List.of();
        flowerTags = flowerTags != null ? List.copyOf(flowerTags) : List.of();
        mutationBias = Math.max(0.0F, mutationBias);
        baseColonySize = Math.max(1, baseColonySize);
        wildAlleleFrequencies = cloneFrequencies(wildAlleleFrequencies);
    }

    @NotNull
    public BeeSpecies toSpecies() {
        float[][] frequencies = wildAlleleFrequencies != null
            ? wildAlleleFrequencies
            : BeeSpeciesRegistry.defaultWildAlleleFrequencies(id, chromosomes);
        return new BeeSpecies(id, chromosomes, productTable, biomeTags, flowerTags,
            mutationBias, baseColonySize, frequencies);
    }

    @NotNull
    public static BeeSpeciesDefinition fromJson(@NotNull ResourceLocation fallbackId, @NotNull JsonObject root) {
        ResourceLocation id = readOptionalId(root, "id");
        if (id == null) {
            id = fallbackId;
        }
        List<ChromosomeDefinition> chromosomeDefinitions = readChromosomes(root);
        chromosomeDefinitions.sort(Comparator.comparingInt(ChromosomeDefinition::index));
        Chromosome[] chromosomes = chromosomeDefinitions.stream()
            .map(ChromosomeDefinition::toChromosome)
            .toArray(Chromosome[]::new);
        return new BeeSpeciesDefinition(
            id,
            chromosomes,
            readProductTable(root),
            readIdList(root, "biome_tags"),
            readIdList(root, "flower_tags"),
            readFloat(root, "mutation_bias", 1.0F),
            readInt(root, "base_colony_size", 5),
            readWildAlleleFrequencies(root));
    }

    private static List<ChromosomeDefinition> readChromosomes(JsonObject root) {
        JsonArray array = requiredArray(root, "chromosomes");
        List<ChromosomeDefinition> chromosomes = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("chromosomes entries must be objects");
            }
            chromosomes.add(ChromosomeDefinition.from(element.getAsJsonObject()));
        }
        if (chromosomes.isEmpty()) {
            throw new JsonParseException("A bee species must define at least one chromosome");
        }
        return chromosomes;
    }

    private static Map<ResourceLocation, Float> readProductTable(JsonObject root) {
        JsonObject object = optionalObject(root, "product_table");
        Map<ResourceLocation, Float> table = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                throw new JsonParseException("Invalid product id: " + entry.getKey());
            }
            table.put(id, entry.getValue().getAsFloat());
        }
        return Map.copyOf(table);
    }

    private static List<ResourceLocation> readIdList(JsonObject root, String key) {
        JsonArray array = optionalArray(root, key);
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException(key + " entries must be resource location strings");
            }
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) {
                throw new JsonParseException("Invalid resource location: " + element.getAsString());
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static float[][] readWildAlleleFrequencies(JsonObject root) {
        if (!root.has("wild_allele_frequencies")) {
            return null;
        }
        JsonArray loci = requiredArray(root, "wild_allele_frequencies");
        float[][] frequencies = new float[loci.size()][];
        for (int i = 0; i < loci.size(); i++) {
            JsonElement element = loci.get(i);
            if (!element.isJsonArray()) {
                throw new JsonParseException("wild_allele_frequencies entries must be arrays");
            }
            JsonArray weights = element.getAsJsonArray();
            float[] parsed = new float[weights.size()];
            for (int j = 0; j < weights.size(); j++) {
                parsed[j] = weights.get(j).getAsFloat();
            }
            frequencies[i] = parsed;
        }
        return frequencies;
    }

    private static JsonArray optionalArray(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null) {
            return new JsonArray();
        }
        if (!value.isJsonArray()) {
            throw new JsonParseException("Expected array for " + key);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject optionalObject(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null) {
            return new JsonObject();
        }
        if (!value.isJsonObject()) {
            throw new JsonParseException("Expected object for " + key);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new JsonParseException("Missing JSON array: " + key);
        }
        return value.getAsJsonArray();
    }

    private static ResourceLocation readOptionalId(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected string for " + key);
        }
        ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
        if (id == null) {
            throw new JsonParseException("Invalid resource location: " + value.getAsString());
        }
        return id;
    }

    private static String requiredString(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Missing string value: " + key);
        }
        return value.getAsString();
    }

    private static int requiredInt(JsonObject root, String key) {
        JsonElement value = root.get(key);
        if (value == null) {
            throw new JsonParseException("Missing integer value: " + key);
        }
        return value.getAsInt();
    }

    private static float readFloat(JsonObject root, String key, float defaultValue) {
        JsonElement value = root.get(key);
        return value == null ? defaultValue : value.getAsFloat();
    }

    private static int readInt(JsonObject root, String key, int defaultValue) {
        JsonElement value = root.get(key);
        return value == null ? defaultValue : value.getAsInt();
    }

    private static float[][] cloneFrequencies(float[][] source) {
        if (source == null) {
            return null;
        }
        float[][] clone = new float[source.length][];
        for (int i = 0; i < source.length; i++) {
            clone[i] = source[i] != null ? source[i].clone() : null;
        }
        return clone;
    }

    private record ChromosomeDefinition(int index, String name, List<LocusDefinition> loci) {
        private static ChromosomeDefinition from(JsonObject object) {
            JsonArray locusArray = requiredArray(object, "loci");
            List<LocusDefinition> loci = new ArrayList<>();
            for (JsonElement element : locusArray) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("loci entries must be objects");
                }
                loci.add(LocusDefinition.from(element.getAsJsonObject()));
            }
            if (loci.isEmpty()) {
                throw new JsonParseException("A chromosome must define at least one locus");
            }
            loci.sort(Comparator.comparingDouble(LocusDefinition::mapPosition));
            return new ChromosomeDefinition(
                requiredInt(object, "index"),
                requiredString(object, "name"),
                List.copyOf(loci));
        }

        private Chromosome toChromosome() {
            GeneLocus[] geneLoci = loci.stream()
                .map(locus -> locus.toGeneLocus(index))
                .toArray(GeneLocus[]::new);
            return new Chromosome(index, name, geneLoci);
        }
    }

    private record LocusDefinition(String id, float mapPosition, String type, int alleleCount, float mutationRate) {
        private static LocusDefinition from(JsonObject object) {
            return new LocusDefinition(
                requiredString(object, "id"),
                readFloat(object, "map_position", Float.NaN),
                readStringOrDefault(object, "type", "quantitative"),
                Math.max(2, readInt(object, "allele_count", 2)),
                readFloat(object, "mutation_rate", Float.NaN));
        }

        private GeneLocus toGeneLocus(int chromosomeIndex) {
            if (!Float.isFinite(mapPosition)) {
                throw new JsonParseException("Locus requires map_position");
            }
            if (!Float.isFinite(mutationRate)) {
                throw new JsonParseException("Locus requires mutation_rate");
            }
            GeneType geneType = "mendelian".equalsIgnoreCase(type) ? GeneType.MENDELIAN : GeneType.QUANTITATIVE;
            return geneType == GeneType.MENDELIAN
                ? GeneLocus.mendelian(id, chromosomeIndex, mapPosition, alleleCount, mutationRate)
                : GeneLocus.quantitative(id, chromosomeIndex, mapPosition, mutationRate);
        }
    }

    private static String readStringOrDefault(JsonObject root, String key, String defaultValue) {
        JsonElement value = root.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected string for " + key);
        }
        return value.getAsString();
    }
}
