package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

/**
 * 抽象牵引实体 - 可被牛牵引的设备基类
 * 无碰撞箱，强制贴地
 */
public abstract class AbstractDraggableEntity extends Entity implements GeoAnimatable {

    // ========== 数据同步键 ==========
    protected static final EntityDataAccessor<Optional<UUID>> OX_UUID =
            SynchedEntityData.defineId(AbstractDraggableEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    // ========== 牵引相关字段 ==========
    @Nullable
    protected PlowOxEntity ox;
    protected int soundCooldownTicks = 0;

    // 绳子是否已创建（用于防止重复创建）
    protected boolean ropesCreated = false;

    // ========== 客户端平滑插值字段 ==========
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYaw;
    protected double lerpPitch;

    // ========== 物理/生命值 ==========
    protected float health = 10.0F;
    protected int wobbleTicks = 0;
    protected float wobbleDirection = 0.0F;

    // ========== 工作状态 ==========
    protected boolean isWorking = false;

    // ========== GeckoLib ==========
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ========== 构造函数 ==========
    public AbstractDraggableEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setMaxUpStep(1.0f);
        this.noPhysics = true;
    }

    // ========== 抽象方法 ==========
    protected abstract void onDraggerMove();
    protected abstract ItemStack getDropItem();

    // ========== 可被子类覆盖的参数 ==========
    protected double getFollowDistance() { return 1.0; }
    protected double getMaxBreakDistance() { return 4.0; }
    protected double getMaxSpeed() { return 0.4; }
    protected double getPullForce() { return 0.25; }
    protected double getDamping() { return 0.92; }
    protected float getTurnSpeed() { return 12.0f; }
    protected double getSideOffset() { return 0.0; }
    protected double getMinWorkSpeed() { return 0.08; }
    protected net.minecraft.sounds.SoundEvent getMovingSound() { return SoundEvents.WOOD_PLACE; }

    /**
     * 获取绳子附着点的相对位置（可被子类覆盖）
     * 默认位置：实体中心上方 70% 高度处
     */
    protected Vec3 getRopeAttachmentOffset() {
        return new Vec3(0, this.getBbHeight() * 0.7, 0);
    }

    /**
     * 获取左绳附着点的侧向偏移（可被子类覆盖）
     * 默认向左偏移 0.3 格
     */
    protected double getLeftRopeSideOffset() {
        return -0.3;
    }

    /**
     * 获取右绳附着点的侧向偏移（可被子类覆盖）
     * 默认向右偏移 0.3 格
     */
    protected double getRightRopeSideOffset() {
        return 0.3;
    }

    // ========== 绳子管理 ==========

    /**
     * 当被绑定到牛时调用
     */
    protected void onAttached() {
        ropesCreated = true;
    }

    /**
     * 当与牛解绑时调用
     */
    protected void onDetached() {
        ropesCreated = false;
    }

    // ========== 牵引绑定方法 ==========

    /**
     * 设置牵引的牛
     */
    public void setOx(@Nullable PlowOxEntity ox) {
        // 解绑旧牛时销毁绳子
        if (this.ox != null && ox == null) {
            onDetached();
        }

        this.ox = ox;
        if (ox != null) {
            this.entityData.set(OX_UUID, Optional.of(ox.getUUID()));
            onAttached();
        } else {
            this.entityData.set(OX_UUID, Optional.empty());
        }
    }

