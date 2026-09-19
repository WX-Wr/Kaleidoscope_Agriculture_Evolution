package com.wxwr.kaleidoscopeagricultureevolution.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AgricultureMobEffect extends MobEffect {
    public enum Behavior {
        FIREBURST,
        SOUR_FRESH,
        SWEET_SOUR,
        FULLNESS,
        CRISP,
        FRUIT_ACID
    }

    private final Behavior behavior;

    public AgricultureMobEffect(Behavior behavior, int color) {
        super(MobEffectCategory.BENEFICIAL, color);
        this.behavior = behavior;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        switch (behavior) {
            case FIREBURST -> applyFireburst(entity);
            case SOUR_FRESH -> maintain(entity, MobEffects.MOVEMENT_SPEED, amplifier);
            case SWEET_SOUR -> {
                maintain(entity, MobEffects.JUMP, 0);
                maintain(entity, MobEffects.DAMAGE_BOOST, 1);
            }
            case FULLNESS -> {
                maintain(entity, MobEffects.ABSORPTION, 0);
                maintain(entity, MobEffects.SATURATION, 0);
            }
            case CRISP -> maintain(entity, MobEffects.JUMP, 1);
            case FRUIT_ACID -> {
                maintain(entity, MobEffects.MOVEMENT_SPEED, 1);
                removeHarmfulEffects(entity);
            }
        }
    }

    private static void applyFireburst(LivingEntity entity) {
        Level level = entity.level();
        if (level.isClientSide) {
            renderFireburstLine(level, entity);
            return;
        }

        Vec3 start = entity.getEyePosition(1.0F);
        Vec3 look = entity.getLookAngle();
        Vec3 end = start.add(look.scale(1.0));
        AABB searchBox = new AABB(start, end).inflate(0.75);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox, AgricultureMobEffect::isValidFireburstTarget)) {
            if (target != entity && isAlongSightLine(entity, target, start, end)) {
                target.setSecondsOnFire(3);
            }
        }
    }

    private static void renderFireburstLine(Level level, LivingEntity entity) {
        Vec3 start = entity.getEyePosition(1.0F);
        Vec3 look = entity.getLookAngle().normalize();
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1.0, 0.0, 0.0);
        } else {
            side = side.normalize();
        }
        Vec3 offset = side.scale(0.02);
        double lineLength = 3.0;
        int samples = 12;

        for (int i = 0; i < samples; i++) {
            double progress = 0.15 + (lineLength - 0.15) * i / (samples - 1.0);
            Vec3 pos = start.add(look.scale(progress)).add(offset);
            level.addParticle(
                    ParticleTypes.FLAME,
                    pos.x,
                    pos.y,
                    pos.z,
                    0.0,
                    0.0,
                    0.0);
        }
    }

    private static boolean isValidFireburstTarget(LivingEntity target) {
        return target instanceof Animal || target instanceof Player;
    }

    private static boolean isAlongSightLine(LivingEntity source, LivingEntity target, Vec3 start, Vec3 end) {
        if (target instanceof ArmorStand) {
            return false;
        }

        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double distanceToSegmentSq = distanceToSegmentSqr(targetCenter, start, end);
        double maxDistance = 0.7 + target.getBbWidth() * 0.5;
        return distanceToSegmentSq <= maxDistance * maxDistance;
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSq = segment.lengthSqr();
        if (lengthSq < 1.0E-6) {
            return point.distanceToSqr(start);
        }

        double projection = point.subtract(start).dot(segment) / lengthSq;
        if (projection < 0.0 || projection > 1.0) {
            Vec3 nearest = projection < 0.0 ? start : end;
            return point.distanceToSqr(nearest);
        }

        Vec3 closestPoint = start.add(segment.scale(projection));
        return point.distanceToSqr(closestPoint);
    }

    private static void maintain(LivingEntity entity, net.minecraft.world.effect.MobEffect effect, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, 3, amplifier, false, false, true));
    }

    private static void removeHarmfulEffects(LivingEntity entity) {
        for (MobEffectInstance instance : entity.getActiveEffects().toArray(MobEffectInstance[]::new)) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                entity.removeEffect(instance.getEffect());
            }
        }
    }
}
