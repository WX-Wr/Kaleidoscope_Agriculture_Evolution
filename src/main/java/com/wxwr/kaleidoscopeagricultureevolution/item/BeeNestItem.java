package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.genome.BeeGenomeData;
import com.wxwr.kaleidoscopeagricultureevolution.blockentity.BeeColonyBlockEntity;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeeGenomeCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.apiculture.storage.BeehiveColonyCapability;
import com.wxwr.kaleidoscopeagricultureevolution.genetics.mixin.BeehiveBlockEntityAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BeeNestItem extends Item {
    public static final int MAX_BEES = 5;

    private static final String TAG_BEES = "Bees";
    private static final String TAG_ENTITY = "Entity";
    private static final String TAG_GENOME = "BeeGenome";
    private static final String MSG = "message.kaleidoscope_agriculture_evolution.beef_nest.";
    private static final String TOOLTIP = "tooltip.kaleidoscope_agriculture_evolution.beef_nest.";

    public BeeNestItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Bee bee)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            return releaseFirstBee(stack, player, level, target.position(), hand);
        }

        return captureBee(stack, player, bee, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Vec3 spawnPos = Vec3.atCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        return releaseFirstBee(context.getItemInHand(), player, level, spawnPos, context.getHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        Vec3 spawnPos = player.getEyePosition().add(player.getLookAngle().scale(2.0D));
        InteractionResult result = releaseFirstBee(stack, player, level, spawnPos, hand);
        return result.consumesAction()
            ? InteractionResultHolder.success(stack)
            : InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        int count = getBeeCount(stack);
        tooltip.add(Component.translatable(TOOLTIP + "count", count, MAX_BEES)
            .withStyle(count > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    public static int getBeeCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BEES, Tag.TAG_LIST)) {
            return 0;
        }
        return tag.getList(TAG_BEES, Tag.TAG_COMPOUND).size();
    }

    public static int transferStoredBees(ItemStack stack, BeeColonyBlockEntity colony) {
        if (stack.isEmpty() || colony == null) {
            return 0;
        }

        ListTag bees = getStoredBees(stack);
        if (bees.isEmpty()) {
            return 0;
        }

        int transferred = 0;
        for (int i = 0; i < bees.size(); i++) {
            CompoundTag stored = bees.getCompound(i);
            if (!stored.contains(TAG_GENOME, Tag.TAG_COMPOUND)) {
                continue;
            }
            BeeGenomeData genome = BeeGenomeData.fromNBT(stored.getCompound(TAG_GENOME));
            if (genome != null) {
                colony.addBee(genome);
                transferred++;
            }
        }

        bees.clear();
        writeStoredBees(stack, bees);
        return transferred;
    }

    public static int transferStoredBees(ItemStack stack, BeehiveBlockEntity hive) {
        if (stack.isEmpty() || hive == null || hive.getLevel() == null) {
            return 0;
        }

        int availableSlots = BeehiveBlockEntity.MAX_OCCUPANTS - hive.getOccupantCount();
        if (availableSlots <= 0) {
            return 0;
        }

        ListTag bees = getStoredBees(stack);
        int transferred = 0;
        while (transferred < availableSlots && !bees.isEmpty()) {
            CompoundTag stored = bees.getCompound(0);
            CompoundTag entityTag = stored.getCompound(TAG_ENTITY).copy();
            if (!entityTag.contains("id", Tag.TAG_STRING)) {
                entityTag.putString("id", "minecraft:bee");
            }

            BeeGenomeData genome = stored.contains(TAG_GENOME, Tag.TAG_COMPOUND)
                ? BeeGenomeData.fromNBT(stored.getCompound(TAG_GENOME))
                : BeeGenomeCapability.readFromEntityTag(entityTag);
            if (genome == null) {
                genome = BeeGenomeData.createWild(hive.getBlockPos().asLong() ^ transferred);
            }
            BeeGenomeCapability.writeToEntityTag(entityTag, genome);

            Bee bee = EntityType.BEE.create(hive.getLevel());
            if (bee == null) {
                break;
            }
            bee.load(entityTag);
            BeeGenomeCapability.Storage genomeStorage = BeeGenomeCapability.get(bee);
            if (genomeStorage != null) {
                genomeStorage.set(genome);
            }

            hive.addOccupantWithPresetTicks(bee, entityTag.getBoolean("HasNectar"), 0);
            bee.discard();
            bees.remove(0);
            transferred++;
        }

        if (transferred > 0) {
            writeStoredBees(stack, bees);
            hive.setChanged();
            BeehiveColonyCapability.Storage colony = BeehiveColonyCapability.get(hive);
            if (colony != null) {
                colony.synchronizeOccupants(hive);
            }
        }
        return transferred;
    }

    public static boolean extractStoredBee(ItemStack stack, BeehiveBlockEntity hive) {
        if (stack.isEmpty() || hive == null || getBeeCount(stack) >= MAX_BEES
            || hive.getOccupantCount() <= 0) {
            return false;
        }

        ListTag storedBees = hive.writeBees();
        if (storedBees.isEmpty()) {
            return false;
        }

        int storedIndex = storedBees.size() - 1;
        CompoundTag stored = storedBees.getCompound(storedIndex);
        CompoundTag entityTag = stored.getCompound(BeehiveBlockEntity.ENTITY_DATA).copy();

        BeehiveColonyCapability.Storage colony = BeehiveColonyCapability.get(hive);
        if (colony != null) {
            colony.synchronizeOccupants(hive);
        }

        BeeGenomeData genome = null;
        if (colony != null && !colony.getHost().getBees().isEmpty()) {
            List<BeeGenomeData> genomes = colony.getHost().getBees();
            genome = genomes.get(genomes.size() - 1);
        }
        if (genome == null) {
            genome = BeeGenomeCapability.readFromEntityTag(entityTag);
        }
        if (genome == null) {
            genome = BeeGenomeData.createWild(hive.getBlockPos().asLong());
        }
        BeeGenomeCapability.writeToEntityTag(entityTag, genome);

        CompoundTag captured = new CompoundTag();
        captured.put(TAG_ENTITY, entityTag);
        captured.put(TAG_GENOME, genome.toNBT());

        List<?> actualStored = ((BeehiveBlockEntityAccessor) (Object) hive).kae$getStored();
        if (actualStored.isEmpty()) {
            return false;
        }
        actualStored.remove(actualStored.size() - 1);

        ListTag netBees = getStoredBees(stack);
        netBees.add(captured);
        writeStoredBees(stack, netBees);
        hive.setChanged();

        if (colony != null) {
            colony.synchronizeOccupants(hive);
        }
        return true;
    }

    private static InteractionResult captureBee(ItemStack stack, Player player, Bee bee, InteractionHand hand) {
        ListTag bees = getStoredBees(stack);
        if (bees.size() >= MAX_BEES) {
            player.displayClientMessage(
                Component.translatable(MSG + "full", MAX_BEES).withStyle(ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }

        CompoundTag stored = new CompoundTag();
        CompoundTag entityTag = new CompoundTag();
        bee.saveWithoutId(entityTag);
        stored.put(TAG_ENTITY, entityTag);

        BeeGenomeData genome = BeeGenomeCapability.getOrCreate(bee);
        if (genome != null) {
            stored.put(TAG_GENOME, genome.toNBT());
        }

        bees.add(stored);
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(TAG_BEES, bees);
        stack.setTag(tag);
        player.setItemInHand(hand, stack);
        bee.discard();

        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP,
            SoundSource.PLAYERS, 0.5F, 1.2F);
        player.displayClientMessage(
            Component.translatable(MSG + "captured", bees.size(), MAX_BEES).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult releaseFirstBee(ItemStack stack, Player player, Level level, Vec3 spawnPos,
                                                     InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        ListTag bees = getStoredBees(stack);
        if (bees.isEmpty()) {
            player.displayClientMessage(
                Component.translatable(MSG + "empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }

        CompoundTag stored = bees.getCompound(0);
        Bee bee = EntityType.BEE.create(serverLevel);
        if (bee == null) {
            return InteractionResult.PASS;
        }

        CompoundTag entityTag = stored.getCompound(TAG_ENTITY).copy();
        entityTag.remove("UUID");
        bee.load(entityTag);
        bee.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, player.getYRot(), 0.0F);
        bee.setDeltaMovement(Vec3.ZERO);

        BeeGenomeCapability.Storage storage = BeeGenomeCapability.get(bee);
        if (storage != null && stored.contains(TAG_GENOME, Tag.TAG_COMPOUND)) {
            storage.set(BeeGenomeData.fromNBT(stored.getCompound(TAG_GENOME)));
        }

        serverLevel.addFreshEntity(bee);
        bees.remove(0);
        writeStoredBees(stack, bees);
        player.setItemInHand(hand, stack);

        level.playSound(null, player.blockPosition(), SoundEvents.BEEHIVE_EXIT,
            SoundSource.PLAYERS, 0.6F, 1.0F);
        player.displayClientMessage(
            Component.translatable(MSG + "released", bees.size(), MAX_BEES).withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    private static ListTag getStoredBees(ItemStack stack) {
        // 只读：绝不创建空 tag
        CompoundTag tag = stack.getTag();
        return tag == null ? new ListTag() : tag.getList(TAG_BEES, Tag.TAG_COMPOUND);
    }

    private static void writeStoredBees(ItemStack stack, ListTag bees) {
        CompoundTag tag = stack.getOrCreateTag();
        if (bees.isEmpty()) {
            tag.remove(TAG_BEES);
            if (tag.isEmpty()) {
                stack.setTag(null);
            }
        } else {
            tag.put(TAG_BEES, bees);
            stack.setTag(tag);
        }
    }
}
