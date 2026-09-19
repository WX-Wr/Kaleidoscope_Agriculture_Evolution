package com.wxwr.kaleidoscopeagricultureevolution.genetics;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Phenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import net.minecraft.world.level.block.Blocks;

public final class HighStrawWheatRules {
    private HighStrawWheatRules() {
    }

    public static boolean isCandidate(CropSpecies species, Phenotype phenotype) {
        return isHighYieldCropCandidate(species, phenotype);
    }

    public static boolean isHighYieldCropCandidate(CropSpecies species, Phenotype phenotype) {
        return species != null
            && phenotype != null
            && (species.getCropBlock() == Blocks.WHEAT
                || species.getCropBlock() == Blocks.CARROTS
                || species.getCropBlock() == Blocks.POTATOES
                || species.getCropBlock() == Blocks.BEETROOTS
                || "farmersdelight:rice".equals(species.getId())
                || "farmersdelight:tomato".equals(species.getId())
                || "farmersdelight:onion".equals(species.getId())
                || "kaleidoscope_cookery:tomato".equals(species.getId())
                || "kaleidoscope_cookery:chili".equals(species.getId())
                || "kaleidoscope_cookery:rice".equals(species.getId()))
            && isHighYieldGrade(phenotype.yieldGrade());
    }

    public static boolean isHighStrawWheatCandidate(CropSpecies species, Phenotype phenotype) {
        return species != null
            && phenotype != null
            && species.getCropBlock() == Blocks.WHEAT
            && isHighYieldGrade(phenotype.yieldGrade());
    }

    public static boolean isHighYieldGrade(char grade) {
        return grade == 'A' || grade == 'S';
    }
}
