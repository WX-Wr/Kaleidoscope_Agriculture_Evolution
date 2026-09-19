package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.block.GlassFermentationBlock;
import com.wxwr.kaleidoscopeagricultureevolution.block.ModBlocks;
import com.wxwr.kaleidoscopeagricultureevolution.block.WoodenFermentationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class FermentationContainerEventHandler {
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        InteractionResult result;
        if (state.is(ModBlocks.GLASS_FERMENTATION.get())) {
            result = handleGlassFermentation(state, level, pos, player, event);
        } else if (state.is(ModBlocks.WOODEN_FERMENTATION.get())) {
            result = handleWoodenFermentation(state, level, pos, player, event);
        } else {
            return;
        }

        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static InteractionResult handleGlassFermentation(BlockState state, Level level, BlockPos pos, Player player,
                                                             PlayerInteractEvent.RightClickBlock event) {
        Vec3 hitLocation = event.getHitVec().getLocation();
        if (GlassFermentationBlock.isTapTarget(state, pos, hitLocation)) {
            InteractionResult result = GlassFermentationBlock.tryRemoveTap(state, level, pos, player, event.getHand(),
                    hitLocation);
            if (result == InteractionResult.PASS) {
                result = GlassFermentationBlock.tryInstallTap(state, level, pos, player, event.getHand(),
                        event.getFace(), hitLocation);
            }
            return result;
        }

        if (!GlassFermentationBlock.isBodyTarget(state, pos, hitLocation)) {
            return InteractionResult.PASS;
        }
        InteractionResult result = GlassFermentationBlock.tryTakeBottleUnderTap(state, level, pos, player,
                event.getHand(), hitLocation);
        if (result == InteractionResult.PASS) {
            result = GlassFermentationBlock.tryExtractNonLiquidItem(level, pos, player, event.getHand());
        }
        if (result == InteractionResult.PASS) {
            result = GlassFermentationBlock.tryExtractLiquid(state, level, pos, player, event.getHand());
        }
        return result;
    }

    private static InteractionResult handleWoodenFermentation(BlockState state, Level level, BlockPos pos, Player player,
                                                              PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = WoodenFermentationBlock.tryExtractNonLiquidItem(state, level, pos, player,
                event.getHand());
        if (result == InteractionResult.PASS) {
            result = WoodenFermentationBlock.tryExtractRiceSack(state, level, pos, player, event.getHand());
        }
        if (result == InteractionResult.PASS) {
            result = WoodenFermentationBlock.tryExtractLiquid(state, level, pos, player, event.getHand());
        }
        return result;
    }
}
