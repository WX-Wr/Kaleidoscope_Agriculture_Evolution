package com.wxwr.kaleidoscopeagricultureevolution.entity;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowAI;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowWorkPlan;
import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueReason;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings({"deprecation", "removal"})
public class PlowOxEntity extends Cow implements GeoAnimatable {
    /**
     * vanilla 导航的「速度平方」换算系数。
     *
     * <p>导航驱动时 {@code MoveControl} 调 {@code setSpeed(V)}，而 {@code Mob.setSpeed} 会把
     * {@code zza} 一并设成 V；{@code Entity.moveRelative} 在输入向量长度平方 &lt; 1 时不做归一化，
     * 于是每 tick 的冲量是 {@code V² × (0.216/f³)}，再经摩擦衰减 {@code 0.91f} 得到稳态速度
     * {@code V² × k}。取常见地面摩擦 f = 0.6（耕地 / 泥土 / 草方块 / 路径都是 0.6）：
     * {@code k = (0.216/0.6³) / (1 - 0.91×0.6) = 1 / 0.454 ≈ 2.203}。
     */
    private static final double NAV_SPEED_FACTOR = 1.0D / (1.0D - 0.91D * 0.6D);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final PlowWorkController workController = new PlowWorkController(this);

    /**
     * 去角点时已下发导航的目标点。
     * 仅用于避免每 tick 重复寻路（见 {@link #tickMoveToCorner()}）；离开 MOVE_TO_CORNER 状态即清空。
     */
    @Nullable
    private Vec3 lastCornerGoal;

    // 绳子注册标记
    private boolean ropesCreated = false;
    // 当前挂载工具的缓存（犁或耧车等，统一为 AbstractDraggableEntity）
    private AbstractDraggableEntity cachedTool;

    /** 牛挂载的工具类型 */
    public enum ToolType {
        PLOW,
        LOUCHE;

        public EntityType<? extends AbstractDraggableEntity> getEntityType() {
            return switch (this) {
                case PLOW -> ModEntities.PLOW_ENTITY.get();
                case LOUCHE -> ModEntities.LOUCHE_ENTITY.get();
            };
        }

        public ItemStack getDropItem() {
            return switch (this) {
                case PLOW -> new ItemStack(ModBlocks.PLOW.get().asItem());
                case LOUCHE -> new ItemStack(ModBlocks.LOUCHE.get().asItem());
            };
        }
    }

    public enum VisualState {
        DEFAULT,
        YOKE,
        RICE_SACKS
    }

    public enum OxState {
        IDLE,            // 无农具，行为等同原版牛（不可挤奶）
        WAITING,         // 有农具，空闲等待指令
        MOVE_TO_CORNER,
        SELECT_CORNER,
        SELECT_DIRECTION,
        MOVE_TO_START,
        START_WORKING,
        WORKING,
        FINISHING,
        ERROR
    }

    private static final EntityDataAccessor<Integer> OX_STATE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> TARGET_CORNER =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> OTHER_CORNER =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> PLOW_PATH =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> TOOL_ENTITY_UUID =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> TOOL_REMOVED =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> VISUAL_STATE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    // 当前挂载工具类型（PLOW / LOUCHE），用于 create / drop / reattach
    private static final EntityDataAccessor<String> TOOL_TYPE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    // Farmer 绑定
    private static final EntityDataAccessor<Optional<UUID>> BOUND_FARMER_UUID =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> BOUND_FARMER_TYPE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BOUND_ORIGINAL_PROFESSION =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    // 喂食加速：0=无加速 >0=小麦加速剩余方块数 -1=炖菜永久加速
    private static final EntityDataAccessor<Integer> BOOST_REMAINING =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> CURRENT_WORK_FIELD_TYPE =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CURRENT_WORK_ACTION =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> WORK_ISSUE_REASON =
            SynchedEntityData.defineId(PlowOxEntity.class, EntityDataSerializers.STRING);

    private static final EquipmentSlot YOKE_SLOT = EquipmentSlot.CHEST;
    private static final EquipmentSlot RICE_SACKS_SLOT = EquipmentSlot.HEAD;

    public PlowOxEntity(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Cow.createAttributes();
    }

