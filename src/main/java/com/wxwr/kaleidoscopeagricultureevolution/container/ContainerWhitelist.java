package com.wxwr.kaleidoscopeagricultureevolution.container;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ContainerWhitelist {
    private static final Gson PRETTY_GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "kaleidoscope_agriculture_evolution_fermentation_whitelist.json";
    private static final String DEFAULT_RESOURCE =
            "/defaults/kaleidoscope_agriculture_evolution_fermentation_whitelist.json";
    private static volatile WhitelistData data = WhitelistData.empty();

    private ContainerWhitelist() {
    }

    public static void loadFromConfigFile() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
            }

            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                data = WhitelistData.from(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException | RuntimeException exception) {
            KaleidoscopeAgricultureEvolution.LOGGER.error(
                    "Failed to load fermentation whitelist config from {}", configFile, exception);
        }
    }

    private static void writeDefaultConfig(Path configFile) throws IOException {
        try (InputStream stream = ContainerWhitelist.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing bundled default whitelist resource: " + DEFAULT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement defaults = JsonParser.parseReader(reader);
                Files.writeString(configFile, PRETTY_GSON.toJson(defaults), StandardCharsets.UTF_8);
            }
        }
        KaleidoscopeAgricultureEvolution.LOGGER.info(
                "Generated default fermentation whitelist config at {}", configFile);
    }

    public static boolean isCrockRawIngredient(ItemStack stack) {
        return contains(data.crockRaw(), stack);
    }

    public static boolean isCrockNonRawIngredient(ItemStack stack) {
        return contains(data.crockNonRaw(), stack);
    }

    public static boolean isGlassAppleVinegarIngredient(ItemStack stack) {
        return contains(data.glassAppleVinegar(), stack);
    }

    public static boolean isGlassPreservingIngredient(ItemStack stack) {
        return contains(data.glassPreserving(), stack);
    }

    public static boolean isGlassIngredientRenderable(ItemStack stack) {
        return isGlassAppleVinegarIngredient(stack) || isGlassPreservingIngredient(stack);
    }

    private static boolean contains(Set<ResourceLocation> ids, ItemStack stack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null && ids.contains(itemId);
    }

    private record WhitelistData(Set<ResourceLocation> crockRaw, Set<ResourceLocation> crockNonRaw,
                                 Set<ResourceLocation> glassAppleVinegar,
                                 Set<ResourceLocation> glassPreserving) {
        private static WhitelistData empty() {
            return new WhitelistData(Set.of(), Set.of(), Set.of(), Set.of());
        }

        private static WhitelistData from(JsonObject root) {
            JsonObject crock = object(root, "crock");
            JsonObject glass = object(root, "glass_fermentation");
            return new WhitelistData(
                    readIds(crock, "raw"),
                    readIds(crock, "non_raw"),
                    readIds(glass, "apple_vinegar"),
                    readIds(glass, "preserving"));
        }

        private static JsonObject object(JsonObject parent, String key) {
            JsonElement value = parent.get(key);
            return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
        }

        private static Set<ResourceLocation> readIds(JsonObject parent, String key) {
            JsonElement value = parent.get(key);
            if (value == null || !value.isJsonArray()) {
                return Set.of();
            }

            Set<ResourceLocation> ids = new HashSet<>();
            JsonArray array = value.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Whitelist entries must be item id strings");
                }
                ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
                if (id == null) {
                    throw new JsonParseException("Invalid item id: " + element.getAsString());
                }
                ids.add(id);
            }
            return Set.copyOf(ids);
        }
    }
}
