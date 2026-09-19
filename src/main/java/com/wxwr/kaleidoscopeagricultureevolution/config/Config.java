package com.wxwr.kaleidoscopeagricultureevolution.config;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class Config {

    // ========================================================================
    //  代码常量
    //
    //  原先的 [general] 与 [cart_physics] 两个配置 section 已整体内联到代码中，
    //  不再作为配置文件选项对外暴露。要调整数值请直接改这里的常量。
    // ========================================================================

    /** 鞭子加速时的速度倍率。 */
    public static final double BOOST_SPEED_MULTIPLIER = 2.0;

    /**
     * 耕牛赶路速度倍率（相对 {@code MOVEMENT_SPEED} 属性）。
     *
     * <p>0.25 → 挂农具时（属性 0.1）= 0.025 格/tick = 0.5 格/秒；
     * 空闲无农具时（属性 0.2）= 0.05 格/tick = 1 格/秒。
     *
     * <p>作用于去角点、去起点，以及空闲时的游荡 / 引诱 / 跟随 / 繁殖 / 逃跑。
     * <b>不影响</b>作业时拖曳农具的行进速度 —— 那个见 {@code PlowOxEntity.getWorkingSpeed()}。
     */
    public static final double TRAVEL_SPEED_MULTIPLIER = 0.25;

    /** 死区半径（格），死区拖曳物理使用。 */
    public static final double DEAD_ZONE_RADIUS = 0.2;

    /** 牵引时对牛移动速度的倍率修正（{@code MULTIPLY_TOTAL}）。 */
    public static final double PULL_SPEED_MODIFIER = -0.5;

    // ========================================================================
    //  [genetics] —— 仍然是配置文件选项
    // ========================================================================

    public static final ForgeConfigSpec SERVER_SPEC;

    public static final ForgeConfigSpec.BooleanValue GENETICS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MUTATION_RATE;
    public static final ForgeConfigSpec.DoubleValue POLLINATION_CHANCE;
    public static final ForgeConfigSpec.BooleanValue VISUAL_TINT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ANALYZER_BOOK_MODE;

    // ========== 缓存值 ==========
    private static boolean cachedGeneticsEnabled = true;
    private static double cachedMutationRate = 1.0;
    private static double cachedPollinationChance = 0.05;
    private static boolean cachedVisualTintEnabled = true;
    private static boolean cachedAnalyzerBookMode = false;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("遗传系统参数").push("genetics");
        GENETICS_ENABLED = builder
                .comment("是否启用作物基因系统。关闭后暂停基因生成、遗传、生长影响、授粉和基因显示，不删除已有存档数据")
                .define("genetics_enabled", true);
        MUTATION_RATE = builder
                .comment("全 局 突 变 率 倍 率（1.0 = 默认频率）")
                .defineInRange("mutation_rate", 1.0, 0.0, 10.0);
        POLLINATION_CHANCE = builder
                .comment("成熟作物每randomTick发生杂交授粉的概率")
                .defineInRange("pollination_chance", 0.05, 0.0, 1.0);
        VISUAL_TINT_ENABLED = builder
                .comment("是否启用基因性状的视觉着色效果")
                .define("visual_tint", true);
        ANALYZER_BOOK_MODE = builder
                .comment("Gene analyzer book output mode")
                .define("analyzer_book_mode", false);
        builder.pop();

        SERVER_SPEC = builder.build();
    }

    @SuppressWarnings("removal")
    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, SERVER_SPEC);
        modEventBus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getModId().equals(KaleidoscopeAgricultureEvolution.MODID)) refreshCache();
        });
        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getModId().equals(KaleidoscopeAgricultureEvolution.MODID)) refreshCache();
        });
    }

    private static void refreshCache() {
        cachedGeneticsEnabled = GENETICS_ENABLED.get();
        cachedMutationRate = MUTATION_RATE.get();
        cachedPollinationChance = POLLINATION_CHANCE.get();
        cachedVisualTintEnabled = VISUAL_TINT_ENABLED.get();
        cachedAnalyzerBookMode = ANALYZER_BOOK_MODE.get();
    }

    public static boolean isGeneticsEnabled() {
        return cachedGeneticsEnabled;
    }

    public static boolean setGeneticsEnabled(boolean enabled) {
        try {
            GENETICS_ENABLED.set(enabled);
            SERVER_SPEC.save();
            refreshCache();
            return true;
        } catch (IllegalStateException | NullPointerException e) {
            KaleidoscopeAgricultureEvolution.LOGGER.warn(
                "Cannot update server config before it is loaded", e);
            return false;
        }
    }

    public static double getMutationRate() {
        return cachedMutationRate;
    }

    public static double getPollinationChance() {
        return cachedPollinationChance;
    }

    public static boolean isVisualTintEnabled() {
        return cachedVisualTintEnabled;
    }

    public static boolean isAnalyzerBookMode() {
        return cachedAnalyzerBookMode;
    }

    public static boolean setAnalyzerBookMode(boolean enabled) {
        try {
            ANALYZER_BOOK_MODE.set(enabled);
            SERVER_SPEC.save();
            refreshCache();
            return true;
        } catch (IllegalStateException | NullPointerException e) {
            KaleidoscopeAgricultureEvolution.LOGGER.warn(
                "Cannot update server config before it is loaded", e);
            return false;
        }
    }
}