    @Override
    protected void registerGoals() {
        // 赶路速度倍率：这里读取一次并写入各 goal 的 speedModifier。
        // 因为 goal 只在实体构造时注册一次，改动 Config.TRAVEL_SPEED_MULTIPLIER 后
        // 需要重启（实体重新加载）才会生效。
        double travel = Config.TRAVEL_SPEED_MULTIPLIER;

        // 所有状态通用
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GatedPanicGoal(2.0 * travel));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));

        // IDLE 状态（无农具）：原版牛行为
        this.goalSelector.addGoal(2, new GatedBreedGoal(1.0 * travel));
        this.goalSelector.addGoal(3, new GatedTemptGoal(1.25 * travel, Ingredient.of(Items.WHEAT), false));
        this.goalSelector.addGoal(4, new GatedFollowParentGoal(1.25 * travel));
        this.goalSelector.addGoal(5, new GatedEatBlockGoal());
        this.goalSelector.addGoal(6, new GatedWanderGoal(1.0 * travel));
    }

    // ==================== IDLE 专用 goal 包装 ====================

    /** 仅在 IDLE / WAITING 状态激活的 PanicGoal */
    private class GatedPanicGoal extends PanicGoal {
        public GatedPanicGoal(double speed) { super(PlowOxEntity.this, speed); }
        @Override public boolean canUse() {
            OxState s = getOxState();
            return (s == OxState.IDLE || s == OxState.WAITING) && super.canUse();
        }
        @Override public boolean canContinueToUse() {
            OxState s = getOxState();
            return (s == OxState.IDLE || s == OxState.WAITING) && super.canContinueToUse();
        }
    }

    /** 仅在 IDLE 状态激活的 BreedGoal */
    private class GatedBreedGoal extends BreedGoal {
        public GatedBreedGoal(double speed) { super(PlowOxEntity.this, speed); }
        @Override public boolean canUse() { return getOxState() == OxState.IDLE && super.canUse(); }
        @Override public boolean canContinueToUse() { return getOxState() == OxState.IDLE && super.canContinueToUse(); }
    }

    /** 仅在 IDLE 状态激活的 TemptGoal（小麦繁殖用）。 */
    private class GatedTemptGoal extends TemptGoal {
        public GatedTemptGoal(double speed, Ingredient items, boolean canScare) {
            super(PlowOxEntity.this, speed, items, canScare);
        }
        @Override public boolean canUse() { return getOxState() == OxState.IDLE && super.canUse(); }
        @Override public boolean canContinueToUse() { return getOxState() == OxState.IDLE && super.canContinueToUse(); }
    }

    /** 仅在 IDLE 状态激活的 FollowParentGoal */
    private class GatedFollowParentGoal extends FollowParentGoal {
        public GatedFollowParentGoal(double speed) { super(PlowOxEntity.this, speed); }
        @Override public boolean canUse() { return getOxState() == OxState.IDLE && super.canUse(); }
        @Override public boolean canContinueToUse() { return getOxState() == OxState.IDLE && super.canContinueToUse(); }
    }

    /** 仅在 IDLE 状态激活的 EatBlockGoal（吃草） */
    private class GatedEatBlockGoal extends EatBlockGoal {
        public GatedEatBlockGoal() { super(PlowOxEntity.this); }
        @Override public boolean canUse() { return getOxState() == OxState.IDLE && super.canUse(); }
        @Override public boolean canContinueToUse() { return getOxState() == OxState.IDLE && super.canContinueToUse(); }
    }

    /** 仅在 IDLE 状态激活的随机漫游 */
    private class GatedWanderGoal extends WaterAvoidingRandomStrollGoal {
        public GatedWanderGoal(double speed) { super(PlowOxEntity.this, speed); }
        @Override public boolean canUse() { return getOxState() == OxState.IDLE && super.canUse(); }
        @Override public boolean canContinueToUse() { return getOxState() == OxState.IDLE && super.canContinueToUse(); }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack item = player.getItemInHand(hand);

        if (item.is(ModItems.YOKE.get())) {
            return tryAttachYoke(player, item);
        } else if (item.is(ModItems.RICE_SACK.get())) {
            return tryAttachRiceSacks(player, item);
        }

        if (item.isEmpty()) {
            if (hasRiceSacks() && player.isShiftKeyDown()) {
                if (!level().isClientSide) {
                    setRiceSacks(false);
                    spawnAtLocation(new ItemStack(ModItems.RICE_SACK.get(), 2));
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        // ---- 原有的喂食加速逻辑 ----
        OxState state = getOxState();

        // 仅在耕作准备/执行阶段接受喂食加速
        if (state == OxState.MOVE_TO_START
                || state == OxState.START_WORKING
                || state == OxState.WORKING) {

            // 小麦：加速 64 格
            if (item.is(Items.WHEAT)) {
                if (!level().isClientSide) {
                    int current = getBoostRemaining();
                    // 已有小麦 boost 则叠加，已有炖菜 boost 则被覆盖
                    int newRemaining = Math.max(current, 0) + 64;
                    setBoostRemaining(newRemaining);
                    if (!player.isCreative()) item.shrink(1);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }

            // 含饱和效果的谜之炖菜：本次全程加速（一次工作只能喂一次）
            if (item.is(Items.SUSPICIOUS_STEW) && hasSaturationEffect(item)) {
                if (getBoostRemaining() == -1) {
                    // 已有炖菜加速，不重复消耗
                    return InteractionResult.sidedSuccess(level().isClientSide);
                }
                if (!level().isClientSide) {
                    setBoostRemaining(-1);
                    this.addEffect(new MobEffectInstance(MobEffects.SATURATION, -1, 0, false, true));
                    if (!player.isCreative()) item.shrink(1);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }

        // 耕牛不能挤奶
        if (item.is(Items.BUCKET)) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    /** 检测可疑炖菜是否含有饱和 (Saturation) 效果（同 FarmerEntity 也会用到）。 */
    static boolean hasSaturationEffect(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Effects", Tag.TAG_LIST)) return false;
        ListTag effects = tag.getList("Effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            CompoundTag effect = effects.getCompound(i);
            // 饱和效果的数字 ID：MobEffect.getId(MobEffects.SATURATION) 为 23
            if (effect.getInt("EffectId") == 23) return true;
        }
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OX_STATE, OxState.IDLE.ordinal());
        this.entityData.define(TARGET_CORNER, Optional.empty());
        this.entityData.define(OTHER_CORNER, Optional.empty());
        this.entityData.define(PLOW_PATH, "");
        this.entityData.define(TOOL_ENTITY_UUID, Optional.empty());
        this.entityData.define(TOOL_REMOVED, true);
        this.entityData.define(VISUAL_STATE, VisualState.DEFAULT.name());
        this.entityData.define(TOOL_TYPE, ToolType.PLOW.name());
        this.entityData.define(BOUND_FARMER_UUID, Optional.empty());
        this.entityData.define(BOUND_FARMER_TYPE, "");
        this.entityData.define(BOUND_ORIGINAL_PROFESSION, "");
        this.entityData.define(BOOST_REMAINING, 0);
        this.entityData.define(CURRENT_WORK_FIELD_TYPE, WorkZoneSerializer.DEFAULT_FIELD_TYPE.key());
        this.entityData.define(CURRENT_WORK_ACTION, WorkZoneSerializer.DEFAULT_ACTION.key());
        this.entityData.define(WORK_ISSUE_REASON, WorkIssueReason.NONE.name());
    }

    // ==================== 状态机方法 ====================

    public OxState getOxState() {
        int index = this.entityData.get(OX_STATE);
        OxState[] values = OxState.values();
        if (index < 0 || index >= values.length) return OxState.WAITING;
        return values[index];
    }

    public void setOxState(OxState state) {
        OxState oldState = getOxState();
        this.entityData.set(OX_STATE, state.ordinal());
        if (oldState == state) {
            return;
        }
        if (state != OxState.MOVE_TO_CORNER) {
            this.lastCornerGoal = null;
        }
        // 状态边界触发 Farmer 转化/还原
        // IDLE / WAITING = "不活跃"；其余状态 = "活跃"
        if (!level().isClientSide) {
            boolean wasActive = OxStateRules.keepsBoundEntityConverted(oldState);
            boolean isActive  = OxStateRules.keepsBoundEntityConverted(state);

            if (!wasActive && isActive) {
                tryConvertBoundToFarmer();
            } else if (wasActive && !isActive) {
                // 农民还需要存食物，延迟还原
                LivingEntity bound = getBoundEntity();
                if (!(bound instanceof FarmerEntity farmer && farmer.shouldStayConvertedDuringOxIdle())) {
                    tryRevertFarmerToOriginal();
                }
            }
        }
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

    public WorkFieldType getCurrentWorkFieldType() {
        try {
            return WorkFieldType.fromKey(this.entityData.get(CURRENT_WORK_FIELD_TYPE));
        } catch (RuntimeException ignored) {
            return WorkZoneSerializer.DEFAULT_FIELD_TYPE;
        }
    }

    public WorkAction getCurrentWorkAction() {
        try {
            return WorkAction.fromKey(this.entityData.get(CURRENT_WORK_ACTION));
        } catch (RuntimeException ignored) {
            return WorkZoneSerializer.DEFAULT_ACTION;
        }
    }

    public void setCurrentWorkZone(WorkFieldType fieldType, WorkAction action) {
        this.entityData.set(CURRENT_WORK_FIELD_TYPE, fieldType.key());
        this.entityData.set(CURRENT_WORK_ACTION, action.key());
    }

    public WorkIssueReason getWorkIssueReason() {
        return readEnumName(this.entityData.get(WORK_ISSUE_REASON), WorkIssueReason.class, WorkIssueReason.NONE);
    }

    public void setWorkIssueReason(WorkIssueReason reason) {
        this.entityData.set(WORK_ISSUE_REASON, reason.name());
    }

    public void enterError(WorkIssueReason reason) {
        setWorkIssueReason(reason);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.workController.reset();
        this.setPlowPathData("");
        deactivateTool();
        setOxState(OxState.ERROR);
    }

    public void exitError() {
        if (getOxState() != OxState.ERROR) return;
        setWorkIssueReason(WorkIssueReason.NONE);
        setOxState(hasMountedTool() ? OxState.WAITING : OxState.IDLE);
    }

    public String getPlowPathData() {
        return this.entityData.get(PLOW_PATH);
    }

    public void setPlowPathData(String data) {
        this.entityData.set(PLOW_PATH, data);
    }

    public PlowAI.Direction getSelectedPlowDir() { return workController.getDirection(); }

    public ToolType getToolType() {
        try { return ToolType.valueOf(this.entityData.get(TOOL_TYPE)); }
        catch (IllegalArgumentException e) { return ToolType.PLOW; }
    }

    public void setToolType(ToolType type) {
        this.entityData.set(TOOL_TYPE, type.name());
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> type, E fallback) {
        if (!tag.contains(key)) return fallback;
        return readEnumName(tag.getString(key), type, fallback);
    }

    private static <E extends Enum<E>> E readEnumName(String name, Class<E> type, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static WorkFieldType readWorkFieldType(CompoundTag tag, String key) {
        if (!tag.contains(key)) return WorkZoneSerializer.DEFAULT_FIELD_TYPE;
        try {
            return WorkFieldType.fromKey(tag.getString(key));
        } catch (RuntimeException ignored) {
            return WorkZoneSerializer.DEFAULT_FIELD_TYPE;
        }
    }

    private static WorkAction readWorkAction(CompoundTag tag, String key) {
        if (!tag.contains(key)) return WorkZoneSerializer.DEFAULT_ACTION;
        try {
            return WorkAction.fromKey(tag.getString(key));
        } catch (RuntimeException ignored) {
            return WorkZoneSerializer.DEFAULT_ACTION;
        }
    }

    public UUID getToolEntityUUID() {
        return this.entityData.get(TOOL_ENTITY_UUID).orElse(null);
    }

    public void setToolEntityUUID(UUID uuid) {
        this.entityData.set(TOOL_ENTITY_UUID, Optional.ofNullable(uuid));
        if (uuid == null) {
            cachedTool = null;
            ropesCreated = false;
        }
    }

    public boolean isToolRemoved() {
        return this.entityData.get(TOOL_REMOVED);
    }

    public void setToolRemoved(boolean removed) {
        this.entityData.set(TOOL_REMOVED, removed);
    }

    public VisualState getVisualState() {
        try {
            return VisualState.valueOf(this.entityData.get(VISUAL_STATE));
        } catch (IllegalArgumentException e) {
            return VisualState.DEFAULT;
        }
    }

    public void setVisualState(VisualState state) {
        this.entityData.set(VISUAL_STATE, state.name());
        switch (state) {
            case YOKE -> {
                this.setItemSlot(YOKE_SLOT, new ItemStack(ModItems.YOKE.get()));
                this.setItemSlot(RICE_SACKS_SLOT, ItemStack.EMPTY);
            }
            case RICE_SACKS -> {
                this.setItemSlot(RICE_SACKS_SLOT, new ItemStack(ModItems.RICE_SACK.get(), 2));
                this.setItemSlot(YOKE_SLOT, ItemStack.EMPTY);
            }
            default -> {
                this.setItemSlot(YOKE_SLOT, ItemStack.EMPTY);
                this.setItemSlot(RICE_SACKS_SLOT, ItemStack.EMPTY);
            }
        }
    }

    public boolean hasYoke() {
        return this.getItemBySlot(YOKE_SLOT).is(ModItems.YOKE.get());
    }

    public void setHasYoke(boolean hasYoke) {
        if (hasYoke) {
            setVisualState(VisualState.YOKE);
        } else if (getVisualState() == VisualState.YOKE) {
            setVisualState(VisualState.DEFAULT);
        }
    }

    public boolean hasRiceSacks() {
        return this.getItemBySlot(RICE_SACKS_SLOT).is(ModItems.RICE_SACK.get());
    }

    public void setRiceSacks(boolean hasRiceSacks) {
        if (hasRiceSacks) {
            setVisualState(VisualState.RICE_SACKS);
            setToolRemoved(true);
            setToolEntityUUID(null);
        } else if (getVisualState() == VisualState.RICE_SACKS) {
            setVisualState(VisualState.DEFAULT);
        }
    }

    public InteractionResult tryAttachYoke(Player player, ItemStack stack) {
        if (!canAttachYoke()) {
            if (!player.level().isClientSide && hasYoke()) {
                player.displayClientMessage(
                        Component.translatable("message.kaleidoscope_agriculture_evolution.yoke_already_attached"), true);
            }
            return InteractionResult.SUCCESS;
        }
        setVisualState(VisualState.YOKE);
        setToolRemoved(true);
        setToolEntityUUID(null);
        if (!player.level().isClientSide && !player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    public InteractionResult tryAttachRiceSacks(Player player, ItemStack stack) {
        if (!canAttachRiceSacks(stack)) {
            return InteractionResult.SUCCESS;
        }
        setVisualState(VisualState.RICE_SACKS);
        setToolRemoved(true);
        setToolEntityUUID(null);
        if (!player.level().isClientSide && !player.isCreative()) {
            stack.shrink(2);
        }
        return InteractionResult.SUCCESS;
    }

    public boolean hasMountedTool() {
        return hasYoke() && !isToolRemoved();
    }

    public boolean canAttachYoke() {
        return getVisualState() == VisualState.DEFAULT;
    }

    public boolean canAttachRiceSacks(ItemStack stack) {
        return getVisualState() == VisualState.DEFAULT && stack.getCount() >= 2;
    }

    public boolean canAttachTool() {
        return hasYoke() && isToolRemoved() && !hasRiceSacks();
    }

    public void attachTool(ToolType type) {
        if (!canAttachTool()) return;
        setToolType(type);
        setToolRemoved(false);
        setToolEntityUUID(null);
        if (!level().isClientSide) {
            ensureToolExists();
        }
    }

    // ==================== 喂食加速 ====================

    /** 0=无加速 >0=小麦加速剩余方块数 -1=炖菜永久加速。 */
    public int getBoostRemaining() {
        return this.entityData.get(BOOST_REMAINING);
    }

    public void setBoostRemaining(int remaining) {
        this.entityData.set(BOOST_REMAINING, remaining);
    }

    double getWorkingSpeed() {
        double base = 0.05; // 约 1 格/秒
        if (getBoostRemaining() != 0) {
            base *= Config.BOOST_SPEED_MULTIPLIER;
        }
        // 牛使用 setDeltaMovement 直驱，MOVEMENT_SPEED 的属性修正不会自动生效，这里手动乘入
        base *= (1.0 + Config.PULL_SPEED_MODIFIER);
        return base;
    }

    /** 赶路速度倍率（见 {@code Config.TRAVEL_SPEED_MULTIPLIER}）。去角点与去起点的速度都从它派生。 */
    double getTravelSpeedModifier() {
        return Config.TRAVEL_SPEED_MULTIPLIER;
    }

    /** 赶路目标速度（格/tick）。去起点直驱直接用它，去角点导航也以它为目标。 */
    double getMoveToCornerEquivalentSpeed() {
        return this.getAttributeValue(Attributes.MOVEMENT_SPEED) * getTravelSpeedModifier();
    }

    /**
     * 去角点导航使用的 speedModifier。
     *
     * <p>不能把目标速度直接当 speedModifier 传进去：导航的稳态速度是
     * {@code V² × NAV_SPEED_FACTOR}（其中 {@code V = speedModifier × MOVEMENT_SPEED 属性}），
     * 而不是 V。这里反解出让导航恰好跑到目标速度的值，从而与直驱的去起点保持一致。
     */
    double getNavigationSpeedModifier() {
        double attribute = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double target = getMoveToCornerEquivalentSpeed();
        if (attribute <= 1.0E-6D || target <= 0.0D) {
            return getTravelSpeedModifier();
        }

        // target = V² × NAV_SPEED_FACTOR  →  V = sqrt(target / NAV_SPEED_FACTOR)
        // V 超过 1.0 时 vanilla 会归一化输入向量、平方换算失效，故上限收到 0.99
        double v = Math.min(Math.sqrt(target / NAV_SPEED_FACTOR), 0.99D);
        return v / attribute;
    }

    // ==================== 工具挂载管理（犁 / 耧车通用） ====================

    /**
     * 获取关联的工具实体 - 多重查找机制
     * 1. 检查缓存
     * 2. 通过保存的 UUID 查找
     * 3. 通过世界搜索找已绑定这头牛的工具（解决重进游戏问题）
     */
    @Nullable
    public AbstractDraggableEntity getToolEntity() {
        UUID uuid = getToolEntityUUID();

        // 1. 检查缓存
        if (cachedTool != null && cachedTool.isAlive()) {
            if (uuid == null || cachedTool.getUUID().equals(uuid)) {
                return cachedTool;
            }
        }

        // 2. 通过保存的 UUID 查找
        if (uuid != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity instanceof AbstractDraggableEntity tool && tool.isAlive()) {
                cachedTool = tool;
                return tool;
            }
        }

        // 3. 关键修复：通过世界搜索，找已经绑定这头牛的工具
        //    这解决了重进游戏时工具先存在但牛不知道的问题
        if (level() instanceof ServerLevel serverLevel) {
            for (AbstractDraggableEntity tool : serverLevel.getEntitiesOfClass(
                    AbstractDraggableEntity.class, getBoundingBox().inflate(32))) {
                if (tool.isAlive() && this.getUUID().equals(tool.getOxUUID())) {
                    // 找到了！更新保存的 UUID
                    setToolEntityUUID(tool.getUUID());
                    cachedTool = tool;
                    return tool;
                }
            }
        }

        // 4. 找不到，清除缓存
        cachedTool = null;
        return null;
    }

    /**
     * 确保工具存在：只在真的没有工具时创建。
     */
    private void ensureToolExists() {
        if (level().isClientSide) return;        if (!hasYoke() || isToolRemoved()) return;

        // 先尝试获取已有的工具（包括通过世界搜索）
        AbstractDraggableEntity existing = getToolEntity();
        if (existing != null) {
            ensureRopesExist(existing);
            return;
        }

        // 双重检查：再搜索一次更大范围（确保没有遗漏）
        if (level() instanceof ServerLevel serverLevel) {
            for (AbstractDraggableEntity tool : serverLevel.getEntitiesOfClass(
                    AbstractDraggableEntity.class, getBoundingBox().inflate(64))) {
                if (tool.isAlive() && this.getUUID().equals(tool.getOxUUID())) {
                    setToolEntityUUID(tool.getUUID());
                    cachedTool = tool;
                    ensureRopesExist(tool);
                    return;
                }
            }
        }

        // 真的没有工具，创建新工具
        spawnTool();
    }

    private void spawnTool() {
        if (level().isClientSide) return;        if (!hasYoke() || isToolRemoved()) return;

        // 最终检查：确保真的没有工具
        AbstractDraggableEntity existing = getToolEntity();
        if (existing != null) return;

        ToolType type = getToolType();

        AbstractDraggableEntity tool = type.getEntityType().create(level());
        if (tool != null) {
            tool.setPos(this.getX(), this.getY(), this.getZ());
            tool.setYRot(this.getYRot());
            tool.setYHeadRot(this.getYRot());
            level().addFreshEntity(tool);
            // 恢复耧车库存 NBT
            if (tool instanceof LoucheEntity louche) {
                var pending = getPersistentData().getCompound("PendingLoucheInv");
                if (!pending.isEmpty()) {
                    louche.restoreFromTag(pending);
                    getPersistentData().remove("PendingLoucheInv");
                }
            }
            setToolEntityUUID(tool.getUUID());
            cachedTool = tool;
            tool.setOx(this);
        }
    }

    public void removeTool() {
        if (level().isClientSide) return;
        AbstractDraggableEntity tool = getToolEntity();
        if (tool != null) {
            ropesCreated = false;
            tool.setOx(null);
            // 先拿掉落物（含库存 NBT）再删除实体
            ItemStack drop = tool.getDropItem();
            tool.discard();
            this.spawnAtLocation(drop);
        }
        cachedTool = null;
    }

    private void ensureRopesExist(AbstractDraggableEntity tool) {
        if (!level().isClientSide && !ropesCreated && tool != null) {
            ropesCreated = true;
        }
    }

    public void startWorking(PlowAI.Direction dir) {
        if (getOxState() != OxState.SELECT_DIRECTION) {
            return;
        }
        if (!hasMountedTool()) {
            setOxState(OxState.IDLE);
            setPlowPathData("");
            return;
        }
        BlockPos target = getTargetCorner();
        BlockPos other = getOtherCorner();
        if (target == null || other == null) {
            return;
        }
        if (!this.workController.start(PlowWorkPlan.rectangular(
                target,
                other,
                dir,
                getCurrentWorkFieldType(),
                getCurrentWorkAction()))) {
            setOxState(OxState.WAITING);
            setPlowPathData("");
            KaleidoscopeAgricultureEvolution.LOGGER.warn(
                    "Rejected plow work area for ox {} because it is too large or has no valid path",
                    this.getUUID());
            return;
        }
        setWorkIssueReason(WorkIssueReason.NONE);
        setOxState(OxState.MOVE_TO_START);
        setPlowPathData(workController.getPathData());
    }

    public void cancelCurrentWork() {
        if (level().isClientSide) return;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0;
        this.zza = 0;
        this.yHeadRot = this.yBodyRot;
        this.setXRot(0);
        this.setMaxUpStep(0.6F);
        this.workController.reset();
        this.setPlowPathData("");
        this.setTargetCorner(null);
        this.setOtherCorner(null);
        setWorkIssueReason(WorkIssueReason.NONE);
        deactivateTool();
        setOxState(OxState.IDLE);
    }

    public boolean pauseWorkForSupply() {
        if (level().isClientSide) return false;
        OxState state = getOxState();
        if (state != OxState.MOVE_TO_START
                && state != OxState.START_WORKING
                && state != OxState.WORKING) {
            return false;
        }
        setPlowPathData(workController.getPathData());
        stopWorkMovement();
        deactivateTool();
        setOxState(OxState.WAITING);
        return true;
    }

    public boolean resumeWorkAfterSupply() {
        if (level().isClientSide || getOxState() != OxState.WAITING) return false;
        if (!hasMountedTool() || !workController.hasRemainingTarget()) return false;
        setPlowPathData(workController.getPathData());
        workController.restartFromSupplyPause();
        return true;
    }

    void stopWorkMovement() {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.xxa = 0;
        this.zza = 0;
        this.yHeadRot = this.yBodyRot;
    }

    void setWorkHeadYaw(float yaw) {
        this.yHeadRot = yaw;
    }

    boolean hasHorizontalCollision() {
        return this.horizontalCollision;
    }

    void activateTool() {
        AbstractDraggableEntity tool = getToolEntity();
        if (tool != null) {
            tool.setWorking(true);
        }
    }

    void deactivateTool() {
        AbstractDraggableEntity tool = getToolEntity();
        if (tool != null) {
            tool.setWorking(false);
        }
    }

    // ==================== 主 Tick 逻辑 ====================

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide || !this.isAlive()) return;

        if (getOxState() == OxState.ERROR) {
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            deactivateTool();
            return;
        }

        // 确保工具存在（除非玩家手动取下）
        if (hasMountedTool()) {
            ensureToolExists();
        }

        // 没挂农具的牛：强制回到 IDLE（原版牛行为）
        if (!hasMountedTool()) {
            OxState st = getOxState();
            if (st != OxState.IDLE) {
                cancelCurrentWork();
            }
        }

        // 农具重新挂上：自动从 IDLE 进入 WAITING
        if (hasMountedTool() && getOxState() == OxState.IDLE) {
            setOxState(OxState.WAITING);
        }

        // 只要不在犁地状态，就停掉工具（处理鞭子中途打断等外部状态切换）
        if (getOxState() != OxState.WORKING) {
            AbstractDraggableEntity tool = getToolEntity();
            if (tool != null && tool.isWorking()) {
                tool.setWorking(false);
            }
        }

        // 状态机更新
        OxState currentState = getOxState();
        switch (currentState) {
            case MOVE_TO_CORNER -> tickMoveToCorner();
            case SELECT_CORNER -> tickSelectCorner();
            case SELECT_DIRECTION -> tickSelectDirection();
            case MOVE_TO_START -> workController.tickMoveToStart();
            case START_WORKING -> workController.tickStartWorking();
            case WORKING -> workController.tickWorking();
            case FINISHING -> workController.tickFinishing();
            default -> {}
        }

        // 退出耕作状态时清除加速效果和饱和buff
        if (getBoostRemaining() != 0
                && currentState != OxState.MOVE_TO_CORNER
                && currentState != OxState.SELECT_CORNER
                && currentState != OxState.SELECT_DIRECTION
                && currentState != OxState.MOVE_TO_START
                && currentState != OxState.START_WORKING
                && currentState != OxState.WORKING) {
            setBoostRemaining(0);
            this.removeEffect(MobEffects.SATURATION);
        }
    }

    private void tickMoveToCorner() {
        if (getOxState() != OxState.MOVE_TO_CORNER) {
            return;
        }
        BlockPos target = getTargetCorner();
        if (target == null) {
            setOxState(OxState.WAITING);
            return;
        }
        Vec3 goal = new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        // 只在「目标变化」或「导航已完成」时重新下发路径。
        // 原实现每 tick 都调 moveTo(...)，等于每 tick 跑一次 A* 寻路：既浪费性能，
        // 也会因反复重置导航目标而让牛走走停停，实际速度低于 getTravelSpeedModifier() 的设定值。
        if (!goal.equals(this.lastCornerGoal) || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(goal.x, goal.y, goal.z, getNavigationSpeedModifier());
            this.lastCornerGoal = goal;
        }

        double dist = this.position().distanceTo(goal);
        if (dist < 5.0) {
            setOxState(OxState.SELECT_DIRECTION);
        }
    }

    private void tickSelectCorner() {
        if (getOxState() != OxState.SELECT_CORNER) {
            return;
        }
        // 等待玩家选择角点，不需要额外移动逻辑。
    }

    private void tickSelectDirection() {
        if (getOxState() != OxState.SELECT_DIRECTION) {
            return;
        }
        BlockPos target = getTargetCorner();
        if (target != null) {
            double dist = this.position().distanceTo(new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
            if (dist < 2.0) {
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                this.xxa = 0;
                this.zza = 0;
                this.yHeadRot = this.yBodyRot;
                this.setXRot(0);
            }
        }
    }

    void consumeBoostBlock() {
        int remaining = getBoostRemaining();
        if (remaining > 0) {
            remaining--;
            setBoostRemaining(remaining);
            if (remaining == 0) {
                // boost 用尽，仅在服务端提示
                if (level() instanceof ServerLevel) {
                    // 提示由 tick() 状态监测统一处理
                }
            }
        }
        // 炖菜 boost (-1) 不消耗，全程有效
    }

    // ==================== Farmer 绑定管理 ====================

        public void bindFarmer(LivingEntity entity) {
        if (level().isClientSide) return;        // 记录原始实体信息
        this.entityData.set(BOUND_FARMER_UUID, Optional.of(entity.getUUID()));
        if (entity instanceof Villager villager) {
            this.entityData.set(BOUND_FARMER_TYPE, "minecraft:villager");
            VillagerProfession prof = villager.getVillagerData().getProfession();
            ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(prof);
            this.entityData.set(BOUND_ORIGINAL_PROFESSION,
                    key != null ? key.toString() : "minecraft:none");
        } else if (entity instanceof WanderingTrader) {
            this.entityData.set(BOUND_FARMER_TYPE, "minecraft:wandering_trader");
            this.entityData.set(BOUND_ORIGINAL_PROFESSION, "minecraft:none");
        }
        // Bug #2 修复：在被绑定实体的 persistentData 中标记所属耕牛，防止重复绑定
        entity.getPersistentData().putUUID("KAE_BoundOx", this.getUUID());
    }

    public UUID getBoundFarmerUUID() {
        return this.entityData.get(BOUND_FARMER_UUID).orElse(null);
    }

    /** 完全清除耕牛的 Farmer 绑定记录（供 Contract 解绑使用）。 */
    public void clearBoundFarmer() {
        // 清理被绑定实体的persistentData中的ox标记
        UUID uuid = getBoundFarmerUUID();
        if (uuid != null && level() instanceof ServerLevel serverLevel) {
            Entity e = serverLevel.getEntity(uuid);
            if (e instanceof LivingEntity living) {
                living.getPersistentData().remove("KAE_BoundOx");
            }
        }
        this.entityData.set(BOUND_FARMER_UUID, Optional.empty());
        this.entityData.set(BOUND_FARMER_TYPE, "");
        this.entityData.set(BOUND_ORIGINAL_PROFESSION, "");
    }

    @Nullable
    LivingEntity getBoundEntity() {
        UUID uuid = getBoundFarmerUUID();
        if (uuid == null) return null;
        if (level() instanceof ServerLevel serverLevel) {
            Entity e = serverLevel.getEntity(uuid);
            if (e instanceof LivingEntity living && living.isAlive()) return living;
        }
        return null;
    }

    private void tryConvertBoundToFarmer() {
        LivingEntity bound = getBoundEntity();
        if (bound == null) {
            return;
        }
        if (bound instanceof FarmerEntity) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();
        FarmerEntity farmer = ModEntities.FARMER.get().create(serverLevel);
        if (farmer == null) {
            return;
        }


        farmer.moveTo(bound.getX(), bound.getY(), bound.getZ(), bound.getYRot(), bound.getXRot());
        farmer.setYRot(bound.getYRot());
        farmer.setXRot(bound.getXRot());
        farmer.yBodyRot = bound.yBodyRot;
        farmer.yHeadRot = bound.yHeadRot;
        farmer.setHealth(bound.getHealth());
        if (bound instanceof AgeableMob ageable) farmer.setAge(ageable.getAge());
        if (bound.hasCustomName()) {
            farmer.setCustomName(bound.getCustomName());
            farmer.setCustomNameVisible(bound.isCustomNameVisible());
        }

        // 保存原职业和实体类型，用于后续还原
        String origProf = this.entityData.get(BOUND_ORIGINAL_PROFESSION);
        farmer.setOriginalProfession(origProf.isEmpty() ? "minecraft:farmer" : origProf);
        farmer.setOriginalEntityType(this.entityData.get(BOUND_FARMER_TYPE));

        farmer.setBoundOxUUID(this.getUUID());

        FarmerBrainController.clearConvertedVillagerState(bound);
        bound.discard();
        serverLevel.addFreshEntity(farmer);
        this.entityData.set(BOUND_FARMER_UUID, Optional.of(farmer.getUUID()));

        farmer.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(farmer.blockPosition()),
                MobSpawnType.CONVERSION, null, null);
        FarmerBrainController.clearConvertedVillagerState(farmer);
        farmer.forceProfessionFarmer();
    }

    public void tryRevertFarmerToOriginal() {
        UUID uuid = getBoundFarmerUUID();
        if (uuid == null) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity e = serverLevel.getEntity(uuid);
        if (!(e instanceof FarmerEntity farmer) || !farmer.isAlive()) {
            return;
        }


        String origType = farmer.getOriginalEntityType();
        EntityType<?> revertType = switch (origType) {
            case "minecraft:wandering_trader" -> EntityType.WANDERING_TRADER;
            default -> EntityType.VILLAGER;
        };

        LivingEntity reverted = (LivingEntity) revertType.create(serverLevel);
        if (reverted == null) return;

        reverted.moveTo(farmer.getX(), farmer.getY(), farmer.getZ(), farmer.getYRot(), farmer.getXRot());
        reverted.setYRot(farmer.getYRot());
        reverted.setXRot(farmer.getXRot());
        reverted.yBodyRot = farmer.yBodyRot;
        reverted.yHeadRot = farmer.yHeadRot;
        reverted.setHealth(farmer.getHealth());
        if (reverted instanceof AgeableMob ageable) ageable.setAge(farmer.getAge());
        if (farmer.hasCustomName()) {
            reverted.setCustomName(farmer.getCustomName());
            reverted.setCustomNameVisible(farmer.isCustomNameVisible());
        }

        // 还原职业
        if (reverted instanceof Villager villager) {
            String profKey = farmer.getOriginalProfessionKey();
            VillagerProfession prof = ForgeRegistries.VILLAGER_PROFESSIONS.getValue(
                    KaleidoscopeAgricultureEvolution.id(profKey));
            if (prof != null) {
                villager.setVillagerData(villager.getVillagerData().setProfession(prof));
            }
        }

        farmer.discard();
        serverLevel.addFreshEntity(reverted);
        // 还原后的实体仍需标记所属耕牛，防止被其他契约重复绑定
        reverted.getPersistentData().putUUID("KAE_BoundOx", this.getUUID());
        // 更新绑定 UUID
        this.entityData.set(BOUND_FARMER_UUID, Optional.of(reverted.getUUID()));
    }

    // ==================== 数据持久化 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("OxState", getOxState().name());
        if (getTargetCorner() != null) tag.putLong("TargetCorner", getTargetCorner().asLong());
        if (getOtherCorner() != null) tag.putLong("OtherCorner", getOtherCorner().asLong());
        tag.putBoolean("ToolRemoved", isToolRemoved());
        tag.putString("VisualState", getVisualState().name());
        tag.putString("ToolType", getToolType().name());
        // Farmer 绑定
        UUID farmerId = getBoundFarmerUUID();
        if (farmerId != null) tag.putUUID("BoundFarmer", farmerId);
        String ft = this.entityData.get(BOUND_FARMER_TYPE);
        if (!ft.isEmpty()) tag.putString("BoundFarmerType", ft);
        String fp = this.entityData.get(BOUND_ORIGINAL_PROFESSION);
        if (!fp.isEmpty()) tag.putString("BoundOrigProfession", fp);
        // 喂食加速
        int boost = getBoostRemaining();
        if (boost != 0) tag.putInt("BoostRemaining", boost);
        // PlowAI 恢复
        workController.save(tag);
        tag.putString("CurrentWorkFieldType", getCurrentWorkFieldType().key());
        tag.putString("CurrentWorkAction", getCurrentWorkAction().key());
        tag.putString("WorkIssueReason", getWorkIssueReason().name());
        // 剩余路径数据（用于重载后恢复）
        String pathData = getPlowPathData();
        if (!pathData.isEmpty()) tag.putString("PlowPathData", pathData);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("OxState")) {
            this.entityData.set(OX_STATE, readEnum(tag, "OxState", OxState.class, OxState.WAITING).ordinal());
        }
        if (tag.contains("TargetCorner")) setTargetCorner(BlockPos.of(tag.getLong("TargetCorner")));
        if (tag.contains("OtherCorner")) setOtherCorner(BlockPos.of(tag.getLong("OtherCorner")));
        // 工具持久化（向后兼容：同时读取旧的 "PlowRemoved" 键）
        if (tag.contains("ToolRemoved")) setToolRemoved(tag.getBoolean("ToolRemoved"));
        else if (tag.contains("PlowRemoved")) setToolRemoved(tag.getBoolean("PlowRemoved"));
        if (tag.contains("VisualState")) {
            setVisualState(readEnum(tag, "VisualState", VisualState.class, VisualState.DEFAULT));
        } else if (tag.contains("HasRiceSacks") && tag.getBoolean("HasRiceSacks")) {
            setVisualState(VisualState.RICE_SACKS);
        } else if (tag.contains("HasYoke") && tag.getBoolean("HasYoke")) {
            setVisualState(VisualState.YOKE);
        } else {
            setVisualState(VisualState.DEFAULT);
        }
        if (tag.contains("ToolType")) {
            setToolType(readEnum(tag, "ToolType", ToolType.class, ToolType.PLOW));
        }
        setCurrentWorkZone(readWorkFieldType(tag, "CurrentWorkFieldType"), readWorkAction(tag, "CurrentWorkAction"));
        setWorkIssueReason(readEnum(tag, "WorkIssueReason", WorkIssueReason.class, WorkIssueReason.NONE));
        if (tag.hasUUID("BoundFarmer"))
            this.entityData.set(BOUND_FARMER_UUID, Optional.of(tag.getUUID("BoundFarmer")));
        if (tag.contains("BoundFarmerType"))
            this.entityData.set(BOUND_FARMER_TYPE, tag.getString("BoundFarmerType"));
        if (tag.contains("BoundOrigProfession"))
            this.entityData.set(BOUND_ORIGINAL_PROFESSION, tag.getString("BoundOrigProfession"));
        // 喂食加速
        if (tag.contains("BoostRemaining"))
            setBoostRemaining(Mth.clamp(tag.getInt("BoostRemaining"), -1, 100000));
        // PlowAI 恢复
        PlowAI.Direction loadedDirection = PlowWorkController.readDirection(tag);
        workController.loadProgress(tag);

        // 恢复工作进度：重建完整路径，然后跳转到保存的位置
        OxState loadedState = getOxState();
        if ((loadedState == OxState.MOVE_TO_START
                || loadedState == OxState.START_WORKING
                || loadedState == OxState.WORKING)
                && getTargetCorner() != null && getOtherCorner() != null
                && loadedDirection != null) {
            if (!workController.restore(
                    PlowWorkPlan.rectangular(
                            getTargetCorner(),
                            getOtherCorner(),
                            loadedDirection,
                            getCurrentWorkFieldType(),
                            getCurrentWorkAction()),
                    tag.getString("PlowPathData"))) {
                setOxState(OxState.WAITING);
                setPlowPathData("");
                return;
            }
            setPlowPathData(workController.getPathData());
        }
    }

    // ==================== GeckoLib 动画 ====================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, this::predicate));
    }

    private PlayState predicate(software.bernie.geckolib.core.animation.AnimationState<PlowOxEntity> state) {
        switch (getOxState()) {
            case WORKING -> state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.plowox.working"));
            case START_WORKING -> state.getController().setAnimation(RawAnimation.begin().then("animation.plowox.start_work", Animation.LoopType.HOLD_ON_LAST_FRAME));
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

    private static final float OX_LEFT_X = -0.5625f;  // -9/16（右定位器，实体左侧）
    private static final float OX_RIGHT_X = 0.5625f;   // 9/16（左定位器，实体右侧）
    private static final float OX_Y = 0.90625f;        // 14.5/16
    private static final float OX_Z = 0.234375f;       // 3.75/16（bb_main 定位器 z=-3.75，取反为实体空间坐标）

    // 三种耕牛模型中的独立 locator: [0, 17.15, -16.25]
    // GeckoLib 模型的 Z 轴与实体拴绳偏移方向相反，因此这里取反并换算为方块单位。
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
        LivingEntity living = (LivingEntity) entity;
        float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        float yawRad = (float) Math.toRadians(yaw);

        double worldX = x + (localX * Math.cos(yawRad) - localZ * Math.sin(yawRad));
        double worldZ = z + (localX * Math.sin(yawRad) + localZ * Math.cos(yawRad));
        return new Vec3(worldX, y + localY, worldZ);
    }

    // ==================== 内部类：条件跟随 AI ====================

    /**
     * 只在 SELECT_DIRECTION 状态下激活的 TemptGoal
     * 利用原版平滑的跟随移动 AI
     */
}
