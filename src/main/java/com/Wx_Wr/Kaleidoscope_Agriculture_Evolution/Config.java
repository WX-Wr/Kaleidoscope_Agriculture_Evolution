package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Config {

    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SEED_ITEMS;
    public static final ForgeConfigSpec.DoubleValue PLOW_SPEED_DEFAULT;
    public static final ForgeConfigSpec.DoubleValue BOOST_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue SEARCH_RADIUS;

    private static Set<Item> cachedSeedItems = new HashSet<>();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("森罗物语：农业进化 配置文件").push("general");

        SEED_ITEMS = builder
                .comment("可播种的种子/作物物品ID列表")
                .defineListAllowEmpty("seed_items",
                        List.of(
                                "minecraft:wheat_seeds",
                                "minecraft:beetroot_seeds",
                                "minecraft:potato",
                                "minecraft:carrot",
                                "minecraft:pumpkin_seeds",
                                "minecraft:melon_seeds"
                        ),
                        obj -> obj instanceof String && ResourceLocation.tryParse((String) obj) != null);

        PLOW_SPEED_DEFAULT = builder
                .comment("基础犁地速度（秒/方块）")
                .defineInRange("plow_speed_default", 0.8, 0.1, 5.0);

        BOOST_SPEED_MULTIPLIER = builder
                .comment("鞭子加速时的速度倍率")
                .defineInRange("boost_speed_multiplier", 2.0, 1.0, 10.0);

        MAX_BLOCKS_PER_TICK = builder
                .comment("每tick最多处理的方块数量")
                .defineInRange("max_blocks_per_tick", 3, 1, 20);

        SEARCH_RADIUS = builder
                .comment("牛搜索犁地范围顶点的半径（格）")
                .defineInRange("search_radius", 5, 1, 16);

        builder.pop();
        SERVER_SPEC = builder.build();
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getModId().equals(Kaleidoscope_Agriculture_Evolution.MODID)) refreshCache();
        });
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getModId().equals(Kaleidoscope_Agriculture_Evolution.MODID)) refreshCache();
        });
    }

    private static void refreshCache() {
        cachedSeedItems = SEED_ITEMS.get().stream()
                .map(ResourceLocation::new)
                .map(ForgeRegistries.ITEMS::getValue)
                .filter(item -> item != null)
                .collect(Collectors.toSet());
    }

    public static Set<Item> getSeedItems() {
        return cachedSeedItems;
    }

    public static boolean isSeed(Item item) {
        return cachedSeedItems.contains(item);
    }
}