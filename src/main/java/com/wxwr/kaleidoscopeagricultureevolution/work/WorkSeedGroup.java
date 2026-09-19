package com.wxwr.kaleidoscopeagricultureevolution.work;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record WorkSeedGroup(
        String id,
        boolean includeBushBlockSeeds,
        List<WorkSeedEntry> entries,
        Set<ResourceLocation> excludedItems) {

    public boolean isExplicitlyIncluded(ResourceLocation itemId) {
        return entries.stream().anyMatch(entry -> entry.item().equals(itemId));
    }

    public Optional<WorkSeedEntry> findEntry(ResourceLocation itemId) {
        return entries.stream()
                .filter(entry -> entry.item().equals(itemId))
                .findFirst();
    }

    public boolean isExcluded(ResourceLocation itemId) {
        return excludedItems.contains(itemId);
    }
}
