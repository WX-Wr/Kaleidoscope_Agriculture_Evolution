package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowPathData;
import org.jetbrains.annotations.Nullable;

/**
 * 每 tick 的农夫感知缓存。这是将行为分派进一步下沉到 Brain 之前
 * 的小型 Sensor/Memory 层。
 */
final class FarmerContext {
    @Nullable
    private final PlowOxEntity ox;
    @Nullable
    private final AbstractDraggableEntity tool;
    @Nullable
    private final LoucheEntity louche;
    private final PlowOxEntity.OxState oxState;
    private final int remainingWork;
    private final boolean inTargetArea;
    private final boolean supplyTravelling;
    private final boolean supplyNeedsStore;

    private FarmerContext(
            @Nullable PlowOxEntity ox,
            @Nullable AbstractDraggableEntity tool,
            @Nullable LoucheEntity louche,
            PlowOxEntity.OxState oxState,
            int remainingWork,
            boolean inTargetArea,
            boolean supplyTravelling,
            boolean supplyNeedsStore) {
        this.ox = ox;
        this.tool = tool;
        this.louche = louche;
        this.oxState = oxState;
        this.remainingWork = remainingWork;
        this.inTargetArea = inTargetArea;
        this.supplyTravelling = supplyTravelling;
        this.supplyNeedsStore = supplyNeedsStore;
    }

    static FarmerContext capture(FarmerEntity farmer, FarmerSupplyManager supplyManager) {
        PlowOxEntity ox = farmer.getBoundOx();
        if (ox == null) {
            return new FarmerContext(
                    null,
                    null,
                    null,
                    PlowOxEntity.OxState.IDLE,
                    0,
                    false,
                    supplyManager.isTravelling(farmer),
                    supplyManager.needsStore(farmer));
        }

        AbstractDraggableEntity tool = ox.getToolEntity();
        LoucheEntity louche = tool instanceof LoucheEntity candidate && candidate.isAlive() ? candidate : null;
        return new FarmerContext(
                ox,
                tool,
                louche,
                ox.getOxState(),
                getRemainingWork(ox),
                farmer.isInTargetArea(ox),
                supplyManager.isTravelling(farmer),
                supplyManager.needsStore(farmer));
    }

    @Nullable
    PlowOxEntity ox() {
        return ox;
    }

    @Nullable
    AbstractDraggableEntity tool() {
        return tool;
    }

    @Nullable
    LoucheEntity louche() {
        return louche;
    }

    PlowOxEntity.OxState oxState() {
        return oxState;
    }

    int remainingWork() {
        return remainingWork;
    }

    boolean inTargetArea() {
        return inTargetArea;
    }

    boolean supplyTravelling() {
        return supplyTravelling;
    }

    boolean supplyNeedsStore() {
        return supplyNeedsStore;
    }

    boolean isFieldWorkState() {
        return oxState == PlowOxEntity.OxState.MOVE_TO_START
                || oxState == PlowOxEntity.OxState.START_WORKING
                || oxState == PlowOxEntity.OxState.WORKING;
    }

    private static int getRemainingWork(PlowOxEntity ox) {
        String pathData = ox.getPlowPathData();
        if (!pathData.isEmpty()) {
            return PlowPathData.parse(pathData).size();
        }
        var corner = ox.getTargetCorner();
        var otherCorner = ox.getOtherCorner();
        if (corner == null || otherCorner == null) {
            return 0;
        }
        return (Math.abs(otherCorner.getX() - corner.getX()) + 1)
                * (Math.abs(otherCorner.getY() - corner.getY()) + 1)
                * (Math.abs(otherCorner.getZ() - corner.getZ()) + 1);
    }
}
