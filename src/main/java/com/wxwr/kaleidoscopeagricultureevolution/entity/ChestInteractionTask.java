package com.wxwr.kaleidoscopeagricultureevolution.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;

import javax.annotation.Nonnull;

/**
 * 箱子交互任务 — 封装"开箱 → 执行 → 关箱"的完整定时循环。
 * 通过构造时传入的 {@link Runnable} 区分取物 / 存物操作。
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>构造（不触发任何副作用）</li>
 *   <li>{@link #start()} — 开始循环（不立即开箱，由首次 {@link #tick()} 负责）</li>
 *   <li>{@link #tick()} — 每 tick 调用，首次调用打开箱子盖，随后执行存取操作，完成返回 true</li>
 *   <li>{@link #cancel()} — 任意时刻取消，保证箱子盖被关闭</li>
 * </ol>
 *
 * <h3>持久化</h3>
 * 调用 {@link #save(CompoundTag)} / {@link #load(CompoundTag, ChestInteractionTask)}
 * 写 / 读可变状态。读回后需调用 {@link #resumeLidAfterLoad()} 恢复箱子盖状态。
 */
public class ChestInteractionTask {

    // ==================== 常量 ====================

    /** 总周期长度 (tick) */
    public static final int TOTAL_TICKS = 120;

    /** 实际执行 tick（开箱后立即执行） */
    public static final int EXECUTE_TICK = 119;

    // ==================== 不可变状态 ====================

    protected final BlockPos chestPos;
    protected final Level level;
    private final String taskType; // "fetch" 或 "store"，用于 NBT 持久化
    private final Runnable executeAction;

    // ==================== 可变状态 ====================

    private int timer = TOTAL_TICKS;
    private boolean started;
    private boolean cancelled;
    private boolean finished;
    private boolean lidOpen;

    // ==================== 构造 ====================

    /**
     * @param chestPos      目标箱子位置（不可为 null）
     * @param level         所在维度
     * @param taskType      任务类型（"fetch" 或 "store"）
     * @param executeAction 存取操作的具体逻辑
     */
    public ChestInteractionTask(@Nonnull BlockPos chestPos, @Nonnull Level level,
                                @Nonnull String taskType, @Nonnull Runnable executeAction) {
        this.chestPos = chestPos;
        this.level = level;
        this.taskType = taskType;
        this.executeAction = executeAction;
    }

    // ==================== 公开 API ====================

    public final void start() {
        if (started) return;
        started = true;
        timer = TOTAL_TICKS;
    }

    public final boolean tick() {
        if (!started || cancelled || finished) return false;

        if (timer == TOTAL_TICKS) {
            setChestLid(true);
        }

        timer--;

        if (timer == EXECUTE_TICK) {
            executeAction.run();
        }

        if (timer <= 0) {
            setChestLid(false);
            finished = true;
            return true;
        }
        return false;
    }

    public final void cancel() {
        if (cancelled || finished) return;
        cancelled = true;
        setChestLid(false);
    }

    // ==================== 查询 ====================

    public final boolean isActive() {
        return started && !cancelled && !finished;
    }

    public final boolean isRunning() {
        return started && !cancelled && !finished && timer > 0;
    }

    public final int getTimer() {
        return timer;
    }

    public final String getTaskType() {
        return taskType;
    }

    // ==================== NBT 持久化 ====================

    public final void save(CompoundTag tag) {
        tag.putInt("ChestTaskTimer", timer);
        tag.putBoolean("ChestTaskStarted", started);
    }

    public static void load(CompoundTag tag, ChestInteractionTask task) {
        task.timer = tag.getInt("ChestTaskTimer");
        task.started = tag.getBoolean("ChestTaskStarted");
        task.cancelled = false;
        task.finished = false;
    }

    public final void resumeLidAfterLoad() {
        if (started && !cancelled && !finished && timer > 0) {
            setChestLid(true);
        }
    }

    // ==================== 内部：箱子盖控制 ====================

    private void setChestLid(boolean open) {
        if (open == lidOpen) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof ChestBlockEntity chest)) return;

        Player fake = FakePlayerFactory.getMinecraft(serverLevel);
        if (open) {
            chest.startOpen(fake);
        } else {
            chest.stopOpen(fake);
        }
        lidOpen = open;
    }
}
