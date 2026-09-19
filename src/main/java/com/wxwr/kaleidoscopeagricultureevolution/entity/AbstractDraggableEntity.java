package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
@SuppressWarnings({"deprecation", "removal"})
public abstract class AbstractDraggableEntity extends Entity implements GeoAnimatable {

    // ========== 数据同步键 ==========
    protected static final EntityDataAccessor<Optional<UUID>> OX_UUID =
            SynchedEntityData.defineId(AbstractDraggableEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 牵引速度修正的 UUID */
    private static final UUID PULL_SPEED_MODIFIER_UUID =
            UUID.fromString("49B0E52E-48F2-4D89-BED7-4F5DF26F1263");
    private static final int GROUND_SCAN_UP = 2;
    private static final int GROUND_SCAN_DOWN = 6;
    private static final double MAX_GROUND_Y_ADJUST_PER_TICK = 0.35D;

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
    @SuppressWarnings("removal")
    public AbstractDraggableEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setMaxUpStep(1.0f);
        // 拖曳需要真实物理（碰撞 + 重力）
        this.noPhysics = false;
    }

    // ========== 抽象方法 ==========
    protected abstract void onDraggerMove();
    protected abstract ItemStack getDropItem();

    // ========== 工作区域校验 ==========

    /** 检查给定坐标是否在牛的工作区域内（两个对角点围成的矩形） */
    protected boolean isInWorkArea(PlowOxEntity ox, BlockPos pos) {
        BlockPos c1 = ox.getTargetCorner();
        BlockPos c2 = ox.getOtherCorner();
        if (c1 == null || c2 == null) return false;
        int minX = Math.min(c1.getX(), c2.getX());
        int maxX = Math.max(c1.getX(), c2.getX());
        int minZ = Math.min(c1.getZ(), c2.getZ());
        int maxZ = Math.max(c1.getZ(), c2.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    // ========== 可被子类覆盖的参数 ==========
    protected double getFollowDistance() { return 1.0; }
    protected double getMaxBreakDistance() { return 4.0; }
    protected double getMinWorkSpeed() { return 0.08; }
    protected net.minecraft.sounds.SoundEvent getMovingSound() { return SoundEvents.WOOD_PLACE; }
    protected boolean shouldSnapToGround() { return true; }

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
     * 设置牵引的牛。
     * 会自动管理牛的速度属性修正。
     */
    public void setOx(@Nullable PlowOxEntity ox) {
        // 从旧牛移除速度修正
        if (this.ox != null && this.ox.isAlive()) {
            var attr = this.ox.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.removeModifier(PULL_SPEED_MODIFIER_UUID);
            }
        }
        // 解绑旧牛时销毁绳子
        if (this.ox != null && ox == null) {
            onDetached();
        }

        this.ox = ox;
        if (ox != null) {
            this.entityData.set(OX_UUID, Optional.of(ox.getUUID()));
            onAttached();
            // 给新牛施加速度修正
            double modifier = Config.PULL_SPEED_MODIFIER;
            if (modifier != 0.0) {
                var attr = ox.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr != null && attr.getModifier(PULL_SPEED_MODIFIER_UUID) == null) {
                    attr.addTransientModifier(new AttributeModifier(
                            PULL_SPEED_MODIFIER_UUID,
                            "Pull speed modifier",
                            modifier,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            }
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

    @Nullable
    public UUID getOxUUID() {
        return this.entityData.get(OX_UUID).orElse(null);
    }

    public void setWorking(boolean working) { this.isWorking = working; }
    public boolean isWorking() { return this.isWorking; }

    /** 子类覆盖：是否需要渲染绳子。耧车等不需要。 */
    public boolean shouldRenderRope() { return true; }

    // ========== 核心牵引逻辑 ==========

    @Override
    public void tick() {
        super.tick();
        this.tickLerp();

        PlowOxEntity ox = getOx();
        if (ox == null && !this.level().isClientSide) {
            this.discard();
            return;
        }
        // 物理由工具自身驱动，以避免扫描所有世界实体。
        if (!this.level().isClientSide) {
            pulledTick();
        }
        handleMovementSound();
        handleWobble();
    }

    // ========== 死区物理 ==========

    /**
     * 死区拖曳物理的每 tick 更新。
     */
    public void pulledTick() {
        PlowOxEntity ox = getOx();
        if (ox == null || !ox.isAlive()) {
            this.discard();
            return;
        }

        // 检查最大断裂距离
        double distanceSq = this.distanceToSqr(ox);
        double maxBreakSq = getMaxBreakDistance() * getMaxBreakDistance();
        if (distanceSq > maxBreakSq) {
            ox.removeTool();
            this.discard();
            return;
        }

        // 1. targetVec + handleRotation
        Vec3 targetVec = getRelativeTargetVec(ox);
        handleRotation(targetVec);
        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }
        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        // 2. 牛在地面时归零 Y 分量
        if (ox.onGround()) {
            targetVec = new Vec3(targetVec.x, 0.0D, targetVec.z);
        }

        // 3. 间距 + 死区 + 速度
        final double targetVecLength = targetVec.length();
        final double r = Config.DEAD_ZONE_RADIUS;
        final double relativeSpacing = Math.max(getFollowDistance() + 0.5D * ox.getBbWidth(), 1.0D);
        final double diff = targetVecLength - relativeSpacing;
        final Vec3 move;
        if (Math.abs(diff) < r) {
            move = this.getDeltaMovement();
        } else {
            move = this.getDeltaMovement().add(
                targetVec.subtract(targetVec.normalize().scale(relativeSpacing + r * Math.signum(diff))));
        }

        // 4. move
        this.onGround();
        double prevX = this.getX();
        double prevZ = this.getZ();
        Vec3 actualMove = ox.onGround() ? new Vec3(move.x, 0.0D, move.z) : move;
        this.move(MoverType.SELF, actualMove);
        alignToGround();
        if (!this.isAlive()) return;

        // 5. 服务端复查脱钩
        if (!this.level().isClientSide) {
            targetVec = getRelativeTargetVec(ox);
            if (targetVec.length() > relativeSpacing + 1.0D) {
                ox.removeTool();
                this.discard();
                return;
            }
        }

        // === 项目特有: 工作触发 ===
        double actualDist = Math.sqrt(
                (this.getX() - prevX) * (this.getX() - prevX) +
                        (this.getZ() - prevZ) * (this.getZ() - prevZ));
        if (isWorking && actualDist > getMinWorkSpeed()) {
            onDraggerMove();
        }
    }

    private void alignToGround() {
        if (!shouldSnapToGround()) return;

        double targetY = findGroundSurfaceY();
        if (Double.isNaN(targetY)) return;

        double dy = targetY - this.getY();
        if (Math.abs(dy) < 0.001D) {
            this.setOnGround(true);
            return;
        }

        double step = Mth.clamp(dy, -MAX_GROUND_Y_ADJUST_PER_TICK, MAX_GROUND_Y_ADJUST_PER_TICK);
        this.move(MoverType.SELF, new Vec3(0.0D, step, 0.0D));
        this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        if (Math.abs(targetY - this.getY()) < 0.05D) {
            this.setOnGround(true);
        }
    }

    private double findGroundSurfaceY() {
        int x = Mth.floor(this.getX());
        int z = Mth.floor(this.getZ());
        int top = Mth.floor(this.getY()) + GROUND_SCAN_UP;
        int bottom = Math.max(
                this.level().getMinBuildHeight(),
                Mth.floor(this.getY()) - GROUND_SCAN_DOWN);

        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = this.level().getBlockState(pos);
            var shape = state.getCollisionShape(this.level(), pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    /**
     * 计算从工具指向牛的相对方向向量（含侧向偏移）。
     * getRelativeTargetVec()
     */
    private Vec3 getRelativeTargetVec(PlowOxEntity ox) {
        final double x = ox.getX() - this.getX();
        final double y = ox.getY() - this.getY();
        final double z = ox.getZ() - this.getZ();
        final float oxYaw = (float) Math.toRadians(ox.getYRot());
        final float nx = -Mth.sin(oxYaw);
        final float nz = Mth.cos(oxYaw);
        final double r = Config.DEAD_ZONE_RADIUS; 
        return new Vec3(x + nx * r, y, z + nz * r);
    }

    /**
     * 瞬时设置旋转朝向目标向量。
     * handleRotation()
     */
    private void handleRotation(final Vec3 target) {
        this.setYRot(getYaw(target));
        this.setXRot(getPitch(target));
    }

    /** 从方向向量计算 yaw。getYaw() */
    static float getYaw(final Vec3 vec) {
        return Mth.wrapDegrees((float) Math.toDegrees(-Mth.atan2(vec.x, vec.z)));
    }

    /** 从方向向量计算 pitch。getPitch() */
    static float getPitch(final Vec3 vec) {
        return Mth.wrapDegrees((float) Math.toDegrees(
                -Mth.atan2(vec.y, Mth.sqrt((float) (vec.x * vec.x + vec.z * vec.z)))));
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

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            PlowOxEntity currentOx = getOx();
            if (currentOx != null && currentOx.isAlive()) {
                var attr = currentOx.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr != null) {
                    attr.removeModifier(PULL_SPEED_MODIFIER_UUID);
                }
            }
        }
        super.remove(reason);
    }

    protected void destroy() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            this.spawnAtLocation(getDropItem());

            PlowOxEntity ox = getOx();
            if (ox != null) {
                ox.setToolEntityUUID(null);
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
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean canCollideWith(Entity other) { return false; }

    @Override
    public boolean isPushable() { return false; }

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
