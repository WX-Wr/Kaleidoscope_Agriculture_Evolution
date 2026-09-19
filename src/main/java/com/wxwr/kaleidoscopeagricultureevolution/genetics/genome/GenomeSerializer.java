package com.wxwr.kaleidoscopeagricultureevolution.genetics.genome;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

public class GenomeSerializer {
    public static final String TAG_KEY = "KaeGenome";
    public static final String TAG_SPECIES = "Species";
    public static final String TAG_DATA = "Data";

    public static CompoundTag serialize(Genome genome) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_SPECIES, genome.getSpecies().getId());
        tag.put(TAG_DATA, new ByteArrayTag(genome.getData()));
        return tag;
    }

    /** @param parent 可为 {@code null}（物品没有 NBT）——语义等同于"没有基因组"。 */
    public static Genome deserialize(@Nullable CompoundTag parent) {
        if (parent == null) return null;
        CompoundTag tag = parent.getCompound(TAG_KEY);
        if (tag.isEmpty()) return null;

        String speciesId = tag.getString(TAG_SPECIES);
        CropSpecies species = CropSpeciesRegistry.get(speciesId);
        if (species == null) return null;

        byte[] data = tag.getByteArray(TAG_DATA);
        return Genome.of(data, species);
    }

    public static boolean hasGenome(@Nullable CompoundTag parent) {
        return parent != null && parent.contains(TAG_KEY, Tag.TAG_COMPOUND);
    }

    public static CompoundTag writeToStack(Genome genome, CompoundTag existingTag) {
        CompoundTag tag = existingTag != null ? existingTag.copy() : new CompoundTag();
        tag.put(TAG_KEY, serialize(genome));
        return tag;
    }
}
