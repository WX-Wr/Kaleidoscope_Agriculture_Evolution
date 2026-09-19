package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.Genome;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.genome.GenomeSerializer;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpeciesRegistry;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.item.GeneAnalyzerItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static final RegistryObject<Item> WHIP = ITEMS.register("whip",
            () -> new WhipItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CONTRACT = ITEMS.register("contract",
            () -> new ContractItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> CATTLE_LEADER = ITEMS.register("cattle_leader",
            () -> new CattleLeaderItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> YOKE = ITEMS.register("yoke",
            () -> new YokeItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GENE_ANALYZER = ITEMS.register("gene_analyzer",
            GeneAnalyzerItem::new);

    public static final RegistryObject<Item> BEEF_NEST = ITEMS.register("beef_nest",
            () -> new BeeNestItem(new Item.Properties()));

    public static final RegistryObject<Item> PLOW = ITEMS.register("plow",
            () -> new PlowItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> LOUCHE = ITEMS.register("louche",
            () -> new LoucheItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CROCK = ITEMS.register("crock",
            () -> new CrockItem(new Item.Properties()));

    public static final RegistryObject<Item> DRYING_BOARD = ITEMS.register("drying_board",
            () -> new DryingBoardItem(new Item.Properties()));

    public static final RegistryObject<Item> WOODEN_FERMENTATION = ITEMS.register("wooden_fermentation",
            () -> new BlockItem(ModBlocks.WOODEN_FERMENTATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> GLASS_FERMENTATION = ITEMS.register("glass_fermentation",
            () -> new BlockItem(ModBlocks.GLASS_FERMENTATION.get(), new Item.Properties()));

    public static final RegistryObject<Item> HONEY_EXTRACTOR = ITEMS.register("honey_extractor",
            () -> new BlockItem(ModBlocks.HONEY_EXTRACTOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> BEE_COLONY = ITEMS.register("bee_colony",
            () -> new BlockItem(ModBlocks.BEE_COLONY.get(), new Item.Properties()));

    public static final RegistryObject<Item> BEESWAX = ITEMS.register("beeswax",
            () -> new BeeswaxItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> SACK = ITEMS.register("sack",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> RICE_SACK = ITEMS.register("rice_sack",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> VINEGAR = ITEMS.register("vinegar",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> SALT = ITEMS.register("salt",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> YEAST = ITEMS.register("yeast",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> YEAST_POWDER = ITEMS.register("yeast_powder",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> CUCUMBER = ITEMS.register("cucumber",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> JIAOTOU = ITEMS.register("jiaotou",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> APPLE_VINEGAR = ITEMS.register("apple_vinegar",
            () -> new AppleVinegarItem(new Item.Properties()));

    public static final RegistryObject<Item> PICKLED_CUCUMBER = registerPickledFood("pickled_cucumber");
    public static final RegistryObject<Item> PICKLED_JIAOTOU = registerPickledFood("pickled_jiaotou");
    public static final RegistryObject<Item> HONEY_GLAZED_CARROT = registerPickledFood("honey_glazed_carrot");
    public static final RegistryObject<Item> HONEY_GLAZED_PUMPKIN_SLICE = registerPickledFood("honey_glazed_pumpkin_slice");
    public static final RegistryObject<Item> HONEY_GLAZED_TOMATO = registerPickledFood("honey_glazed_tomato");
    public static final RegistryObject<Item> PICKLED_BAMBOO_SHOOT = registerPickledFood("pickled_bamboo_shoot");
    public static final RegistryObject<Item> PICKLED_ONION = registerPickledFood("pickled_onion");
    public static final RegistryObject<Item> SOUR_BAMBOO = registerPickledFood("sour_bamboo");
    public static final RegistryObject<Item> PICKLED_PEPPER = registerPickledFood("pickled_pepper");
    public static final RegistryObject<Item> SPICY_CABBAGE = registerPickledFood("spicy_cabbage");
    public static final RegistryObject<Item> CENTURY_EGG = registerPickledFood("century_egg");
    public static final RegistryObject<Item> PICKLED_COD = registerPickledFood("pickled_cod");
    public static final RegistryObject<Item> PICKLED_SALMON = registerPickledFood("pickled_salmon");
    public static final RegistryObject<Item> SALTED_BEEF = registerPickledFood("salted_beef");
    public static final RegistryObject<Item> SALTED_MUTTON = registerPickledFood("salted_mutton");
    public static final RegistryObject<Item> SALTED_PORK = registerPickledFood("salted_pork");
    public static final RegistryObject<Item> SALTED_RABBIT = registerPickledFood("salted_rabbit");
    public static final RegistryObject<Item> SALTED_CHICKEN = registerPickledFood("salted_chicken");

    public static final RegistryObject<Item> PORTION_SUGAR =
            DurablePortionItem.register("portion_sugar", 6, () -> new ItemStack(Items.SUGAR), () -> ItemStack.EMPTY);

    public static final RegistryObject<Item> PORTION_SALT =
            DurablePortionItem.register("portion_salt", 3, () -> new ItemStack(SALT.get()), () -> ItemStack.EMPTY);

    public static final RegistryObject<Item> PORTION_WATER_BOTTLE =
            DurablePortionItem.registerBottled("portion_water_bottle", 3, ModItems::waterBottle);

    public static final RegistryObject<Item> PORTION_HONEY_BOTTLE =
            DurablePortionItem.registerBottled("portion_honey_bottle", 3, () -> new ItemStack(Items.HONEY_BOTTLE));

    public static final RegistryObject<Item> PORTION_VINEGAR =
            DurablePortionItem.registerBottled("portion_vinegar", 3, () -> new ItemStack(VINEGAR.get()));

    public static final RegistryObject<Item> PORTION_APPLE_VINEGAR =
            DurablePortionItem.registerBottled("portion_apple_vinegar", 3, () -> new ItemStack(APPLE_VINEGAR.get()));

    public static final RegistryObject<Item> PORTION_YEAST =
            DurablePortionItem.registerBottled("portion_yeast", 3, () -> new ItemStack(YEAST.get()));

    public static final RegistryObject<Item> PORTION_YEAST_POWDER =
            DurablePortionItem.register("portion_yeast_powder", 6,
                    () -> new ItemStack(YEAST_POWDER.get()), () -> ItemStack.EMPTY);

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WHIP.get());
            event.accept(CONTRACT.get());
            event.accept(CATTLE_LEADER.get());
            event.accept(YOKE.get());
            event.accept(GENE_ANALYZER.get());
            event.accept(BEEF_NEST.get());
            event.accept(SACK.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(BEESWAX.get());
            for (ItemStack stack : createFullGenomeSeedStacks()) {
                event.accept(stack);
            }
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(RICE_SACK.get());
            event.accept(VINEGAR.get());
            event.accept(SALT.get());
            event.accept(YEAST.get());
            event.accept(YEAST_POWDER.get());
            event.accept(CUCUMBER.get());
            event.accept(JIAOTOU.get());
            event.accept(APPLE_VINEGAR.get());
            event.accept(PICKLED_CUCUMBER.get());
            event.accept(PICKLED_JIAOTOU.get());
            event.accept(HONEY_GLAZED_CARROT.get());
            event.accept(HONEY_GLAZED_PUMPKIN_SLICE.get());
            event.accept(HONEY_GLAZED_TOMATO.get());
            event.accept(PICKLED_BAMBOO_SHOOT.get());
            event.accept(PICKLED_ONION.get());
            event.accept(SOUR_BAMBOO.get());
            event.accept(PICKLED_PEPPER.get());
            event.accept(SPICY_CABBAGE.get());
            event.accept(CENTURY_EGG.get());
            event.accept(PICKLED_COD.get());
            event.accept(PICKLED_SALMON.get());
            event.accept(SALTED_BEEF.get());
            event.accept(SALTED_MUTTON.get());
            event.accept(SALTED_PORK.get());
            event.accept(SALTED_RABBIT.get());
            event.accept(SALTED_CHICKEN.get());
        }
    }

    private static List<ItemStack> createFullGenomeSeedStacks() {
        List<CropSpecies> speciesList = new ArrayList<>(CropSpeciesRegistry.getAll());
        speciesList.sort(Comparator.comparing(CropSpecies::getId));

        List<ItemStack> stacks = new ArrayList<>();
        for (CropSpecies species : speciesList) {
            Genome genome = createMaxGenome(species);
            CompoundTag tag = GenomeSerializer.writeToStack(genome, new CompoundTag());
            for (Item seedItem : CropSpeciesRegistry.getSeedItems(species)) {
                ItemStack stack = new ItemStack(seedItem);
                stack.setTag(tag.copy());
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static Genome createMaxGenome(CropSpecies species) {
        Genome.Builder builder = new Genome.Builder(species);
        for (int flat = 0; flat < species.getTotalLoci(); flat++) {
            int max = species.getLocus(flat).getMaxValue();
            builder.setAlleles(flat, max, max);
        }
        return builder.build();
    }

    private static RegistryObject<Item> registerPickledFood(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().stacksTo(64)));
    }

    private static ItemStack waterBottle() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
    }
}
