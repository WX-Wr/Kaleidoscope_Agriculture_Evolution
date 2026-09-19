package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads crop species IDs from data/<namespace>/bee_pollination_blacklist/*.json.
 */
public final class BeePollinationBlacklistReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final BeePollinationBlacklistReloadListener INSTANCE =
        new BeePollinationBlacklistReloadListener();

    private BeePollinationBlacklistReloadListener() {
        super(GSON, "bee_pollination_blacklist");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources,
                         ResourceManager resourceManager, ProfilerFiller profiler) {
        Set<ResourceLocation> blacklistedSpecies = new LinkedHashSet<>();
        resources.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> readSpeciesIds(entry.getKey(), entry.getValue(), blacklistedSpecies));
        BeePollinationBlacklist.reload(blacklistedSpecies);
    }

    private static void readSpeciesIds(ResourceLocation resourceId, JsonElement root,
                                       Set<ResourceLocation> output) {
        JsonArray entries;
        if (root != null && root.isJsonArray()) {
            entries = root.getAsJsonArray();
        } else if (root != null && root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();
            JsonElement list = object.has("crops") ? object.get("crops") : object.get("blacklist");
            if (list == null || !list.isJsonArray()) {
                throw new JsonParseException("Expected a JSON array in " + resourceId);
            }
            entries = list.getAsJsonArray();
        } else {
            throw new JsonParseException("Expected a JSON array in " + resourceId);
        }

        for (JsonElement entry : entries) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Blacklist entries must be resource location strings in "
                    + resourceId);
            }
            ResourceLocation speciesId = ResourceLocation.tryParse(entry.getAsString());
            if (speciesId == null) {
                throw new JsonParseException("Invalid crop species ID in " + resourceId + ": "
                    + entry.getAsString());
            }
            output.add(speciesId);
        }
    }
}
