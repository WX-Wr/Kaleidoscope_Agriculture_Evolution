package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-reloadable crop species blacklist for bee pollen collection and deposit.
 */
public final class BeePollinationBlacklist {
    private static volatile Set<ResourceLocation> blacklistedSpecies = Set.of();

    private BeePollinationBlacklist() {
    }

    public static void reload(@Nullable Collection<ResourceLocation> speciesIds) {
        Set<ResourceLocation> normalized = new LinkedHashSet<>();
        if (speciesIds != null) {
            for (ResourceLocation speciesId : speciesIds) {
                if (speciesId != null) {
                    normalized.add(speciesId);
                }
            }
        }
        blacklistedSpecies = Collections.unmodifiableSet(normalized);
    }

    public static boolean contains(@Nullable CropSpecies species) {
        if (species == null) {
            return false;
        }
        ResourceLocation speciesId = ResourceLocation.tryParse(species.getId());
        return speciesId != null && blacklistedSpecies.contains(speciesId);
    }

    @NotNull
    public static Set<ResourceLocation> getAll() {
        return blacklistedSpecies;
    }
}
