package com.wxwr.kaleidoscopeagricultureevolution.genetics.client;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.CropAge;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class HighYieldCropModel extends ConditionalReplacementBakedModel {
    private static final int VERTICAL_SEARCH_RADIUS = 2;

    public static final ResourceLocation HIGH_STRAW_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_straw_wheat");
    public static final ResourceLocation HIGH_YIELD_CHILI_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_chili");
    public static final ResourceLocation HIGH_YIELD_COOKERY_TOMATO_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_tomato_v2");
    public static final ResourceLocation HIGH_YIELD_CARROT_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_carrot");
    public static final ResourceLocation HIGH_YIELD_POTATO_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_potato");
    public static final ResourceLocation HIGH_YIELD_BEET_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_beet");
    public static final ResourceLocation HIGH_YIELD_RICE_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_rice");
    public static final ResourceLocation HIGH_YIELD_RICE_V2_DOWN_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_rice_v2_down");
    public static final ResourceLocation HIGH_YIELD_RICE_V2_MIDDLE_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_rice_v2_middle");
    public static final ResourceLocation HIGH_YIELD_RICE_V2_TOP_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_rice_v2_top");
    public static final ResourceLocation HIGH_YIELD_RICE_SUPPORTING_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_rice_supporting");
    public static final ResourceLocation HIGH_YIELD_TOMATO_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_tomato");
    public static final ResourceLocation HIGH_YIELD_TOMATO_ON_ROPE_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_tomato_on_rope");
    public static final ResourceLocation HIGH_YIELD_ONION_LOC =
        KaleidoscopeAgricultureEvolution.rl("block/crops/high_yield_onion");

    public HighYieldCropModel(BakedModel originalModel, BakedModel highYieldModel) {
        super(originalModel, HighYieldModelProperty.HIGH_YIELD_PROPERTY);
        this.highYieldModel = highYieldModel;
        this.riceDownModel = null;
        this.riceMiddleModel = null;
        this.riceTopModel = null;
    }

    public HighYieldCropModel(BakedModel originalModel, BakedModel riceDownModel,
                              BakedModel riceMiddleModel, BakedModel riceTopModel) {
        super(originalModel, HighYieldModelProperty.HIGH_YIELD_PROPERTY);
        this.highYieldModel = null;
        this.riceDownModel = riceDownModel;
        this.riceMiddleModel = riceMiddleModel;
        this.riceTopModel = riceTopModel;
    }

    private final BakedModel highYieldModel;
    private final BakedModel riceDownModel;
    private final BakedModel riceMiddleModel;
    private final BakedModel riceTopModel;

    @Override
    protected boolean shouldUseReplacement(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                           ModelData modelData) {
        return isMature(state) && isHighYieldAtOrNear(level, pos, state);
    }

    @Override
    protected BakedModel getReplacementModel(BlockState state, ModelData modelData) {
        if (riceDownModel != null && riceMiddleModel != null && riceTopModel != null) {
            Integer location = getRiceLocation(state);
            if (location != null) {
                if (location == 0) return riceDownModel;
                if (location == 1) return riceMiddleModel;
                if (location == 2) return riceTopModel;
            }
        }
        return highYieldModel;
    }

    private boolean isMature(BlockState state) {
        if (state == null) return false;
        int age = CropAge.get(state);
        CropSpecies species = CropSpeciesRegistry.fromBlock(state.getBlock());
        return age >= 0 && species != null && age >= species.getMaxAge();
    }

    private boolean isHighYieldAtOrNear(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (ClientGenomeData.isHighYield(pos)) return true;
        CropSpecies species = CropSpeciesRegistry.fromBlock(state.getBlock());
        if (species == null || level == null) return false;

        for (int dy = 1; dy <= VERTICAL_SEARCH_RADIUS; dy++) {
            BlockPos below = pos.below(dy);
            if (CropSpeciesRegistry.fromBlock(level.getBlockState(below).getBlock()) == species
                    && ClientGenomeData.isHighYield(below)) {
                return true;
            }

            BlockPos above = pos.above(dy);
            if (CropSpeciesRegistry.fromBlock(level.getBlockState(above).getBlock()) == species
                    && ClientGenomeData.isHighYield(above)) {
                return true;
            }
        }

        return false;
    }

    private Integer getRiceLocation(BlockState state) {
        if (state == null) return null;
        for (Property<?> property : state.getProperties()) {
            if ("location".equals(property.getName()) && property instanceof IntegerProperty integerProperty) {
                return state.getValue(integerProperty);
            }
        }
        return null;
    }
}
