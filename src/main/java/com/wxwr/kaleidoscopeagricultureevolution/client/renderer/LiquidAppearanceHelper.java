package com.wxwr.kaleidoscopeagricultureevolution.client.renderer;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.block.CrockBlock;
import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationContent;
import com.wxwr.kaleidoscopeagricultureevolution.block.FermentationTapState;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class LiquidAppearanceHelper {
    private LiquidAppearanceHelper() {
    }

    // ========================================================================
    //  状态：待定 —— 基于“方块状态模型切换”的液体外观方案
    //
    //  下面这一组（resolve ×2、newModelMap、LiquidAppearance、StageModels，
    //  以及 ContainerType.modelFor / glassPrefix）目前<b>没有任何调用点</b>：
    //  实际渲染走 LiquidContentRenderer 的手绘几何。
    //  另外 modelFor 生成的 block/..._stageN 模型资产并不存在，
    //  因此这组代码即使接线也无法工作 —— 启用前需要先补资产。
    //  在决定之前请勿删除。
    // ========================================================================

    @Nullable
    public static LiquidAppearance resolve(ContainerType container, FermentationTapState tap, @Nullable LiquidVisual liquid,
                                           int fillLevel) {
        ResourceLocation model = container.modelFor(tap, fillLevel);
        if (liquid == null || fillLevel <= 0) {
            return new LiquidAppearance(model, null);
        }
        return new LiquidAppearance(model, liquid.texture());
    }

    public static ResourceLocation resolve(CrockBlock.Content content, int fillLevel,
                                           Map<CrockBlock.Content, StageModels> models,
                                           @Nullable ResourceLocation emptyModel) {
        if (content == CrockBlock.Content.EMPTY || fillLevel <= 0) {
            return emptyModel;
        }

        StageModels stageModels = models.get(content);
        if (stageModels == null) {
            return emptyModel;
        }
        return stageModels.modelFor(fillLevel);
    }

    public static EnumMap<CrockBlock.Content, StageModels> newModelMap() {
        return new EnumMap<>(CrockBlock.Content.class);
    }

    @Nullable
    public static LiquidVisual fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModItems.PORTION_WATER_BOTTLE.get())
                || (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER)) {
            return LiquidVisual.WATER;
        }
        if (stack.is(ModItems.PORTION_VINEGAR.get()) || stack.is(ModItems.VINEGAR.get())) {
            return LiquidVisual.VINEGAR;
        }
        if (stack.is(ModItems.PORTION_APPLE_VINEGAR.get()) || stack.is(ModItems.APPLE_VINEGAR.get())) {
            return LiquidVisual.APPLE_VINEGAR;
        }
        if (stack.is(ModItems.PORTION_HONEY_BOTTLE.get()) || stack.is(Items.HONEY_BOTTLE)) {
            return LiquidVisual.HONEY;
        }
        if (stack.is(ModItems.PORTION_YEAST.get()) || stack.is(ModItems.YEAST.get())) {
            return LiquidVisual.YEAST_SOLUTION;
        }
        return null;
    }

    @Nullable
    public static LiquidVisual fromCrockContent(CrockBlock.Content content) {
        return switch (content) {
            case WATER -> LiquidVisual.WATER;
            case VINEGAR -> LiquidVisual.VINEGAR;
            case HONEY -> LiquidVisual.HONEY;
            case EMPTY -> null;
        };
    }

    @Nullable
    public static LiquidVisual fromFermentationContent(FermentationContent content) {
        return switch (content) {
            case WATER -> LiquidVisual.WATER;
            case VINEGAR -> LiquidVisual.VINEGAR;
            case APPLE_VINEGAR -> LiquidVisual.APPLE_VINEGAR;
            case HONEY -> LiquidVisual.HONEY;
            case YEAST -> LiquidVisual.YEAST_SOLUTION;
            case RICE -> LiquidVisual.RICE;
            case EMPTY -> null;
        };
    }

    public enum ContainerType {
        CROCK("block/crock/crock", 3),
        WOODEN_FERMENTATION("block/wooden_fermentation/wooden_fermentation", 4),
        GLASS_FERMENTATION("block/glass_fermentation/glass_fermentation", 3);

        private final String modelPrefix;
        private final int maxStages;

        ContainerType(String modelPrefix, int maxStages) {
            this.modelPrefix = modelPrefix;
            this.maxStages = maxStages;
        }

        private ResourceLocation modelFor(FermentationTapState tap, int fillLevel) {
            String prefix = this == GLASS_FERMENTATION ? glassPrefix(tap) : modelPrefix;
            if (fillLevel <= 0) {
                return KaleidoscopeAgricultureEvolution.rl(prefix);
            }
            int stage = Math.max(1, Math.min(maxStages, fillLevel));
            return KaleidoscopeAgricultureEvolution.rl(prefix + "_stage" + stage);
        }

        private String glassPrefix(FermentationTapState tap) {
            return switch (tap) {
                case NONE -> modelPrefix;
                case CLOSE -> modelPrefix + "_close";
                case OPEN -> modelPrefix + "_open";
            };
        }
    }

    public enum LiquidVisual {
        WATER("water"),
        VINEGAR("vinegar"),
        APPLE_VINEGAR("vinegar"),
        HONEY("honey"),
        YEAST_SOLUTION("yeast_solution"),
        RICE("rice");

        private final String texturePath;

        LiquidVisual(String texturePath) {
            this.texturePath = texturePath;
        }

        public ResourceLocation texture() {
            return KaleidoscopeAgricultureEvolution.rl("block/farm_tools/" + texturePath);
        }
    }

    public record LiquidAppearance(ResourceLocation model, @Nullable ResourceLocation liquidTexture) {
        public boolean hasLiquid() {
            return liquidTexture != null;
        }
    }

    public record StageModels(ResourceLocation stage1, ResourceLocation stage2, ResourceLocation stage3,
                              @Nullable ResourceLocation stage4) {
        public ResourceLocation modelFor(int fillLevel) {
            int stage = Math.max(1, fillLevel);
            if (stage4 != null) {
                return switch (stage) {
                    case 1 -> stage1;
                    case 2 -> stage2;
                    case 3 -> stage3;
                    default -> stage4;
                };
            }
            return switch (stage) {
                case 1 -> stage1;
                case 2 -> stage2;
                default -> stage3;
            };
        }
    }
}
