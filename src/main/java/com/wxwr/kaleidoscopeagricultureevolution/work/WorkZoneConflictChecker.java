package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.wxwr.kaleidoscopeagricultureevolution.region.CuboidGeometry;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class WorkZoneConflictChecker {
    private WorkZoneConflictChecker() {
    }

    public static Optional<BlockPos[]> firstIntersection(BlockPos[] candidate, Iterable<BlockPos[]> existing) {
        for (BlockPos[] range : existing) {
            BlockPos[] intersection = CuboidGeometry.intersection(candidate, range);
            if (intersection != null) {
                return Optional.of(intersection);
            }
        }
        return Optional.empty();
    }
}
