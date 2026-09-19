package com.wxwr.kaleidoscopeagricultureevolution.work;

public record WorkPathRule(
        String id,
        int workYOffset,
        boolean requiresWaterAtWorkY,
        int airAbove,
        int operationBlockYOffset,
        int waterYOffset,
        int airAboveWater) {
}
