package com.wxwr.kaleidoscopeagricultureevolution.genetics.storage;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CropGenomeCapability {

    public static final Capability<Storage> CAPABILITY = CapabilityManager.get(
        new CapabilityToken<>() {});
    public static final ResourceLocation KEY =
        KaleidoscopeAgricultureEvolution.rl("crop_genomes");

    public static void registerCapability(RegisterCapabilitiesEvent event) {
        event.register(Storage.class);
    }

    @SubscribeEvent
    public static void attachToChunk(AttachCapabilitiesEvent<net.minecraft.world.level.chunk.LevelChunk> event) {
        LevelChunk chunk = event.getObject();
        event.addCapability(KEY, new Provider(chunk));
    }

    public static Storage get(LevelChunk chunk) {
        return chunk.getCapability(CAPABILITY).orElse(null);
    }

    public static CropGenomeEntry getOrCreate(Level level, BlockPos pos, CropSpecies species) {
        if (level == null || species == null || !level.hasChunkAt(pos)) return null;
        if (CropSpeciesRegistry.fromBlock(level.getBlockState(pos).getBlock()) != species) return null;

        LevelChunk chunk = level.getChunkAt(pos);
        Storage storage = get(chunk);
        if (storage == null) return null;

        CropGenomeEntry entry = storage.get(pos);
        if (entry != null && entry.getGenome() != null) {
            return entry;
        }

        CropGenomeEntry inherited = findVerticalNeighborEntry(level, pos, species);
        Genome genome = inherited != null && inherited.getGenome() != null
                ? inherited.getGenome().copy()
                : CropSpeciesRegistry.generateWildGenome(species);
        entry = new CropGenomeEntry(genome);
        storage.put(pos, entry);
        chunk.setUnsaved(true);
        return entry;
    }

    private static CropGenomeEntry findVerticalNeighborEntry(Level level, BlockPos pos, CropSpecies species) {
        CropGenomeEntry below = getEntryIfSameSpecies(level, pos.below(), species);
        if (below != null) return below;
        return getEntryIfSameSpecies(level, pos.above(), species);
    }

    private static CropGenomeEntry getEntryIfSameSpecies(Level level, BlockPos pos, CropSpecies species) {
        if (!level.hasChunkAt(pos)) return null;
        if (CropSpeciesRegistry.fromBlock(level.getBlockState(pos).getBlock()) != species) return null;
        LevelChunk chunk = level.getChunkAt(pos);
        Storage storage = get(chunk);
        return storage != null ? storage.get(pos) : null;
    }

    public static class Storage {
        private final Long2ObjectOpenHashMap<CropGenomeEntry> entries = new Long2ObjectOpenHashMap<>();

        public CropGenomeEntry get(BlockPos pos) {
            return entries.get(pos.asLong());
        }

        public void put(BlockPos pos, CropGenomeEntry entry) {
            entries.put(pos.asLong(), entry);
        }

        public void remove(BlockPos pos) {
            entries.remove(pos.asLong());
        }

        public boolean hasAt(BlockPos pos) {
            return entries.containsKey(pos.asLong());
        }

        public int size() { return entries.size(); }

        public it.unimi.dsi.fastutil.longs.Long2ObjectMap.FastEntrySet<CropGenomeEntry> allEntries() {
            return entries.long2ObjectEntrySet();
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (var entry : entries.long2ObjectEntrySet()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putLong("Pos", entry.getLongKey());
                entryTag.put("Entry", entry.getValue().toNBT());
                list.add(entryTag);
            }
            tag.put("Entries", list);
            return tag;
        }

        public void deserializeNBT(CompoundTag tag) {
            entries.clear();
            if (!tag.contains("Entries")) return;
            ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                long pos = entryTag.getLong("Pos");
                CropGenomeEntry entry = CropGenomeEntry.fromNBT(entryTag.getCompound("Entry"));
                if (entry != null) {
                    entries.put(pos, entry);
                }
            }
        }

        public void validate(BlockPos pos, Block expectedBlock, net.minecraft.world.level.LevelAccessor level) {
            if (!entries.containsKey(pos.asLong())) return;
            if (!level.getBlockState(pos).is(expectedBlock)) {
                entries.remove(pos.asLong());
            }
        }
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Storage storage;
        private final LazyOptional<Storage> lazy;

        public Provider(LevelChunk chunk) {
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
