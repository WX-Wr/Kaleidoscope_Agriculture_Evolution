package com.wxwr.kaleidoscopeagricultureevolution.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.block.CrockBlock;
import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationContent;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.FermentationContainerBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.item.DurablePortionItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class ContainerRecipeConfig {
    public static final String APPLE_VINEGAR_ID = "apple_vinegar";
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME =
            "kaleidoscope_agriculture_evolution_container_recipes.json";
    private static final String DEFAULT_RESOURCE =
            "/defaults/kaleidoscope_agriculture_evolution_container_recipes.json";
    private static volatile RecipeData data = RecipeData.empty();

    private ContainerRecipeConfig() {
    }

    public static void loadFromConfigFile() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
            }

            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                data = RecipeData.from(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException | RuntimeException exception) {
            KaleidoscopeAgricultureEvolution.LOGGER.error(
                    "Failed to load container recipe config from {}", configFile, exception);
        }
    }

    public static List<CrockRecipeDefinition> crockRecipes() {
        return data.crockRecipes();
    }

    public static List<GlassRecipeDefinition> glassPreservingRecipes() {
        return data.glassPreservingRecipes();
    }

    public static GlassRecipeDefinition glassRecipe(String id) {
        return data.glassRecipes().get(id);
    }

    public static GlassRecipeDefinition appleVinegarRecipe() {
        return glassRecipe(APPLE_VINEGAR_ID);
    }

    private static void writeDefaultConfig(Path configFile) throws IOException {
        try (InputStream stream = ContainerRecipeConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing bundled default recipe resource: " + DEFAULT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement defaults = JsonParser.parseReader(reader);
                Files.writeString(configFile, PRETTY_GSON.toJson(defaults), StandardCharsets.UTF_8);
            }
        }
        KaleidoscopeAgricultureEvolution.LOGGER.info(
                "Generated default container recipe config at {}", configFile);
    }

    public record CrockRecipeDefinition(
            List<ResourceLocation> rawItems,
            List<ResourceLocation> extraRawItems,
            ResourceLocation output,
            int outputCount,
            CrockBlock.Content liquid,
            ResourceLocation portionItem,
            int portionCount,
            int durationTicks) {

        public boolean matchesRaw(ItemStack stack) {
            return matches(rawItems, stack);
        }

        public boolean requiresExtraRaw() {
            return !extraRawItems.isEmpty();
        }

        public boolean matchesExtraRaw(ItemStack stack) {
            return matches(extraRawItems, stack);
        }

        public Item outputItem() {
            Item item = ForgeRegistries.ITEMS.getValue(output);
            return item == null ? net.minecraft.world.item.Items.AIR : item;
        }

        private static boolean matches(List<ResourceLocation> ids, ItemStack stack) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return itemId != null && ids.contains(itemId);
        }
    }

    public record IngredientDefinition(
            List<ResourceLocation> items,
            int count,
            boolean portions) {

        public boolean matches(ItemStack stack) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return itemId != null && items.contains(itemId);
        }

        public int countFrom(ItemStack stack) {
            if (!matches(stack)) {
                return 0;
            }
            if (portions) {
                return stack.getItem() instanceof DurablePortionItem portionItem
                        ? portionItem.getPortions(stack)
                        : 0;
            }
            return stack.getCount();
        }

        public ItemStack normalizeOne(ItemStack held) {
            if (portions && held.getItem() instanceof DurablePortionItem portionItem) {
                return portionItem.withPortions(1);
            }
            return held.copyWithCount(1);
        }

        public Predicate<ItemStack> asPredicate() {
            return this::matches;
        }
    }

    public record GlassRecipeDefinition(
            String id,
            FermentationContent inputContent,
            int minFillLevel,
            int maxFillLevel,
            List<IngredientDefinition> ingredients,
            ResourceLocation output,
            int outputCount,
            int durationTicks,
            boolean drainLiquid,
            FermentationContent resultContent,
            int resultFillLevel) {

        public Item outputItem() {
            return ForgeRegistries.ITEMS.getValue(output);
        }

        public Optional<IngredientDefinition> ingredientFor(ItemStack stack) {
            return ingredients.stream().filter(ingredient -> ingredient.matches(stack)).findFirst();
        }

        public boolean matchesFillLevel(int fillLevel) {
            return fillLevel >= minFillLevel && fillLevel <= maxFillLevel;
        }

        public boolean matchesState(FermentationContent content, int fillLevel) {
            return content == inputContent && matchesFillLevel(fillLevel);
        }

        public boolean canAccept(ItemStack stack, FermentationContainerBlockEntity container) {
            return ingredientFor(stack).filter(ingredient -> countIngredient(container, ingredient) < ingredient.count())
                    .isPresent();
        }

        public boolean hasIngredients(FermentationContainerBlockEntity container) {
            for (IngredientDefinition ingredient : ingredients) {
                if (countIngredient(container, ingredient) < ingredient.count()) {
                    return false;
                }
            }
            return true;
        }

        public int countIngredient(FermentationContainerBlockEntity container, IngredientDefinition ingredient) {
            return countIngredientInternal(container, ingredient);
        }

        public void consumeIngredients(FermentationContainerBlockEntity container) {
            for (IngredientDefinition ingredient : ingredients) {
                int required = ingredient.count();
                if (ingredient.portions()) {
                    ResourceLocation id = ingredient.items().isEmpty() ? null : ingredient.items().get(0);
                    Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
                    if (item != null) {
                        container.removeNonLiquidPortions(item, required);
                    }
                } else {
                    container.removeNonLiquidMatching(ingredient.asPredicate(), required);
                }
            }
        }

        public ItemStack outputStack() {
            Item item = outputItem();
            return item == null ? ItemStack.EMPTY : new ItemStack(item, outputCount);
        }

        public boolean drainsLiquid() {
            return drainLiquid;
        }

        public FermentationContent resultContentOr(FermentationContent fallback) {
            return resultContent == null ? fallback : resultContent;
        }

        public int resultFillLevelOr(int fallback) {
            return resultFillLevel >= 0 ? resultFillLevel : fallback;
        }

        private int countIngredientInternal(FermentationContainerBlockEntity container, IngredientDefinition ingredient) {
            int count = 0;
            for (ItemStack stack : container.getNonLiquidItems()) {
                if (!ingredient.matches(stack)) {
                    continue;
                }
                if (ingredient.portions()) {
                    if (stack.getItem() instanceof DurablePortionItem portionItem) {
                        count += portionItem.getPortions(stack);
                    }
                } else {
                    count += stack.getCount();
                }
            }
            return count;
        }
    }

    private record RecipeData(
            List<CrockRecipeDefinition> crockRecipes,
            Map<String, GlassRecipeDefinition> glassRecipes,
            List<GlassRecipeDefinition> glassPreservingRecipes) {

        private static RecipeData empty() {
            return new RecipeData(List.of(), Map.of(), List.of());
        }

        private static RecipeData from(JsonObject root) {
            JsonObject crock = object(root, "crock");
            JsonObject glass = object(root, "glass_fermentation");

            List<CrockRecipeDefinition> crockRecipes = new ArrayList<>();
            for (JsonElement element : array(crock, "recipes")) {
                crockRecipes.add(readCrockRecipe(element.getAsJsonObject()));
            }

            Map<String, GlassRecipeDefinition> glassRecipes = new java.util.LinkedHashMap<>();
            List<GlassRecipeDefinition> glassPreservingRecipes = new ArrayList<>();
            for (JsonElement element : array(glass, "recipes")) {
                GlassRecipeDefinition recipe = readGlassRecipe(element.getAsJsonObject());
                glassRecipes.put(recipe.id(), recipe);
                if (!APPLE_VINEGAR_ID.equals(recipe.id())) {
                    glassPreservingRecipes.add(recipe);
                }
            }
            return new RecipeData(
                    List.copyOf(crockRecipes),
                    Map.copyOf(glassRecipes),
                    List.copyOf(glassPreservingRecipes));
        }
    }

    private static CrockRecipeDefinition readCrockRecipe(JsonObject object) {
        return new CrockRecipeDefinition(
                readIds(object, "raw"),
                optionalIds(object, "extra_raw"),
                readId(object, "output"),
                positiveInt(object, "output_count", 1),
                crockContent(string(object, "liquid")),
                readId(object, "portion_item"),
                positiveInt(object, "portion_count", 1),
                secondsToTicks(positiveInt(object, "duration_seconds", 1)));
    }

    private static GlassRecipeDefinition readGlassRecipe(JsonObject object) {
        int exactFillLevel = object.has("fill_level")
                ? nonNegativeInt(object, "fill_level", 0)
                : -1;
        int minFillLevel = exactFillLevel >= 0
                ? exactFillLevel
                : nonNegativeInt(object, "min_fill_level", 0);
        int maxFillLevel = exactFillLevel >= 0
                ? exactFillLevel
                : nonNegativeInt(object, "max_fill_level", 3);
        String resultContent = optionalString(object, "result_content").orElse("");
        return new GlassRecipeDefinition(
                string(object, "id"),
                fermentationContent(string(object, "input_content")),
                minFillLevel,
                maxFillLevel,
                readIngredients(object, "ingredients"),
                readId(object, "output"),
                positiveInt(object, "output_count", 1),
                secondsToTicks(positiveInt(object, "duration_seconds", 1)),
                booleanValue(object, "drain_liquid", false),
                resultContent.isEmpty() || "same".equals(resultContent)
                        ? null
                        : fermentationContent(resultContent),
                optionalNonNegativeInt(object, "result_fill_level", -1));
    }

    private static List<IngredientDefinition> readIngredients(JsonObject object, String key) {
        List<IngredientDefinition> ingredients = new ArrayList<>();
        for (JsonElement element : array(object, key)) {
            JsonObject ingredient = element.getAsJsonObject();
            ingredients.add(new IngredientDefinition(
                    readIds(ingredient, "items"),
                    positiveInt(ingredient, "count", 1),
                    "portion".equalsIgnoreCase(string(ingredient, "type"))));
        }
        if (ingredients.isEmpty()) {
            throw new JsonParseException("Recipe must define at least one ingredient: " + string(object, "id"));
        }
        return List.copyOf(ingredients);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new JsonParseException("Missing JSON object: " + key);
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new JsonParseException("Missing JSON array: " + key);
        }
        return value.getAsJsonArray();
    }

    private static List<ResourceLocation> readIds(JsonObject object, String key) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement element : array(object, key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("Item IDs must be strings: " + key);
            }
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) {
                throw new JsonParseException("Invalid item ID: " + element.getAsString());
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    private static List<ResourceLocation> optionalIds(JsonObject object, String key) {
        if (!object.has(key)) {
            return List.of();
        }
        return readIds(object, key);
    }

    private static ResourceLocation readId(JsonObject object, String key) {
        ResourceLocation id = ResourceLocation.tryParse(string(object, key));
        if (id == null) {
            throw new JsonParseException("Invalid item ID: " + object.get(key));
        }
        return id;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Missing string value: " + key);
        }
        return value.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected string value: " + key);
        }
        return Optional.of(value.getAsString());
    }

    private static int positiveInt(JsonObject object, String key, int defaultValue) {
        int value = number(object, key, defaultValue);
        if (value <= 0) {
            throw new JsonParseException(key + " must be positive");
        }
        return value;
    }

    private static int nonNegativeInt(JsonObject object, String key, int defaultValue) {
        int value = number(object, key, defaultValue);
        if (value < 0) {
            throw new JsonParseException(key + " must not be negative");
        }
        return value;
    }

    private static int optionalNonNegativeInt(JsonObject object, String key, int defaultValue) {
        return object.has(key) ? nonNegativeInt(object, key, defaultValue) : defaultValue;
    }

    private static int number(JsonObject object, String key, int defaultValue) {
        JsonElement value = object.get(key);
        return value == null ? defaultValue : value.getAsInt();
    }

    private static boolean booleanValue(JsonObject object, String key, boolean defaultValue) {
        JsonElement value = object.get(key);
        return value == null ? defaultValue : value.getAsBoolean();
    }

    private static int secondsToTicks(int seconds) {
        return seconds * 20;
    }

    private static CrockBlock.Content crockContent(String name) {
        for (CrockBlock.Content content : CrockBlock.Content.values()) {
            if (content.getSerializedName().equals(name)) {
                return content;
            }
        }
        throw new JsonParseException("Unknown crock liquid: " + name);
    }

    private static FermentationContent fermentationContent(String name) {
        for (FermentationContent content : FermentationContent.values()) {
            if (content.getSerializedName().equals(name)) {
                return content;
            }
        }
        throw new JsonParseException("Unknown fermentation content: " + name);
    }
}
