package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.compatibility;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bridges crop flower-source categories and bee flower preferences.
 *
 * <p>These are semantic tags, separate from Minecraft block tags. Bee species'
 * {@code flower_tags} continue to describe nearby blocks used by colony
 * environment evaluation.</p>
 */
public final class BeeFlowerCompatibility {
    public static final ResourceLocation GRAIN =
        KaleidoscopeAgricultureEvolution.rl("flower_source/grain");
    public static final ResourceLocation ROOT =
        KaleidoscopeAgricultureEvolution.rl("flower_source/root");
    public static final ResourceLocation FRUIT =
        KaleidoscopeAgricultureEvolution.rl("flower_source/fruit");
    public static final ResourceLocation VEGETABLE =
        KaleidoscopeAgricultureEvolution.rl("flower_source/vegetable");
    public static final ResourceLocation WETLAND =
        KaleidoscopeAgricultureEvolution.rl("flower_source/wetland");
    public static final ResourceLocation TROPICAL =
        KaleidoscopeAgricultureEvolution.rl("flower_source/tropical");
    public static final ResourceLocation COLD =
        KaleidoscopeAgricultureEvolution.rl("flower_source/cold");

    private static final Map<ResourceLocation, Set<ResourceLocation>> CROP_TAGS =
        new LinkedHashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> BEE_PREFERENCES =
        new LinkedHashMap<>();
    private static boolean defaultsRegistered;

    private BeeFlowerCompatibility() {
    }

    public static void registerDefaults() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;

        registerCropTags("kaleidoscope_agriculture_evolution:wheat", GRAIN);
        registerCropTags("kaleidoscope_agriculture_evolution:carrot", ROOT, VEGETABLE);
        registerCropTags("kaleidoscope_agriculture_evolution:potato", ROOT);
        registerCropTags("kaleidoscope_agriculture_evolution:beetroot", ROOT, VEGETABLE);

        registerCropTags("farmersdelight:tomato", FRUIT, VEGETABLE, TROPICAL);
        registerCropTags("farmersdelight:onion", ROOT, VEGETABLE);
        registerCropTags("farmersdelight:rice", GRAIN, WETLAND, TROPICAL);
        registerCropTags("kaleidoscope_cookery:tomato", FRUIT, VEGETABLE, TROPICAL);
        registerCropTags("kaleidoscope_cookery:chili", FRUIT, VEGETABLE, TROPICAL);
        registerCropTags("kaleidoscope_cookery:rice", GRAIN, WETLAND, TROPICAL);

        registerBeePreferences(BeeSpeciesRegistry.TROPICAL_SPECIES,
            FRUIT, VEGETABLE, TROPICAL, WETLAND);
        registerBeePreferences(BeeSpeciesRegistry.TEMPERATE_SPECIES,
            GRAIN, ROOT, VEGETABLE, FRUIT);
        registerBeePreferences(BeeSpeciesRegistry.COLD_SPECIES,
            GRAIN, ROOT, VEGETABLE, COLD);
    }

    public static void registerCropTags(CropSpecies species, ResourceLocation... tags) {
        if (species != null) {
            registerCropTags(species.getId(), tags);
        }
    }

    public static void registerCropTags(String speciesId, ResourceLocation... tags) {
        ResourceLocation id = parseId(speciesId);
        if (id != null) {
            registerCropTags(id, tags);
        }
    }

    public static void registerCropTags(ResourceLocation speciesId, ResourceLocation... tags) {
        if (speciesId == null) {
            return;
        }
        CROP_TAGS.put(speciesId, immutableTags(tags));
    }

    public static void registerBeePreferences(BeeSpecies species, ResourceLocation... tags) {
        if (species != null) {
            registerBeePreferences(species.getId(), tags);
        }
    }

    public static void registerBeePreferences(ResourceLocation speciesId, ResourceLocation... tags) {
        if (speciesId == null) {
            return;
        }
        BEE_PREFERENCES.put(speciesId, immutableTags(tags));
    }

    @NotNull
    public static Set<ResourceLocation> getCropTags(@Nullable CropSpecies species) {
        registerDefaults();
        if (species == null) {
            return Set.of();
        }
        return CROP_TAGS.getOrDefault(speciesId(species), Set.of());
    }

    @NotNull
    public static Set<ResourceLocation> getBeePreferences(@Nullable BeeSpecies species) {
        registerDefaults();
        if (species == null) {
            return Set.of();
        }
        return BEE_PREFERENCES.getOrDefault(species.getId(), Set.of());
    }

    public static boolean matches(@Nullable BeeSpecies bee, @Nullable CropSpecies crop) {
        return sharedTagCount(getBeePreferences(bee), getCropTags(crop)) > 0;
    }

    public static boolean matches(@Nullable BeePhenotype phenotype, @Nullable CropSpecies crop) {
        if (phenotype == null) {
            return false;
        }
        return matches(BeeSpeciesRegistry.get(phenotype.speciesId()), crop);
    }

    /**
     * Returns a normalized selection weight in the range [0, 1].
     * A matching source benefits from flower affinity; an unknown source is
     * deliberately rejected until it is registered in the mapping table.
     */
    public static double compatibility(@Nullable BeePhenotype phenotype,
                                       @Nullable CropSpecies crop) {
        if (phenotype == null || crop == null) {
            return 0.0D;
        }

        Set<ResourceLocation> beeTags = getBeePreferences(
            BeeSpeciesRegistry.get(phenotype.speciesId()));
        Set<ResourceLocation> cropTags = getCropTags(crop);
        int sharedTags = sharedTagCount(beeTags, cropTags);
        if (sharedTags <= 0) {
            return 0.0D;
        }

        double tagMatch = Math.min(1.0D, sharedTags / (double) Math.max(1, cropTags.size()));
        double affinity = clamp01(phenotype.flowerAffinity());
        return clamp01((0.55D + 0.45D * tagMatch) * (0.65D + 0.35D * affinity));
    }

    public static void clear() {
        CROP_TAGS.clear();
        BEE_PREFERENCES.clear();
        defaultsRegistered = false;
    }

    private static ResourceLocation speciesId(CropSpecies species) {
        return parseId(species.getId());
    }

    private static ResourceLocation parseId(String id) {
        return id != null ? ResourceLocation.tryParse(id) : null;
    }

    private static Set<ResourceLocation> immutableTags(ResourceLocation... tags) {
        if (tags == null || tags.length == 0) {
            return Set.of();
        }
        Set<ResourceLocation> normalized = new LinkedHashSet<>();
        for (ResourceLocation tag : tags) {
            if (tag != null) {
                normalized.add(tag);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static int sharedTagCount(Collection<ResourceLocation> first,
                                      Collection<ResourceLocation> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return 0;
        }
        Set<ResourceLocation> overlap = new LinkedHashSet<>(first);
        overlap.retainAll(second);
        return overlap.size();
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
