package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Temporary crop pollen carried by a bee.
 *
 * <p>The payload is deliberately separate from bee genetics. It is a bridge
 * value that can be consumed by the existing crop crossing pipeline.</p>
 */
public record BeePollenPayload(ResourceLocation sourceCropSpeciesId,
                               Genome pollenGenome,
                               int freshnessTicks,
                               @Nullable BlockPos sourcePosition) {
    public static final int DEFAULT_FRESHNESS_TICKS = 1200;
    public static final int MAX_FRESHNESS_TICKS = 24000;

    private static final String TAG_SOURCE_SPECIES = "SourceCropSpecies";
    private static final String TAG_GENOME = "Genome";
    private static final String TAG_SPECIES = "Species";
    private static final String TAG_DATA = "Data";
    private static final String TAG_FRESHNESS = "Freshness";
    private static final String TAG_SOURCE_POS = "SourcePos";

    public BeePollenPayload {
        sourceCropSpeciesId = sourceCropSpeciesId != null
            ? sourceCropSpeciesId
            : pollenGenome != null
                ? ResourceLocation.tryParse(pollenGenome.getSpecies().getId())
                : null;
        pollenGenome = pollenGenome != null ? pollenGenome.copy() : null;
        freshnessTicks = Mth.clamp(freshnessTicks, 0, MAX_FRESHNESS_TICKS);
        sourcePosition = sourcePosition != null ? sourcePosition.immutable() : null;
    }

    @NotNull
    public static BeePollenPayload create(Genome pollenGenome, @Nullable BlockPos sourcePosition) {
        return create(pollenGenome, sourcePosition, DEFAULT_FRESHNESS_TICKS);
    }

    @NotNull
    public static BeePollenPayload create(Genome pollenGenome, @Nullable BlockPos sourcePosition,
                                          int freshnessTicks) {
        ResourceLocation speciesId = pollenGenome == null
            ? null
            : ResourceLocation.tryParse(pollenGenome.getSpecies().getId());
        return new BeePollenPayload(speciesId, pollenGenome, freshnessTicks, sourcePosition);
    }

    @NotNull
    public BeePollenPayload copy() {
        return new BeePollenPayload(sourceCropSpeciesId, pollenGenome, freshnessTicks, sourcePosition);
    }

    @NotNull
    public Genome getPollenGenomeCopy() {
        return pollenGenome.copy();
    }

    public boolean isFresh() {
        return pollenGenome != null && freshnessTicks > 0;
    }

    public boolean isCompatibleWith(@Nullable CropSpecies species) {
        if (!isFresh() || species == null || sourceCropSpeciesId == null) {
            return false;
        }
        ResourceLocation targetId = ResourceLocation.tryParse(species.getId());
        return sourceCropSpeciesId.equals(targetId)
            && pollenGenome.getSpecies() == species;
    }

    @NotNull
    public BeePollenPayload decay(int ticks) {
        if (ticks <= 0) {
            return copy();
        }
        return new BeePollenPayload(sourceCropSpeciesId, pollenGenome,
            freshnessTicks - ticks, sourcePosition);
    }

    @NotNull
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        if (sourceCropSpeciesId != null) {
            tag.putString(TAG_SOURCE_SPECIES, sourceCropSpeciesId.toString());
        }
        tag.putInt(TAG_FRESHNESS, freshnessTicks);
        if (sourcePosition != null) {
            tag.putLong(TAG_SOURCE_POS, sourcePosition.asLong());
        }
        if (pollenGenome != null) {
            CompoundTag genomeTag = new CompoundTag();
            genomeTag.putString(TAG_SPECIES, pollenGenome.getSpecies().getId());
            genomeTag.putByteArray(TAG_DATA, pollenGenome.getData());
            tag.put(TAG_GENOME, genomeTag);
        }
        return tag;
    }

    @Nullable
    public static BeePollenPayload fromNBT(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return null;
        }

        CompoundTag genomeTag = tag.getCompound(TAG_GENOME);
        if (genomeTag.isEmpty()) {
            return null;
        }

        CropSpecies species = CropSpeciesRegistry.get(genomeTag.getString(TAG_SPECIES));
        if (species == null) {
            return null;
        }

        Genome genome = Genome.of(genomeTag.getByteArray(TAG_DATA), species);
        ResourceLocation sourceSpecies = ResourceLocation.tryParse(
            tag.getString(TAG_SOURCE_SPECIES));
        if (sourceSpecies == null) {
            sourceSpecies = ResourceLocation.tryParse(species.getId());
        }

        BlockPos sourcePosition = tag.contains(TAG_SOURCE_POS)
            ? BlockPos.of(tag.getLong(TAG_SOURCE_POS))
            : null;
        return new BeePollenPayload(sourceSpecies, genome,
            tag.getInt(TAG_FRESHNESS), sourcePosition);
    }
}
