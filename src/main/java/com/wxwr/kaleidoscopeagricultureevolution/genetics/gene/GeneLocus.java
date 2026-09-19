package com.wxwr.kaleidoscopeagricultureevolution.genetics.gene;

public class GeneLocus {
    public static final int QUANTITATIVE_ALLELE_COUNT = 5;

    private final String id;
    private final int chromosomeIndex;
    private final float mapPosition;
    private final GeneType type;
    private final int alleleCount;
    private final float mutationRate;

    public GeneLocus(String id, int chromosomeIndex, float mapPosition, GeneType type,
                     int alleleCount, float mutationRate) {
        this.id = id;
        this.chromosomeIndex = chromosomeIndex;
        this.mapPosition = mapPosition;
        this.type = type;
        this.alleleCount = alleleCount;
        this.mutationRate = mutationRate;
    }

    public static GeneLocus mendelian(String id, int chromosomeIndex, float mapPosition,
                                      int alleleCount, float mutationRate) {
        return new GeneLocus(id, chromosomeIndex, mapPosition, GeneType.MENDELIAN, alleleCount, mutationRate);
    }

    public static GeneLocus quantitative(String id, int chromosomeIndex, float mapPosition, float mutationRate) {
        return new GeneLocus(id, chromosomeIndex, mapPosition, GeneType.QUANTITATIVE,
            QUANTITATIVE_ALLELE_COUNT, mutationRate);
    }

    public String getId() { return id; }
    public int getChromosomeIndex() { return chromosomeIndex; }
    public float getMapPosition() { return mapPosition; }
    public GeneType getType() { return type; }
    public int getAlleleCount() { return alleleCount; }
    public float getMutationRate() { return mutationRate; }
    public int getMaxValue() { return alleleCount - 1; }
}
