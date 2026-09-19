package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.breeding.BeeBreedingEngine;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraitSource;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.honeyextractor.HoneyExtractorTraits;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BeeColonyHost implements HoneyExtractorTraitSource {
    private static final String TAG_BEES = "Bees";
    private static final String TAG_PHENOTYPE = "Phenotype";

    private final List<BeeGenomeData> bees = new ArrayList<>();
    private BeePhenotype cachedPhenotype = BeePhenotype.neutral();
    private boolean phenotypeDirty = true;
    private int colonyAgeTicks;

    public BeeColonyHost() {
    }

    public BeeColonyHost(List<BeeGenomeData> bees) {
        setBees(bees);
    }

    public List<BeeGenomeData> getBees() {
        return Collections.unmodifiableList(bees);
    }

    public void setBees(List<BeeGenomeData> genomes) {
        bees.clear();
        if (genomes != null) {
            for (BeeGenomeData genome : genomes) {
                if (genome != null) {
                    bees.add(genome);
                }
            }
        }
        phenotypeDirty = true;
    }

    public void addBee(BeeGenomeData genome) {
        if (genome == null) {
            return;
        }
        bees.add(genome);
        phenotypeDirty = true;
    }

    public boolean markAllAnalyzed() {
        boolean changed = false;
        for (int i = 0; i < bees.size(); i++) {
            BeeGenomeData genome = bees.get(i);
            if (!genome.isAnalyzed()) {
                bees.set(i, genome.withAnalyzed(true));
                changed = true;
            }
        }
        return changed;
    }

    public void trimToSize(int maxBees) {
        int target = Math.max(0, maxBees);
        if (bees.size() > target) {
            bees.subList(target, bees.size()).clear();
            phenotypeDirty = true;
        }
    }

    public BeePhenotype getPhenotype() {
        if (phenotypeDirty) {
            cachedPhenotype = evaluatePhenotype();
            phenotypeDirty = false;
        }
        return cachedPhenotype;
    }

    public int getColonySize() {
        return bees.size();
    }

    public int getColonyAgeTicks() {
        return colonyAgeTicks;
    }

    public boolean tick(RandomSource random) {
        return tick(random, 8);
    }

    public boolean tick(RandomSource random, int maxBees) {
        colonyAgeTicks++;
        if (random == null || bees.isEmpty()) {
            return false;
        }

        int colonyLimit = Math.max(0, maxBees);
        boolean changed = false;
        if (bees.size() >= 2 && random.nextFloat() < getPhenotype().stability() * 0.01D) {
            int first = random.nextInt(bees.size());
            int second = random.nextInt(bees.size() - 1);
            if (second >= first) {
                second++;
            }
            BeeGenomeData child = BeeBreedingEngine.breed(bees.get(first), bees.get(second),
                    new java.util.Random(random.nextLong())).orElse(null);
            if (child != null) {
                bees.set(first, child);
                phenotypeDirty = true;
                changed = true;
            }
        } else if (bees.size() < colonyLimit && random.nextFloat() < getPhenotype().specialProductChance()) {
            bees.add(bees.get(random.nextInt(bees.size())));
            phenotypeDirty = true;
            changed = true;
        }
        return changed;
    }

    @Override
    @NotNull
    public HoneyExtractorTraits getHoneyExtractorTraits(@NotNull ItemStack stack) {
        return getPhenotype().toHoneyExtractorTraits();
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Age", colonyAgeTicks);
        tag.put(TAG_PHENOTYPE, getPhenotype().toHoneyExtractorTraits().toNBT());
        ListTag list = new ListTag();
        for (BeeGenomeData genome : bees) {
            list.add(genome.toNBT());
        }
        tag.put(TAG_BEES, list);
        return tag;
    }

    public void fromNBT(CompoundTag tag) {
        bees.clear();
        if (tag == null || tag.isEmpty()) {
            phenotypeDirty = true;
            return;
        }

        colonyAgeTicks = Math.max(0, tag.getInt("Age"));
        if (tag.contains(TAG_BEES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_BEES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                BeeGenomeData genome = BeeGenomeData.fromNBT(list.getCompound(i));
                if (genome != null) {
                    bees.add(genome);
                }
            }
        }
        phenotypeDirty = true;
    }

    private BeePhenotype evaluatePhenotype() {
        if (bees.isEmpty()) {
            return BeePhenotype.neutral();
        }

        BeePhenotype aggregate = BeePhenotype.ofGenome(bees.get(0));
        double size = bees.size();
        double foraging = 0.0D;
        double honey = 0.0D;
        double wax = 0.0D;
        double stability = 0.0D;
        double pollination = 0.0D;
        for (BeeGenomeData genome : bees) {
            BeePhenotype phenotype = BeePhenotype.ofGenome(genome);
            foraging += phenotype.foragingEfficiency();
            honey += phenotype.honeyYieldMult();
            wax += phenotype.waxYieldMult();
            stability += phenotype.stability();
            pollination += phenotype.pollinationEfficiency();
        }

        return new BeePhenotype(
            aggregate.speciesId(),
            aggregate.precipitationTolerance(),
            aggregate.nocturnalActivity(),
            aggregate.flowerAffinity(),
            foraging / size,
            pollination / size,
            honey / size,
            wax / size,
            aggregate.pollenYieldMult(),
            aggregate.lifespanMult(),
            aggregate.diseaseResistance(),
            aggregate.aggression(),
            stability / size,
            aggregate.specialProductChance()
        );
    }
}
