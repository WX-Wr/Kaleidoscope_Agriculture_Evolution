package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class DryingBoardBlockEntity extends BaseBlockEntity {
    public static final int LAYER_EMPTY = 0;
    public static final int LAYER_WATER = 1;
    public static final int LAYER_SALT_1 = 2;
    public static final int LAYER_SALT_2 = 3;
    public static final int LAYER_SALT_3 = 4;
    public static final int LAYER_SALT_4 = 5;

    private static final String LAYER_STAGE_TAG = "LayerStage";
    private static final String FINISH_GAME_TIME_TAG = "FinishGameTime";
    private static final String SWITCH_GAME_TIMES_TAG = "SwitchGameTimes";
    private static final int SWITCH_COUNT = 4;
    private static final int MIN_SWITCH_TICKS = 50 * 20;
    private static final int MAX_SWITCH_TICKS = 70 * 20;
    private static final int TOTAL_DRYING_TICKS = 5 * 60 * 20;

    private final long[] switchGameTimes = new long[SWITCH_COUNT];
    private int layerStage = LAYER_EMPTY;
    private long finishGameTime;

    public DryingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYING_BOARD.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DryingBoardBlockEntity dryingBoard) {
        dryingBoard.tick(level);
    }

    public boolean isLayerEmpty() {
        return layerStage == LAYER_EMPTY;
    }

    public int getLayerStage() {
        return layerStage;
    }

    public void startDrying(Level level) {
        long startGameTime = level.getGameTime();
        long switchGameTime = startGameTime;
        for (int i = 0; i < SWITCH_COUNT; i++) {
            switchGameTime += randomSwitchDelay(level);
            switchGameTimes[i] = switchGameTime;
        }

        layerStage = LAYER_WATER;
        finishGameTime = startGameTime + TOTAL_DRYING_TICKS;
        refresh();
    }

    private void tick(Level level) {
        if (layerStage == LAYER_EMPTY) {
            return;
        }

        int nextStage = stageFor(level.getGameTime());
        boolean changed = nextStage != layerStage;
        layerStage = nextStage;
        if (finishGameTime > 0L && level.getGameTime() >= finishGameTime) {
            finishGameTime = 0L;
            changed = true;
        }
        if (changed) {
            refresh();
        }
    }

    private int stageFor(long gameTime) {
        int stage = LAYER_WATER;
        for (int i = 0; i < SWITCH_COUNT; i++) {
            if (switchGameTimes[i] > 0L && gameTime >= switchGameTimes[i]) {
                stage = LAYER_SALT_1 + i;
            }
        }
        return stage;
    }

    private static int randomSwitchDelay(Level level) {
        return MIN_SWITCH_TICKS + level.random.nextInt(MAX_SWITCH_TICKS - MIN_SWITCH_TICKS + 1);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        layerStage = tag.getInt(LAYER_STAGE_TAG);
        finishGameTime = tag.getLong(FINISH_GAME_TIME_TAG);
        Arrays.fill(switchGameTimes, 0L);
        long[] savedSwitches = tag.getLongArray(SWITCH_GAME_TIMES_TAG);
        System.arraycopy(savedSwitches, 0, switchGameTimes, 0, Math.min(savedSwitches.length, switchGameTimes.length));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(LAYER_STAGE_TAG, layerStage);
        tag.putLong(FINISH_GAME_TIME_TAG, finishGameTime);
        tag.putLongArray(SWITCH_GAME_TIMES_TAG, switchGameTimes);
    }
}
