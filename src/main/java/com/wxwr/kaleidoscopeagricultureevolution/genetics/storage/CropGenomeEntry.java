package com.wxwr.kaleidoscopeagricultureevolution.genetics.storage;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import net.minecraft.nbt.CompoundTag;

public class CropGenomeEntry {
    private Genome genome;
    private Genome pollenGenome;
    private boolean pollinated;

    public CropGenomeEntry(Genome genome) {
        this.genome = genome;
        this.pollenGenome = null;
        this.pollinated = false;
    }

    public Genome getGenome() { return genome; }
    public void setGenome(Genome g) { this.genome = g; }

    public Genome getPollenGenome() { return pollenGenome; }
    public void setPollenGenome(Genome pg) { this.pollenGenome = pg; }
    public boolean hasPollen() { return pollenGenome != null; }
    public boolean isPollinated() { return pollinated; }
    public void setPollinated(boolean pollinated) { this.pollinated = pollinated; }

    public static CropGenomeEntry fromNBT(CompoundTag tag) {
        CompoundTag genomeTag = tag.getCompound("Genome");
        CompoundTag pollenTag = tag.getCompound("Pollen");

        CropSpecies species = genomeTag.isEmpty() ? null : CropSpeciesRegistry.get(genomeTag.getString("Species"));
        if (species == null) return null;

        Genome genome = Genome.of(genomeTag.getByteArray("Data"), species);
        if (genome == null) return null;

        CropGenomeEntry entry = new CropGenomeEntry(genome);
        entry.setPollinated(tag.getBoolean("Pollinated"));
        if (!pollenTag.isEmpty()) {
            CropSpecies pollenSpecies = CropSpeciesRegistry.get(pollenTag.getString("Species"));
            Genome pollen = pollenSpecies != null ? Genome.of(pollenTag.getByteArray("Data"), pollenSpecies) : null;
            if (pollen != null) entry.setPollenGenome(pollen);
        }
        return entry;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        CompoundTag genomeTag = new CompoundTag();
        genomeTag.putString("Species", genome.getSpecies().getId());
        genomeTag.putByteArray("Data", genome.getData());
        tag.put("Genome", genomeTag);
        tag.putBoolean("Pollinated", pollinated);

        if (pollenGenome != null) {
            CompoundTag pollenTag = new CompoundTag();
            pollenTag.putString("Species", pollenGenome.getSpecies().getId());
            pollenTag.putByteArray("Data", pollenGenome.getData());
            tag.put("Pollen", pollenTag);
        }
        return tag;
    }
}
