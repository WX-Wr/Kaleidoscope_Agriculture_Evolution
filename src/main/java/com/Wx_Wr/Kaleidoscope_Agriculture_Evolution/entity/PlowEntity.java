package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Optional;
import java.util.UUID;

public class PlowEntity extends AbstractDraggableEntity {

    // 保存所属牛的 UUID
    private static final EntityDataAccessor<Optional<UUID>> OX_UUID =
            SynchedEntityData.defineId(PlowEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public PlowEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OX_UUID, Optional.empty());
    }

    // ==================== 核心牵引行为 ====================

    @Override
    protected void onDraggerMove() {
        BlockPos below = this.blockPosition().below();
        BlockState stateBelow = level().getBlockState(below);

        // 翻耕泥土
        if (isTillable(stateBelow)) {
            level().setBlock(below, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0), 3);
            spawnBlockParticles(below, stateBelow);
            if (!this.level().isClientSide) {
                this.level().playSound(null, below,
                        net.minecraft.sounds.SoundEvents.HOE_TILL,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            }
        }

        // 破坏路径上的花花草草
        BlockPos at = this.blockPosition();
        BlockState stateAt = level().getBlockState(at);
        if (isBreakablePlant(stateAt)) {
            level().destroyBlock(at, true);
        }
        if (isBreakablePlant(stateBelow)) {
            level().destroyBlock(below, true);
        }
    }

    private boolean isBreakablePlant(BlockState state) {
        return state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS);
    }

    @Override
    protected ItemStack getDropItem() {
        return new ItemStack(ModBlocks.PLOW.get().asItem());
    }

    @Override
    protected double getFollowDistance() { return 2.5; }
    @Override
    protected double getPullForce() { return 0.3; }
    @Override
    protected double getMaxSpeed() { return 0.35; }
    @Override
    protected float getTurnSpeed() { return 10.0f; }

    private boolean isTillable(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) ||
                state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
    }

    // ==================== 牛的绑定管理 ====================

    public void setOx(PlowOxEntity ox) {
        if (ox != null) {
            this.entityData.set(OX_UUID, Optional.of(ox.getUUID()));
        } else {
            this.entityData.set(OX_UUID, Optional.empty());
        }
    }

    public UUID getOxUUID() {
        return this.entityData.get(OX_UUID).orElse(null);
    }

    /**
     * 获取绑定的牛实体
     */
    public PlowOxEntity getOx() {
        UUID uuid = getOxUUID();
        if (uuid == null) return null;

        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof PlowOxEntity ox && ox.isAlive()) {
                return ox;
            }
        }
        return null;
    }

    // ==================== 朝向更新 ====================

    @Override
    public void tick() {
        super.tick();
    }

    // ==================== 数据持久化 ====================

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID oxId = getOxUUID();
        if (oxId != null) {
            tag.putUUID("OxUUID", oxId);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OxUUID")) {
            this.entityData.set(OX_UUID, Optional.of(tag.getUUID("OxUUID")));
        }
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 5, this::predicate));
    }

    private PlayState predicate(AnimationState<PlowEntity> state) {
        if (isWorking() && this.getDeltaMovement().horizontalDistance() > getMinWorkSpeed()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plow.plowing"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plow.idle"));
        }
        return PlayState.CONTINUE;
    }

    // ==================== 绳子连接点 ====================

    private static final float PLOW_LEFT_X = -0.5625f;  // -9/16
    private static final float PLOW_RIGHT_X = 0.5625f;   // 9/16
    private static final float PLOW_Y = 0.31875f;        // 5.1/16
    private static final float PLOW_Z = -0.76875f;       // -12.3/16

    @Override
    public Vec3 getLeftRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, PLOW_LEFT_X, PLOW_Y, PLOW_Z, partialTick);
    }

    @Override
    public Vec3 getRightRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, PLOW_RIGHT_X, PLOW_Y, PLOW_Z, partialTick);
    }

    private static Vec3 calculateAttachPoint(Entity entity, float localX, float localY, float localZ, float partialTick) {
        double x = entity.xOld + (entity.getX() - entity.xOld) * partialTick;
        double y = entity.yOld + (entity.getY() - entity.yOld) * partialTick;
        double z = entity.zOld + (entity.getZ() - entity.zOld) * partialTick;
        float yaw = entity.getViewYRot(partialTick);
        float yawRad = (float) Math.toRadians(yaw);

        // 模型 root rotation = 180 度补偿
        float rotatedX = -localX;
        float rotatedZ = -localZ;

        double worldX = x + (rotatedX * Math.cos(yawRad) - rotatedZ * Math.sin(yawRad));
        double worldZ = z + (rotatedX * Math.sin(yawRad) + rotatedZ * Math.cos(yawRad));
        return new Vec3(worldX, y + localY, worldZ);
    }
}