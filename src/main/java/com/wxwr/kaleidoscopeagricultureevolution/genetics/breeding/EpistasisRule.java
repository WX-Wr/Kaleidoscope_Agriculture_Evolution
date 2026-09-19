package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

public interface EpistasisRule {
    void apply(Genome genome, CropSpecies species, PhenotypeBuilder builder);
}
