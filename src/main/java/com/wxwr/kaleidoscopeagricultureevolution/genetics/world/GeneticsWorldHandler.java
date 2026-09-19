package com.wxwr.kaleidoscopeagricultureevolution.genetics.world;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.CropAge;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.HighStrawWheatRules;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.breeding.CrossingEngine;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.pollination.BeePollenPayload;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.GenomeSerializer;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Phenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeEntry;
import com.wxwr.kaleidoscopeagricultureevolution.network.CropVisualSyncPacket;
import com.wxwr.kaleidoscopeagricultureevolution.network.KaeNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GeneticsWorldHandler {
    private static final Random RANDOM = new Random();
    private static final int PENDING_PLANTING_TICKS = 5;
    private static final Map<UUID, PendingSeedGenome> PENDING_PLANTING = new HashMap<>();

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!Config.isGeneticsEnabled()) return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        CropSpecies species = CropSpeciesRegistry.fromBlock(block);
        if (species == null) return;

        LevelChunk chunk = level.getChunkAt(pos);
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) return;

        CropGenomeEntry entry = CropGenomeCapability.getOrCreate(level, pos, species);
        if (entry != null && !event.getPlayer().getAbilities().instabuild) {
            event.setCanceled(true);
            event.setExpToDrop(0);

            if (isMature(state, species)) {
                dropMatureCropResources(level, pos, species, entry);
            } else {
                dropGeneticSeedStack(level, pos, species, entry.getGenome(), 1);
            }

            level.levelEvent(event.getPlayer(), 2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        storage.remove(pos);
        chunk.setUnsaved(true);
        syncCropVisualState(level, chunk, pos, null, null);
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!Config.isGeneticsEnabled()) return;

        LevelChunk chunk = event.getChunk();
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) return;

        for (var stored : storage.allEntries()) {
            BlockPos pos = BlockPos.of(stored.getLongKey());
            CropGenomeEntry entry = stored.getValue();
            if (entry == null || entry.getGenome() == null) continue;

            CropSpecies species = CropSpeciesRegistry.fromBlock(chunk.getLevel().getBlockState(pos).getBlock());
            if (species == null) continue;

            KaeNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer),
                visualPacket(pos, species, entry));
        }
    }

    @SubscribeEvent
    public static void onCropPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!Config.isGeneticsEnabled()) return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;

        BlockPos pos = event.getPos();
        CropSpecies species = CropSpeciesRegistry.fromBlock(event.getPlacedBlock().getBlock());
        if (species == null) return;
        if (!isInitialCropPlacement(event, species)) return;

        Genome genome = consumePendingGenome(event.getEntity(), pos, species);
        if (genome == null) {
            genome = CropSpeciesRegistry.generateWildGenome(species);
        }

        writeGenomeToCrop(level, pos, species, genome, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) return;
        if (!Config.isGeneticsEnabled()) {
            PENDING_PLANTING.remove(event.player.getUUID());
            return;
        }

        PendingSeedGenome pending = PENDING_PLANTING.get(event.player.getUUID());
        if (pending == null) return;

        if (tryApplyPendingPlanting(event.player, pending)) {
            PENDING_PLANTING.remove(event.player.getUUID());
            return;
        }

        pending.ticksRemaining--;
        if (pending.ticksRemaining <= 0) {
            PENDING_PLANTING.remove(event.player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onCropGrowPre(BlockEvent.CropGrowEvent.Pre event) {
        if (!Config.isGeneticsEnabled()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block block = state.getBlock();
        CropSpecies species = CropSpeciesRegistry.fromBlock(block);
        if (species == null) return;

        Level level = (Level) event.getLevel();
        LevelChunk chunk = level.getChunkAt(pos);
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) return;

        CropGenomeEntry entry = CropGenomeCapability.getOrCreate(level, pos, species);
        if (entry == null || entry.getGenome() == null) return;

        Phenotype phenotype = Phenotype.ofGenome(entry.getGenome(), species);
        float growthMult = phenotype.growthSpeedMult;
        growthMult *= computeEnvironmentFactor(level, pos, phenotype);

        if (growthMult <= 0.01f) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }

        if (growthMult < 1.0f) {
            if (RANDOM.nextFloat() >= growthMult * 0.5f) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                return;
            }
        }

        if (growthMult > 1.0f) {
            float extra = growthMult - 1.0f;
            int extraGrowths = (int) extra;
            float frac = extra - extraGrowths;
            if (RANDOM.nextFloat() < frac) extraGrowths++;
            for (int i = 0; i < extraGrowths; i++) {
                int currentAge = getCropAge(state);
                int maxAge = species.getMaxAge();
                if (currentAge < maxAge) {
                    level.setBlock(pos, CropAge.set(state, currentAge + 1),
                        Block.UPDATE_CLIENTS);
                    state = level.getBlockState(pos);
                }
            }
        }

        // 成熟时进行异花授粉
        if (isBecomingMature(state, species)) {
            tryPollinate(level, pos, chunk, entry, species);
        }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!Config.isGeneticsEnabled()) return;
        if (event.getLevel().isClientSide()) return;

        CropSpecies species = CropSpeciesRegistry.fromBlock(event.getBlock().getBlock());
        if (species == null) return;

        PendingSeedGenome pending = PENDING_PLANTING.get(event.getEntity().getUUID());
        if (pending == null || pending.species != species) return;

        if (writeGenomeToCrop(event.getLevel(), event.getPos(), species, pending.genome, false)) {
            PENDING_PLANTING.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.isGeneticsEnabled()) return;
        if (event.getLevel().isClientSide()) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;

        Item seedItem = held.getItem();
        CropSpecies species = CropSpeciesRegistry.fromSeed(seedItem);
        if (species == null) return;

        BlockState clickedState = event.getLevel().getBlockState(event.getPos());
        Block clickedBlock = clickedState.getBlock();
        if (CropSpeciesRegistry.fromBlock(clickedBlock) == null) return;

        Level level = (Level) event.getLevel();
        LevelChunk chunk = level.getChunkAt(event.getPos());
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) return;

        CropGenomeEntry targetEntry = storage.get(event.getPos());
        CropSpecies targetSpecies = CropSpeciesRegistry.fromBlock(clickedBlock);
        if (targetEntry == null || targetSpecies == null) return;
        if (targetSpecies != species) return;

        Genome pollenGenome = GenomeSerializer.deserialize(held.getTag());
        if (pollenGenome == null) {
            pollenGenome = CropSpeciesRegistry.generateWildGenome(species);
        }
        boolean pollinated = applyPollination(level, event.getPos(), chunk, targetEntry, species, pollenGenome);
        Component message = pollinated
            ? Component.translatable("message.kaleidoscope_agriculture_evolution.pollinated")
            : Component.translatable("message.kaleidoscope_agriculture_evolution.already_pollinated");
        event.getEntity().displayClientMessage(message, true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPreparePlanting(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.isGeneticsEnabled()) return;
        if (event.getLevel().isClientSide()) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;

        CropSpecies species = CropSpeciesRegistry.fromSeed(held.getItem());
        if (species == null) return;
        if (CropSpeciesRegistry.fromBlock(event.getLevel().getBlockState(event.getPos()).getBlock()) != null) return;

        Genome genome = GenomeSerializer.deserialize(held.getTag());
        PENDING_PLANTING.put(event.getEntity().getUUID(),
            new PendingSeedGenome(candidatePlantPositions(event), species,
                genome != null ? genome.copy() : null, PENDING_PLANTING_TICKS));
    }

    private static void tryPollinate(Level level, BlockPos pos, LevelChunk chunk,
                                     CropGenomeEntry entry, CropSpecies species) {
        if (!Config.isGeneticsEnabled()) return;
        if (RANDOM.nextFloat() > Config.getPollinationChance()) return;
        if (RANDOM.nextFloat() < species.getSelfPollinationBias()) return;

        BlockPos[] neighbors = {
            pos.north(), pos.south(), pos.east(), pos.west(),
            pos.north().east(), pos.north().west(), pos.south().east(), pos.south().west()
        };

        for (BlockPos npos : neighbors) {
            LevelChunk neighborChunk = level.getChunkAt(npos);
            CropGenomeCapability.Storage nStorage = CropGenomeCapability.get(neighborChunk);
            CropGenomeEntry neighborEntry = nStorage != null ? nStorage.get(npos) : null;
            if (neighborEntry == null || neighborEntry == entry) continue;
            if (neighborEntry.getGenome() == null) continue;
            if (neighborEntry.getGenome().getSpecies() != species) continue;
            if (CropSpeciesRegistry.fromBlock(level.getBlockState(npos).getBlock()) != species) continue;

            applyPollination(level, pos, chunk, entry, species, neighborEntry.getGenome());
            break;
        }
    }

    private static boolean applyPollination(Level level, BlockPos pos, LevelChunk chunk,
                                            CropGenomeEntry entry, CropSpecies species,
                                            Genome pollenGenome) {
        if (entry == null || entry.getGenome() == null || pollenGenome == null) return false;
        if (entry.isPollinated()) return false;
        if (entry.getGenome().getSpecies() != species) return false;
        if (pollenGenome.getSpecies() != species) return false;

        Genome offspring = CrossingEngine.cross(entry.getGenome(), pollenGenome, species, RANDOM);
        entry.setGenome(offspring);
        entry.setPollenGenome(null);
        entry.setPollinated(true);
        chunk.setUnsaved(true);
        syncCropVisualState(level, chunk, pos, species, entry);
        return true;
    }

    /**
     * Narrow bridge for bee pollen. The crop crossing implementation remains
     * in this handler; bee code only supplies a validated pollen payload.
     */
    public static boolean applyBeePollination(Level level, BlockPos pos, BeePollenPayload payload) {
        if (!Config.isGeneticsEnabled() || level == null || pos == null
                || payload == null || !payload.isFresh()
                || payload.sourceCropSpeciesId() == null) {
            return false;
        }

        if (!level.hasChunkAt(pos)) {
            return false;
        }

        CropSpecies species = CropSpeciesRegistry.get(payload.sourceCropSpeciesId().toString());
        if (species == null || !payload.isCompatibleWith(species)
                || CropSpeciesRegistry.fromBlock(level.getBlockState(pos).getBlock()) != species) {
            return false;
        }

        LevelChunk chunk = level.getChunkAt(pos);
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) {
            return false;
        }

        CropGenomeEntry entry = storage.get(pos);
        if (entry == null || entry.getGenome() == null) {
            entry = CropGenomeCapability.getOrCreate(level, pos, species);
        }
        return applyPollination(level, pos, chunk, entry, species,
            payload.getPollenGenomeCopy());
    }

    private static void dropMatureCropResources(Level level, BlockPos pos, CropSpecies species,
                                                CropGenomeEntry entry) {
        Genome genome = entry.getGenome();
        if (genome == null) return;

        Phenotype parentPhenotype = Phenotype.ofGenome(genome, species);
        int seedCount = Math.max(1, Math.round(1 + parentPhenotype.seedMult));
        int produceCount = Math.max(1, Math.round(parentPhenotype.yieldMult));

        if (species.getProduceItem() != null && species.getProduceItem() != species.getSeedItem()) {
            Block.popResource(level, pos, new ItemStack(species.getProduceItem(), produceCount));
        } else {
            seedCount += produceCount;
        }

        dropGeneticSeedStack(level, pos, species, genome, seedCount);
    }

    private static void dropGeneticSeedStack(Level level, BlockPos pos, CropSpecies species,
                                             Genome genome, int count) {
        if (species.getSeedItem() == null || genome == null || count <= 0) return;

        while (count > 0) {
            ItemStack seedStack = new ItemStack(species.getSeedItem());
            int stackCount = Math.min(count, seedStack.getMaxStackSize());
            seedStack.setCount(stackCount);
            seedStack.setTag(GenomeSerializer.writeToStack(genome, seedStack.getOrCreateTag()));
            Block.popResource(level, pos, seedStack);
            count -= stackCount;
        }
    }

    private static boolean isMature(BlockState state, CropSpecies species) {
        int age = getCropAge(state);
        return age >= 0 && age >= species.getMaxAge();
    }

    private static boolean isBecomingMature(BlockState state, CropSpecies species) {
        int currentAge = getCropAge(state);
        return currentAge >= 0 && currentAge >= species.getMaxAge() - 1;
    }

    private static int getCropAge(BlockState state) {
        return CropAge.get(state);
    }

    private static float computeEnvironmentFactor(Level level, BlockPos pos, Phenotype phenotype) {
        float factor = 1.0f;
        BlockState soilState = level.getBlockState(pos.below());
        // 干燥农田的基础湿度惩罚
        if (soilState.hasProperty(FarmBlock.MOISTURE)) {
            int moisture = soilState.getValue(FarmBlock.MOISTURE);
            factor *= 0.25f + 0.75f * (moisture / 7.0f);
        }
        // 低于火把光照时的光照惩罚
        int light = Math.max(level.getBrightness(LightLayer.SKY, pos),
            level.getBrightness(LightLayer.BLOCK, pos));
        if (light < 9) factor *= 0.5f;
        return factor;
    }

    private static Genome consumePendingGenome(Entity entity, BlockPos pos, CropSpecies species) {
        if (!(entity instanceof Player player)) return null;

        PendingSeedGenome pending = PENDING_PLANTING.remove(player.getUUID());
        if (pending == null || pending.species != species) {
            return findGenomeInHands(player, species);
        }

        return pending.genome != null ? pending.genome.copy() : null;
    }

    private static Genome findGenomeInHands(Player player, CropSpecies species) {
        Genome mainHandGenome = genomeFromStack(player.getMainHandItem(), species);
        if (mainHandGenome != null) return mainHandGenome;
        return genomeFromStack(player.getOffhandItem(), species);
    }

    private static Genome genomeFromStack(ItemStack stack, CropSpecies species) {
        if (stack.isEmpty() || CropSpeciesRegistry.fromSeed(stack.getItem()) != species) return null;

        Genome genome = GenomeSerializer.deserialize(stack.getTag());
        return genome != null ? genome.copy() : null;
    }

    private static boolean tryApplyPendingPlanting(Player player, PendingSeedGenome pending) {
        Level level = player.level();
        if (level.isClientSide()) return false;

        for (BlockPos pos : pending.candidatePositions) {
            if (writeGenomeToCrop(level, pos, pending.species, pending.genome, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean writeGenomeToCrop(Level level, BlockPos pos, CropSpecies species, Genome genome,
                                             boolean overwriteExisting) {
        if (!level.hasChunkAt(pos)) return false;
        if (CropSpeciesRegistry.fromBlock(level.getBlockState(pos).getBlock()) != species) return false;

        LevelChunk chunk = level.getChunkAt(pos);
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) return false;
        if (!overwriteExisting && storage.get(pos) != null) return true;

        Genome plantedGenome = genome != null ? genome.copy() : CropSpeciesRegistry.generateWildGenome(species);
        CropGenomeEntry entry = new CropGenomeEntry(plantedGenome);
        storage.put(pos, entry);
        chunk.setUnsaved(true);
        syncCropVisualState(level, chunk, pos, species, entry);
        return true;
    }

    private static boolean isHighYieldCrop(CropSpecies species, CropGenomeEntry entry) {
        if (entry == null || entry.getGenome() == null) return false;
        Phenotype phenotype = Phenotype.ofGenome(entry.getGenome(), species);
        return HighStrawWheatRules.isHighYieldCropCandidate(species, phenotype);
    }

    private static void syncCropVisualState(Level level, LevelChunk chunk, BlockPos pos,
                                            CropSpecies species, CropGenomeEntry entry) {
        if (level.isClientSide()) return;
        KaeNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk),
            visualPacket(pos, species, entry));

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    private static CropVisualSyncPacket visualPacket(BlockPos pos, CropSpecies species, CropGenomeEntry entry) {
        if (species == null || entry == null || entry.getGenome() == null) {
            return new CropVisualSyncPacket(pos, false, false, 'D');
        }

        Phenotype phenotype = Phenotype.ofGenome(entry.getGenome(), species);
        boolean highYieldCrop = HighStrawWheatRules.isHighYieldCropCandidate(species, phenotype);
        return new CropVisualSyncPacket(pos, true, highYieldCrop, phenotype.yieldGrade());
    }

    private static boolean isInitialCropPlacement(BlockEvent.EntityPlaceEvent event, CropSpecies species) {
        BlockState placed = event.getPlacedBlock();
        if (getCropAge(placed) != 0) {
            return false;
        }

        BlockState replaced = event.getBlockSnapshot().getReplacedBlock();
        return CropSpeciesRegistry.fromBlock(replaced.getBlock()) != species;
    }

    private static List<BlockPos> candidatePlantPositions(PlayerInteractEvent.RightClickBlock event) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        BlockPos clicked = event.getPos();
        positions.add(clicked.immutable());
        positions.add(clicked.above().immutable());
        positions.add(clicked.relative(event.getFace()).immutable());
        positions.add(clicked.relative(event.getFace()).above().immutable());
        return new ArrayList<>(positions);
    }

    private static class PendingSeedGenome {
        private final List<BlockPos> candidatePositions;
        private final CropSpecies species;
        private final Genome genome;
        private int ticksRemaining;

        private PendingSeedGenome(List<BlockPos> candidatePositions, CropSpecies species,
                                  Genome genome, int ticksRemaining) {
            this.candidatePositions = candidatePositions;
            this.species = species;
            this.genome = genome;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
