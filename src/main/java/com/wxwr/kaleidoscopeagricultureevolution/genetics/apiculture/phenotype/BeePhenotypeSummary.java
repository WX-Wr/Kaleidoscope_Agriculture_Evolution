package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class BeePhenotypeSummary {
    private BeePhenotypeSummary() {
    }

    public static MutableComponent create(BeePhenotype phenotype) {
        BeePhenotype safe = phenotype != null ? phenotype : BeePhenotype.neutral();
        MutableComponent summary = Component.literal("")
            .append(Component.literal("  "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_phenotype_summary"))
            .append(Component.literal(" "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_foraging"))
            .append(Component.literal(" " + safe.foragingGrade()))
            .append(Component.literal("  "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_honey_yield"))
            .append(Component.literal(" " + safe.honeyGrade()))
            .append(Component.literal("  "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_wax_yield"))
            .append(Component.literal(" " + safe.waxGrade()))
            .append(Component.literal("  "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_stability"))
            .append(Component.literal(" " + safe.stabilityGrade()));

        return summary.append(Component.literal("\n  "))
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_phenotype_values",
                String.format(Locale.ROOT, "%.2fx", safe.foragingEfficiency()),
                String.format(Locale.ROOT, "%.2fx", safe.honeyYieldMult()),
                String.format(Locale.ROOT, "%.2fx", safe.waxYieldMult()),
                String.format(Locale.ROOT, "%.0f%%", safe.stability() * 100.0D)));
    }
}
