package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination.BeePollenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BeeGenomeCapability {
    public static final Capability<Storage> CAPABILITY = CapabilityManager.get(
        new CapabilityToken<>() {});
    public static final ResourceLocation KEY =
        KaleidoscopeAgricultureEvolution.rl("bee_genome");

    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(Storage.class);
    }

    @SubscribeEvent
    public static void attachToBee(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Bee bee) {
            event.addCapability(KEY, new Provider(bee));
        }
    }

    public static Storage get(Bee bee) {
        return bee.getCapability(CAPABILITY).orElse(null);
    }

    public static BeeGenomeData getOrCreate(Bee bee) {
        Storage storage = get(bee);
        return storage != null ? storage.getOrCreate(bee) : null;
    }

    @Nullable
    public static BeeGenomeData readFromEntityTag(@Nullable CompoundTag entityTag) {
        if (entityTag == null || !entityTag.contains("ForgeCaps", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag caps = entityTag.getCompound("ForgeCaps");
        if (!caps.contains(KEY.toString(), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        return BeeGenomeData.fromNBT(caps.getCompound(KEY.toString()));
    }

    public static void writeToEntityTag(CompoundTag entityTag, @Nullable BeeGenomeData data) {
        if (entityTag == null || data == null) {
            return;
        }
        CompoundTag caps = entityTag.contains("ForgeCaps", net.minecraft.nbt.Tag.TAG_COMPOUND)
            ? entityTag.getCompound("ForgeCaps")
            : new CompoundTag();
        caps.put(KEY.toString(), data.toNBT());
        entityTag.put("ForgeCaps", caps);
    }

    public static class Storage {
        private BeeGenomeData data;
        private BeePollenPayload pollen;
        private BlockPos pollinationTarget;
        private int pollinationTargetAge;
        private int lastPollinationSearchTick = Integer.MIN_VALUE;

        public BeeGenomeData get() {
            return data;
        }

        public BeeGenomeData getOrCreate(Bee bee) {
            if (data == null) {
                data = BeeGenomeData.createWild(seedFromBee(bee));
            }
            return data;
        }

        public void set(BeeGenomeData data) {
            this.data = data;
        }

        @Nullable
        public BeePollenPayload getPollen() {
            return pollen;
        }

        public void setPollen(@Nullable BeePollenPayload pollen) {
            this.pollen = pollen != null ? pollen.copy() : null;
        }

        public boolean hasFreshPollen() {
            return pollen != null && pollen.isFresh();
        }

        public void clearPollen() {
            pollen = null;
        }

        @Nullable
        public BlockPos getPollinationTarget() {
            return pollinationTarget;
        }

        public void setPollinationTarget(@Nullable BlockPos target) {
            pollinationTarget = target != null ? target.immutable() : null;
            pollinationTargetAge = 0;
        }

        public void clearPollinationTarget() {
            pollinationTarget = null;
            pollinationTargetAge = 0;
        }

        public boolean isPollinationTargetRefreshDue(int currentTick, int refreshInterval) {
            if (pollinationTarget == null) {
                return lastPollinationSearchTick == Integer.MIN_VALUE
                    || (long) currentTick - lastPollinationSearchTick >= refreshInterval;
            }
            return pollinationTargetAge >= refreshInterval;
        }

        public void markPollinationSearch(int currentTick) {
            lastPollinationSearchTick = currentTick;
            pollinationTargetAge = 0;
        }

        public void agePollinationTarget() {
            if (pollinationTarget != null && pollinationTargetAge < Integer.MAX_VALUE) {
                pollinationTargetAge++;
            }
        }

        public boolean tickPollen(int ticks) {
            if (pollen == null || ticks <= 0) {
                return false;
            }
            BeePollenPayload decayed = pollen.decay(ticks);
            if (!decayed.isFresh()) {
                pollen = null;
            } else {
                pollen = decayed;
            }
            return true;
        }

        public boolean hasGenome() {
            return data != null;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = data != null ? data.toNBT() : new CompoundTag();
            if (pollen != null) {
                tag.put("Pollen", pollen.toNBT());
            }
            return tag;
        }

        public void deserializeNBT(CompoundTag tag) {
            data = BeeGenomeData.fromNBT(tag);
            pollen = tag != null && tag.contains("Pollen", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? BeePollenPayload.fromNBT(tag.getCompound("Pollen"))
                : null;
        }

        private static long seedFromBee(Bee bee) {
            UUID uuid = bee.getUUID();
            long seed = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
            seed ^= bee.blockPosition().asLong();
            seed ^= bee.level().dimension().location().hashCode();
            return seed;
        }
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Storage storage;
        private final LazyOptional<Storage> lazy;

        public Provider(Bee bee) {
            this.storage = new Storage();
            this.lazy = LazyOptional.of(() -> storage);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            if (cap == CAPABILITY) {
                return lazy.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return storage.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            storage.deserializeNBT(nbt);
        }
    }
}
