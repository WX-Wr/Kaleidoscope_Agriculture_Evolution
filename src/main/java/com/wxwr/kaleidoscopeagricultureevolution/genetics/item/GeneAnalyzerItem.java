package com.wxwr.kaleidoscopeagricultureevolution.genetics.item;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.colony.BeeColonyState;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotypeSummary;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeeGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.species.BeeSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.BeeColonyBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.HighStrawWheatRules;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.Chromosome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.gene.GeneLocus;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Phenotype;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.storage.CropGenomeEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeneAnalyzerItem extends Item {
    private static final String ANALYSIS_BOOK_MARKER = "KaeGeneticAnalysisBook";
    private static final int BOOK_LINES_PER_PAGE = 13;

    public GeneAnalyzerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        Level level = ctx.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!Config.isGeneticsEnabled()) {
            ctx.getPlayer().displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.disabled").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity hive) {
            BeehiveColonyCapability.Storage colony = BeehiveColonyCapability.get(hive);
            if (colony == null) {
                return InteractionResult.PASS;
            }

            colony.synchronizeOccupants(hive);
            if (outputReport(player, buildBeeColonyReport(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.analyzer_header.beehive"),
                colony.getHost().getBees(), colony.getPhenotype(), colony.getState()))) {
                if (colony.getHost().markAllAnalyzed()) {
                    hive.setChanged();
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof BeeColonyBlockEntity colony) {
            if (outputReport(player, buildBeeColonyReport(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.analyzer_header.bee_colony"),
                colony.getBees(), colony.getPhenotype(), colony.getState()))) {
                if (colony.markAllAnalyzed()) {
                    colony.setChanged();
                }
            }
            return InteractionResult.SUCCESS;
        }

        CropSpecies species = CropSpeciesRegistry.fromBlock(state.getBlock());
        if (species == null) return InteractionResult.PASS;

        LevelChunk chunk = level.getChunkAt(pos);
        CropGenomeCapability.Storage storage = CropGenomeCapability.get(chunk);
        if (storage == null) {
            ctx.getPlayer().displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.no_genome").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        CropGenomeEntry entry = storage.get(pos);
        if (entry == null || entry.getGenome() == null) {
            entry = CropGenomeCapability.getOrCreate(level, pos, species);
        }
        if (entry == null || entry.getGenome() == null) {
            ctx.getPlayer().displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.no_genome").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        Genome genome = entry.getGenome();
        Phenotype phenotype = Phenotype.ofGenome(genome, species);
        boolean highYield = HighStrawWheatRules.isHighYieldCropCandidate(species, phenotype);
        boolean highStrawWheat = species.getCropBlock() == Blocks.WHEAT && highYield;

        List<Component> report = new ArrayList<>();
        MutableComponent analyzerHeader;
        if (highStrawWheat) {
            analyzerHeader = Component.translatable(
                "message.kaleidoscope_agriculture_evolution.genetics.analyzer_header.high_straw_wheat");
        } else if (highYield) {
            analyzerHeader = Component.translatable(
                "message.kaleidoscope_agriculture_evolution.genetics.analyzer_header.high_yield",
                Component.translatable(species.getCropBlock().getDescriptionId()));
        } else {
            analyzerHeader = Component.translatable(
                "message.kaleidoscope_agriculture_evolution.genetics.analyzer_header",
                Component.translatable(species.getCropBlock().getDescriptionId()));
        }
        report.add(analyzerHeader.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // 表型摘要
        report.add(
            Component.literal("  ")
                .append(Component.translatable("tooltip.kaleidoscope_agriculture_evolution.genetics.growth").withStyle(ChatFormatting.GREEN))
                .append(gradedGrade(phenotype.growthGrade())));
        report.add(
            Component.literal("  ")
                .append(Component.translatable("tooltip.kaleidoscope_agriculture_evolution.genetics.yield").withStyle(ChatFormatting.GOLD))
                .append(gradedGrade(phenotype.yieldGrade())));
        report.add(
            Component.literal("  ")
                .append(Component.translatable("tooltip.kaleidoscope_agriculture_evolution.genetics.seed").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" " + multiplier(phenotype.seedMult)).withStyle(ChatFormatting.WHITE)));

        // 染色体详情
        report.add(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.genotype_header")
            .withStyle(ChatFormatting.YELLOW));

        Chromosome[] chromosomes = species.getChromosomes();
        for (int c = 0; c < chromosomes.length; c++) {
            Chromosome chr = chromosomes[c];
            MutableComponent line = Component.literal(" ")
                .append(Component.literal(chr.getName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY));

            for (int l = 0; l < chr.getLocusCount(); l++) {
                int flat = species.getFlatLocusIndex(c, l);
                int a = genome.getAlleleA(flat);
                int b = genome.getAlleleB(flat);
                GeneLocus locus = chr.getLoci()[l];

                line.append(Component.literal(locus.getId()).withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(String.format("[%d/%d]", a, b))
                    .withStyle(a == b ? ChatFormatting.WHITE : ChatFormatting.AQUA));
                if (l < chr.getLocusCount() - 1) line.append(Component.literal("  "));
            }
            report.add(line);
        }

        if (entry.hasPollen()) {
            report.add(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.pollen_present")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        outputReport(player, report);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(target instanceof Bee bee)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!Config.isGeneticsEnabled()) {
            player.displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.disabled").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        BeeGenomeData data = BeeGenomeCapability.getOrCreate(bee);
        if (data == null) {
            player.displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.no_bee_genome").withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.SUCCESS;
        }

        List<Component> report = buildBeeGenomeReport(bee, data);
        if (!outputReport(player, report)) {
            return InteractionResult.SUCCESS;
        }

        BeeGenomeCapability.Storage storage = BeeGenomeCapability.get(bee);
        if (storage != null && !data.isAnalyzed()) {
            storage.set(data.withAnalyzed(true));
        }

        return InteractionResult.SUCCESS;
    }

    private static Component gradedGrade(char grade) {
        ChatFormatting color = switch (grade) {
            case 'S' -> ChatFormatting.GOLD;
            case 'A' -> ChatFormatting.GREEN;
            case 'B' -> ChatFormatting.YELLOW;
            case 'C' -> ChatFormatting.GRAY;
            default -> ChatFormatting.DARK_GRAY;
        };
        return Component.literal(" " + grade).withStyle(color);
    }

    private static List<Component> buildBeeGenomeReport(Bee bee, BeeGenomeData data) {
        List<Component> report = new ArrayList<>();
        MutableComponent header = Component.translatable(
            "message.kaleidoscope_agriculture_evolution.genetics.analyzer_header.bee",
            bee.getDisplayName());
        report.add(header.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        appendBeeIdentity(report, data);
        appendBeeGenomeDetails(report, data);
        return report;
    }

    private static List<Component> buildBeeColonyReport(MutableComponent header, List<BeeGenomeData> bees,
                                                        BeePhenotype phenotype, BeeColonyState colonyState) {
        List<Component> report = new ArrayList<>();
        report.add(header.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        report.add(Component.literal("  ")
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_colony_size", bees.size())
                .withStyle(ChatFormatting.AQUA)));
        report.add(BeePhenotypeSummary.create(phenotype));
        appendBeeColonyState(report, colonyState);
        report.add(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_colony_members")
            .withStyle(ChatFormatting.YELLOW));

        for (int i = 0; i < bees.size(); i++) {
            BeeGenomeData data = bees.get(i);
            report.add(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_colony_member", i + 1)
                .withStyle(ChatFormatting.GOLD));
            appendBeeIdentity(report, data);
            appendBeeGenomeDetails(report, data);
        }
        return report;
    }

    private static void appendBeeIdentity(List<Component> report, BeeGenomeData data) {
        report.add(Component.literal("  ")
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_species")
                .withStyle(ChatFormatting.AQUA))
            .append(Component.translatable(data.getSpeciesTranslationKey()).withStyle(ChatFormatting.WHITE)));
        report.add(Component.literal("  ")
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_origin")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.translatable(data.getOriginTranslationKey()).withStyle(ChatFormatting.WHITE)));
        report.add(Component.literal("  ")
            .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_generation")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(data.getGeneration())).withStyle(ChatFormatting.WHITE)));
    }

    private static void appendBeeColonyState(List<Component> report, BeeColonyState state) {
        if (state == null) {
            return;
        }

        report.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_health", percent(state.getHealth() / 100.0D)));
        report.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_honey_storage",
            storage(state.getHoneyStorage())));
        report.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_pollen_storage",
            storage(state.getPollenStorage())));
        report.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_disease_level",
            percent(state.getDiseaseLevel())));
        report.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_mating_progress",
            percent(state.getMatingProgress())));
    }

    private static void appendBeeGenomeDetails(List<Component> report, BeeGenomeData data) {
        BeePhenotype phenotype = BeePhenotype.ofGenome(data);
        report.add(BeePhenotypeSummary.create(phenotype));
        report.addAll(buildBeePhenotypeDetails(phenotype));
        report.add(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_genotype_header")
            .withStyle(ChatFormatting.YELLOW));

        BeeSpecies beeSpecies = BeeSpeciesRegistry.get(data.getSpeciesId());
        Chromosome[] chromosomes = beeSpecies.getChromosomes();
        for (int c = 0; c < chromosomes.length; c++) {
            Chromosome chromosome = chromosomes[c];
            MutableComponent line = Component.literal("  ")
                .append(Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.bee_chromosome", c + 1))
                .withStyle(ChatFormatting.WHITE);

            for (int l = 0; l < chromosome.getLocusCount(); l++) {
                int flat = beeSpecies.getFlatLocusIndex(c, l);
                int a = data.getAlleleA(flat);
                int b = data.getAlleleB(flat);
                GeneLocus locus = chromosome.getLoci()[l];

                line.append(Component.literal("  "))
                    .append(Component.translatable("bee_locus.kaleidoscope_agriculture_evolution."
                        + locus.getId()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(" [%d/%d]", a, b))
                        .withStyle(a == b ? ChatFormatting.WHITE : ChatFormatting.AQUA));
            }
            report.add(line);
        }
    }

    private static List<Component> buildBeePhenotypeDetails(BeePhenotype phenotype) {
        List<Component> details = new ArrayList<>();
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_precipitation_tolerance",
            percent(phenotype.precipitationTolerance())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_nocturnal_activity",
            percent(phenotype.nocturnalActivity())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_flower_affinity",
            percent(phenotype.flowerAffinity())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_foraging_efficiency",
            multiplier(phenotype.foragingEfficiency())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_pollination_efficiency",
            multiplier(phenotype.pollinationEfficiency())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_honey_yield_multiplier",
            multiplier(phenotype.honeyYieldMult())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_wax_yield_multiplier",
            multiplier(phenotype.waxYieldMult())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_pollen_yield_multiplier",
            multiplier(phenotype.pollenYieldMult())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_lifespan_multiplier",
            multiplier(phenotype.lifespanMult())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_disease_resistance",
            percent(phenotype.diseaseResistance())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_aggression",
            percent(phenotype.aggression())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_stability_value",
            percent(phenotype.stability())));
        details.add(phenotypeLine("message.kaleidoscope_agriculture_evolution.genetics.bee_special_product_chance",
            percent(phenotype.specialProductChance())));
        return details;
    }

    private static Component phenotypeLine(String key, String value) {
        return Component.literal("  ")
            .append(Component.translatable(key).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0D);
    }

    private static String multiplier(double value) {
        return String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String storage(double value) {
        return String.format(Locale.ROOT, "%.1f / 1000.0", value);
    }

    private static boolean outputReport(Player player, List<Component> report) {
        if (!Config.isAnalyzerBookMode()) {
            for (Component line : report) {
                player.displayClientMessage(line, false);
            }
            return true;
        }

        ItemStack offhand = player.getOffhandItem();
        boolean overwriteExistingBook = isAnalysisBook(offhand);
        if (!overwriteExistingBook && !offhand.is(Items.BOOK)) {
            player.displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.genetics.book_required")
                    .withStyle(ChatFormatting.GRAY), false);
            return false;
        }

        ItemStack writtenBook = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        tag.putString(WrittenBookItem.TAG_TITLE,
            Component.translatable("book.kaleidoscope_agriculture_evolution.genetics.title").getString());
        tag.putString(WrittenBookItem.TAG_AUTHOR, player.getName().getString());
        tag.put(WrittenBookItem.TAG_PAGES, createBookPages(report));
        tag.putBoolean(ANALYSIS_BOOK_MARKER, true);

        if (overwriteExistingBook) {
            offhand.setTag(tag);
            return true;
        }

        writtenBook.setTag(tag);

        offhand.shrink(1);
        if (offhand.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, writtenBook);
        } else if (!player.getInventory().add(writtenBook)) {
            player.drop(writtenBook, false);
        }
        return true;
    }

    private static ListTag createBookPages(List<Component> report) {
        ListTag pages = new ListTag();
        MutableComponent page = Component.empty();
        int lines = 0;

        for (String text : createBookLines(report)) {
            if (lines >= BOOK_LINES_PER_PAGE) {
                pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
                page = Component.empty();
                lines = 0;
            }
            appendBookLine(page, text, lines++);
        }

        if (lines > 0 || pages.isEmpty()) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        }
        return pages;
    }

    private static List<String> createBookLines(List<Component> report) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < report.size(); i++) {
            String text = report.get(i).getString();
            if (i == 0) {
                lines.add(text.replace("=", "").trim());
            } else {
                addBookLines(lines, text);
            }
        }
        return lines;
    }

    private static void addBookLines(List<String> lines, String text) {
        for (String paragraph : text.replace('\r', '\n').split("\n", -1)) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                lines.add("");
                continue;
            }

            String[] entries = trimmed.split("\\s{2,}");
            for (String entry : entries) {
                String item = entry.trim();
                if (!item.isEmpty()) {
                    lines.add(item);
                }
            }
        }
    }

    private static void appendBookLine(MutableComponent page, String text, int lineIndex) {
        if (lineIndex > 0) {
            page.append(Component.literal("\n"));
        }
        page.append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }

    private static boolean isAnalysisBook(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK) || !stack.hasTag()) {
            return false;
        }

        CompoundTag tag = stack.getTag();
        if (tag.getBoolean(ANALYSIS_BOOK_MARKER)) {
            return true;
        }

        String title = tag.getString(WrittenBookItem.TAG_TITLE);
        return title.equals("基因分析报告") || title.equals("Genetic Analysis Report")
            || title.equals(Component.translatable("book.kaleidoscope_agriculture_evolution.genetics.title").getString());
    }
}
