package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Checks whether a block position can be plowed, based on three rules:
 * 1. Block below (y-1) must be tillable soil
 * 2. Block at y must not be a non-plant solid block
 * 3. Block above (y+1) must be air
 */
public class PlowableChecker {

    public static boolean isPlowable(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!isTillableSoil(below)) return false;

        BlockState at = level.getBlockState(pos);
        if (!isPlantCompatible(at)) return false;

        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir()) return false;

        return true;
    }

    public static boolean isObstacle(Level level, BlockPos pos, Set<BlockPos> processed) {
        if (processed.contains(pos)) return true;
        return !isPlowable(level, pos);
    }

    private static boolean isTillableSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.FARMLAND);
    }

    private static boolean isPlantCompatible(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS);
    }
}
