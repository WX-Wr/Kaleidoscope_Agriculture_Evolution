package com.wxwr.kaleidoscopeagricultureevolution.genetics.mixin;

import com.wxwr.kaleidoscopeagricultureevolution.util.ArmorWaxing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public abstract class ItemStackWaxLayerMixin {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int reduceDamageWithWaxLayer(int amount, int originalAmount, RandomSource random,
                                         @Nullable ServerPlayer user) {
        return ArmorWaxing.reduceDurabilityDamage((ItemStack) (Object) this, amount, random);
    }
}
