package com.wxwr.kaleidoscopeagricultureevolution.work;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public record WorkItemRequirement(
        @Nullable ResourceLocation item,
        @Nullable ResourceLocation tag,
        @Nullable String seedGroup,
        @Nullable ResourceLocation effect,
        int max) {

    public boolean isSeedGroupRequirement() {
        return seedGroup != null && !seedGroup.isEmpty();
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || isSeedGroupRequirement()) {
            return false;
        }
        if (item != null) {
            ResourceLocation stackId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (!item.equals(stackId)) {
                return false;
            }
        }
        if (tag != null && !stack.is(TagKey.create(Registries.ITEM, tag))) {
            return false;
        }
        return effect == null || hasEffect(stack, effect);
    }

    private static boolean hasEffect(ItemStack stack, ResourceLocation effectId) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Effects", Tag.TAG_LIST)) {
            return false;
        }
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
        if (effect == null) {
            return false;
        }
        int legacyId = MobEffect.getId(effect);
        ListTag effects = tag.getList("Effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            if (effects.getCompound(i).getInt("EffectId") == legacyId) {
                return true;
            }
        }
        return false;
    }
}
