package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.event;

import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.PlowOxEntity;  // 添加这个导入
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.entity.ai.PlowAI;
import com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item.WhipItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.Kaleidoscope_Agriculture_Evolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class WhipEventHandler {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (player.isShiftKeyDown()) return;
        if (event.getLevel().isClientSide) return;

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getBoolean("editing")) return;

        // 有牛处于跟随模式时，不允许删除范围
        for (Entity e : player.level().getEntitiesOfClass(PlowOxEntity.class,
                player.getBoundingBox().inflate(20))) {
            if (e instanceof PlowOxEntity ox && ox.getOxState() == PlowOxEntity.OxState.FOLLOW) {
                return;
            }
        }

        BlockPos target = event.getPos();
        ListTag ranges = tag.getList("ranges", Tag.TAG_COMPOUND);
        if (ranges.isEmpty()) return;

        for (int i = ranges.size() - 1; i >= 0; i--) {
            CompoundTag range = ranges.getCompound(i);
            BlockPos c1 = BlockPos.of(range.getLong("c1"));
            BlockPos c2 = BlockPos.of(range.getLong("c2"));
            if (isWithinRange(target, c1, c2)) {
                ranges.remove(i);
                tag.put("ranges", ranges);
                player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.range_removed"), true);
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (player.isShiftKeyDown()) return;
        if (event.getLevel().isClientSide) return;

        CompoundTag tag = stack.getOrCreateTag();
        if (tag.getBoolean("editing")) return;

        BlockPos below = event.getPos();
        BlockPos target = below.above();

        for (Entity e : player.level().getEntitiesOfClass(PlowOxEntity.class,
                new AABB(below).inflate(10))) {
            if (e instanceof PlowOxEntity ox && ox.getOxState() == PlowOxEntity.OxState.SELECT_DIRECTION) {
                BlockPos corner = ox.getTargetCorner();
                if (corner == null) continue;

                if (corner.offset(1, -1, 0).equals(below)) {
                    ox.startPlowing(PlowAI.Direction.PLUS_X);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.plow_dir_x_pos"), true);
                    event.setCanceled(true);
                    return;
                }
                if (corner.offset(-1, -1, 0).equals(below)) {
                    ox.startPlowing(PlowAI.Direction.MINUS_X);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.plow_dir_x_neg"), true);
                    event.setCanceled(true);
                    return;
                }
                if (corner.offset(0, -1, 1).equals(below)) {
                    ox.startPlowing(PlowAI.Direction.PLUS_Z);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.plow_dir_z_pos"), true);
                    event.setCanceled(true);
                    return;
                }
                if (corner.offset(0, -1, -1).equals(below)) {
                    ox.startPlowing(PlowAI.Direction.MINUS_Z);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.plow_dir_z_neg"), true);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // 指派附近跟随中的牛去角落
        for (Entity e : player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(20))) {
            if (e instanceof PlowOxEntity ox && ox.getOxState() == PlowOxEntity.OxState.FOLLOW
                    && player.getUUID().equals(ox.getFollowOwnerUUID())) {

                ListTag ranges = tag.getList("ranges", Tag.TAG_COMPOUND);
                for (int i = 0; i < ranges.size(); i++) {
                    CompoundTag range = ranges.getCompound(i);
                    BlockPos c1 = BlockPos.of(range.getLong("c1"));
                    BlockPos c2 = BlockPos.of(range.getLong("c2"));

                    BlockPos v1 = c1;
                    BlockPos v2 = c2;
                    BlockPos v3 = new BlockPos(c2.getX(), c1.getY(), c1.getZ());
                    BlockPos v4 = new BlockPos(c1.getX(), c2.getY(), c2.getZ());
                    BlockPos[] vertices = {v1, v2, v3, v4};
                    BlockPos[] opposites = {v2, v1, v4, v3};

                    for (int idx = 0; idx < 4; idx++) {
                        if (target.equals(vertices[idx])) {
                            ox.setTargetCorner(vertices[idx]);
                            ox.setOtherCorner(opposites[idx]);
                            ox.setOxState(PlowOxEntity.OxState.MOVE_TO_CORNER);
                            player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.move_to_corner"), true);
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (player.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof PlowOxEntity ox)) return;
        if (player.level().isClientSide) return;

        ox.setOxState(PlowOxEntity.OxState.FOLLOW);
        ox.setFollowOwnerUUID(player.getUUID());
        player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.follow_mode"), true);
        event.setCanceled(true);
    }

    private static boolean isWithinRange(BlockPos target, BlockPos c1, BlockPos c2) {
        int minX = Math.min(c1.getX(), c2.getX());
        int maxX = Math.max(c1.getX(), c2.getX());
        int minZ = Math.min(c1.getZ(), c2.getZ());
        int maxZ = Math.max(c1.getZ(), c2.getZ());
        int minY = Math.min(c1.getY(), c2.getY());
        int maxY = Math.max(c1.getY(), c2.getY());

        return target.getX() >= minX && target.getX() <= maxX
                && target.getZ() >= minZ && target.getZ() <= maxZ
                && target.getY() >= minY - 1 && target.getY() <= maxY - 1;
    }
}