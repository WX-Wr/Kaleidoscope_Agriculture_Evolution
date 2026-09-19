package com.wxwr.kaleidoscopeagricultureevolution.genetics.mixin;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeeGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveBlockEntityMixin {
    @Inject(method = "addOccupantWithPresetTicks", at = @At("HEAD"))
    private void kae$ensureBeeGenome(Entity entity, boolean hasNectar, int ticksInHive, CallbackInfo callbackInfo) {
        if (entity instanceof Bee bee) {
            BeeGenomeCapability.getOrCreate(bee);
        }
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void kae$tickColony(Level level, BlockPos pos, BlockState state,
                                       BeehiveBlockEntity hive, CallbackInfo callbackInfo) {
        BeehiveColonyCapability.tick(level, pos, state, hive);
    }
}
