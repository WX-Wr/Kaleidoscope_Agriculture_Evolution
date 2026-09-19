package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.phenotype.BeePhenotypeSummary;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import com.wxwr.kaleidoscopeagricultureevolution.item.BeeNestItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public final class BeehiveGeneticsEventHandler {
    private BeehiveGeneticsEventHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.isGeneticsEnabled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!state.is(Blocks.BEEHIVE) && !state.is(Blocks.BEE_NEST)) {
            return;
        }
        boolean isBeehive = state.is(Blocks.BEEHIVE);
        boolean isBeeNest = state.is(Blocks.BEE_NEST);
        if (!(level.getBlockEntity(event.getPos()) instanceof BeehiveBlockEntity hive)) {
            return;
        }

        ItemStack held = event.getItemStack();
        Player player = event.getEntity();
        if (held.getItem() instanceof BeeNestItem && isBeeNest) {
            if (!level.isClientSide()) {
                int count = BeeNestItem.getBeeCount(held);
                if (count >= BeeNestItem.MAX_BEES) {
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.beef_nest.full",
                        BeeNestItem.MAX_BEES), true);
                } else if (BeeNestItem.extractStoredBee(held, hive)) {
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.genetics.beehive_extracted",
                        count + 1, BeeNestItem.MAX_BEES), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.genetics.beehive_empty"), true);
                }
            }
            cancel(event, level);
            return;
        }

        if (held.getItem() instanceof BeeNestItem && isBeehive
            && BeeNestItem.getBeeCount(held) > 0) {
            if (!level.isClientSide()) {
                int transferred = BeeNestItem.transferStoredBees(held, hive);
                if (transferred > 0) {
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.genetics.beehive_loaded", transferred), true);
                } else {
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.genetics.beehive_full"), true);
                }
            }
            cancel(event, level);
            return;
        }

        if (held.isEmpty() && !player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                BeehiveColonyCapability.Storage colony = BeehiveColonyCapability.get(hive);
                if (colony != null) {
                    colony.synchronizeOccupants(hive);
                    player.displayClientMessage(Component.translatable(
                        "message.kaleidoscope_agriculture_evolution.genetics.bee_colony_size", colony.getBeeCount()), false);
                    player.displayClientMessage(BeePhenotypeSummary.create(colony.getPhenotype()), false);
                }
            }
            cancel(event, level);
        }
    }

    private static void cancel(PlayerInteractEvent.RightClickBlock event, Level level) {
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);
    }
}
