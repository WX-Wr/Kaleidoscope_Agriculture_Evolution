package com.wxwr.kaleidoscopeagricultureevolution.util;

import net.minecraft.util.Mth;
import net.minecraft.core.Direction;

public final class HoneyExtractorMath {
    public static final double STANDARD_FULL_HONEY_AMOUNT = 100.0D;
    public static final double MAX_HONEY_AMOUNT = 200.0D;

    public static final float HONEY_SURFACE_MIN_Y = 0.3F;
    public static final float HONEY_SURFACE_HALF_Y = 0.4F;
    public static final float HONEY_SURFACE_FULL_Y = 0.48F;
    public static final float HONEY_SURFACE_OVER_Y = 0.7F;

    private HoneyExtractorMath() {
    }

    public static float surfaceY(double honeyAmount) {
        float amount = (float) Mth.clamp(honeyAmount / STANDARD_FULL_HONEY_AMOUNT,
                0.0D, MAX_HONEY_AMOUNT / STANDARD_FULL_HONEY_AMOUNT);
        if (amount <= 0.5F) {
            return Mth.lerp(smoothStep(amount / 0.5F), HONEY_SURFACE_MIN_Y, HONEY_SURFACE_HALF_Y);
        }
        if (amount <= 1.0F) {
            return Mth.lerp(smoothStep((amount - 0.5F) / 0.5F), HONEY_SURFACE_HALF_Y, HONEY_SURFACE_FULL_Y);
        }
        return Mth.lerp(smoothStep(amount - 1.0F), HONEY_SURFACE_FULL_Y, HONEY_SURFACE_OVER_Y);
    }

    public static double overfillOmegaMultiplier(double honeyAmount) {
        float surfaceY = surfaceY(honeyAmount);
        if (surfaceY <= HONEY_SURFACE_FULL_Y) {
            return 1.0D;
        }
        if (surfaceY >= HONEY_SURFACE_OVER_Y) {
            return 0.0D;
        }

        double x = (surfaceY - HONEY_SURFACE_FULL_Y) / (HONEY_SURFACE_OVER_Y - HONEY_SURFACE_FULL_Y);
        double reduction = (Math.exp(x) - 1.0D) / (Math.E - 1.0D);
        return Mth.clamp(1.0D - reduction, 0.0D, 1.0D);
    }

    public static double coastDecelerationFactor(double honeyAmount, double baseDamping) {
        double base = Math.max(0.0D, baseDamping);
        float surfaceY = surfaceY(honeyAmount);
        if (surfaceY <= HONEY_SURFACE_FULL_Y) {
            return base;
        }

        double h = surfaceY - HONEY_SURFACE_FULL_Y;
        return base * 10.0D * h;
    }

    public static float visualYaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 180.0F;
        };
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
