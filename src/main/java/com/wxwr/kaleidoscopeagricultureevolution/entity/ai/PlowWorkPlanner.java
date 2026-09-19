package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Owns route generation and route progress for a single work request.
 * Entity classes should only coordinate this object with their movement and
 * animation state.
 */
public final class PlowWorkPlanner {
    private final PlowAI pathfinder;

    @Nullable
    private PlowWorkPlan plan;

    public PlowWorkPlanner(Level level) {
        this.pathfinder = new PlowAI(level);
    }

    public boolean start(PlowWorkPlan plan) {
        this.plan = plan;
        boolean started = pathfinder.start(
                plan.region(),
                plan.startPos(),
                plan.direction(),
                plan.action(),
                plan.pathRule());
        if (!started) {
            this.plan = null;
        }
        return started;
    }

    public void reset() {
        pathfinder.reset();
        plan = null;
    }

    /** Clears the generated route while keeping the current work description. */
    public void resetRoute() {
        pathfinder.reset();
    }

    @Nullable
    public BlockPos getTargetPos() {
        return pathfinder.getTargetPos();
    }

    public void onReachedTarget() {
        pathfinder.onReachedTarget();
    }

    public void skipToRemaining(String pathData) {
        pathfinder.skipToRemaining(pathData);
    }

    public boolean isFinished() {
        return pathfinder.isFinished();
    }

    public String getPathData() {
        return pathfinder.getPathData();
    }

    public int getRemainingPathCount() {
        return pathfinder.getRemainingPathCount();
    }

    @Nullable
    public PlowWorkPlan getPlan() {
        return plan;
    }

    @Nullable
    public PlowAI.Direction getDirection() {
        return plan == null ? null : plan.direction();
    }
}
