package com.wxwr.kaleidoscopeagricultureevolution.genetics.species;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public class BuiltinSpecies {

    public static void registerAll() {
        Chromosome[] chromosomes = CropSpeciesRegistry.getSharedChromosomes();
        int totalLoci = totalLoci(chromosomes);

        registerWheat(chromosomes, totalLoci);
        registerCarrot(chromosomes, totalLoci);
        registerPotato(chromosomes, totalLoci);
        registerBeetroot(chromosomes, totalLoci);
        registerFarmersDelight(chromosomes, totalLoci);
        registerKaleidoscopeCookery(chromosomes, totalLoci);
    }

    private static void registerWheat(Chromosome[] chromosomes, int totalLoci) {
        float[][] wildFreqs = createWildFreqs(totalLoci, chromosomes);
        // 小麦是自花授粉（闭花受精）—— 高自交倾向
        CropSpecies wheat = new CropSpecies("kaleidoscope_agriculture_evolution:wheat", chromosomes,
            Blocks.WHEAT, Items.WHEAT_SEEDS, Items.WHEAT,
            0.70f, 7, wildFreqs);
        CropSpeciesRegistry.register(wheat);
    }

    private static void registerCarrot(Chromosome[] chromosomes, int totalLoci) {
        float[][] wildFreqs = createWildFreqs(totalLoci, chromosomes);
        // 胡萝卜是异花授粉 —— 较低的自交倾向
        CropSpecies carrot = new CropSpecies("kaleidoscope_agriculture_evolution:carrot", chromosomes,
            Blocks.CARROTS, Items.CARROT, Items.CARROT,
            0.30f, 7, wildFreqs);
        CropSpeciesRegistry.register(carrot);
    }

    private static void registerPotato(Chromosome[] chromosomes, int totalLoci) {
        float[][] wildFreqs = createWildFreqs(totalLoci, chromosomes);
        // 马铃薯自交亲和，但受益于异花授粉
        CropSpecies potato = new CropSpecies("kaleidoscope_agriculture_evolution:potato", chromosomes,
            Blocks.POTATOES, Items.POTATO, Items.POTATO,
            0.50f, 7, wildFreqs);
        CropSpeciesRegistry.register(potato);
    }

    private static void registerBeetroot(Chromosome[] chromosomes, int totalLoci) {
        float[][] wildFreqs = createWildFreqs(totalLoci, chromosomes);
        CropSpecies beetroot = new CropSpecies("kaleidoscope_agriculture_evolution:beetroot", chromosomes,
            Blocks.BEETROOTS, Items.BEETROOT_SEEDS, Items.BEETROOT,
            0.65f, 3, wildFreqs);
        CropSpeciesRegistry.register(beetroot);
    }

    private static void registerFarmersDelight(Chromosome[] chromosomes, int totalLoci) {
        CropSpecies tomato = registerOptionalSpecies("farmersdelight:tomato", chromosomes, totalLoci,
                optionalBlock("tomatoes"), optionalItem("tomato_seeds"), optionalItem("tomato"),
                0.35f, 3);
        if (tomato != null) {
            CropSpeciesRegistry.registerBlockAlias(optionalBlock("budding_tomatoes"), tomato);
            CropSpeciesRegistry.registerBlockAlias(optionalBlock("tomatoes_on_rope"), tomato);
        }

        registerOptionalSpecies("farmersdelight:onion", chromosomes, totalLoci,
                optionalBlock("onions"), optionalItem("onion"), optionalItem("onion"),
                0.60f, 7);

        CropSpecies rice = registerOptionalSpecies("farmersdelight:rice", chromosomes, totalLoci,
                optionalBlock("rice_panicles"), optionalItem("rice"), optionalItem("rice_panicle"),
                0.45f, 3);
        if (rice != null) {
            CropSpeciesRegistry.registerBlockAlias(optionalBlock("rice"), rice);
        }
    }

    private static void registerKaleidoscopeCookery(Chromosome[] chromosomes, int totalLoci) {
        registerOptionalSpecies("kaleidoscope_cookery:tomato", chromosomes, totalLoci,
                optionalBlock("kaleidoscope_cookery", "tomato_crop"),
                optionalItem("kaleidoscope_cookery", "tomato_seed"),
                optionalItem("kaleidoscope_cookery", "tomato"),
                0.35f, 7);

        registerOptionalSpecies("kaleidoscope_cookery:chili", chromosomes, totalLoci,
                optionalBlock("kaleidoscope_cookery", "chili_crop"),
                optionalItem("kaleidoscope_cookery", "chili_seed"),
                optionalItem("kaleidoscope_cookery", "red_chili"),
                0.35f, 7);

        registerOptionalSpecies("kaleidoscope_cookery:rice", chromosomes, totalLoci,
                optionalBlock("kaleidoscope_cookery", "rice_crop"),
                optionalItem("kaleidoscope_cookery", "rice"),
                optionalItem("kaleidoscope_cookery", "rice_panicle"),
                0.45f, 7);
        CropSpecies rice = CropSpeciesRegistry.get("kaleidoscope_cookery:rice");
        if (rice != null) {
            CropSpeciesRegistry.registerSeedAlias(optionalItem("kaleidoscope_cookery", "wild_rice"), rice);
        }
    }

    private static CropSpecies registerOptionalSpecies(String id, Chromosome[] chromosomes, int totalLoci,
                                                       Block cropBlock, Item seedItem, Item produceItem,
                                                       float selfPollinationBias, int maxAge) {
        if (cropBlock == null || seedItem == null) return null;
        CropSpecies species = new CropSpecies(id, chromosomes, cropBlock, seedItem, produceItem,
                selfPollinationBias, maxAge, createWildFreqs(totalLoci, chromosomes));
        CropSpeciesRegistry.register(species);
        return species;
    }

    private static Block optionalBlock(String path) {
        return optionalBlock("farmersdelight", path);
    }

    private static Block optionalBlock(String namespace, String path) {
        ResourceLocation id = KaleidoscopeAgricultureEvolution.rl(namespace, path);
        return ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
    }

    private static Item optionalItem(String path) {
        return optionalItem("farmersdelight", path);
    }

    private static Item optionalItem(String namespace, String path) {
        ResourceLocation id = KaleidoscopeAgricultureEvolution.rl(namespace, path);
        return ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
    }

    private static float[][] createWildFreqs(int totalLoci, Chromosome[] chromosomes) {
        float[][] freqs = new float[totalLoci][];
        for (int c = 0; c < chromosomes.length; c++) {
            Chromosome chr = chromosomes[c];
            for (int l = 0; l < chr.getLocusCount(); l++) {
                int flat = getFlatIndex(chromosomes, c, l);
                freqs[flat] = wildFreqsForLocus(chr.getLoci()[l].getType());
            }
        }
        return freqs;
    }

    private static float[] wildFreqsForLocus(GeneType type) {
        if (type == GeneType.MENDELIAN) {
            // 野生型：90% 显性、8% 隐性、2% 稀有等位基因
            return new float[]{0.90f, 0.08f, 0.02f};
        } else {
            // 数量性状：野生型偏向 0-4 刻度的低端。
            return new float[]{
                0.30f, 0.30f, 0.22f, 0.13f, 0.05f
            };
        }
    }

    private static int totalLoci(Chromosome[] chromosomes) {
        int total = 0;
        for (Chromosome c : chromosomes) total += c.getLocusCount();
        return total;
    }

    static int getFlatIndex(Chromosome[] chromosomes, int chr, int locus) {
        int offset = 0;
        for (int i = 0; i < chr; i++) offset += chromosomes[i].getLocusCount();
        return offset + locus;
    }
}
