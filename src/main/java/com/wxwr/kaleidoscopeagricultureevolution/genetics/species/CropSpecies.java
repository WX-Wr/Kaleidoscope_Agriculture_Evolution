package com.wxwr.kaleidoscopeagricultureevolution.genetics.species;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CropSpecies {
    private final String id;
    private final Chromosome[] chromosomes;
    private final int[] flatIndexOffsets;
    private final int totalLoci;
    private final Block cropBlock;
    private final Item seedItem;
    private final Item produceItem;
    private final float selfPollinationBias;
    private final int maxAge;
    private final float[][] wildAlleleFrequencies;

    public CropSpecies(String id, Chromosome[] chromosomes, Block cropBlock, Item seedItem,
                       Item produceItem, float selfPollinationBias, int maxAge,
                       float[][] wildAlleleFrequencies) {
        this.id = id;
        this.chromosomes = chromosomes;
        this.cropBlock = cropBlock;
        this.seedItem = seedItem;
        this.produceItem = produceItem;
        this.selfPollinationBias = selfPollinationBias;
        this.maxAge = maxAge;
        this.wildAlleleFrequencies = wildAlleleFrequencies;

        this.flatIndexOffsets = new int[chromosomes.length];
        int offset = 0;
        for (int i = 0; i < chromosomes.length; i++) {
            flatIndexOffsets[i] = offset;
            offset += chromosomes[i].getLocusCount();
        }
        this.totalLoci = offset;
    }

    public String getId() { return id; }
    public Chromosome[] getChromosomes() { return chromosomes; }
    public int getTotalLoci() { return totalLoci; }
    public Block getCropBlock() { return cropBlock; }
    public Item getSeedItem() { return seedItem; }
    public Item getProduceItem() { return produceItem; }
    public float getSelfPollinationBias() { return selfPollinationBias; }
    public int getMaxAge() { return maxAge; }

    public int getFlatLocusIndex(int chromosomeIndex, int locusIndex) {
        if (chromosomeIndex < 0 || chromosomeIndex >= chromosomes.length) return -1;
        if (locusIndex < 0 || locusIndex >= chromosomes[chromosomeIndex].getLocusCount()) return -1;
        return flatIndexOffsets[chromosomeIndex] + locusIndex;
    }

    public int getChromosomeFromFlat(int flatIndex) {
        for (int c = chromosomes.length - 1; c >= 0; c--) {
            if (flatIndex >= flatIndexOffsets[c]) return c;
        }
        return 0;
    }

    public int getLocusFromFlat(int flatIndex) {
        int chr = getChromosomeFromFlat(flatIndex);
        return flatIndex - flatIndexOffsets[chr];
    }

    public GeneLocus getLocus(int flatIndex) {
        int chr = getChromosomeFromFlat(flatIndex);
        int locus = flatIndex - flatIndexOffsets[chr];
        return chromosomes[chr].getLoci()[locus];
    }

    public float[] getWildAlleleFrequencies(int flatIndex) {
        if (flatIndex < 0 || flatIndex >= wildAlleleFrequencies.length) return null;
        return wildAlleleFrequencies[flatIndex];
    }

    public static Chromosome[] buildSharedChromosomes() {
        return new Chromosome[]{
            new Chromosome(0, "生长", new GeneLocus[]{
                GeneLocus.quantitative("生长速度", 0, 0f, 0.005f),
//                GeneLocus.mendelian("height", 0, 25f, 2, 0.002f),
//                GeneLocus.mendelian("photoperiod", 0, 45f, 2, 0.002f),
            }),
            new Chromosome(1, "产量", new GeneLocus[]{
                GeneLocus.quantitative("产量", 1, 0f, 0.005f),
                GeneLocus.quantitative("果实大小", 1, 22f, 0.005f),
                GeneLocus.quantitative("结实率", 1, 40f, 0.005f),
//                GeneLocus.mendelian("determinate", 1, 55f, 2, 0.002f),
            }),
//            new Chromosome(2, "Resistance", new GeneLocus[]{
//                GeneLocus.mendelian("fungal_res", 2, 0f, 2, 0.002f),
//                GeneLocus.quantitative("blight_tol", 2, 20f, 0.005f),
//                GeneLocus.quantitative("pest_res", 2, 40f, 0.005f),
//            }),
//            new Chromosome(3, "Environment", new GeneLocus[]{
//                GeneLocus.quantitative("drought_tol", 3, 0f, 0.005f),
//                GeneLocus.quantitative("cold_tol", 3, 20f, 0.005f),
//                GeneLocus.quantitative("heat_tol", 3, 40f, 0.005f),
//                GeneLocus.quantitative("shade_tol", 3, 60f, 0.005f),
//            }),
//            new Chromosome(4, "Quality", new GeneLocus[]{
//                GeneLocus.quantitative("protein", 4, 0f, 0.005f),
//                GeneLocus.quantitative("sweetness", 4, 20f, 0.005f),
//                GeneLocus.quantitative("shelf_life", 4, 40f, 0.005f),
//            }),
//            new Chromosome(5, "Color", new GeneLocus[]{
//                GeneLocus.mendelian("grain_color", 5, 0f, 3, 0.003f),
//                GeneLocus.mendelian("flesh_color", 5, 15f, 3, 0.003f),
//                GeneLocus.quantitative("leaf_tint", 5, 35f, 0.005f),
//                GeneLocus.mendelian("anthocyanin", 5, 50f, 2, 0.002f),
//            }),
//            new Chromosome(6, "Development", new GeneLocus[]{
//                GeneLocus.quantitative("maturation", 6, 0f, 0.005f),
//                GeneLocus.mendelian("shattering", 6, 20f, 2, 0.002f),
//                GeneLocus.mendelian("vernalization", 6, 40f, 2, 0.002f),
//            }),
        };
    }
}
