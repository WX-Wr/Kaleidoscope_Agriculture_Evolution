package com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.CropAge;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.compatibility.BeeFlowerCompatibility;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeeGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeEntry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.world.GeneticsWorldHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Adds crop pollen behavior to vanilla bees without replacing their existing
 * navigation or nectar behavior.
 */
public final class BeePollinationHandler {
    private static final int ACTION_INTERVAL_TICKS = 10;
    private static final int TARGET_REFRESH_INTERVAL_TICKS = 100;
    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_VERTICAL_RADIUS = 3;
    private static final double INTERACTION_DISTANCE_SQUARED = 4.0D;
    private static final double NAVIGATION_SPEED = 1.0D;

    private BeePollinationHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Bee bee)) {
            return;
        }

        Level level = bee.level();
        if (level.isClientSide() || !Config.isGeneticsEnabled()) {
            return;
        }

        BeeGenomeCapability.Storage storage = BeeGenomeCapability.get(bee);
        if (storage == null) {
            return;
        }

        BeeGenomeData genome = storage.getOrCreate(bee);
        storage.tickPollen(1);
        storage.agePollinationTarget();

        if (bee.tickCount % ACTION_INTERVAL_TICKS != 0 || bee.hasNectar()) {
            return;
        }

        BeePhenotype phenotype = BeePhenotype.ofGenome(genome);
        BeePollenPayload pollen = storage.getPollen();
        if (pollen != null && pollen.isFresh()) {
            tryDeposit(level, bee, phenotype, storage, pollen);
        } else {
            tryCollect(level, bee, phenotype, storage);
        }
    }

    private static void tryCollect(Level level, Bee bee, BeePhenotype phenotype,
                                   BeeGenomeCapability.Storage storage) {
        CropCandidate candidate = resolveTarget(level, bee, phenotype, storage, null, null);
        if (candidate == null) {
            return;
        }

        if (!isWithinInteractionDistance(bee, candidate.pos())) {
            moveToCrop(bee, candidate.pos(), phenotype);
            return;
        }

        if (!passesPollinationRoll(bee, phenotype, candidate.compatibility())) {
            return;
        }

        CropGenomeEntry entry = CropGenomeCapability.getOrCreate(level, candidate.pos(), candidate.species());
        if (entry == null || entry.getGenome() == null) {
            return;
        }

        storage.setPollen(BeePollenPayload.create(entry.getGenome(), candidate.pos()));
        storage.clearPollinationTarget();
    }

    private static void tryDeposit(Level level, Bee bee, BeePhenotype phenotype,
                                   BeeGenomeCapability.Storage storage, BeePollenPayload pollen) {
        CropCandidate candidate = resolveTarget(level, bee, phenotype, storage, pollen,
            pollen.sourcePosition());
        if (candidate == null) {
            return;
        }

        if (!isWithinInteractionDistance(bee, candidate.pos())) {
            moveToCrop(bee, candidate.pos(), phenotype);
            return;
        }

        if (!passesPollinationRoll(bee, phenotype, candidate.compatibility())) {
            return;
        }

        if (GeneticsWorldHandler.applyBeePollination(level, candidate.pos(), pollen)) {
            storage.clearPollen();
            storage.clearPollinationTarget();
        }
    }

    @Nullable
    private static CropCandidate resolveTarget(Level level, Bee bee, BeePhenotype phenotype,
                                               BeeGenomeCapability.Storage storage,
                                               BeePollenPayload pollen, BlockPos excludedPos) {
        BlockPos cachedPos = storage.getPollinationTarget();
        if (cachedPos != null) {
            CropCandidate cached = inspectCandidate(level, bee, phenotype, cachedPos,
                pollen, excludedPos);
            if (cached != null
                    && !storage.isPollinationTargetRefreshDue(bee.tickCount,
                    TARGET_REFRESH_INTERVAL_TICKS)) {
                return cached;
            }
            storage.clearPollinationTarget();
        }

        if (!storage.isPollinationTargetRefreshDue(bee.tickCount,
            TARGET_REFRESH_INTERVAL_TICKS)) {
            return null;
        }

        storage.markPollinationSearch(bee.tickCount);
        CropCandidate candidate = findBestCandidate(level, bee, phenotype, pollen, excludedPos);
        if (candidate != null) {
            storage.setPollinationTarget(candidate.pos());
        }
        return candidate;
    }

    private static CropCandidate findBestCandidate(Level level, Bee bee, BeePhenotype phenotype,
                                                    BeePollenPayload pollen, BlockPos excludedPos) {
        List<CropCandidate> candidates = findCandidates(level, bee, phenotype, pollen, excludedPos);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<CropCandidate> findCandidates(Level level, Bee bee, BeePhenotype phenotype,
                                                      BeePollenPayload pollen, BlockPos excludedPos) {
        BlockPos center = bee.blockPosition();
        List<CropCandidate> candidates = new ArrayList<>();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_VERTICAL_RADIUS; dy <= SEARCH_VERTICAL_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (excludedPos != null && excludedPos.equals(pos)) {
                        continue;
                    }
                    if (!level.hasChunkAt(pos)) {
                        continue;
                    }

                    CropCandidate candidate = inspectCandidate(level, bee, phenotype, pos,
                        pollen, excludedPos);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(candidate -> targetScore(
            candidate.distanceSquared(), candidate.compatibility())));
        return candidates;
    }

    @Nullable
    private static CropCandidate inspectCandidate(Level level, Bee bee, BeePhenotype phenotype,
                                                  BlockPos pos, BeePollenPayload pollen,
                                                  BlockPos excludedPos) {
        if (pos == null || (excludedPos != null && excludedPos.equals(pos))
                || !level.hasChunkAt(pos)) {
            return null;
        }

        BlockState state = level.getBlockState(pos);
        CropSpecies species = CropSpeciesRegistry.fromBlock(state.getBlock());
        if (!isFloweringCrop(state, species)) {
            return null;
        }
        // The blacklist only blocks pollination targets. Blacklisted crops can
        // still provide pollen for other eligible crops.
        if (pollen != null && BeePollinationBlacklist.contains(species)) {
            return null;
        }

        double compatibility = BeeFlowerCompatibility.compatibility(phenotype, species);
        if (compatibility <= 0.0D
                || (pollen != null && !pollen.isCompatibleWith(species))) {
            return null;
        }

        CropGenomeCapability.Storage cropStorage =
            CropGenomeCapability.get(level.getChunkAt(pos));
        CropGenomeEntry entry = cropStorage != null ? cropStorage.get(pos) : null;
        if (pollen != null && entry != null && entry.isPollinated()) {
            return null;
        }

        double distance = bee.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D,
            pos.getZ() + 0.5D);
        return new CropCandidate(pos, species, compatibility, distance);
    }

    private static boolean isFloweringCrop(BlockState state, CropSpecies species) {
        int age = CropAge.get(state);
        return species != null && isFloweringAge(age, species.getMaxAge());
    }

    private static boolean isWithinInteractionDistance(Bee bee, BlockPos pos) {
        return bee.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
            <= INTERACTION_DISTANCE_SQUARED;
    }

    private static void moveToCrop(Bee bee, BlockPos pos, BeePhenotype phenotype) {
        if (!bee.getNavigation().isDone()) {
            return;
        }
        double speed = Math.max(0.65D, Math.min(1.35D,
            NAVIGATION_SPEED * (0.85D + phenotype.foragingEfficiency() * 0.15D)));
        bee.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY() + 0.75D,
            pos.getZ() + 0.5D, speed);
    }

    private static boolean passesPollinationRoll(Bee bee, BeePhenotype phenotype,
                                                 double compatibility) {
        return bee.getRandom().nextDouble() < pollinationChance(
            phenotype.pollinationEfficiency(), compatibility);
    }

    public static boolean isFloweringAge(int age, int maxAge) {
        return age >= Math.max(0, maxAge - 1);
    }

    public static double pollinationChance(double pollinationEfficiency,
                                           double compatibility) {
        double normalizedEfficiency = (pollinationEfficiency - 0.65D) / 1.10D;
        double chance = 0.15D + normalizedEfficiency * 0.45D
            + Math.max(0.0D, Math.min(1.0D, compatibility)) * 0.40D;
        return Math.max(0.05D, Math.min(0.95D, chance));
    }

    public static double targetScore(double distanceSquared, double compatibility) {
        return Math.max(0.0D, distanceSquared) * 0.02D
            - Math.max(0.0D, Math.min(1.0D, compatibility));
    }

    private record CropCandidate(BlockPos pos, CropSpecies species,
                                 double compatibility, double distanceSquared) {
    }
}
