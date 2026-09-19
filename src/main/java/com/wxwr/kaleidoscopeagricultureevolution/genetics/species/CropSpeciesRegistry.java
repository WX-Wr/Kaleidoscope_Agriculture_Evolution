package com.wxwr.kaleidoscopeagricultureevolution.genetics.species;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;

import java.util.*;

public class CropSpeciesRegistry {
    private static final Map<String, CropSpecies> speciesMap = new HashMap<>();
    private static final Map<net.minecraft.world.item.Item, CropSpecies> seedMap = new HashMap<>();
    private static final Map<String, LinkedHashSet<net.minecraft.world.item.Item>> seedItemsBySpecies = new HashMap<>();
    private static final Map<net.minecraft.world.level.block.Block, CropSpecies> blockMap = new HashMap<>();
    private static final Chromosome[] sharedChromosomes = CropSpecies.buildSharedChromosomes();
    private static final Random random = new Random();

    public static void register(CropSpecies species) {
        speciesMap.put(species.getId(), species);
        registerSeedItem(species.getSeedItem(), species);
        if (species.getCropBlock() != null) {
            blockMap.put(species.getCropBlock(), species);
        }
    }

    public static void registerSeedAlias(net.minecraft.world.item.Item item, CropSpecies species) {
        registerSeedItem(item, species);
    }

    private static void registerSeedItem(net.minecraft.world.item.Item item, CropSpecies species) {
        if (item == null || species == null) {
            return;
        }
        seedMap.put(item, species);
        seedItemsBySpecies.computeIfAbsent(species.getId(), id -> new LinkedHashSet<>()).add(item);
    }

    public static void registerBlockAlias(net.minecraft.world.level.block.Block block, CropSpecies species) {
        if (block != null && species != null) {
            blockMap.put(block, species);
        }
    }

    public static CropSpecies get(String id) {
        return speciesMap.get(id);
    }

    public static CropSpecies fromSeed(net.minecraft.world.item.Item item) {
        return seedMap.get(item);
    }

    public static CropSpecies fromBlock(net.minecraft.world.level.block.Block block) {
        return blockMap.get(block);
    }

    public static Collection<CropSpecies> getAll() {
        return Collections.unmodifiableCollection(speciesMap.values());
    }

    public static Collection<net.minecraft.world.item.Item> getSeedItems(CropSpecies species) {
        if (species == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<net.minecraft.world.item.Item> items = seedItemsBySpecies.get(species.getId());
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(items);
    }

    public static Collection<net.minecraft.world.level.block.Block> getRegisteredCropBlocks() {
        return Collections.unmodifiableCollection(blockMap.keySet());
    }

    public static Chromosome[] getSharedChromosomes() {
        return sharedChromosomes;
    }

    public static Genome generateWildGenome(CropSpecies species) {
        Genome.Builder builder = new Genome.Builder(species);
        int totalLoci = species.getTotalLoci();
        for (int flat = 0; flat < totalLoci; flat++) {
            float[] freqs = species.getWildAlleleFrequencies(flat);
            if (freqs == null || freqs.length == 0) {
                builder.setAlleles(flat, 0, 0);
            } else {
                int alleleA = weightedRandomAllele(freqs, random);
                int alleleB = weightedRandomAllele(freqs, random);
                builder.setAlleles(flat, alleleA, alleleB);
            }
        }
        return builder.build();
    }

    private static int weightedRandomAllele(float[] freqs, Random rand) {
        if (freqs.length <= 1) return 0;
        float total = 0;
        for (float f : freqs) total += f;
        float r = rand.nextFloat() * total;
        float cumulative = 0;
        for (int i = 0; i < freqs.length; i++) {
            cumulative += freqs[i];
            if (r <= cumulative) return i;
        }
        return freqs.length - 1;
    }

    public static void clear() {
        speciesMap.clear();
        seedMap.clear();
        seedItemsBySpecies.clear();
        blockMap.clear();
    }
}
