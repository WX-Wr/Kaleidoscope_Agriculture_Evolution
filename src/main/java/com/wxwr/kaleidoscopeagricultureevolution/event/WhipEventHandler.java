package com.wxwr.kaleidoscopeagricultureevolution.event;

import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import com.wxwr.kaleidoscopeagricultureevolution.entity.ai.PlowAI;
import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import com.wxwr.kaleidoscopeagricultureevolution.region.Region;
import com.wxwr.kaleidoscopeagricultureevolution.region.RegionMode;
import com.wxwr.kaleidoscopeagricultureevolution.region.RegionSerializer;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkIssueReason;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRule;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkRuleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class WhipEventHandler {

    // 用于缓存删除目标的 NBT 键
    private static final String KEY_DELETE_TARGET = "deleteTarget";

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (event.getLevel().isClientSide) return;

        RegionMode mode = WhipItem.getMode(stack);

        if (player.isShiftKeyDown()) {
            // Shift+左键：在 DELETE 模式下确认删除缓存的区域
            handleShiftLeft(player, stack, mode, event);
        } else {
            // 普通左键
            if (mode == RegionMode.DELETE) {
                handleDeleteBuffer(player, stack, event);
            }
            // 已移除旧版一键删除，所有删除操作现在都通过 DELETE 模式进行
            // 使用两步确认（左键缓存区域，Shift+左键确认删除）
        }
    }

    private static void handleShiftLeft(Player player, ItemStack stack, RegionMode mode,
                                         PlayerInteractEvent.LeftClickBlock event) {
        RegionMode.SelectState state = WhipItem.getSelectState(stack);
        if (state != RegionMode.SelectState.IDLE) return;

        // 只读检查用 getTag()；只有确认要删除时才经 deleteByGroup 创建可写 tag
        CompoundTag tag = stack.getTag();
        if (mode == RegionMode.DELETE && tag != null && tag.contains(KEY_DELETE_TARGET)) {
            // 按组删除缓存的区域
            BlockPos target = BlockPos.of(tag.getLong(KEY_DELETE_TARGET));
            int[] info = WhipItem.findRegionIndex(stack, target);
            if (info[1] >= 0) {
                WhipItem.deleteByGroup(stack, info[1]);
                tag.remove(KEY_DELETE_TARGET);
                player.displayClientMessage(
                        Component.translatable("message.kaleidoscope_agriculture_evolution.range_removed"), true);
                event.setCanceled(true);
            }
        }
    }

    private static void handleDeleteBuffer(Player player, ItemStack stack,
                                            PlayerInteractEvent.LeftClickBlock event) {
        RegionMode.SelectState state = WhipItem.getSelectState(stack);
        if (state != RegionMode.SelectState.IDLE) return;

        // 有牛处于选区/方向选择流程时，不允许删除范围
        for (Entity e : player.level().getEntitiesOfClass(PlowOxEntity.class,
                player.getBoundingBox().inflate(20))) {
            if (e instanceof PlowOxEntity ox
                    && (ox.getOxState() == PlowOxEntity.OxState.SELECT_CORNER
                    || ox.getOxState() == PlowOxEntity.OxState.SELECT_DIRECTION)) {
                return;
            }
        }

        BlockPos target = event.getPos().above();
        Region r = WhipItem.findContainingRegion(stack, target);
        if (r != null) {
            stack.getOrCreateTag().putLong(KEY_DELETE_TARGET, target.asLong());
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.delete_buffered"), true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (player.isShiftKeyDown()) return;
        if (event.getLevel().isClientSide) return;

        RegionMode.SelectState state = WhipItem.getSelectState(stack);

        if (state != RegionMode.SelectState.IDLE) return;

        // 必须已选中一头牛
        UUID selectedUUID = WhipItem.getSelectedOx(stack);
        if (selectedUUID == null) {
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.no_ox_selected"), true);
            return;
        }

        BlockPos below = event.getPos();
        BlockPos target = below.above();

        // 查找选中的牛是否在附近
        PlowOxEntity selectedOx = findOxByUUID(player, selectedUUID);
        if (selectedOx == null) {
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.selected_ox_not_found"), true);
            return;
        }

        if (selectedOx.hasRiceSacks()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 未挂农具的牛不能进入工作状态
        if (!selectedOx.hasMountedTool()) {
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.no_tool_attached"), true);
            return;
        }

        PlowOxEntity.OxState oxState = selectedOx.getOxState();
        if (oxState == PlowOxEntity.OxState.SELECT_DIRECTION) {
            BlockPos corner = selectedOx.getTargetCorner();
            if (corner != null) {
                PlowAI.Direction matched = null;
                if (corner.offset(1, -1, 0).equals(below))      matched = PlowAI.Direction.PLUS_X;
                else if (corner.offset(-1, -1, 0).equals(below)) matched = PlowAI.Direction.MINUS_X;
                else if (corner.offset(0, -1, 1).equals(below))  matched = PlowAI.Direction.PLUS_Z;
                else if (corner.offset(0, -1, -1).equals(below)) matched = PlowAI.Direction.MINUS_Z;

                if (matched != null) {
                    selectedOx.startWorking(matched);
                    event.setCanceled(true);
                }
                return;
            }
        }

        if (oxState == PlowOxEntity.OxState.SELECT_CORNER) {
            // 只读：绝不创建空 tag；无 tag 即无已保存区域，直接结束
            CompoundTag tag = stack.getTag();
            if (tag == null) return;
            ListTag ranges = tag.getList("ranges", Tag.TAG_COMPOUND);
            for (int i = 0; i < ranges.size(); i++) {
                CompoundTag range = ranges.getCompound(i);
                BlockPos c1, c2;
                if (range.contains("c1") && range.contains("c2")) {
                    c1 = BlockPos.of(range.getLong("c1"));
                    c2 = BlockPos.of(range.getLong("c2"));
                } else {
                    continue;
                }

                BlockPos v1 = c1;
                BlockPos v2 = c2;
                BlockPos v3 = new BlockPos(c2.getX(), c1.getY(), c1.getZ());
                BlockPos v4 = new BlockPos(c1.getX(), c2.getY(), c2.getZ());
                BlockPos[] vertices = {v1, v2, v3, v4};
                BlockPos[] opposites = {v2, v1, v4, v3};

                for (int idx = 0; idx < 4; idx++) {
                    if (target.equals(vertices[idx])) {
                        WorkFieldType fieldType = RegionSerializer.getFieldType(range);
                        WorkAction action = RegionSerializer.getWorkAction(range);
                        selectedOx.setCurrentWorkZone(fieldType, action);
                        if (!validateToolForZone(player, selectedOx, fieldType, action)) {
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }
                        selectedOx.setTargetCorner(vertices[idx]);
                        selectedOx.setOtherCorner(opposites[idx]);
                        selectedOx.setOxState(PlowOxEntity.OxState.MOVE_TO_CORNER);
                        event.setCanceled(true);
                        return;
                    }
                }
            }
        }
    }

    /** 校验当前工作区域是否匹配耕牛挂载的工具。 */
    private static boolean validateToolForZone(Player player, PlowOxEntity ox,
                                               WorkFieldType fieldType, WorkAction action) {
        WorkRule rule = WorkRuleRegistry.findRule(fieldType, action).orElse(null);
        if (rule == null) {
            ox.enterError(WorkIssueReason.INVALID_WORK_ZONE);
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.work_rule_missing"), true);
            return false;
        }
        if (!ox.hasMountedTool()) {
            ox.enterError(WorkIssueReason.TOOL_MISSING);
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.no_tool_attached"), true);
            return false;
        }
        if (!rule.tool().matches(ox.getToolType())) {
            ox.enterError(WorkIssueReason.TOOL_MISMATCH);
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.tool_mismatch"), true);
            return false;
        }
        return true;
    }

    /**
     * 按 UUID 取出玩家选中的耕牛。
     *
     * <p>原先用 {@code getEntitiesOfClass(..., inflate(32))} 线性扫描，每次右键都要遍历附近
     * 全部实体；现改为 {@link ServerLevel#getEntity(UUID)} 的 O(1) 查找，并保留原来
     * 「必须在玩家周围 32 格内」的判定（用 AABB 相交，语义与实体扫描一致）。
     */
    private static PlowOxEntity findOxByUUID(Player player, UUID uuid) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(uuid);
        if (entity instanceof PlowOxEntity ox
                && ox.isAlive()
                && player.getBoundingBox().inflate(32).intersects(ox.getBoundingBox())) {
            return ox;
        }
        return null;
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) return;
        if (player.isShiftKeyDown()) return;
        if (!(event.getTarget() instanceof PlowOxEntity ox)) return;
        if (player.level().isClientSide) return;
        if (ox.hasRiceSacks()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        PlowOxEntity.OxState oxState = ox.getOxState();
        if (oxState == PlowOxEntity.OxState.ERROR) {
            ox.exitError();
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (oxState != PlowOxEntity.OxState.WAITING
                && oxState != PlowOxEntity.OxState.MOVE_TO_CORNER
                && oxState != PlowOxEntity.OxState.SELECT_CORNER
                && oxState != PlowOxEntity.OxState.SELECT_DIRECTION
                && oxState != PlowOxEntity.OxState.MOVE_TO_START
                && oxState != PlowOxEntity.OxState.START_WORKING
                && oxState != PlowOxEntity.OxState.WORKING
                && oxState != PlowOxEntity.OxState.FINISHING) {
            return;
        }
        if (oxState == PlowOxEntity.OxState.MOVE_TO_CORNER
                || oxState == PlowOxEntity.OxState.MOVE_TO_START
                || oxState == PlowOxEntity.OxState.START_WORKING
                || oxState == PlowOxEntity.OxState.WORKING
                || oxState == PlowOxEntity.OxState.FINISHING) {
            ox.cancelCurrentWork();
            player.displayClientMessage(
                    Component.translatable("message.kaleidoscope_agriculture_evolution.work_cancelled"), true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (oxState != PlowOxEntity.OxState.WAITING) {
            return;
        }

        // 将当前牛记录为鞭子选中的牛，后续选角和方向指令只对这头牛生效
        WhipItem.setSelectedOx(stack, ox.getUUID());
        ox.setTargetCorner(null);
        ox.setOtherCorner(null);
        ox.setPlowPathData("");
        ox.setOxState(PlowOxEntity.OxState.SELECT_CORNER);
        player.displayClientMessage(
                Component.translatable("message.kaleidoscope_agriculture_evolution.select_direction_mode"), true);
        event.setCanceled(true);
    }
}