    /**
     * 获取牵引的牛
     */
    @Nullable
    public PlowOxEntity getOx() {
        if (!this.level().isClientSide && this.ox != null && this.ox.isAlive()) {
            return this.ox;
        }
        if (!this.level().isClientSide) {
            Optional<UUID> uuid = this.entityData.get(OX_UUID);
            if (uuid.isPresent() && this.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid.get());
                if (entity instanceof PlowOxEntity ox && ox.isAlive()) {
                    this.ox = ox;
                    return ox;
                } else {
                    this.entityData.set(OX_UUID, Optional.empty());
                    this.ox = null;
                }
            }
        }
        return null;
    }

    public boolean hasOx() { return getOx() != null; }

    public void setWorking(boolean working) { this.isWorking = working; }
    public boolean isWorking() { return this.isWorking; }

    // ========== 核心牵引逻辑 ==========

    @Override
    public void tick() {
        super.tick();
        this.tickLerp();

        PlowOxEntity ox = getOx();
        if (ox != null && ox.isAlive()) {
            this.tickFollowing(ox);
        } else if (ox == null && !this.level().isClientSide) {
            this.discard();
            return;
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        handleMovementSound();
        handleWobble();
    }

    private void tickFollowing(PlowOxEntity ox) {
        double distanceSq = this.distanceToSqr(ox);
        double maxBreakDistanceSq = getMaxBreakDistance() * getMaxBreakDistance();

        if (distanceSq > maxBreakDistanceSq) {
            ox.removePlow();
            this.discard();
            return;
        }

        Vec3 targetPos = getTargetPosition(ox);

        Vec3 desiredVelocity = targetPos.subtract(this.position());
        double maxSpeed = getMaxSpeed();
        if (desiredVelocity.length() > maxSpeed) {
            desiredVelocity = desiredVelocity.normalize().scale(maxSpeed);
        }

        Vec3 acceleration = desiredVelocity.subtract(this.getDeltaMovement());
        acceleration = acceleration.scale(getPullForce());
        this.setDeltaMovement(this.getDeltaMovement().add(acceleration));
        this.setDeltaMovement(this.getDeltaMovement().scale(getDamping()));

        if (this.getDeltaMovement().length() > maxSpeed) {
            this.setDeltaMovement(this.getDeltaMovement().normalize().scale(maxSpeed));
        }

        float targetYaw = ox.getYRot();
        float deltaYaw = Mth.wrapDegrees(targetYaw - this.getYRot());
        deltaYaw = Mth.clamp(deltaYaw, -getTurnSpeed(), getTurnSpeed());
        this.setYRot(this.getYRot() + deltaYaw);

        if (isWorking && this.getDeltaMovement().horizontalDistance() > getMinWorkSpeed()) {
            onDraggerMove();
        }
    }

    private Vec3 getTargetPosition(PlowOxEntity ox) {
        float yaw = ox.getYRot();
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double followDist = getFollowDistance();
        double sideOffset = getSideOffset();

        double x = ox.getX() + sin * followDist + cos * sideOffset;
        double z = ox.getZ() - cos * followDist + sin * sideOffset;
        double y = ox.getY();

        return new Vec3(x, y, z);
    }

    private void handleMovementSound() {
        double dx = this.getX() - this.xOld;
        double dz = this.getZ() - this.zOld;
        double distanceTravelled = Math.sqrt(dx * dx + dz * dz);

        if (distanceTravelled > 0.15 && soundCooldownTicks <= 0 && !this.level().isClientSide) {
            this.level().playSound(null, this.blockPosition(),
                    getMovingSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
            soundCooldownTicks = 40;
        }
        if (soundCooldownTicks > 0) soundCooldownTicks--;
    }

    private void handleWobble() {
        if (this.wobbleTicks > 0) {
            float wobbleAmount = (float) Math.sin((10 - this.wobbleTicks) * Math.PI / 10) * wobbleDirection;
            this.setYRot(this.getYRot() + wobbleAmount);
            this.wobbleTicks--;
            if (this.wobbleTicks == 0) {
                this.setYRot(this.getYRot() - wobbleDirection);
            }
        }
    }

    // ========== 客户端平滑插值 ==========

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = posRotationIncrements;
    }

    protected void tickLerp() {
        if (this.lerpSteps > 0) {
            double dx = (this.lerpX - this.getX()) / this.lerpSteps;
            double dy = (this.lerpY - this.getY()) / this.lerpSteps;
            double dz = (this.lerpZ - this.getZ()) / this.lerpSteps;
            this.setYRot((float) (this.getYRot() + Mth.wrapDegrees(this.lerpYaw - this.getYRot()) / this.lerpSteps));
            this.setXRot((float) (this.getXRot() + (this.lerpPitch - this.getXRot()) / this.lerpSteps));
            this.lerpSteps--;
            this.setOnGround(true);
            this.move(MoverType.SELF, new Vec3(dx, dy, dz));
            this.setRot(this.getYRot(), this.getXRot());
        }
    }

    // ========== 数据持久化 ==========

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OX_UUID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        PlowOxEntity ox = getOx();
        if (ox != null) tag.putUUID("OxUUID", ox.getUUID());
        tag.putFloat("Health", this.health);
        tag.putBoolean("IsWorking", this.isWorking);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OxUUID")) {
            this.entityData.set(OX_UUID, Optional.of(tag.getUUID("OxUUID")));
        }
        if (tag.contains("Health")) this.health = tag.getFloat("Health");
        if (tag.contains("IsWorking")) this.isWorking = tag.getBoolean("IsWorking");
    }

    // ========== 交互与伤害 ==========

    @Override
    public @NotNull InteractionResult interact(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || !this.isAlive()) return false;

        if (source.getEntity() instanceof Player) {
            this.health -= amount;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1.0F, 1.0F);
            this.wobbleTicks = 10;
            this.wobbleDirection = this.random.nextBoolean() ? 5.0F : -5.0F;

            if (this.health <= 0.0F) {
                destroy();
            }
            return true;
        }
        return false;
    }

    protected void destroy() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            this.spawnAtLocation(getDropItem());

            PlowOxEntity ox = getOx();
            if (ox != null) {
                ox.setPlowEntityUUID(null);
            }
            onDetached();
            this.remove(RemovalReason.KILLED);
        }
    }

    public float getHealth() { return this.health; }
    public void setHealth(float health) { this.health = health; }

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return !this.isRemoved(); }

    protected void spawnBlockParticles(BlockPos pos, BlockState state) {
        if (!this.level().isClientSide) return;
        for (int i = 0; i < 30; i++) {
            double x = pos.getX() + this.level().random.nextDouble();
            double y = pos.getY() + this.level().random.nextDouble();
            double z = pos.getZ() + this.level().random.nextDouble();
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 0, 0, 0);
        }
    }

    // ==================== 绳子连接点 ====================

    public Vec3 getLeftRopeAttachPoint(float partialTick) {
        Vec3 offset = getRopeAttachmentOffset();
        double sideOffset = getLeftRopeSideOffset();
        return getInterpolatedPosition(partialTick).add(sideOffset, offset.y, 0);
    }

    public Vec3 getRightRopeAttachPoint(float partialTick) {
        Vec3 offset = getRopeAttachmentOffset();
        double sideOffset = getRightRopeSideOffset();
        return getInterpolatedPosition(partialTick).add(sideOffset, offset.y, 0);
    }

    protected Vec3 getInterpolatedPosition(float partialTick) {
        double x = this.xOld + (this.getX() - this.xOld) * partialTick;
        double y = this.yOld + (this.getY() - this.yOld) * partialTick;
        double z = this.zOld + (this.getZ() - this.zOld) * partialTick;
        return new Vec3(x, y, z);
    }

    // ==================== GeoAnimatable 接口实现 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 子类覆盖
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object o) {
        return tickCount;
    }
}