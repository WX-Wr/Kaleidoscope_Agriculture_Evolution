package com.wxwr.kaleidoscopeagricultureevolution.genetics.client;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.CropAge;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeEntry;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.Map;

public class GeneticsClientSetup {

    private static final ModelResourceLocation WHEAT_STAGE7 =
        BlockModelShaper.stateToModelLocation(Blocks.WHEAT.defaultBlockState()
            .setValue(CropBlock.AGE, CropBlock.MAX_AGE));
    private static final ModelResourceLocation CARROTS_STAGE7 =
        BlockModelShaper.stateToModelLocation(Blocks.CARROTS.defaultBlockState()
            .setValue(CropBlock.AGE, CropBlock.MAX_AGE));
    private static final ModelResourceLocation POTATOES_STAGE7 =
        BlockModelShaper.stateToModelLocation(Blocks.POTATOES.defaultBlockState()
            .setValue(CropBlock.AGE, CropBlock.MAX_AGE));
    private static final ModelResourceLocation BEETROOTS_STAGE3 =
        BlockModelShaper.stateToModelLocation(Blocks.BEETROOTS.defaultBlockState()
            .setValue(BeetrootBlock.AGE, BeetrootBlock.MAX_AGE));
    private static final ModelResourceLocation RICE_STAGE3_SUPPORTING =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rice"),
            "age=3,supporting=true");
    private static final ModelResourceLocation RICE_PANICLES_STAGE3 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rice_panicles"),
            "age=3");
    private static final ModelResourceLocation TOMATOES_STAGE3 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "tomatoes"),
            "age=3,ropelogged=false");
    private static final ModelResourceLocation TOMATOES_STAGE3_ON_ROPE =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "tomatoes"),
            "age=3,ropelogged=true");
    private static final ModelResourceLocation HANGING_TOMATOES_STAGE3 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "tomatoes_on_rope"),
            "age=3");
    private static final ModelResourceLocation ONIONS_STAGE7 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("farmersdelight", "onions"),
            "age=7");
    private static final ModelResourceLocation COOKERY_CHILI_STAGE7 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("kaleidoscope_cookery", "chili_crop"),
            "age=7");
    private static final ModelResourceLocation COOKERY_TOMATO_STAGE7 =
        new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("kaleidoscope_cookery", "tomato_crop"),
            "age=7");
    private static final ModelResourceLocation[] COOKERY_RICE_STAGE7 = {
        cookeryRiceStage7(0, false),
        cookeryRiceStage7(0, true),
        cookeryRiceStage7(1, false),
        cookeryRiceStage7(1, true),
        cookeryRiceStage7(2, false),
        cookeryRiceStage7(2, true)
    };
    private static final ResourceLocation HIGH_STRAW_WHEAT = HighYieldCropModel.HIGH_STRAW_LOC;
    private static final ResourceLocation HIGH_YIELD_CHILI = HighYieldCropModel.HIGH_YIELD_CHILI_LOC;
    private static final ResourceLocation HIGH_YIELD_COOKERY_TOMATO = HighYieldCropModel.HIGH_YIELD_COOKERY_TOMATO_LOC;
    private static final ResourceLocation HIGH_YIELD_CARROT = HighYieldCropModel.HIGH_YIELD_CARROT_LOC;
    private static final ResourceLocation HIGH_YIELD_POTATO = HighYieldCropModel.HIGH_YIELD_POTATO_LOC;
    private static final ResourceLocation HIGH_YIELD_BEET = HighYieldCropModel.HIGH_YIELD_BEET_LOC;
    private static final ResourceLocation HIGH_YIELD_RICE = HighYieldCropModel.HIGH_YIELD_RICE_LOC;
    private static final ResourceLocation HIGH_YIELD_RICE_V2_DOWN = HighYieldCropModel.HIGH_YIELD_RICE_V2_DOWN_LOC;
    private static final ResourceLocation HIGH_YIELD_RICE_V2_MIDDLE = HighYieldCropModel.HIGH_YIELD_RICE_V2_MIDDLE_LOC;
    private static final ResourceLocation HIGH_YIELD_RICE_V2_TOP = HighYieldCropModel.HIGH_YIELD_RICE_V2_TOP_LOC;
    private static final ResourceLocation HIGH_YIELD_RICE_SUPPORTING = HighYieldCropModel.HIGH_YIELD_RICE_SUPPORTING_LOC;
    private static final ResourceLocation HIGH_YIELD_TOMATO = HighYieldCropModel.HIGH_YIELD_TOMATO_LOC;
    private static final ResourceLocation HIGH_YIELD_TOMATO_ON_ROPE = HighYieldCropModel.HIGH_YIELD_TOMATO_ON_ROPE_LOC;
    private static final ResourceLocation HIGH_YIELD_ONION = HighYieldCropModel.HIGH_YIELD_ONION_LOC;

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(HIGH_STRAW_WHEAT);
        event.register(HIGH_YIELD_CARROT);
        event.register(HIGH_YIELD_POTATO);
        event.register(HIGH_YIELD_BEET);
        if (hasFarmersDelight()) {
            event.register(HIGH_YIELD_RICE);
            event.register(HIGH_YIELD_RICE_SUPPORTING);
            event.register(HIGH_YIELD_TOMATO);
            event.register(HIGH_YIELD_TOMATO_ON_ROPE);
            event.register(HIGH_YIELD_ONION);
        }
        if (hasKaleidoscopeCookery()) {
            event.register(HIGH_YIELD_CHILI);
            event.register(HIGH_YIELD_COOKERY_TOMATO);
            event.register(HIGH_YIELD_RICE_V2_DOWN);
            event.register(HIGH_YIELD_RICE_V2_MIDDLE);
            event.register(HIGH_YIELD_RICE_V2_TOP);
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        Block[] blocks = CropSpeciesRegistry.getRegisteredCropBlocks().toArray(new Block[0]);
        event.register(GeneticsClientSetup::cropTint, blocks);
    }

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        BakedModel wheat7 = models.get(WHEAT_STAGE7);
        BakedModel highStraw = models.get(HIGH_STRAW_WHEAT);
        if (wheat7 != null && highStraw != null && !(wheat7 instanceof HighYieldCropModel)) {
            models.put(WHEAT_STAGE7, new HighYieldCropModel(wheat7, highStraw));
        }

        BakedModel carrots7 = models.get(CARROTS_STAGE7);
        BakedModel highYieldCarrot = models.get(HIGH_YIELD_CARROT);
        if (carrots7 != null && highYieldCarrot != null && !(carrots7 instanceof HighYieldCropModel)) {
            models.put(CARROTS_STAGE7, new HighYieldCropModel(carrots7, highYieldCarrot));
        }

        BakedModel potatoes7 = models.get(POTATOES_STAGE7);
        BakedModel highYieldPotato = models.get(HIGH_YIELD_POTATO);
        if (potatoes7 != null && highYieldPotato != null && !(potatoes7 instanceof HighYieldCropModel)) {
            models.put(POTATOES_STAGE7, new HighYieldCropModel(potatoes7, highYieldPotato));
        }

        BakedModel beetroots3 = models.get(BEETROOTS_STAGE3);
        BakedModel highYieldBeet = models.get(HIGH_YIELD_BEET);
        if (beetroots3 != null && highYieldBeet != null && !(beetroots3 instanceof HighYieldCropModel)) {
            models.put(BEETROOTS_STAGE3, new HighYieldCropModel(beetroots3, highYieldBeet));
        }

        if (hasFarmersDelight()) {
            BakedModel rice3Supporting = models.get(RICE_STAGE3_SUPPORTING);
            BakedModel highYieldRiceSupporting = models.get(HIGH_YIELD_RICE_SUPPORTING);
            if (rice3Supporting != null && highYieldRiceSupporting != null && !(rice3Supporting instanceof HighYieldCropModel)) {
                models.put(RICE_STAGE3_SUPPORTING, new HighYieldCropModel(rice3Supporting, highYieldRiceSupporting));
            }

            BakedModel ricePanicles3 = models.get(RICE_PANICLES_STAGE3);
            BakedModel highYieldRice = models.get(HIGH_YIELD_RICE);
            if (ricePanicles3 != null && highYieldRice != null && !(ricePanicles3 instanceof HighYieldCropModel)) {
                models.put(RICE_PANICLES_STAGE3, new HighYieldCropModel(ricePanicles3, highYieldRice));
            }

            BakedModel tomatoes3 = models.get(TOMATOES_STAGE3);
            BakedModel highYieldTomato = models.get(HIGH_YIELD_TOMATO);
            if (tomatoes3 != null && highYieldTomato != null && !(tomatoes3 instanceof HighYieldCropModel)) {
                models.put(TOMATOES_STAGE3, new HighYieldCropModel(tomatoes3, highYieldTomato));
            }

            BakedModel tomatoes3OnRope = models.get(TOMATOES_STAGE3_ON_ROPE);
            BakedModel highYieldTomatoOnRope = models.get(HIGH_YIELD_TOMATO_ON_ROPE);
            if (tomatoes3OnRope != null && highYieldTomatoOnRope != null && !(tomatoes3OnRope instanceof HighYieldCropModel)) {
                models.put(TOMATOES_STAGE3_ON_ROPE, new HighYieldCropModel(tomatoes3OnRope, highYieldTomatoOnRope));
            }

            BakedModel hangingTomatoes3 = models.get(HANGING_TOMATOES_STAGE3);
            if (hangingTomatoes3 != null && highYieldTomatoOnRope != null && !(hangingTomatoes3 instanceof HighYieldCropModel)) {
                models.put(HANGING_TOMATOES_STAGE3, new HighYieldCropModel(hangingTomatoes3, highYieldTomatoOnRope));
            }

            BakedModel onions7 = models.get(ONIONS_STAGE7);
            BakedModel highYieldOnion = models.get(HIGH_YIELD_ONION);
            if (onions7 != null && highYieldOnion != null && !(onions7 instanceof HighYieldCropModel)) {
                models.put(ONIONS_STAGE7, new HighYieldCropModel(onions7, highYieldOnion));
            }
        }

        if (hasKaleidoscopeCookery()) {
            BakedModel tomato7 = models.get(COOKERY_TOMATO_STAGE7);
            BakedModel highYieldCookeryTomato = models.get(HIGH_YIELD_COOKERY_TOMATO);
            if (tomato7 != null && highYieldCookeryTomato != null && !(tomato7 instanceof HighYieldCropModel)) {
                models.put(COOKERY_TOMATO_STAGE7, new HighYieldCropModel(tomato7, highYieldCookeryTomato));
            }

            BakedModel chili7 = models.get(COOKERY_CHILI_STAGE7);
            BakedModel highYieldChili = models.get(HIGH_YIELD_CHILI);
            if (chili7 != null && highYieldChili != null && !(chili7 instanceof HighYieldCropModel)) {
                models.put(COOKERY_CHILI_STAGE7, new HighYieldCropModel(chili7, highYieldChili));
            }

            BakedModel riceDown = models.get(HIGH_YIELD_RICE_V2_DOWN);
            BakedModel riceMiddle = models.get(HIGH_YIELD_RICE_V2_MIDDLE);
            BakedModel riceTop = models.get(HIGH_YIELD_RICE_V2_TOP);
            if (riceDown != null && riceMiddle != null && riceTop != null) {
                for (ModelResourceLocation riceStage7 : COOKERY_RICE_STAGE7) {
                    BakedModel original = models.get(riceStage7);
                    if (original != null && !(original instanceof HighYieldCropModel)) {
                        models.put(riceStage7, new HighYieldCropModel(original, riceDown, riceMiddle, riceTop));
                    }
                }
            }
        }
    }

    private static boolean hasFarmersDelight() {
        return ModList.get().isLoaded("farmersdelight");
    }

    private static boolean hasKaleidoscopeCookery() {
        return ModList.get().isLoaded("kaleidoscope_cookery");
    }

    private static ModelResourceLocation cookeryRiceStage7(int location, boolean waterlogged) {
        return new ModelResourceLocation(KaleidoscopeAgricultureEvolution.rl("kaleidoscope_cookery", "rice_crop"),
            "age=7,location=" + location + ",waterlogged=" + waterlogged);
    }

    private static int cropTint(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        if (!Config.isGeneticsEnabled()) return -1;
        if (!Config.isVisualTintEnabled()) return -1;
        if (tintIndex != 0) return -1;
        if (level == null || pos == null) return -1;

        CropSpecies species = CropSpeciesRegistry.fromBlock(state.getBlock());
        if (species == null) return -1;

        int age = getCropAge(state);
        if (age < 0) return -1;

        // 仅保留高产品种的成熟金色着色；基因驱动的谷粒/叶片着色已移除
        if (age >= species.getMaxAge() && ClientGenomeData.isHighYield(pos)) {
            return 0xF5D442;
        }
        return -1;
    }

    private static int getCropAge(BlockState state) {
        return CropAge.get(state);
    }
}
