package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowAI;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowWorkPlan;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowWorkPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Executes a generated work route on the ox.
 *
 * <p>Route generation stays in {@link PlowWorkPlanner}; this class only
 * coordinates movement, the start animation, active work and finishing.</p>
 */
public final class PlowWorkController {
    private static final int START_WORKING_DURATION = 35;
    private static final int FINISHING_DURATION = 30;
    private static final int PATH_SYNC_INTERVAL = 20;

    private final PlowOxEntity ox;
    private final PlowWorkPlanner planner;
    private int startWorkingTick;
    private int finishingTick;
    private int pathSyncTick;

    public PlowWorkController(PlowOxEntity ox) {
        this.ox = ox;
        this.planner = new PlowWorkPlanner(ox.level());
    }

    public boolean start(PlowWorkPlan plan) {
        return planner.start(plan);
    }

    public void reset() {
        planner.reset();
        startWorkingTick = 0;
        finishingTick = 0;
        pathSyncTick = 0;
    }

    public void resetRoute() {
        planner.resetRoute();
    }

    @Nullable
    public PlowAI.Direction getDirection() {
        return planner.getDirection();
    }

    public String getPathData() {
        return planner.getPathData();
    }

    @Nullable
    public BlockPos getTargetPos() {
        return planner.getTargetPos();
    }

    public void skipToRemaining(String pathData) {
        planner.skipToRemaining(pathData);
    }

    public boolean hasRemainingTarget() {
        return planner.getTargetPos() != null;
    }

    public void restartFromSupplyPause() {
        startWorkingTick = 0;
        ox.setXRot(0);
        ox.setOxState(PlowOxEntity.OxState.START_WORKING);
    }

    public void onReachedTarget() {
        planner.onReachedTarget();
    }

    public boolean restore(PlowWorkPlan plan, String remainingPathData) {
        if (!start(plan)) {
            return false;
        }
        if (remainingPathData != null && !remainingPathData.isEmpty()) {
            skipToRemaining(remainingPathData);
        }
        return true;
    }

    public static PlowAI.Direction readDirection(CompoundTag tag) {
        if (!tag.contains("SelectedPlowDir")) {
            return null;
        }
        try {
            return PlowAI.Direction.valueOf(tag.getString("SelectedPlowDir"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public void save(CompoundTag tag) {
        PlowAI.Direction direction = getDirection();
        if (direction != null) {
            tag.putString("SelectedPlowDir", direction.name());
        }
        if (startWorkingTick != 0) {
            tag.putInt("StartWorkingTick", startWorkingTick);
        }
        if (finishingTick != 0) {
            tag.putInt("FinishingTick", finishingTick);
        }
    }

    public void loadProgress(CompoundTag tag) {
        if (tag.contains("StartWorkingTick")) {
            startWorkingTick = Mth.clamp(tag.getInt("StartWorkingTick"), 0, START_WORKING_DURATION);
        }
        if (tag.contains("FinishingTick")) {
            finishingTick = Mth.clamp(tag.getInt("FinishingTick"), 0, FINISHING_DURATION);
        }
    }

    public void tickMoveToStart() {
        if (ox.getOxState() != PlowOxEntity.OxState.MOVE_TO_START) {
            return;
        }

        BlockPos target = getTargetPos();
        if (target == null) {
            ox.setOxState(PlowOxEntity.OxState.WAITING);
            resetRoute();
            ox.setPlowPathData("");
            return;
        }

        moveToward(target);
        if (ox.position().distanceTo(centerOf(target)) < 0.1) {
            ox.setOxState(PlowOxEntity.OxState.START_WORKING);
            startWorkingTick = 0;
        }
    }

    public void tickStartWorking() {
        if (ox.getOxState() != PlowOxEntity.OxState.START_WORKING) {
            return;
        }

        ox.stopWorkMovement();

        LivingEntity bound = ox.getBoundEntity();
        if (bound instanceof FarmerEntity farmer
                && (farmer.getMovementState() != FarmerEntity.MovementState.WORKING
                || farmer.distanceToSqr(ox) > 25.0)) {
            startWorkingTick = 0;
            ox.setXRot(0);
            return;
        }

        startWorkingTick++;
        ox.setXRot(30.0f * Math.min(startWorkingTick / (float) START_WORKING_DURATION, 1.0f));
        ox.setMaxUpStep(1.0F);
        if (startWorkingTick > START_WORKING_DURATION) {
            ox.setOxState(PlowOxEntity.OxState.WORKING);
            ox.activateTool();
        }
    }

    public void tickWorking() {
        if (ox.getOxState() != PlowOxEntity.OxState.WORKING) {
            return;
        }

        BlockPos target = getTargetPos();
        if (target == null) {
            ox.setOxState(PlowOxEntity.OxState.FINISHING);
            finishingTick = 0;
            resetRoute();
            ox.setPlowPathData("");
            ox.deactivateTool();
            return;
        }

        moveToward(target);
        ox.setXRot(30.0f);
        if (ox.position().distanceTo(centerOf(target)) < 0.1) {
            planner.onReachedTarget();
            ox.consumeBoostBlock();
        }

        pathSyncTick++;
        if (pathSyncTick >= PATH_SYNC_INTERVAL) {
            pathSyncTick = 0;
            ox.setPlowPathData(getPathData());
        }
    }

    public void tickFinishing() {
        if (ox.getOxState() != PlowOxEntity.OxState.FINISHING) {
            return;
        }

        ox.stopWorkMovement();
        ox.setXRot(30.0f * Math.max(1.0f - finishingTick / (float) FINISHING_DURATION, 0.0f));
        ox.setMaxUpStep(0.6F);
        finishingTick++;
        if (finishingTick > FINISHING_DURATION) {
            ox.setOxState(PlowOxEntity.OxState.WAITING);
            finishingTick = 0;
        }
    }

    private void moveToward(BlockPos target) {
        double targetX = target.getX() + 0.5;
        double targetY = target.getY();
        double targetZ = target.getZ() + 0.5;
        Vec3 direction = new Vec3(targetX - ox.getX(), targetY - ox.getY(), targetZ - ox.getZ()).normalize();
        double speed = ox.getOxState() == PlowOxEntity.OxState.MOVE_TO_START
                ? ox.getMoveToCornerEquivalentSpeed()
                : ox.getWorkingSpeed();

        if (targetY > ox.getY() && ox.onGround() && ox.hasHorizontalCollision()) {
            ox.setDeltaMovement(ox.getDeltaMovement().x, 0.42, ox.getDeltaMovement().z);
        } else {
            ox.setDeltaMovement(direction.x * speed, ox.getDeltaMovement().y, direction.z * speed);
        }

        float newYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float yawDiff = Mth.wrapDegrees(newYaw - ox.getYRot());
        if (Math.abs(yawDiff) > 2.0f) {
            ox.setYRot(ox.getYRot() + yawDiff);
        }
        ox.setWorkHeadYaw(ox.getOxState() == PlowOxEntity.OxState.MOVE_TO_START
                ? ox.getYRot()
                : newYaw);
        if (ox.getOxState() == PlowOxEntity.OxState.MOVE_TO_START) {
            ox.setXRot(0);
        }
    }

    private static Vec3 centerOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
