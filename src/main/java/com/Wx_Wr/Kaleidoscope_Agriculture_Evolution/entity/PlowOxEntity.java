package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.block.ModBlocks;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai.PlowAI;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlowOxEntity extends Cow implements GeoAnimatable {

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final PlowAI plowAI = new PlowAI(this.level());
    private PlowAI.Direction selectedPlowDir;

    // 绳子注册标记
    private boolean ropesCreated = false;
    // 犁的缓存
    private PlowEntity cachedPlow;

    public enum OxState {
        IDLE,
        FOLLOW,
        MOVE_TO_CORNER,
        SELECT_DIRECTION,
        MOVE_TO_START,
        START_PLOWING,
        PLOWING,
        FINISHING
    }

    private static final EntityDataAccessor<Integer> OX_STATE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> TARGET_CORNER =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> OTHER_CORNER =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> PLOW_PATH =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> PLOW_ENTITY_UUID =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID followOwnerUUID;
    private int startPlowingTick = 0;
    private int finishingTick = 0;
    private int pathSyncTimer = 0;

    public PlowOxEntity(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Cow.createAttributes();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0, Ingredient.of(ModItems.WHIP.get()), false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OX_STATE, OxState.IDLE.ordinal());
        this.entityData.define(TARGET_CORNER, Optional.empty());
        this.entityData.define(OTHER_CORNER, Optional.empty());
        this.entityData.define(PLOW_PATH, "");
        this.entityData.define(PLOW_ENTITY_UUID, Optional.empty());
    }

    // ==================== 状态机方法 ====================

    public OxState getOxState() {
        return OxState.values()[this.entityData.get(OX_STATE)];
    }

    public void setOxState(OxState state) {
        this.entityData.set(OX_STATE, state.ordinal());
    }

    public BlockPos getTargetCorner() {
        return this.entityData.get(TARGET_CORNER).orElse(null);
    }

    public void setTargetCorner(BlockPos pos) {
        this.entityData.set(TARGET_CORNER, Optional.ofNullable(pos));
    }

    public BlockPos getOtherCorner() {
        return this.entityData.get(OTHER_CORNER).orElse(null);
    }

    public void setOtherCorner(BlockPos pos) {
        this.entityData.set(OTHER_CORNER, Optional.ofNullable(pos));
    }

    public String getPlowPathData() {
        return this.entityData.get(PLOW_PATH);
    }

    public void setPlowPathData(String data) {
        this.entityData.set(PLOW_PATH, data);
    }

    public PlowAI.Direction getSelectedPlowDir() { return selectedPlowDir; }

    public UUID getFollowOwnerUUID() { return followOwnerUUID; }
    public void setFollowOwnerUUID(UUID uuid) { this.followOwnerUUID = uuid; }

    public UUID getPlowEntityUUID() {
        return this.entityData.get(PLOW_ENTITY_UUID).orElse(null);
    }

    public void setPlowEntityUUID(UUID uuid) {
        this.entityData.set(PLOW_ENTITY_UUID, Optional.ofNullable(uuid));
        if (uuid == null) {
            cachedPlow = null;
            ropesCreated = false;
        }
    }

    // ==================== 犁的管理（核心修复） ====================

    /**
     * 获取关联的犁实体 - 多重查找机制
     * 1. 检查缓存
     * 2. 通过保存的 UUID 查找
     * 3. 通过世界搜索找已绑定这头牛的犁（解决重进游戏问题）
     */
    public PlowEntity getPlowEntity() {
        UUID uuid = getPlowEntityUUID();

        // 1. 检查缓存
        if (cachedPlow != null && cachedPlow.isAlive()) {
            if (uuid == null || cachedPlow.getUUID().equals(uuid)) {
                return cachedPlow;
            }
        }

        // 2. 通过保存的 UUID 查找
        if (uuid != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof PlowEntity plow && plow.isAlive()) {
                cachedPlow = plow;
                return plow;
            }
        }

        // 3. 关键修复：通过世界搜索，找已经绑定这头牛的犁
        //    这解决了重进游戏时犁先存在但牛不知道的问题
        if (level() instanceof ServerLevel serverLevel) {
            for (PlowEntity plow : serverLevel.getEntitiesOfClass(PlowEntity.class, getBoundingBox().inflate(32))) {
                if (plow.isAlive() && this.getUUID().equals(plow.getOxUUID())) {
                    // 找到了！更新保存的 UUID
                    setPlowEntityUUID(plow.getUUID());
                    cachedPlow = plow;
                    return plow;
                }
            }
        }

        // 4. 找不到，清除缓存
        cachedPlow = null;
        return null;
    }

    /**
     * 确保犁存在 - 只在真的没有犁时创建
     */
    private void ensurePlowExists() {
        if (level().isClientSide) return;

        // 先尝试获取已有的犁（包括通过世界搜索）
        PlowEntity existingPlow = getPlowEntity();
        if (existingPlow != null) {
            // 犁已存在，更新绳子
            ensureRopesExist(existingPlow);
            return;
        }

        // 双重检查：再搜索一次更大范围（确保没有遗漏）
        if (level() instanceof ServerLevel serverLevel) {
            for (PlowEntity plow : serverLevel.getEntitiesOfClass(PlowEntity.class, getBoundingBox().inflate(64))) {
                if (plow.isAlive() && this.getUUID().equals(plow.getOxUUID())) {
                    // 找到了！更新 UUID 并返回
                    setPlowEntityUUID(plow.getUUID());
                    cachedPlow = plow;
                    ensureRopesExist(plow);
                    return;
                }
            }
        }

        // 真的没有犁，创建新犁
        spawnPlow();
    }

    private void spawnPlow() {
        if (level().isClientSide) return;

        // 最终检查：确保真的没有犁
        PlowEntity existingPlow = getPlowEntity();
        if (existingPlow != null) return;

        Kaleidoscope_Agriculture_Evolution.LOGGER.debug("Creating new plow for ox " + this.getUUID());

        PlowEntity plow = ModEntities.PLOW_ENTITY.get().create(level());
        if (plow != null) {
            plow.setPos(this.getX(), this.getY(), this.getZ());
            plow.setYRot(this.getYRot());
            plow.setYHeadRot(this.getYRot());
            level().addFreshEntity(plow);
            setPlowEntityUUID(plow.getUUID());
            cachedPlow = plow;
            plow.setOx(this);  // 这会保存牛的 UUID 到犁
        }
    }

    public void removePlow() {
        if (level().isClientSide) return;

        PlowEntity plow = getPlowEntity();
        if (plow != null) {
            ropesCreated = false;
            plow.setOx(null);
            plow.discard();
        }
        setPlowEntityUUID(null);
        cachedPlow = null;
        this.spawnAtLocation(new ItemStack(ModBlocks.PLOW.get().asItem()));
    }

    private void ensureRopesExist(PlowEntity plow) {
        if (!level().isClientSide && !ropesCreated && plow != null) {
            ropesCreated = true;
        }
    }

    public void startPlowing(PlowAI.Direction dir) {
        BlockPos target = getTargetCorner();
        BlockPos other = getOtherCorner();
        if (target == null || other == null) return;
        this.selectedPlowDir = dir;
        this.plowAI.start(target, other, dir);
        setOxState(OxState.MOVE_TO_START);
        setPlowPathData(plowAI.getPathData());
    }

    private void activatePlow() {
        PlowEntity plow = getPlowEntity();
        if (plow != null) {
            plow.setWorking(true);
        }
    }

    private void deactivatePlow() {
        PlowEntity plow = getPlowEntity();
        if (plow != null) {
            plow.setWorking(false);
        }
    }

    public static List<BlockPos> parsePlowPathData(String data) {
        List<BlockPos> points = new ArrayList<>();
        if (data.isEmpty()) return points;
        for (String entry : data.split(";")) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(",");
            if (parts.length != 3) continue;
            try {
                points.add(new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                ));
            } catch (NumberFormatException ignored) {}
        }
        return points;
    }

    // ==================== 主 Tick 逻辑 ====================

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide || !this.isAlive()) return;

        // 确保犁存在（会自动处理已有犁的查找）
        ensurePlowExists();

        // 只要不在犁地状态，就停掉犁（处理鞭子中途打断等外部状态切换）
        if (getOxState() != OxState.PLOWING) {
            PlowEntity plow = getPlowEntity();
            if (plow != null && plow.isWorking()) {
                plow.setWorking(false);
            }
        }

        // 状态机更新
        switch (getOxState()) {
            case FOLLOW -> tickFollow();
            case MOVE_TO_CORNER -> tickMoveToCorner();
            case SELECT_DIRECTION -> tickSelectDirection();
            case MOVE_TO_START -> tickMoveToStart();
            case START_PLOWING -> tickStartPlowing();
            case PLOWING -> tickPlowing();
            case FINISHING -> tickFinishing();
            default -> {}
        }
    }

    private void tickFollow() {
        if (this.followOwnerUUID == null) {
            setOxState(OxState.IDLE);
            return;
        }
        Player owner = this.level().getPlayerByUUID(this.followOwnerUUID);
        if (owner == null || owner.isSpectator()) {
            setOxState(OxState.IDLE);
            return;
        }
        // TemptGoal (FollowOwnerGoal) 负责实际跟随移动，这里只做状态校验
        if (this.distanceToSqr(owner) > 900.0) { // 超过 30 格放弃
            setOxState(OxState.IDLE);
        }
    }

    private void tickMoveToCorner() {
        BlockPos target = getTargetCorner();
        if (target == null) {
            setOxState(OxState.IDLE);
            return;
        }
        this.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);

        double dist = this.position().distanceTo(new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
        if (dist < 5.0) {
            setOxState(OxState.SELECT_DIRECTION);
        }
    }

    private void tickSelectDirection() {
        BlockPos target = getTargetCorner();
        if (target != null) {
            double dist = this.position().distanceTo(new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
            if (dist < 2.0) {
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                this.xxa = 0;
                this.zza = 0;
            }
        }
    }

    private void tickMoveToStart() {
        BlockPos target = plowAI.getTargetPos();
        if (target == null) {
            setOxState(OxState.IDLE);
            plowAI.reset();
            setPlowPathData("");
            return;
        }

        double targetX = target.getX() + 0.5;
        double targetY = target.getY();
        double targetZ = target.getZ() + 0.5;

        Vec3 dir = new Vec3(targetX - this.getX(), targetY - this.getY(), targetZ - this.getZ()).normalize();
        double speed = 0.1;

        // Jump assist: when target is higher, give upward velocity to step up
        if (targetY > this.getY() && this.onGround() && this.horizontalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.42, this.getDeltaMovement().z);
        } else {
            this.setDeltaMovement(dir.x * speed, this.getDeltaMovement().y, dir.z * speed);
        }

        float newYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float yawDiff = Mth.wrapDegrees(newYaw - this.getYRot());
        if (Math.abs(yawDiff) > 2.0f) {
            this.setYRot(this.getYRot() + yawDiff);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }

        if (this.position().distanceTo(new Vec3(targetX, targetY, targetZ)) < 0.1) {
            setOxState(OxState.START_PLOWING);
            startPlowingTick = 0;
        }
    }

    private void tickStartPlowing() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0;
        this.zza = 0;
        // Increase step height so the ox can walk up full blocks while plowing
        this.setMaxUpStep(1.0F);
        startPlowingTick++;
        // start_plow 动画长度 1.75 秒 = 35 ticks
        if (startPlowingTick > 35) {
            setOxState(OxState.PLOWING);
            activatePlow();
        }
    }

    private void tickPlowing() {
        BlockPos target = plowAI.getTargetPos();
        if (target == null) {
            setOxState(OxState.FINISHING);
            finishingTick = 0;
            plowAI.reset();
            setPlowPathData("");
            deactivatePlow();
            return;
        }

        double targetX = target.getX() + 0.5;
        double targetY = target.getY();
        double targetZ = target.getZ() + 0.5;

        Vec3 dir = new Vec3(targetX - this.getX(), targetY - this.getY(), targetZ - this.getZ()).normalize();
        double speed = 0.1;

        // Jump assist: when target is higher, give upward velocity to step up
        if (targetY > this.getY() && this.onGround() && this.horizontalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.42, this.getDeltaMovement().z);
        } else {
            this.setDeltaMovement(dir.x * speed, this.getDeltaMovement().y, dir.z * speed);
        }

        float newYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float yawDiff = Mth.wrapDegrees(newYaw - this.getYRot());
        if (Math.abs(yawDiff) > 2.0f) {
            this.setYRot(this.getYRot() + yawDiff);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
        }

        if (this.position().distanceTo(new Vec3(targetX, targetY, targetZ)) < 0.1) {
            plowAI.onReachedTarget();
        }

        // Sync remaining path to client every 20 ticks (1 second)
        pathSyncTimer++;
        if (pathSyncTimer >= 20) {
            pathSyncTimer = 0;
            setPlowPathData(plowAI.getPathData());
        }
    }

    private void tickFinishing() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0;
        this.zza = 0;
        // Restore default step height
        this.setMaxUpStep(0.6F);
        finishingTick++;
        if (finishingTick > 30) {
            setOxState(OxState.IDLE);
            finishingTick = 0;
        }
    }

    // ==================== 数据持久化 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("OxState", getOxState().name());
        if (followOwnerUUID != null) tag.putUUID("FollowOwner", followOwnerUUID);
        if (getTargetCorner() != null) tag.putLong("TargetCorner", getTargetCorner().asLong());
        if (getOtherCorner() != null) tag.putLong("OtherCorner", getOtherCorner().asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOxState(OxState.valueOf(tag.getString("OxState")));
        if (tag.hasUUID("FollowOwner")) this.followOwnerUUID = tag.getUUID("FollowOwner");
        if (tag.contains("TargetCorner")) setTargetCorner(BlockPos.of(tag.getLong("TargetCorner")));
        if (tag.contains("OtherCorner")) setOtherCorner(BlockPos.of(tag.getLong("OtherCorner")));
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(AnimationState<PlowOxEntity> state) {
        switch (getOxState()) {
            case PLOWING -> state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.plowing"));
            case START_PLOWING -> state.getController().setAnimation(RawAnimation.begin().then("animation.plowox.start_plow", Animation.LoopType.HOLD_ON_LAST_FRAME));
            case MOVE_TO_START -> {
                double speed = getDeltaMovement().horizontalDistance();
                if (speed > 0.005) state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.walk"));
                else state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.idle"));
            }
            case FINISHING -> state.getController().setAnimation(RawAnimation.begin().then("animation.plowox.stop_plow", Animation.LoopType.PLAY_ONCE));
            default -> {
                double speed = getDeltaMovement().horizontalDistance();
                if (speed > 0.15) state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.run"));
                else if (speed > 0.005) state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.walk"));
                else state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.idle"));
            }
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return this.tickCount;
    }

    // ==================== 绳子连接点 ====================

    private static final float OX_LEFT_X = -0.5625f;  // -9/16
    private static final float OX_RIGHT_X = 0.5625f;   // 9/16
    private static final float OX_Y = 0.90625f;        // 14.5/16
    private static final float OX_Z = -0.234375f;      // -3.75/16

    public Vec3 getLeftRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, OX_LEFT_X, OX_Y, OX_Z, partialTick);
    }

    public Vec3 getRightRopeAttachPoint(float partialTick) {
        return calculateAttachPoint(this, OX_RIGHT_X, OX_Y, OX_Z, partialTick);
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

    // ==================== 内部类：条件跟随 AI ====================

    /**
     * 只在 FOLLOW 状态下激活的 TemptGoal
     * 利用原版平滑的跟随移动 AI
     */
    private static class FollowOwnerGoal extends TemptGoal {
        private final PlowOxEntity ox;

        public FollowOwnerGoal(PlowOxEntity ox, double speed, Ingredient items, boolean canScare) {
            super(ox, speed, items, canScare);
            this.ox = ox;
        }

        @Override
        public boolean canUse() {
            return ox.getOxState() == OxState.FOLLOW && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return ox.getOxState() == OxState.FOLLOW && super.canContinueToUse();
        }
    }
}