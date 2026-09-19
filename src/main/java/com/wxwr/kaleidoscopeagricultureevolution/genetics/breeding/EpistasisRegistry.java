package com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.ArrayList;
import java.util.List;

public class EpistasisRegistry {
    private final List<EpistasisRule> rules = new ArrayList<>();

    public void register(EpistasisRule rule) {
        rules.add(rule);
    }

    public void applyAll(Genome genome, CropSpecies species, PhenotypeBuilder builder) {
        for (EpistasisRule rule : rules) {
            rule.apply(genome, species, builder);
        }
    }

    public static EpistasisRegistry createDefault() {
        // 所有非生长/产量基因当前都已注释掉 —— 没有激活的上位效应规则
        return new EpistasisRegistry();
    }
}
