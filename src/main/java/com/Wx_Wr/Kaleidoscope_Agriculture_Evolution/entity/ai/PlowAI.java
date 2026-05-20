package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class PlowAI {

    private final PlowOxEntity ox;
    private BlockPos cornerA;
    private BlockPos cornerB;
    private Direction plowDir;
    private Direction stepDir;
    private int currentRow;
    private int totalRows;
    private BlockPos rowStart;
    private BlockPos rowEnd;
    private boolean movingToRowEnd;
    private boolean movingToStart;
    private boolean finished;
    private final List<BlockPos[]> allRows = new ArrayList<>();

    public enum Direction {
        PLUS_X, MINUS_X, PLUS_Z, MINUS_Z
    }

    public PlowAI(PlowOxEntity ox) {
        this.ox = ox;
    }

    public void start(BlockPos cornerA, BlockPos cornerB, Direction plowDir) {
        this.cornerA = cornerA;
        this.cornerB = cornerB;
        this.plowDir = plowDir;
        this.currentRow = 0;
        this.finished = false;
        this.movingToStart = true;
        this.movingToRowEnd = false;

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        switch (plowDir) {
            case PLUS_X, MINUS_X -> {
                totalRows = maxZ - minZ + 1;
                stepDir = (cornerA.getZ() == minZ) ? Direction.PLUS_Z : Direction.MINUS_Z;
            }
            case PLUS_Z, MINUS_Z -> {
                totalRows = maxX - minX + 1;
                stepDir = (cornerA.getX() == minX) ? Direction.PLUS_X : Direction.MINUS_X;
            }
        }

        allRows.clear();
        for (int row = 0; row < totalRows; row++) {
            boolean reversed = (row % 2 == 1);
            BlockPos s, e;
            switch (plowDir) {
                case PLUS_X -> {
                    int z = (stepDir == Direction.PLUS_Z) ? minZ + row : maxZ - row;
                    s = new BlockPos(reversed ? maxX : cornerA.getX(), cornerA.getY(), z);
                    e = new BlockPos(reversed ? cornerA.getX() : maxX, cornerA.getY(), z);
                }
                case MINUS_X -> {
                    int z = (stepDir == Direction.PLUS_Z) ? minZ + row : maxZ - row;
                    s = new BlockPos(reversed ? minX : cornerA.getX(), cornerA.getY(), z);
                    e = new BlockPos(reversed ? cornerA.getX() : minX, cornerA.getY(), z);
                }
                case PLUS_Z -> {
                    int x = (stepDir == Direction.PLUS_X) ? minX + row : maxX - row;
                    s = new BlockPos(x, cornerA.getY(), reversed ? maxZ : cornerA.getZ());
                    e = new BlockPos(x, cornerA.getY(), reversed ? cornerA.getZ() : maxZ);
                }
                case MINUS_Z -> {
                    int x = (stepDir == Direction.PLUS_X) ? minX + row : maxX - row;
                    s = new BlockPos(x, cornerA.getY(), reversed ? minZ : cornerA.getZ());
                    e = new BlockPos(x, cornerA.getY(), reversed ? cornerA.getZ() : minZ);
                }
                default -> { s = cornerA; e = cornerA; }
            }
            allRows.add(new BlockPos[]{s, e});
        }

        if (!allRows.isEmpty()) {
            this.rowStart = allRows.get(0)[0];
            this.rowEnd = allRows.get(0)[1];
        }
    }

    public List<BlockPos[]> getAllRows() {
        return allRows;
    }

    public BlockPos getTargetPos() {
        if (finished) return null;

        if (movingToStart) {
            return rowStart;
        }

        if (movingToRowEnd) {
            return rowEnd;
        } else {
            BlockPos nextRowStart = getNextRowStart();
            if (nextRowStart == null) {
                finished = true;
                return null;
            }
            return nextRowStart;
        }
    }

    public void onReachedTarget() {
        if (finished) return;

        if (movingToStart) {
            movingToStart = false;
            movingToRowEnd = true;
            return;
        }

        if (movingToRowEnd) {
            currentRow++;
            if (currentRow >= totalRows) {
                finished = true;
                return;
            }
            BlockPos[] row = allRows.get(currentRow);
            this.rowStart = row[0];
            this.rowEnd = row[1];
            movingToStart = true;
            movingToRowEnd = false;
        }
    }

    private BlockPos getNextRowStart() {
        if (currentRow + 1 >= totalRows) return null;
        return allRows.get(currentRow + 1)[0];
    }

    public boolean isFinished() {
        return finished;
    }

    public void reset() {
        this.finished = true;
        this.movingToStart = false;
        this.movingToRowEnd = false;
        this.cornerA = null;
        this.cornerB = null;
        this.currentRow = 0;
        this.totalRows = 0;
        this.rowStart = null;
        this.rowEnd = null;
        this.allRows.clear();
    }
}