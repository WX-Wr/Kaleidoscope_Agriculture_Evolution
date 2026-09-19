package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BeeSpeciesReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final BeeSpeciesReloadListener INSTANCE = new BeeSpeciesReloadListener();

    private BeeSpeciesReloadListener() {
        super(GSON, "bee_species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<BeeSpecies> species = new ArrayList<>();
        object.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    return;
                }
                species.add(BeeSpeciesDefinition.fromJson(entry.getKey(), entry.getValue().getAsJsonObject()).toSpecies());
            });
        BeeSpeciesRegistry.reload(species);
    }
}
