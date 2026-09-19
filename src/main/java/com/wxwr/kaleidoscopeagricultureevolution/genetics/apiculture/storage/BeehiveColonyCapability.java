package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyEnvironment;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyHost;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyState;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Stores the genetic colony model alongside a vanilla beehive block entity. */
public final class BeehiveColonyCapability {
    public static final Capability<Storage> CAPABILITY = CapabilityManager.get(
        new CapabilityToken<>() { });
    public static final ResourceLocation KEY =
        KaleidoscopeAgricultureEvolution.rl("beehive_colony");

    private static final int ENVIRONMENT_REFRESH_INTERVAL = 20;
    private static final String TAG_HOST = "Host";
    private static final String TAG_STATE = "State";
    private static final String TAG_INITIALIZED = "Initialized";
    private static final String TAG_OBSERVED_COUNT = "ObservedCount";

    private BeehiveColonyCapability() {
    }

    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(Storage.class);
    }

    @SubscribeEvent
    public static void attachToBeehive(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof BeehiveBlockEntity hive) {
            event.addCapability(KEY, new Provider(hive));
        }
    }

    @Nullable
    public static Storage get(BeehiveBlockEntity hive) {
        return hive.getCapability(CAPABILITY).orElse(null);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BeehiveBlockEntity hive) {
        if (level.isClientSide()) {
            return;
        }

        Storage storage = get(hive);
        if (storage != null && storage.tick(level, pos, state, hive)) {
            hive.setChanged();
        }
    }

    public static final class Storage {
        private final BeeColonyHost host = new BeeColonyHost();
        private final BeeColonyState state = new BeeColonyState();
        private BeeColonyEnvironment environment = BeeColonyEnvironment.neutral(BeePhenotype.neutral());
        private boolean initialized;
        private int observedCount = -1;
        private long lastEnvironmentTick = Long.MIN_VALUE;

        public BeeColonyHost getHost() {
            return host;
        }

        public BeeColonyState getState() {
            return state;
        }

        public BeeColonyEnvironment getEnvironment() {
            return environment;
        }

        public BeePhenotype getPhenotype() {
            return host.getPhenotype();
        }

        public int getBeeCount() {
            return host.getColonySize();
        }

        public void addBee(BeeGenomeData genome) {
            if (genome == null) {
                return;
            }
            host.addBee(genome);
            initialized = true;
        }

        public boolean tick(Level level, BlockPos pos, BlockState blockState, BeehiveBlockEntity hive) {
            boolean changed = synchronizeOccupants(hive);
            BeePhenotype phenotype = host.getPhenotype();

            if (level.getGameTime() - lastEnvironmentTick >= ENVIRONMENT_REFRESH_INTERVAL
                || !environment.speciesId().equals(phenotype.speciesId())) {
                environment = BeeColonyEnvironment.evaluate(level, pos, phenotype);
                lastEnvironmentTick = level.getGameTime();
                changed = true;
            }

            if (host.tick(level.random, hive.getOccupantCount())) {
                changed = true;
            }
            phenotype = host.getPhenotype();
            if (state.tick(phenotype, host.getColonySize(), level.random, environment)) {
                changed = true;
            }
            return changed;
        }

        /** Synchronizes only when vanilla occupants enter or leave, preserving simulated genes otherwise. */
        public boolean synchronizeOccupants(BeehiveBlockEntity hive) {
            int currentCount = hive.getOccupantCount();
            if (initialized && observedCount == currentCount) {
                return false;
            }

            List<BeeGenomeData> genomes = readStoredGenomes(hive);
            if (!initialized || observedCount < 0) {
                host.setBees(genomes);
            } else if (currentCount < observedCount) {
                host.trimToSize(currentCount);
            } else if (currentCount > observedCount) {
                for (int i = observedCount; i < genomes.size(); i++) {
                    host.addBee(genomes.get(i));
                }
            } else if (host.getColonySize() != currentCount) {
                host.setBees(genomes);
            }

            initialized = true;
            boolean changed = observedCount != currentCount || host.getColonySize() != currentCount;
            observedCount = currentCount;
            state.sync(host.getPhenotype(), host.getColonySize());
            return changed;
        }

        public void markOccupantAdded(int count) {
            observedCount = Math.max(0, count);
            initialized = true;
            state.sync(host.getPhenotype(), host.getColonySize());
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_HOST, host.toNBT());
            tag.put(TAG_STATE, state.toNBT());
            tag.putBoolean(TAG_INITIALIZED, initialized);
            tag.putInt(TAG_OBSERVED_COUNT, observedCount);
            return tag;
        }

        public void deserializeNBT(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return;
            }
            if (tag.contains(TAG_HOST, Tag.TAG_COMPOUND)) {
                host.fromNBT(tag.getCompound(TAG_HOST));
            }
            if (tag.contains(TAG_STATE, Tag.TAG_COMPOUND)) {
                state.fromNBT(tag.getCompound(TAG_STATE));
            }
            initialized = tag.getBoolean(TAG_INITIALIZED);
            observedCount = tag.contains(TAG_OBSERVED_COUNT, Tag.TAG_INT)
                ? tag.getInt(TAG_OBSERVED_COUNT) : -1;
            environment = BeeColonyEnvironment.neutral(host.getPhenotype());
            lastEnvironmentTick = Long.MIN_VALUE;
        }

        private static List<BeeGenomeData> readStoredGenomes(BeehiveBlockEntity hive) {
            List<BeeGenomeData> genomes = new ArrayList<>();
            ListTag stored = hive.writeBees();
            for (int i = 0; i < stored.size(); i++) {
                CompoundTag entry = stored.getCompound(i);
                CompoundTag entityData = entry.getCompound(BeehiveBlockEntity.ENTITY_DATA);
                BeeGenomeData genome = BeeGenomeCapability.readFromEntityTag(entityData);
                if (genome == null) {
                    genome = BeeGenomeData.createWild(seedFor(hive.getBlockPos(), i));
                }
                genomes.add(genome);
            }
            return genomes;
        }

        private static long seedFor(BlockPos pos, int index) {
            return pos.asLong() ^ (0x9E3779B97F4A7C15L * (index + 1L));
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Storage storage = new Storage();
        private final LazyOptional<Storage> lazy = LazyOptional.of(() -> storage);

        @SuppressWarnings("unused")
        private Provider(BeehiveBlockEntity hive) {
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == CAPABILITY ? lazy.cast() : LazyOptional.empty();
        }

        @Override
        @NotNull
        public CompoundTag serializeNBT() {
            return storage.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            storage.deserializeNBT(nbt);
        }
    }
}
