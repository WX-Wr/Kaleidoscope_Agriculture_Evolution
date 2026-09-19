package com.wxwr.kaleidoscopeagricultureevolution.blockentity;

import com.wxwr.kaleidoscopeagricultureevolution.item.ModItems;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraitResolver;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import com.wxwr.kaleidoscopeagricultureevolution.network.HoneyExtractorStartPacket;
import com.wxwr.kaleidoscopeagricultureevolution.network.KaeNetwork;
import com.wxwr.kaleidoscopeagricultureevolution.particle.ModParticles;
import com.wxwr.kaleidoscopeagricultureevolution.util.HoneyExtractorMath;
import com.wxwr.kaleidoscopeagricultureevolution.util.MouseWheelSpeedCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class HoneyExtractorBlockEntity extends BaseBlockEntity {
    public static final int MAX_HONEYCOMBS = 2;
    private static final int SLOT_COUNT = 2;

    private static final String RAW_OMEGA_TAG = "RawOmega";
    private static final String CURRENT_OMEGA_TAG = "CurrentOmega";
    private static final String CRANK_ANGLE_TAG = "CrankAngle";
    private static final String BASKET_ANGLE_TAG = "BasketAngle";
    private static final String HONEY_AMOUNT_TAG = "HoneyAmount";
    private static final String HIGH_SPEED_TIME_TAG = "HighSpeedTime";
    private static final String STATE_ONLY_SYNC_TAG = "StateOnlySync";

    private static final String SLOT0_CONTENT_TAG = "Slot0Content";
    private static final String SLOT0_PROGRESS_TAG = "Slot0Progress";
    private static final String SLOT0_HONEY_ALPHA_TAG = "Slot0HoneyAlpha";
    private static final String SLOT0_BEESWAX_ALPHA_TAG = "Slot0BeeswaxAlpha";
    private static final String SLOT0_TRAITS_TAG = "Slot0Traits";
    private static final String SLOT0_BEESWAX_COUNT_TAG = "Slot0BeeswaxCount";
    private static final String SLOT1_CONTENT_TAG = "Slot1Content";
    private static final String SLOT1_PROGRESS_TAG = "Slot1Progress";
    private static final String SLOT1_HONEY_ALPHA_TAG = "Slot1HoneyAlpha";
    private static final String SLOT1_BEESWAX_ALPHA_TAG = "Slot1BeeswaxAlpha";
    private static final String SLOT1_TRAITS_TAG = "Slot1Traits";
    private static final String SLOT1_BEESWAX_COUNT_TAG = "Slot1BeeswaxCount";

    private static final double ONE_COMB_MAX_OMEGA = 30.0D;
    private static final double TWO_COMBS_MAX_OMEGA_MULTIPLIER = 0.5D;
    // Fixed UI scale: two-comb full speed (15 rad/s) reaches 270 degrees.
    private static final double GAUGE_DISPLAY_MAX_OMEGA = 20.0D;
    private static final double WHEEL_TO_CRANK_OMEGA_FACTOR = 0.25D;
    private static final double SMOOTH_FACTOR = 0.25D;
    private static final double COAST_DECELERATION_FACTOR = 0.7D;
    private static final double STOP_OMEGA = 0.0D;
    private static final double PARTICLE_PHASE_SPEED = 1.0D;
    private static final double MIN_HONEY_PRODUCING_OMEGA = ONE_COMB_MAX_OMEGA / 8.0D;
    private static final double PROGRESS_PER_OMEGA_SECOND = 0.42D;
    private static final double HONEY_BOTTLE_AMOUNT = 10.0D;
    private static final int RAW_INPUT_TICKS = 4;
    private static final double HONEY_YIELD_PER_SLOT = 1.0D / MAX_HONEYCOMBS;

    private final SlotData[] slots = {new SlotData(), new SlotData()};
    private final Map<UUID, InteractionLock> interactionLocks = new HashMap<>();
    private double rawOmega;
    private double currentOmega;
    private double crankAngle;
    private double basketAngle;
    private double honeyAmount;
    private double highSpeedTime;
    private int rawInputTicks;
    private double particleShakeAnim;
    private transient boolean stateOnlySync;

    public HoneyExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HONEY_EXTRACTOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HoneyExtractorBlockEntity extractor) {
        extractor.tick(level);
    }

    private void tick(Level level) {
        tickInteractionLocks(level);
        double deltaSeconds = 1.0D / 20.0D;
        boolean hasWheelInput = rawInputTicks > 0;

        if (hasWheelInput) {
            currentOmega = MouseWheelSpeedCalculator.smoothOmega(currentOmega, rawOmega, SMOOTH_FACTOR);
        } else {
            currentOmega = decelerateOmega(currentOmega,
                    HoneyExtractorMath.coastDecelerationFactor(honeyAmount, COAST_DECELERATION_FACTOR),
                    deltaSeconds);
        }
        currentOmega = clampOmegaForLoad(currentOmega);
        if (!hasWheelInput && Math.abs(currentOmega) < STOP_OMEGA) {
            currentOmega = 0.0D;
        }

        crankAngle = MouseWheelSpeedCalculator.integrateDegrees(crankAngle, currentOmega, deltaSeconds, 1.0D);
        basketAngle = MouseWheelSpeedCalculator.integrateDegrees(basketAngle, currentOmega, deltaSeconds, 2.0D);

        if (rawInputTicks > 0) {
            rawInputTicks--;
        } else {
            rawOmega = 0.0D;
        }

        boolean completed = updateProgress(deltaSeconds);
        syncVisualAlphas();
        if (level.isClientSide) {
            spawnHoneyParticles(level, getBlockState(), deltaSeconds);
        }
        if (completed && !level.isClientSide) {
            refreshStateOnly();
            return;
        }
        if (!level.isClientSide && (Math.abs(currentOmega) > 0.0D || honeyAmount > 0.0D)) {
            setChanged();
        }
    }

    public void applyWheelInput(double scrollDelta, double deltaSeconds) {
        rawOmega = MouseWheelSpeedCalculator.rawOmegaFromScroll(scrollDelta, deltaSeconds,
                WHEEL_TO_CRANK_OMEGA_FACTOR, maxOmegaForLoad());
        rawInputTicks = RAW_INPUT_TICKS;
        setChanged();
    }

    /**
     * 开始一次摇蜜互动。互动状态按玩家记录，位置和视角由服务端锁定。
     */
    public boolean beginInteraction(Player player) {
        if (level == null || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        if (interactionLocks.containsKey(player.getUUID())) {
            return true;
        }

        InteractionLock lock = new InteractionLock(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        interactionLocks.put(player.getUUID(), lock);
        if (player instanceof ServerPlayer serverPlayer) {
            KaeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new HoneyExtractorStartPacket(worldPosition));
        }
        return true;
    }

    public boolean isInteracting(Player player) {
        return interactionLocks.containsKey(player.getUUID());
    }

    public void endInteraction(Player player) {
        interactionLocks.remove(player.getUUID());
    }

    public boolean addHoneycomb(ItemStack held, boolean creative) {
        if (!held.is(Items.HONEYCOMB)) {
            return false;
        }
        SlotData slot = findFirstEmptySlot();
        if (slot == null) {
            return false;
        }

        slot.setHoneycomb(HoneyExtractorTraitResolver.resolve(held));
        if (!creative) {
            held.shrink(1);
        }
        refreshStateOnly();
        return true;
    }

    public ItemStack removeHoneycomb() {
        SlotData slot = findRemovableHoneycombSlot();
        if (slot == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(Items.HONEYCOMB);
        HoneyExtractorTraitResolver.writeToStack(stack, slot.traits);
        slot.clear();
        refreshStateOnly();
        return stack;
    }

    public ItemStack removeBeeswax() {
        SlotData slot = findBeeswaxSlot();
        if (slot == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(ModItems.BEESWAX.get(), slot.beeswaxItemCount);
        HoneyExtractorTraitResolver.writeToStack(stack, slot.traits);
        slot.clear();
        refreshStateOnly();
        return stack;
    }

    public int getHoneycombCount() {
        return countSlots(SlotContent.HONEYCOMB);
    }

    public int getBeeswaxCount() {
        int count = 0;
        for (SlotData slot : slots) {
            if (slot.isBeeswax()) {
                count += slot.beeswaxItemCount;
            }
        }
        return count;
    }

    public int getSlotCount() {
        return SLOT_COUNT;
    }

    public SlotSnapshot getSlotSnapshot(int index) {
        return slots[validateSlotIndex(index)].snapshot();
    }

    public float getRenderedCrankAngle(float partialTick) {
        return MouseWheelSpeedCalculator.renderDegrees(crankAngle, currentOmega, partialTick, 1.0D);
    }

    public float getRenderedBasketAngle(float partialTick) {
        return MouseWheelSpeedCalculator.renderDegrees(basketAngle, currentOmega, partialTick, 2.0D);
    }

    public double getCurrentOmega() {
        return currentOmega;
    }

    public static double getGaugeDisplayMaxOmega() {
        return GAUGE_DISPLAY_MAX_OMEGA;
    }

    public static double getMinHoneyProducingOmega() {
        return MIN_HONEY_PRODUCING_OMEGA;
    }

    public double getProgress() {
        return getHighestHoneycombProgress();
    }

    public double getHoneyAmount() {
        return honeyAmount;
    }

    public boolean canBottleHoney() {
        return honeyAmount >= HONEY_BOTTLE_AMOUNT;
    }

    public boolean bottleHoney() {
        if (!canBottleHoney()) {
            return false;
        }
        honeyAmount = Mth.clamp(honeyAmount - HONEY_BOTTLE_AMOUNT, 0.0D, HoneyExtractorMath.MAX_HONEY_AMOUNT);
        refreshStateOnly();
        return true;
    }

    private boolean updateProgress(double deltaSeconds) {
        if (countSlots(SlotContent.HONEYCOMB) <= 0) {
            return false;
        }

        double speed = Math.abs(currentOmega);
        if (speed < MIN_HONEY_PRODUCING_OMEGA) {
            return false;
        }

        boolean completed = false;
        double progressStep = speed * PROGRESS_PER_OMEGA_SECOND * deltaSeconds;
        for (SlotData slot : slots) {
            if (!slot.isHoneycomb()) {
                continue;
            }

            double previousProgress = slot.progress;
            slot.progress = Mth.clamp(slot.progress + progressStep * slot.traits.progressSpeedMultiplier(),
                    0.0D, 100.0D);
            honeyAmount = Mth.clamp(honeyAmount
                            + (slot.progress - previousProgress) * HONEY_YIELD_PER_SLOT * slot.traits.honeyYieldMultiplier(),
                    0.0D, HoneyExtractorMath.MAX_HONEY_AMOUNT);
            if (slot.progress >= 100.0D) {
                slot.setBeeswax();
                completed = true;
            }
        }
        return completed;
    }

    private static double decelerateOmega(double omega, double decelerationFactor, double deltaSeconds) {
        double retention = Math.max(0.0D, 1.0D - decelerationFactor * deltaSeconds);
        return omega * retention;
    }

    private double clampOmegaForLoad(double omega) {
        double maxOmega = maxOmegaForLoad();
        return Mth.clamp(omega, -maxOmega, maxOmega);
    }

    private double maxOmegaForLoad() {
        double loadMaxOmega = getHoneycombCount() >= MAX_HONEYCOMBS
                ? ONE_COMB_MAX_OMEGA * TWO_COMBS_MAX_OMEGA_MULTIPLIER
                : ONE_COMB_MAX_OMEGA;
        return loadMaxOmega * HoneyExtractorMath.overfillOmegaMultiplier(honeyAmount);
    }

    private void syncVisualAlphas() {
        for (SlotData slot : slots) {
            slot.syncVisualAlphas();
        }
    }

    private void tickInteractionLocks(Level level) {
        if (level.isClientSide || interactionLocks.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, InteractionLock>> iterator = interactionLocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, InteractionLock> entry = iterator.next();
            Player player = level.getPlayerByUUID(entry.getKey());
            if (!(player instanceof ServerPlayer) || !player.isAlive() || player.level() != level) {
                iterator.remove();
                continue;
            }

            InteractionLock lock = entry.getValue();
            player.setPos(lock.x, lock.y, lock.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(lock.yaw);
            player.setXRot(lock.pitch);
            player.yRotO = lock.yaw;
            player.xRotO = lock.pitch;
            player.yHeadRot = lock.yaw;
            player.yHeadRotO = lock.yaw;
        }
    }

    private void refreshStateOnly() {
        stateOnlySync = true;
        try {
            refresh();
        } finally {
            stateOnlySync = false;
        }
    }

    private void spawnHoneyParticles(Level level, BlockState state, double deltaSeconds) {
        double speed = Math.abs(currentOmega);
        int activeHoneycombs = getHoneycombCount();
        if (activeHoneycombs <= 0 || speed < MIN_HONEY_PRODUCING_OMEGA) {
            particleShakeAnim = 0.0D;
            return;
        }

        double basketOmega = speed * 2.0D;
        double particleIntensity = basketOmega / 10.0D;
        particleShakeAnim = (particleShakeAnim
                + speed * deltaSeconds * PARTICLE_PHASE_SPEED * particleIntensity) % 2.0D;
        if (particleShakeAnim <= 0.4D) {
            return;
        }

        int count = (int) (Math.sin((particleShakeAnim - 0.4D) * Math.PI) * 10.0D * particleIntensity);
        if (count <= 0) {
            return;
        }

        RandomSource random = level.random;
        double rotation = Math.toRadians(HoneyExtractorMath.visualYaw(
                state.getValue(com.wxwr.kaleidoscopeagricultureevolution.block.HoneyExtractorBlock.FACING))
                + basketAngle);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double direction = Math.signum(currentOmega);

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.1D + random.nextDouble() * 0.08D;
            double localX = Math.cos(angle) * radius;
            double localZ = Math.sin(angle) * radius;
            double rotatedX = localX * cos - localZ * sin;
            double rotatedZ = localX * sin + localZ * cos;
            double outwardX = rotatedX / radius;
            double outwardZ = rotatedZ / radius;
            double tangentX = -outwardZ * direction;
            double tangentZ = outwardX * direction;

            double x = worldPosition.getX() + 0.5D + rotatedX;
            double y = worldPosition.getY() + 0.2D + random.nextDouble() * 0.9D;
            double z = worldPosition.getZ() + 0.5D + rotatedZ;
            double outwardSpeed = 0.0015D + random.nextDouble() * 0.01D;
            double tangentSpeed = Math.min(0.05D, speed * 0.003D);

            level.addParticle(ModParticles.HONEY_EXTRACTOR.get(), x, y, z,
                    outwardX * outwardSpeed + tangentX * tangentSpeed,
                    0.015D + random.nextDouble() * 0.025D,
                    outwardZ * outwardSpeed + tangentZ * tangentSpeed);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        boolean stateOnly = tag.getBoolean(STATE_ONLY_SYNC_TAG);
        double savedRawOmega = rawOmega;
        double savedCurrentOmega = currentOmega;
        double savedCrankAngle = crankAngle;
        double savedBasketAngle = basketAngle;
        double savedHighSpeedTime = highSpeedTime;
        super.load(tag);
        rawOmega = tag.getDouble(RAW_OMEGA_TAG);
        currentOmega = tag.getDouble(CURRENT_OMEGA_TAG);
        crankAngle = tag.getDouble(CRANK_ANGLE_TAG);
        basketAngle = tag.getDouble(BASKET_ANGLE_TAG);
        honeyAmount = Mth.clamp(tag.getDouble(HONEY_AMOUNT_TAG), 0.0D, HoneyExtractorMath.MAX_HONEY_AMOUNT);
        highSpeedTime = Math.max(0.0D, tag.getDouble(HIGH_SPEED_TIME_TAG));
        loadSlot(tag, 0, slots[0]);
        loadSlot(tag, 1, slots[1]);
        if (stateOnly) {
            rawOmega = savedRawOmega;
            currentOmega = savedCurrentOmega;
            crankAngle = savedCrankAngle;
            basketAngle = savedBasketAngle;
            highSpeedTime = savedHighSpeedTime;
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(STATE_ONLY_SYNC_TAG, stateOnlySync);
        return tag;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble(RAW_OMEGA_TAG, rawOmega);
        tag.putDouble(CURRENT_OMEGA_TAG, currentOmega);
        tag.putDouble(CRANK_ANGLE_TAG, crankAngle);
        tag.putDouble(BASKET_ANGLE_TAG, basketAngle);
        tag.putDouble(HONEY_AMOUNT_TAG, honeyAmount);
        tag.putDouble(HIGH_SPEED_TIME_TAG, highSpeedTime);
        saveSlot(tag, 0, slots[0]);
        saveSlot(tag, 1, slots[1]);
    }

    private void loadSlot(CompoundTag tag, int index, SlotData slot) {
        String contentTag = index == 0 ? SLOT0_CONTENT_TAG : SLOT1_CONTENT_TAG;
        String progressTag = index == 0 ? SLOT0_PROGRESS_TAG : SLOT1_PROGRESS_TAG;
        String honeyAlphaTag = index == 0 ? SLOT0_HONEY_ALPHA_TAG : SLOT1_HONEY_ALPHA_TAG;
        String waxAlphaTag = index == 0 ? SLOT0_BEESWAX_ALPHA_TAG : SLOT1_BEESWAX_ALPHA_TAG;
        String traitsTag = index == 0 ? SLOT0_TRAITS_TAG : SLOT1_TRAITS_TAG;
        String beeswaxCountTag = index == 0 ? SLOT0_BEESWAX_COUNT_TAG : SLOT1_BEESWAX_COUNT_TAG;
        slot.content = SlotContent.fromId(tag.getInt(contentTag));
        slot.progress = Mth.clamp(tag.getDouble(progressTag), 0.0D, 100.0D);
        slot.honeyVisualAlpha = Mth.clamp(tag.getFloat(honeyAlphaTag), 0.0F, 1.0F);
        slot.beeswaxVisualAlpha = Mth.clamp(tag.getFloat(waxAlphaTag), 0.0F, 1.0F);
        slot.traits = tag.contains(traitsTag, net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? HoneyExtractorTraits.fromNBT(tag.getCompound(traitsTag))
                : HoneyExtractorTraits.neutral();
        slot.beeswaxItemCount = Math.max(1, tag.getInt(beeswaxCountTag));
        slot.syncVisualAlphas();
    }

    private void saveSlot(CompoundTag tag, int index, SlotData slot) {
        String contentTag = index == 0 ? SLOT0_CONTENT_TAG : SLOT1_CONTENT_TAG;
        String progressTag = index == 0 ? SLOT0_PROGRESS_TAG : SLOT1_PROGRESS_TAG;
        String honeyAlphaTag = index == 0 ? SLOT0_HONEY_ALPHA_TAG : SLOT1_HONEY_ALPHA_TAG;
        String waxAlphaTag = index == 0 ? SLOT0_BEESWAX_ALPHA_TAG : SLOT1_BEESWAX_ALPHA_TAG;
        String traitsTag = index == 0 ? SLOT0_TRAITS_TAG : SLOT1_TRAITS_TAG;
        String beeswaxCountTag = index == 0 ? SLOT0_BEESWAX_COUNT_TAG : SLOT1_BEESWAX_COUNT_TAG;
        tag.putInt(contentTag, slot.content.id);
        tag.putDouble(progressTag, slot.progress);
        tag.putFloat(honeyAlphaTag, slot.honeyVisualAlpha);
        tag.putFloat(waxAlphaTag, slot.beeswaxVisualAlpha);
        tag.put(traitsTag, slot.traits.toNBT());
        tag.putInt(beeswaxCountTag, slot.beeswaxItemCount);
    }

    private int countSlots(SlotContent content) {
        int count = 0;
        for (SlotData slot : slots) {
            if (slot.content == content) {
                count++;
            }
        }
        return count;
    }

    private double getHighestHoneycombProgress() {
        double highest = 0.0D;
        for (SlotData slot : slots) {
            if (slot.isHoneycomb()) {
                highest = Math.max(highest, slot.progress);
            }
        }
        return highest;
    }

    private SlotData findFirstEmptySlot() {
        for (SlotData slot : slots) {
            if (slot.content == SlotContent.EMPTY) {
                return slot;
            }
        }
        return null;
    }

    private SlotData findRemovableHoneycombSlot() {
        for (SlotData slot : slots) {
            if (slot.isHoneycomb() && slot.progress <= 0.0001D) {
                return slot;
            }
        }
        return null;
    }

    private SlotData findBeeswaxSlot() {
        for (SlotData slot : slots) {
            if (slot.isBeeswax()) {
                return slot;
            }
        }
        return null;
    }

    private int validateSlotIndex(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Slot index out of bounds: " + index);
        }
        return index;
    }

    public enum SlotContent {
        EMPTY(0),
        HONEYCOMB(1),
        BEESWAX(2);

        private final int id;

        SlotContent(int id) {
            this.id = id;
        }

        private static SlotContent fromId(int id) {
            for (SlotContent content : values()) {
                if (content.id == id) {
                    return content;
                }
            }
            return EMPTY;
        }
    }

    public record SlotSnapshot(SlotContent content, double progress, float honeyVisualAlpha,
                               float beeswaxVisualAlpha, int beeswaxItemCount, HoneyExtractorTraits traits) {
    }

    private static final class SlotData {
        private SlotContent content = SlotContent.EMPTY;
        private double progress;
        private float honeyVisualAlpha;
        private float beeswaxVisualAlpha;
        private int beeswaxItemCount = 1;
        private HoneyExtractorTraits traits = HoneyExtractorTraits.neutral();

        private boolean isHoneycomb() {
            return content == SlotContent.HONEYCOMB;
        }

        private boolean isBeeswax() {
            return content == SlotContent.BEESWAX;
        }

        private void setHoneycomb(HoneyExtractorTraits traits) {
            content = SlotContent.HONEYCOMB;
            progress = 0.0D;
            beeswaxItemCount = 1;
            this.traits = traits != null ? traits : HoneyExtractorTraits.neutral();
            syncVisualAlphas();
        }

        private void setBeeswax() {
            content = SlotContent.BEESWAX;
            progress = 0.0D;
            beeswaxItemCount = Math.max(1, (int) Math.round(traits.beeswaxYieldMultiplier()));
            syncVisualAlphas();
        }

        private void clear() {
            content = SlotContent.EMPTY;
            progress = 0.0D;
            honeyVisualAlpha = 0.0F;
            beeswaxVisualAlpha = 0.0F;
            beeswaxItemCount = 1;
            traits = HoneyExtractorTraits.neutral();
        }

        private void syncVisualAlphas() {
            if (content == SlotContent.HONEYCOMB) {
                float transition = (float) Mth.clamp(progress / 100.0D, 0.0D, 1.0D);
                float eased = transition * transition * (3.0F - 2.0F * transition);
                honeyVisualAlpha = 1.0F - eased;
                beeswaxVisualAlpha = eased;
                return;
            }
            if (content == SlotContent.BEESWAX) {
                honeyVisualAlpha = 0.0F;
                beeswaxVisualAlpha = 1.0F;
                return;
            }
            honeyVisualAlpha = 0.0F;
            beeswaxVisualAlpha = 0.0F;
        }

        private SlotSnapshot snapshot() {
            return new SlotSnapshot(content, progress, honeyVisualAlpha, beeswaxVisualAlpha,
                    beeswaxItemCount, traits);
        }
    }

    private record InteractionLock(double x, double y, double z, float yaw, float pitch) {
    }
}
