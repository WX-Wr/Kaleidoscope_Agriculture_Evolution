package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 耕牛 / 农民的工作规则注册表。
 *
 * <p><b>工作规则与路径规则已内联到代码中</b>（见 {@link #builtinWorkRules()} 与
 * {@link #builtinPathRules()}），不再从 {@code config/} 读取，也不再向 {@code config/}
 * 释放对应的默认 JSON —— 它们属于固定的玩法规则，直接改这里即可。
 *
 * <p>只有「种子分组」仍从 {@code config/kaleidoscope_agriculture_evolution_farmer_seed_groups.json}
 * 读取：它需要引用其它模组（农夫乐事 / 森罗厨房）的物品 ID，是玩家最可能想自己扩展的部分。
 */
public final class WorkRuleRegistry {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SEED_GROUPS_CONFIG =
            "kaleidoscope_agriculture_evolution_farmer_seed_groups.json";
    private static final String SEED_GROUPS_DEFAULT = "/defaults/" + SEED_GROUPS_CONFIG;

    private static volatile RuleData data = RuleData.empty();

    private WorkRuleRegistry() {
    }

    /**
     * 载入规则。
     *
     * <p>工作规则与路径规则是内联常量，永远可用；种子分组来自配置文件。
     * 即使种子分组读失败，内联规则也会照常生效（只是种子分组为空）。
     */
    public static void loadFromConfigFiles() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Map<WorkKey, WorkRule> workRules = builtinWorkRules();
        Map<String, WorkPathRule> pathRules = builtinPathRules();
        try {
            Files.createDirectories(configDir);
            Map<String, WorkSeedGroup> seedGroups = loadSeedGroups(configDir.resolve(SEED_GROUPS_CONFIG));
            data = new RuleData(Map.copyOf(workRules), Map.copyOf(seedGroups), Map.copyOf(pathRules));
            KaleidoscopeAgricultureEvolution.LOGGER.info(
                    "Loaded farmer work rules: {} built-in work rules, {} seed groups from json, {} built-in path rules",
                    workRules.size(), seedGroups.size(), pathRules.size());
        } catch (IOException | RuntimeException exception) {
            data = new RuleData(Map.copyOf(workRules), Map.of(), Map.copyOf(pathRules));
            KaleidoscopeAgricultureEvolution.LOGGER.error(
                    "Failed to load farmer seed group config from {}; built-in work rules still active",
                    configDir, exception);
        }
    }

    public static Optional<WorkRule> findRule(WorkFieldType fieldType, WorkAction action) {
        return Optional.ofNullable(data.workRules().get(new WorkKey(fieldType, action)));
    }

    public static Optional<WorkSeedGroup> findSeedGroup(String id) {
        return Optional.ofNullable(data.seedGroups().get(id));
    }

    public static Optional<WorkPathRule> findPathRule(String id) {
        return Optional.ofNullable(data.pathRules().get(id));
    }

    public static Optional<WorkSeedGroup> findSeedGroup(WorkFieldType fieldType, WorkAction action) {
        return findRule(fieldType, action)
                .flatMap(rule -> rule.required().stream()
                        .map(WorkItemRequirement::seedGroup)
                        .filter(java.util.Objects::nonNull)
                        .findFirst())
                .flatMap(WorkRuleRegistry::findSeedGroup);
    }

    public static WorkPathRule pathRuleFor(WorkFieldType fieldType, WorkAction action) {
        return findRule(fieldType, action)
                .flatMap(rule -> findPathRule(rule.pathRule()))
                .orElseGet(() -> {
                    boolean paddy = fieldType == WorkFieldType.PADDY_FIELD;
                    return new WorkPathRule(
                            fieldType.key(),
                            0,
                            paddy,
                            paddy ? 0 : 1,
                            -1,
                            0,
                            paddy ? 3 : 0);
                });
    }

    public static Collection<WorkRule> workRules() {
        return data.workRules().values();
    }

    public static Collection<WorkSeedGroup> seedGroups() {
        return data.seedGroups().values();
    }

    public static Collection<WorkPathRule> pathRules() {
        return data.pathRules().values();
    }

    // ========================================================================
    //  内联规则定义
    //
    //  原先这两组分别来自 config/ 下的 farmer_work_rules.json 与 farmer_path_rules.json，
    //  现已合并到代码中。工具与必需/非必需补给都由 JSON 时代的设计直接照搬，行为不变。
    // ========================================================================

    /**
     * 工作规则：地类 × 工作内容 → 所需农具、必需/非必需补给、路径规则 id。
     *
     * <p>非必需补给（小麦、带饱和效果的谜之炖菜）缺失时只进入 WARN；
     * 必需补给（旱地/水田种子、腐肉、骨粉）缺失时进入 ERROR。
     */
    private static Map<WorkKey, WorkRule> builtinWorkRules() {
        List<WorkItemRequirement> optionalSupplies = List.of(
                itemRequirement("minecraft:wheat", 64),
                effectRequirement("minecraft:suspicious_stew", "minecraft:saturation", 1));
        List<WorkItemRequirement> fertilizers = List.of(
                itemRequirement("minecraft:rotten_flesh", 64),
                itemRequirement("minecraft:bone_meal", 64));

        Map<WorkKey, WorkRule> rules = new LinkedHashMap<>();
        addRule(rules, WorkFieldType.DRY_FIELD, WorkAction.TILL,
                WorkToolRequirement.PLOW, List.of(), optionalSupplies, "dry_field");
        addRule(rules, WorkFieldType.PADDY_FIELD, WorkAction.TILL,
                WorkToolRequirement.PLOW, List.of(), optionalSupplies, "paddy_field");
        addRule(rules, WorkFieldType.DRY_FIELD, WorkAction.SOW,
                WorkToolRequirement.LOUCHE, List.of(seedGroupRequirement("dry_seeds", 64)),
                optionalSupplies, "dry_field");
        addRule(rules, WorkFieldType.PADDY_FIELD, WorkAction.SOW,
                WorkToolRequirement.LOUCHE, List.of(seedGroupRequirement("paddy_seeds", 64)),
                optionalSupplies, "paddy_field");
        addRule(rules, WorkFieldType.DRY_FIELD, WorkAction.FERTILIZE,
                WorkToolRequirement.LOUCHE, fertilizers, List.of(), "dry_field");
        addRule(rules, WorkFieldType.PADDY_FIELD, WorkAction.FERTILIZE,
                WorkToolRequirement.LOUCHE, fertilizers, List.of(), "paddy_field");
        return rules;
    }

    /**
     * 路径规则：工作点判定的层级偏移。
     *
     * <ul>
     *   <li>{@code dry_field} —— 工作点本身即可操作方块，上方 1 格空气。</li>
     *   <li>{@code paddy_field} —— 操作方块在水下一格（{@code operationBlockYOffset = -1}），
     *       水层高度偏移 0，水上方 3 格空气。</li>
     * </ul>
     */
    private static Map<String, WorkPathRule> builtinPathRules() {
        Map<String, WorkPathRule> rules = new LinkedHashMap<>();
        rules.put("dry_field", new WorkPathRule("dry_field", 0, false, 1, -1, 0, 0));
        rules.put("paddy_field", new WorkPathRule("paddy_field", 0, false, 1, -1, 0, 3));
        return rules;
    }

    private static void addRule(Map<WorkKey, WorkRule> rules, WorkFieldType field, WorkAction action,
                                WorkToolRequirement tool, List<WorkItemRequirement> required,
                                List<WorkItemRequirement> optional, String pathRule) {
        rules.put(new WorkKey(field, action),
                new WorkRule(field, action, tool, required, optional, pathRule));
    }

    private static WorkItemRequirement itemRequirement(String item, int max) {
        return new WorkItemRequirement(id(item), null, null, null, max);
    }

    private static WorkItemRequirement effectRequirement(String item, String effect, int max) {
        return new WorkItemRequirement(id(item), null, null, id(effect), max);
    }

    private static WorkItemRequirement seedGroupRequirement(String seedGroup, int max) {
        return new WorkItemRequirement(null, null, seedGroup, null, max);
    }

    // ========================================================================
    //  种子分组 —— 仍然来自 config/ 下的 JSON
    // ========================================================================

    private static Map<String, WorkSeedGroup> loadSeedGroups(Path configFile) throws IOException {
        ensureDefaultConfig(configFile, SEED_GROUPS_DEFAULT);
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject groups = object(root, "seed_groups");
            Map<String, WorkSeedGroup> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : groups.entrySet()) {
                result.put(entry.getKey(), readSeedGroup(entry.getKey(), entry.getValue()));
            }
            return result;
        }
    }

    private static void ensureDefaultConfig(Path configFile, String defaultResource) throws IOException {
        if (Files.exists(configFile)) {
            return;
        }
        try (InputStream stream = WorkRuleRegistry.class.getResourceAsStream(defaultResource)) {
            if (stream == null) {
                throw new IOException("Missing bundled default work rule resource: " + defaultResource);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement defaults = JsonParser.parseReader(reader);
                Files.writeString(configFile, PRETTY_GSON.toJson(defaults), StandardCharsets.UTF_8);
            }
        }
        KaleidoscopeAgricultureEvolution.LOGGER.info("Generated default seed group config at {}", configFile);
    }

    private static WorkSeedGroup readSeedGroup(String id, JsonElement element) {
        if (element.isJsonArray()) {
            return new WorkSeedGroup(id, false, readSeedEntries(element.getAsJsonArray()), Set.of());
        }
        if (!element.isJsonObject()) {
            throw new JsonParseException("Seed group must be an object or array: " + id);
        }
        JsonObject object = element.getAsJsonObject();
        JsonArray entries = optionalArray(object, "entries").or(() -> optionalArray(object, "items"))
                .orElse(new JsonArray());
        return new WorkSeedGroup(
                id,
                booleanValue(object, "include_bush_block_seeds", false),
                readSeedEntries(entries),
                readIdSet(object, "exclude"));
    }

    private static List<WorkSeedEntry> readSeedEntries(JsonArray array) {
        List<WorkSeedEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            entries.add(new WorkSeedEntry(
                    id(string(object, "item")),
                    optionalId(object, "crop").orElse(null),
                    booleanValue(object, "water_crop", false),
                    booleanValue(object, "required", false)));
        }
        return List.copyOf(entries);
    }

    private static Set<ResourceLocation> readIdSet(JsonObject object, String key) {
        Optional<JsonArray> array = optionalArray(object, key);
        if (array.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> ids = new HashSet<>();
        for (JsonElement element : array.get()) {
            ids.add(id(element.getAsString()));
        }
        return Set.copyOf(ids);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new JsonParseException("Missing JSON object: " + key);
        }
        return value.getAsJsonObject();
    }

    private static Optional<JsonArray> optionalArray(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!value.isJsonArray()) {
            throw new JsonParseException("Expected JSON array: " + key);
        }
        return Optional.of(value.getAsJsonArray());
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

    private static Optional<ResourceLocation> optionalId(JsonObject object, String key) {
        return optionalString(object, key).map(WorkRuleRegistry::id);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new JsonParseException("Invalid resource location: " + value);
        }
        return id;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean defaultValue) {
        JsonElement value = object.get(key);
        return value == null ? defaultValue : value.getAsBoolean();
    }

    private record WorkKey(WorkFieldType fieldType, WorkAction action) {
    }

    private record RuleData(
            Map<WorkKey, WorkRule> workRules,
            Map<String, WorkSeedGroup> seedGroups,
            Map<String, WorkPathRule> pathRules) {
        private static RuleData empty() {
            return new RuleData(Map.of(), Map.of(), Map.of());
        }
    }
}
