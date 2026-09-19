package com.wxwr.kaleidoscopeagricultureevolution.genetics.gene;

public class Chromosome {
    private final int index;
    private final String name;
    private final GeneLocus[] loci;

    public Chromosome(int index, String name, GeneLocus[] loci) {
        this.index = index;
        this.name = name;
        this.loci = loci;
    }

    public int getIndex() { return index; }
    public String getName() { return name; }
    public GeneLocus[] getLoci() { return loci; }
    public int getLocusCount() { return loci.length; }

    public double haldaneDistance(int fromLocus, int toLocus) {
        if (fromLocus == toLocus) return 0;
        float d = Math.abs(loci[toLocus].getMapPosition() - loci[fromLocus].getMapPosition());
        double morgans = d / 100.0;
        return 0.5 * (1.0 - Math.exp(-2.0 * morgans));
    }

    public double crossoverProbability(int locusIndex) {
        if (locusIndex <= 0 || locusIndex >= loci.length) return 0;
        return haldaneDistance(locusIndex - 1, locusIndex);
    }
}
