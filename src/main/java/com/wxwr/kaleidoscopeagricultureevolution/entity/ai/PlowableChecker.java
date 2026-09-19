package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkPathRule;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * 根据以下三条规则检查方块位置是否可犁：
 * 1. 下方方块（y-1）必须为可耕种土壤
 * 2. y 处方块不能是非植物的实体方块
 * 3. 上方方块（y+1）必须为空气
 */
public class PlowableChecker {
    private static Block cachedRichSoil;
    private static Block cachedRichSoilFarmland;
    private static boolean richSoilLookupDone;

    public static boolean isPlowable(Level level, BlockPos pos) {
        return isWorkable(level, pos, WorkAction.TILL, dryPathRule());
    }

    public static boolean isWorkable(Level level, BlockPos workPos,
                                     WorkAction action, WorkPathRule pathRule) {
        BlockPos operationPos = workPos.offset(0, pathRule.operationBlockYOffset(), 0);
        BlockState operation = level.getBlockState(operationPos);
        if (!isValidOperationBlock(operation, action)) return false;

        if (pathRule.requiresWaterAtWorkY()) {
            BlockPos waterPos = workPos.offset(0, pathRule.waterYOffset(), 0);
            if (!isWater(level.getBlockState(waterPos))) return false;
            for (int i = 1; i <= pathRule.airAboveWater(); i++) {
                if (!level.getBlockState(waterPos.above(i)).isAir()) return false;
            }
            return true;
        }

        if (!isPlantCompatible(level.getBlockState(workPos))) return false;
        for (int i = 1; i <= pathRule.airAbove(); i++) {
            if (!level.getBlockState(workPos.above(i)).isAir()) return false;
        }
        return true;
    }

    public static boolean isObstacle(Level level, BlockPos pos, Set<BlockPos> processed) {
        return isObstacle(level, pos, processed, WorkAction.TILL, dryPathRule());
    }

    public static boolean isObstacle(Level level, BlockPos pos, Set<BlockPos> processed,
                                     WorkAction action, WorkPathRule pathRule) {
        if (processed.contains(pos)) return true;
        return !isPathPassable(level, pos, pathRule);
    }

    public static boolean isPathPassable(Level level, BlockPos pos, WorkPathRule pathRule) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || isPlant(state)) return true;
        return pathRule.requiresWaterAtWorkY() && isWater(state);
    }

    private static WorkPathRule dryPathRule() {
        return new WorkPathRule("dry_field", 0, false, 1, -1, 0, 0);
    }

    private static boolean isValidOperationBlock(BlockState state, WorkAction action) {
        return switch (action) {
            case TILL -> isTillableSoil(state);
            case SOW, FERTILIZE -> isFarmableSoil(state);
        };
    }

    private static boolean isFarmableSoil(BlockState state) {
        return state.getBlock() instanceof FarmBlock || isRichSoil(state);
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    private static boolean isTillableSoil(BlockState state) {
        return state.getBlock() instanceof FarmBlock
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || isRichSoil(state);
    }

    private static boolean isPlantCompatible(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS);
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS);
    }

    private static boolean isRichSoil(BlockState state) {
        Block richSoil = getRichSoil();
        Block richSoilFarmland = cachedRichSoilFarmland;
        return (richSoil != null && state.is(richSoil))
                || (richSoilFarmland != null && state.is(richSoilFarmland));
    }

    private static Block getRichSoil() {
        if (!richSoilLookupDone) {
            richSoilLookupDone = true;
            cachedRichSoil = ForgeRegistries.BLOCKS.getValue(
                    KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rich_soil"));
            cachedRichSoilFarmland = ForgeRegistries.BLOCKS.getValue(
                    KaleidoscopeAgricultureEvolution.rl("farmersdelight", "rich_soil_farmland"));
        }
        return cachedRichSoil;
    }
}
