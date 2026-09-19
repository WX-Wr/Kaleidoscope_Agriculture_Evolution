package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record BeeColonyEnvironment(ResourceLocation speciesId,
                                   boolean biomeMatched,
                                   boolean flowerMatched,
                                   int flowerCount,
                                   double productionMultiplier,
                                   double pollinationMultiplier,
                                   double stabilityModifier,
                                   double diseasePressure) {
    private static final int FLOWER_HORIZONTAL_RADIUS = 5;
    private static final int FLOWER_VERTICAL_RADIUS = 2;
    private static final int FLOWER_SOFT_CAP = 8;

    public BeeColonyEnvironment {
        BeePhenotype neutral = BeePhenotype.neutral();
        speciesId = speciesId != null ? speciesId : neutral.speciesId();
        flowerCount = Math.max(0, flowerCount);
        productionMultiplier = sanitizeMultiplier(productionMultiplier);
        pollinationMultiplier = sanitizeMultiplier(pollinationMultiplier);
        stabilityModifier = Mth.clamp(stabilityModifier, -1.0D, 1.0D);
        diseasePressure = Mth.clamp(diseasePressure, 0.0D, 1.0D);
    }

    @NotNull
    public static BeeColonyEnvironment neutral(@Nullable BeePhenotype phenotype) {
        BeePhenotype safe = phenotype != null ? phenotype : BeePhenotype.neutral();
        return new BeeColonyEnvironment(safe.speciesId(), true, true, FLOWER_SOFT_CAP,
            1.0D, 1.0D, 0.0D, 0.0D);
    }

    @NotNull
    public static BeeColonyEnvironment evaluate(@Nullable Level level, @Nullable BlockPos pos,
                                                @Nullable BeePhenotype phenotype) {
        BeePhenotype safe = phenotype != null ? phenotype : BeePhenotype.neutral();
        if (level == null || pos == null) {
            return neutral(safe);
        }

        BeeSpecies species = BeeSpeciesRegistry.get(safe.speciesId());
        boolean biomeMatched = matchesBiome(level, pos, species.getBiomeTags());
        int flowerCount = countMatchingFlowers(level, pos, species.getFlowerTags());
        boolean flowerMatched = species.getFlowerTags().isEmpty() || flowerCount > 0;

        double biomeMultiplier = biomeMatched
            ? 1.0D + safe.precipitationTolerance() * 0.05D
            : 0.70D + safe.precipitationTolerance() * 0.20D;
        double flowerAvailability = Mth.clamp(flowerCount / (double) FLOWER_SOFT_CAP, 0.0D, 1.0D);
        double flowerMultiplier = species.getFlowerTags().isEmpty()
            ? 1.0D
            : 0.45D + flowerAvailability * (0.75D + safe.flowerAffinity() * 0.35D);

        double productionMultiplier = Mth.clamp(biomeMultiplier * flowerMultiplier, 0.25D, 1.75D);
        double pollinationMultiplier = Mth.clamp(
            flowerMultiplier * (biomeMatched ? 1.0D : 0.85D),
            0.20D, 1.75D);
        double stabilityModifier = (biomeMatched ? 0.03D : -0.08D)
            + (flowerMatched ? flowerAvailability * 0.04D : -0.06D);
        double diseasePressure = (biomeMatched ? 0.0D : 0.018D)
            + (flowerMatched ? 0.0D : 0.012D);

        return new BeeColonyEnvironment(safe.speciesId(), biomeMatched, flowerMatched, flowerCount,
            productionMultiplier, pollinationMultiplier, stabilityModifier, diseasePressure);
    }

    private static boolean matchesBiome(Level level, BlockPos pos, List<ResourceLocation> biomeTags) {
        if (biomeTags == null || biomeTags.isEmpty()) {
            return true;
        }
        for (ResourceLocation tagId : biomeTags) {
            if (level.getBiome(pos).is(TagKey.create(Registries.BIOME, tagId))) {
                return true;
            }
        }
        return false;
    }

    private static int countMatchingFlowers(Level level, BlockPos center, List<ResourceLocation> flowerTags) {
        if (flowerTags == null || flowerTags.isEmpty()) {
            return FLOWER_SOFT_CAP;
        }

        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - FLOWER_HORIZONTAL_RADIUS; x <= center.getX() + FLOWER_HORIZONTAL_RADIUS; x++) {
            for (int y = center.getY() - FLOWER_VERTICAL_RADIUS; y <= center.getY() + FLOWER_VERTICAL_RADIUS; y++) {
                for (int z = center.getZ() - FLOWER_HORIZONTAL_RADIUS; z <= center.getZ() + FLOWER_HORIZONTAL_RADIUS; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (matchesFlowerTag(state, flowerTags)) {
                        count++;
                        if (count >= FLOWER_SOFT_CAP) {
                            return count;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static boolean matchesFlowerTag(BlockState state, List<ResourceLocation> flowerTags) {
        for (ResourceLocation tagId : flowerTags) {
            if (state.is(TagKey.create(Registries.BLOCK, tagId))) {
                return true;
            }
        }
        return false;
    }

    private static double sanitizeMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.0D;
        }
        return Mth.clamp(value, 0.0D, 64.0D);
    }
}
