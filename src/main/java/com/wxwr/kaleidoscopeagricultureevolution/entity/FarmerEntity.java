package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.mojang.serialization.Dynamic;
import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueReason;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

import java.util.Optional;
import java.util.UUID;

public class FarmerEntity extends Villager implements GeoAnimatable {

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final FarmerSupplyManager supplyManager = new FarmerSupplyManager();
    private final FarmerWorkManager workManager = new FarmerWorkManager();
    private final FarmerBrainController brainController = new FarmerBrainController();

    // ==================== 状态机 ====================
    // 参考 TouhouLittleMaid BrainAI：
    //   MOVING_TO_TARGET - 牛在 SELECT_DIRECTION/MOVE_TO_CORNER/START_WORKING 时，农夫赶往目标区域
    //   WORKING          - 牛在 WORKING 时，农夫保持在目标区域内
    //   IDLE             - 其余时间还原原职业，村民 Brain 自主运作

    public enum MovementState {
        IDLE,
        MOVING_TO_TARGET,
        WORKING
    }

    /** 箱子交互阶段（FETCHING/STORING 已被 {@link ChestInteractionTask} 替代）。 */
    enum ChestTask {
        NONE,       // 未找到箱子或无任务
        TO_FETCH,   // 前往箱子取饲料
        FETCHED,    // 已取完，前往牛身边工作
        TO_STORE    // 前往箱子存饲料
    }

    private static final EntityDataAccessor<Optional<UUID>> BOUND_OX_UUID =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> ORIGINAL_PROFESSION =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> MOVEMENT_STATE =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ORIGINAL_ENTITY_TYPE =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CHEST_TASK =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);
    /** 客户端动画标记：箱子交互正在进行中（替代 FETCHING/STORING 的客户端可见性）。 */
    private static final EntityDataAccessor<Boolean> CHEST_INTERACTING =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> WORK_ISSUE_STATE =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> WORK_ISSUE_REASON =
            SynchedEntityData.defineId(FarmerEntity.class, EntityDataSerializers.STRING);

    private MovementState preIssueMovementState = MovementState.IDLE;
    private ChestTask preIssueChestTask = ChestTask.NONE;

    /**
     * 目标区域：牛后方偏右 15 度、半径 2 格的扇形。
     */
    private static final double TARGET_DISTANCE = 2.0;
    private static final double FAN_HALF_ANGLE = 15.0;   // 正负 15 度
    private static final double BEHIND_OFFSET_DEG = 195.0; // yaw + 180 + 15 = 牛后方偏右 15 度
    private static final double RUN_DISTANCE = 5.0;
    private static final double WALK_SPEED = 0.12;
    private static final double RUN_SPEED = 0.20;
    private static final double ARRIVE_THRESHOLD = 0.3;

    // ==================== 箱子存取 ====================

    ChestTask getChestTask() {
        return readChestTask(this.entityData.get(CHEST_TASK));
    }

    void setChestTask(ChestTask task) {
        this.entityData.set(CHEST_TASK, task.name());
    }

    static ChestTask readChestTask(String name) {
        try {
            return ChestTask.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ChestTask.NONE;
        }
    }

    void setChestInteracting(boolean interacting) {
        this.entityData.set(CHEST_INTERACTING, interacting);
    }

    // ==================== 构造 & 属性 ====================

    public FarmerEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        this.setVillagerData(this.getVillagerData().setProfession(VillagerProfession.FARMER));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes();
    }

    @Override
    public MerchantOffers getOffers() {
        return new MerchantOffers();
    }

    @Override
    protected void updateTrades() {
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.provider(FarmerBrain.getMemoryTypes(), FarmerBrain.getSensorTypes());
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamicIn) {
        Brain<Villager> brain = this.brainProvider().makeBrain(dynamicIn);
        FarmerBrain.registerBrainGoals(brain);
        return brain;
    }

    @Override
    protected void registerGoals() {
        // 不继承 Villager 的 AI goals（MoveThroughVillage 等）。
        // 避免与原版 Brain / goalSelector 竞争移动控制。
        // 所有自定义 AI 由 customServerAiStep() 统一处理。
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BOUND_OX_UUID, Optional.empty());
        this.entityData.define(ORIGINAL_PROFESSION, VillagerProfession.FARMER.name());
        this.entityData.define(MOVEMENT_STATE, MovementState.IDLE.name());
        this.entityData.define(ORIGINAL_ENTITY_TYPE, "");
        this.entityData.define(CHEST_TASK, ChestTask.NONE.name());
        this.entityData.define(CHEST_INTERACTING, false);
        this.entityData.define(WORK_ISSUE_STATE, WorkIssueState.NORMAL.name());
        this.entityData.define(WORK_ISSUE_REASON, WorkIssueReason.NONE.name());
    }

    // ==================== 物品拾取与交互 ====================

    @Override
    public boolean canPickUpLoot() {
        return true; // 任何状态下都可以捡东西
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (LoucheEntity.isSeedItem(stack.getItem())) return true; // 拾取所有有效种子
        if (stack.is(Items.WHEAT)) return true;
        if (stack.is(Items.SUSPICIOUS_STEW) && PlowOxEntity.hasSaturationEffect(stack)) return true;
        return super.wantsToPickUp(stack);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack handItem = player.getItemInHand(hand);
        SimpleContainer inv = this.getInventory();

        if (handItem.getItem() instanceof WhipItem && getWorkIssueState() == WorkIssueState.ERROR) {
            if (!level().isClientSide) {
                exitWorkError();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (handItem.isEmpty() && getWorkIssueState() == WorkIssueState.WARN) {
            if (!level().isClientSide) {
                supplyManager.tryStartWarnRecovery(this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        // 拿着小麦、饱和炖菜或种子右键：存入背包
        if (handItem.is(Items.WHEAT)
                || LoucheEntity.isSeedItem(handItem.getItem())
                || (handItem.is(Items.SUSPICIOUS_STEW) && PlowOxEntity.hasSaturationEffect(handItem))) {
            if (inv.canAddItem(handItem)) {
                if (!level().isClientSide) {
                    ItemStack copy = handItem.copy();
                    copy.setCount(1);
                    inv.addItem(copy);
                    if (!player.isCreative()) handItem.shrink(1);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        // 禁用交易界面：不是存入物品操作也直接消费事件，不打开 GUI
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ==================== 职业 & 还原信息管理 ====================

    /**
     * 通过注册表 key 设置原始职业（PlowOxEntity 转化的入口）。
     */
    public void setOriginalProfession(String registryKey) {
        this.entityData.set(ORIGINAL_PROFESSION, registryKey);
    }

    /**
     * 获取原始职业（还原用），NONE/NITWIT 照常返回。
     */
    private VillagerProfession getOriginalProfession() {
        String key = this.entityData.get(ORIGINAL_PROFESSION);
        if (key.isEmpty()) return VillagerProfession.FARMER;
        VillagerProfession prof = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(
                KaleidoscopeAgricultureEvolution.id(key));
        return prof != null ? prof : VillagerProfession.FARMER;
    }

    public String getOriginalProfessionKey() {
        String key = this.entityData.get(ORIGINAL_PROFESSION);
        return key.isEmpty() ? "minecraft:farmer" : key;
    }

    public void setOriginalEntityType(String type) {
        this.entityData.set(ORIGINAL_ENTITY_TYPE, type);
    }

    public String getOriginalEntityType() {
        String t = this.entityData.get(ORIGINAL_ENTITY_TYPE);
        return t.isEmpty() ? "minecraft:villager" : t;
    }

    /**
     * 动态切换职业：工作时切为农民，空闲时还原。
     */
    private void applyProfession(boolean working) {
        VillagerProfession target = working ? VillagerProfession.FARMER : getOriginalProfession();
        VillagerData data = this.getVillagerData();
        if (data.getProfession() != target) {
            super.setVillagerData(data.setProfession(target));
        }
    }

    /**
     * 新创建时强制职业为农民（绕过 setVillagerData override 的状态检查）。
     * 由 {@link PlowOxEntity#tryConvertBoundToFarmer()} 在 finalizeSpawn 之后调用，
     * 防止因 MOVEMENT_STATE 默认为 IDLE 导致交易界面立即显示原始职业。
     */
    void forceProfessionFarmer() {
        VillagerData data = this.getVillagerData();
        if (data.getProfession() != VillagerProfession.FARMER) {
            super.setVillagerData(data.setProfession(VillagerProfession.FARMER));
        }
    }

    /**
     * 仅锁定 setVillagerData 被外部调用时的职业，但允许内部通过 applyProfession 自由切换。
     */
    @Override
    public void setVillagerData(VillagerData data) {
        // 外部调用（如 Brain 初始化）：始终写回当前应显示的 profession
        MovementState state = getMovementState();
        VillagerProfession wanted = (state == MovementState.IDLE) ? getOriginalProfession() : VillagerProfession.FARMER;
        super.setVillagerData(data.setProfession(wanted));
    }

    // ==================== 状态机 ====================

    public MovementState getMovementState() {
        try {
            return MovementState.valueOf(this.entityData.get(MOVEMENT_STATE));
        } catch (IllegalArgumentException e) {
            return MovementState.IDLE;
        }
    }

    private void setMovementState(MovementState state) {
        MovementState old = getMovementState();
        this.entityData.set(MOVEMENT_STATE, state.name());
    }

    public WorkIssueState getWorkIssueState() {
        try {
            return WorkIssueState.valueOf(this.entityData.get(WORK_ISSUE_STATE));
        } catch (IllegalArgumentException e) {
            return WorkIssueState.NORMAL;
        }
    }

    public WorkIssueReason getWorkIssueReason() {
        try {
            return WorkIssueReason.valueOf(this.entityData.get(WORK_ISSUE_REASON));
        } catch (IllegalArgumentException e) {
            return WorkIssueReason.NONE;
        }
    }

    void enterWorkIssue(WorkIssueState state, WorkIssueReason reason) {
        if (state == WorkIssueState.NORMAL) {
            clearWorkIssue();
            return;
        }
        if (getWorkIssueState() == WorkIssueState.NORMAL) {
            this.preIssueMovementState = getMovementState();
            this.preIssueChestTask = getChestTask();
        }
        this.entityData.set(WORK_ISSUE_STATE, state.name());
        this.entityData.set(WORK_ISSUE_REASON, reason.name());
        if (state == WorkIssueState.ERROR) {
            stopMovement();
            this.getNavigation().stop();
            FarmerBrainController.clearVolatileMemories(this.getBrain());
        }
    }

    void clearWorkIssue() {
        this.entityData.set(WORK_ISSUE_STATE, WorkIssueState.NORMAL.name());
        this.entityData.set(WORK_ISSUE_REASON, WorkIssueReason.NONE.name());
    }

    private void exitWorkError() {
        if (getWorkIssueState() != WorkIssueState.ERROR) {
            return;
        }
        clearWorkIssue();
        setChestTask(preIssueChestTask);
        setMovementState(preIssueMovementState);
    }

    private MovementState computeMovementState(FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) return MovementState.IDLE;
        if (context.supplyTravelling()) return MovementState.MOVING_TO_TARGET;

        // 牛空闲/等待：农夫空闲
        PlowOxEntity.OxState oxState = context.oxState();
        if (OxStateRules.farmerShouldIdle(oxState)) {
            return MovementState.IDLE;
        }

        // 跟随阶段（尚未确定工作区域）：赶路
        if (oxState == PlowOxEntity.OxState.SELECT_CORNER
                || oxState == PlowOxEntity.OxState.SELECT_DIRECTION) {
            return MovementState.MOVING_TO_TARGET;
        }

        // 箱子任务进行中：保持赶路状态
        if (context.supplyTravelling()) {
            return MovementState.MOVING_TO_TARGET;
        }

        // 箱子任务完成（或没有箱子）：检查是否到达目标区域
        if (context.inTargetArea()) {
            return MovementState.WORKING;
        }
        return MovementState.MOVING_TO_TARGET;
    }

    // ==================== 绑定管理 ====================

    @Nullable
    public UUID getBoundOxUUID() {
        return this.entityData.get(BOUND_OX_UUID).orElse(null);
    }

    public void setBoundOxUUID(@Nullable UUID uuid) {
        this.entityData.set(BOUND_OX_UUID, Optional.ofNullable(uuid));
    }

    public boolean isBound() {
        return getBoundOxUUID() != null;
    }

    @Nullable
    public PlowOxEntity getBoundOx() {
        UUID uuid = getBoundOxUUID();
        if (uuid == null) return null;
        if (level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof PlowOxEntity ox && ox.isAlive()) return ox;
        }
        if (!level().isClientSide && uuid != null) setBoundOxUUID(null);
        return null;
    }

    // ==================== 目标区域 ====================

    /**
     * 目标区域圆心：牛后方偏右 15 度、距离 TARGET_DISTANCE 格处。
     */
    private Vec3 getTargetAreaCenter(PlowOxEntity ox) {
        double angle = Math.toRadians(ox.getYRot() + BEHIND_OFFSET_DEG);
        double tx = ox.getX() - Math.sin(angle) * TARGET_DISTANCE;
        double tz = ox.getZ() + Math.cos(angle) * TARGET_DISTANCE;
        return new Vec3(tx, ox.getY(), tz);
    }

    BlockPos targetAreaBlock(PlowOxEntity ox) {
        return BlockPos.containing(getTargetAreaCenter(ox));
    }

    /**
     * 农夫是否在目标扇形区域内。
     * 参照角度与 {@link #getTargetAreaCenter} 保持一致，使用
     * {@code BEHIND_OFFSET_DEG - 15.0 = 180}（牛正后方）作为扇形中心，
     * 容忍范围为正负（{@link #FAN_HALF_ANGLE} + 5 度）。
     */
    boolean isInTargetArea(PlowOxEntity ox) {
        Vec3 toMe = this.position().subtract(ox.position());
        double dist = toMe.horizontalDistance();
        if (dist > TARGET_DISTANCE + 0.3) return false;

        double angleToMe = Math.toDegrees(Math.atan2(-toMe.x, toMe.z));
        // 扇形中心 = 牛正后方 (yaw + 180 度)，与 getTargetAreaCenter 的 195 度偏右中心
        // 保持正负 20 度覆盖范围，确保 195 度的目标点在此范围内
        double behindAngle = ox.getYRot() + 180.0;
        double diff = Mth.wrapDegrees(angleToMe - behindAngle);
        return Math.abs(diff) < FAN_HALF_ANGLE + 5.0; // 正负 20 度
    }

    // ==================== 主 Tick ====================

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
        }
        if (!this.level().isClientSide && this.isAlive() && this.getBrain() == null) {
            this.brain = this.makeBrain(new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
        }
        if (!this.level().isClientSide && this.isAlive()) {
            if (this.tickCount % 40 == 0) {
            }
            brainController.bootstrap(this);
        }
        super.tick();
    }

    @Override
    protected void customServerAiStep() {
        FarmerContext context = FarmerContext.capture(this, supplyManager);
        PlowOxEntity ox = context.ox();
        MovementState state = computeMovementState(context);
        setMovementState(state);

        if (ox == null) {
            supplyManager.cancel(this);
            setChestTask(ChestTask.NONE);
            selfRevertToOriginal();
            return;
        }

        if (getWorkIssueState() == WorkIssueState.ERROR) {
            supplyManager.cancel(this);
            stopMovement();
            FarmerBrainController.clearVolatileMemories(this.getBrain());
            applyProfession(true);
            return;
        }

        applyProfession(state != MovementState.IDLE);
        context = FarmerContext.capture(this, supplyManager);
        brainController.update(this, context, state);

        if (state == MovementState.IDLE && !context.supplyNeedsStore()) {
        }

        boolean brainManaged = state != MovementState.IDLE
                || getChestTask() == ChestTask.TO_STORE
                || context.supplyNeedsStore();
        if (brainManaged && this.level() instanceof ServerLevel serverLevel) {
            brainController.tick(this, serverLevel);
        }

        if (brainManaged) {
            return;
        }

        super.customServerAiStep();
    }

    // ==================== MOVING_TO_TARGET ====================

    void tickMoveToTarget(PlowOxEntity ox) {
        Vec3 center = getTargetAreaCenter(ox);

        if (isInTargetArea(ox)) {
            // 已在扇形区域内：微调至圆心后面停止
            // 与 WORKING 逻辑统一：距中心 < ARRIVE_THRESHOLD 则完全停止，
            // 否则以低速靠近，防止全速冲入、漂出、再冲入的振荡循环
            Vec3 diff = center.subtract(this.position());
            if (diff.horizontalDistance() < ARRIVE_THRESHOLD) {
                stopMovement();
            } else {
                applyMovement(diff.normalize(), WALK_SPEED * 0.5);
            }
            return;
        }

        // 尚未到达：全速赶往
        Vec3 dir = center.subtract(this.position());
        double dist = dir.horizontalDistance();
        double speed = dist > RUN_DISTANCE ? RUN_SPEED : WALK_SPEED;
        dir = dir.normalize();
        applyMovement(dir, speed);
    }

    // ==================== WORKING ====================

    void tickWorking(FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) return;
        Vec3 center = getTargetAreaCenter(ox);

        // 在区域内时尝试喂食加速并补种耧车
        if (context.inTargetArea()) {
            workManager.tick(this, context);
            Vec3 diff = center.subtract(this.position());
            if (diff.horizontalDistance() < 0.15) {
                stopMovement();
            } else {
                applyMovement(diff.normalize(), WALK_SPEED * 0.5);
            }
        } else {
            // 偏离了：走回去
            Vec3 dir = center.subtract(this.position()).normalize();
            applyMovement(dir, WALK_SPEED);
        }
    }

    /** 是否还有未完成的箱子相关工作（牛还原农民前检查）。 */
    boolean needsStore() {
        return FarmerContext.capture(this, supplyManager).supplyNeedsStore();
    }

    boolean shouldStayConvertedDuringOxIdle() {
        ChestTask task = getChestTask();
        return needsStore()
                || task == ChestTask.TO_FETCH
                || supplyManager.isWarnRecoveryReturning(this)
                || getWorkIssueState() != WorkIssueState.NORMAL;
    }

    void tickIdleFinish(FarmerContext context) {
        PlowOxEntity ox = context.ox();
        if (ox == null) {
            return;
        }
        supplyManager.retrieveLoucheInventory(this, context);
        if (supplyManager.isWorkDone() && getChestTask() == ChestTask.NONE
                && (ox.getOxState() == PlowOxEntity.OxState.IDLE
                    || ox.getOxState() == PlowOxEntity.OxState.WAITING)) {
            ox.tryRevertFarmerToOriginal();
            supplyManager.clearWorkDone();
        }
    }

    @Nullable
    BlockPos supplyChestPos() {
        return supplyManager.chestPos();
    }

    boolean isAtSupplyChest() {
        return supplyManager.isAtChest(this);
    }

    boolean tryStartFetchInteraction() {
        boolean started = supplyManager.tryStartFetchInteraction(this);
        return started;
    }

    boolean tryStartStoreInteraction() {
        boolean started = supplyManager.tryStartStoreInteraction(this);
        return started;
    }

    boolean isSupplyInteracting() {
        return supplyManager.isInteracting();
    }

    FarmerSupplyManager supplyManager() {
        return supplyManager;
    }

    void syncBrainContext(FarmerContext context, MovementState state) {
        brainController.update(this, context, state);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        supplyManager.cancel(this); // 防御深度：无论如何丢弃农民时都确保箱子被关闭
        super.remove(reason);
    }

    // ==================== 移动辅助 ====================

    private void applyMovement(Vec3 dir, double speed) {
        this.setDeltaMovement(dir.x * speed, this.getDeltaMovement().y, dir.z * speed);

        if (this.onGround() && this.horizontalCollision &&
                getTargetAreaCenter(getBoundOx()).y > this.getY()) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.42, this.getDeltaMovement().z);
        }

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float diff = Mth.wrapDegrees(targetYaw - this.getYRot());
        this.setYRot(this.getYRot() + Mth.clamp(diff, -10f, 10f));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        this.setXRot(0);
    }

    private void stopMovement() {
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0;
        this.zza = 0;
        this.yHeadRot = this.yBodyRot; // 停止时也锁头朝向身体方向
        this.setXRot(0);
        // 同时停止原版移动控制器，防止 Mob.serverAiStep() 中
        // MoveControl.tick() 重新设置 xxa/zza 覆盖我们的停止指令
        this.getMoveControl().setWantedPosition(
                this.getX(), this.getY(), this.getZ(), 0.0);
    }

    // ==================== 数据持久化 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID boundId = getBoundOxUUID();
        if (boundId != null) tag.putUUID("BoundOx", boundId);
        tag.putString("OriginalProfession", this.entityData.get(ORIGINAL_PROFESSION));
        String ot = this.entityData.get(ORIGINAL_ENTITY_TYPE);
        if (!ot.isEmpty()) tag.putString("OriginalEntityType", ot);
        // 移动状态
        tag.putString("MovementState", this.entityData.get(MOVEMENT_STATE));
        tag.putString("WorkIssueState", this.entityData.get(WORK_ISSUE_STATE));
        tag.putString("WorkIssueReason", this.entityData.get(WORK_ISSUE_REASON));
        tag.putString("PreIssueMovementState", this.preIssueMovementState.name());
        tag.putString("PreIssueChestTask", this.preIssueChestTask.name());
        // 背包持久化
        tag.put("Inventory", this.getInventory().createTag());
        supplyManager.save(tag, this);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // 必须在 super 之前恢复 MOVEMENT_STATE：Villager 的反序列化会调用
        // setVillagerData()，其 override 依赖 getMovementState() 来判断职业
        if (tag.contains("MovementState"))
            this.entityData.set(MOVEMENT_STATE, tag.getString("MovementState"));
        if (tag.contains("WorkIssueState"))
            this.entityData.set(WORK_ISSUE_STATE, tag.getString("WorkIssueState"));
        if (tag.contains("WorkIssueReason"))
            this.entityData.set(WORK_ISSUE_REASON, tag.getString("WorkIssueReason"));
        if (tag.contains("PreIssueMovementState")) {
            try {
                this.preIssueMovementState = MovementState.valueOf(tag.getString("PreIssueMovementState"));
            } catch (IllegalArgumentException ignored) {
                this.preIssueMovementState = MovementState.IDLE;
            }
        }
        if (tag.contains("PreIssueChestTask")) {
            this.preIssueChestTask = readChestTask(tag.getString("PreIssueChestTask"));
        }
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("BoundOx")) setBoundOxUUID(tag.getUUID("BoundOx"));
        if (tag.contains("OriginalProfession"))
            this.entityData.set(ORIGINAL_PROFESSION, tag.getString("OriginalProfession"));
        if (tag.contains("OriginalEntityType"))
            this.entityData.set(ORIGINAL_ENTITY_TYPE, tag.getString("OriginalEntityType"));
        // 移动状态已在上方恢复，跳过重复读取
        // 背包持久化
        if (tag.contains("Inventory"))
            this.getInventory().fromTag(tag.getList("Inventory", 10));
        supplyManager.load(tag, this);
    }

    // ==================== 自我还原（耕牛死亡时调用） ====================

    /**
     * 耕牛已死亡时，自行还原为原始村民/流浪商人实体。
     * 与 {@link PlowOxEntity#tryRevertFarmerToOriginal()} 逻辑一致，
     * 但不更新耕牛的绑定 UUID（耕牛已不存在）。
     */
    private void selfRevertToOriginal() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (!isAlive()) return;

        String origType = getOriginalEntityType();
        EntityType<?> revertType = switch (origType) {
            case "minecraft:wandering_trader" -> EntityType.WANDERING_TRADER;
            default -> EntityType.VILLAGER;
        };

        LivingEntity reverted = (LivingEntity) revertType.create(serverLevel);
        if (reverted == null) return;

        reverted.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        reverted.setYRot(getYRot());
        reverted.setXRot(getXRot());
        reverted.yBodyRot = yBodyRot;
        reverted.yHeadRot = yHeadRot;
        reverted.setHealth(getHealth());
        if (reverted instanceof AgeableMob ageable) ageable.setAge(getAge());
        if (hasCustomName()) {
            reverted.setCustomName(getCustomName());
            reverted.setCustomNameVisible(isCustomNameVisible());
        }

        // 还原职业
        if (reverted instanceof Villager villager) {
            String profKey = getOriginalProfessionKey();
            VillagerProfession prof = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(
                    KaleidoscopeAgricultureEvolution.id(profKey));
            if (prof != null) {
                villager.setVillagerData(villager.getVillagerData().setProfession(prof));
            }
        }

        // 清理 persistentData 中的耕牛标记（如果有的话）
        reverted.getPersistentData().remove("KAE_BoundOx");

        discard();
        serverLevel.addFreshEntity(reverted);
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(software.bernie.geckolib.core.animation.AnimationState<FarmerEntity> state) {
        // 开/关箱子动画优先
        if (this.entityData.get(CHEST_INTERACTING)) {
            state.getController().setAnimation(
                    RawAnimation.begin().then("animation.farmer.open_chest",
                            Animation.LoopType.HOLD_ON_LAST_FRAME));
            return PlayState.CONTINUE;
        }

        MovementState ms = getMovementState();
        double speed = getDeltaMovement().horizontalDistance();

        switch (ms) {
            case WORKING -> {
                if (speed > 0.15) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.run"));
                } else if (speed > 0.005) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.walk"));
                } else {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.idle"));
                }
            }
            case MOVING_TO_TARGET -> {
                if (speed > 0.15) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.run"));
                } else if (speed > 0.005) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.walk"));
                } else {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.idle"));
                }
            }
            default -> { // IDLE
                if (speed > 0.15) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.run"));
                } else if (speed > 0.005) {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.walk"));
                } else {
                    state.getController().setAnimation(
                            RawAnimation.begin().thenLoop("animation.farmer.idle"));
                }
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
}
