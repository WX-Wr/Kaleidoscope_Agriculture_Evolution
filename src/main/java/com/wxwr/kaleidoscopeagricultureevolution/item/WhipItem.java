package com.wxwr.kaleidoscopeagricultureevolution.item;

import com.wxwr.kaleidoscopeagricultureevolution.region.*;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneConflictChecker;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneProfile;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneSerializer;
import com.wxwr.kaleidoscopeagricultureevolution.work.WhipMenuLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WhipItem extends Item {

    // ---- NBT 键名 -----------------------------------------------------------

    static final String KEY_MODE         = "mode";
    static final String KEY_SELECT_STATE = "selectState";
    static final String KEY_CORNER1      = "corner1";
    static final String KEY_CORNER2      = "corner2";
    static final String KEY_CENTER       = "center";
    static final String KEY_RADIUS       = "radius";
    static final String KEY_CYL_MIN_Y    = "cylMinY";
    static final String KEY_CYL_MAX_Y    = "cylMaxY";
    static final String KEY_RANGES       = "ranges";
    static final String KEY_C1           = "c1";
    static final String KEY_C2           = "c2";
    static final String KEY_GROUP         = "group";
    static final String KEY_NEXT_GROUP_ID = "nextGroupId";
    static final String KEY_SELECTED_OX   = "selectedOx";
    static final String KEY_MENU_LAYER     = "menuLayer";
    static final String KEY_SELECTED_FIELD_TYPE = "selectedFieldType";
    static final String KEY_SELECTED_WORK_ACTION = "selectedWorkAction";

    // 消息前缀
    private static final String MSG = "message.kaleidoscope_agriculture_evolution.";

    public WhipItem(Properties properties) {
        super(properties);
    }

    // ---- 主要交互 ---------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        if (level.isClientSide) return InteractionResultHolder.pass(stack);

        // 写入路径：handleShiftRight / handleRight 在多数分支会写 NBT，故此处需要可写 tag。
        // 只读查询请改用 getMode(ItemStack) 等 null-safe 版本，绝不创建空 tag。
        CompoundTag tag = stack.getOrCreateTag();
        RegionMode mode = getMode(tag);
        RegionMode.SelectState state = getSelectState(tag);

        if (player.isShiftKeyDown()) {
            // Shift+右键 — 根据状态进入/退出/完成选择
            handleShiftRight(level, player, stack, tag, mode, state);
        } else {
            // 普通右键 — 在EDITING状态下设置第二个点，或从PREVIEW状态重新进入
            handleRight(level, player, stack, tag, mode, state);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || level.isClientSide) return InteractionResult.PASS;

        // 在EDITING状态下右键方块 — 与普通右键行为一致
        // 写入路径：handleRight 会写 NBT，故此处需要可写 tag
        CompoundTag tag = stack.getOrCreateTag();
        RegionMode mode = getMode(tag);
        RegionMode.SelectState state = getSelectState(tag);

        if (!player.isShiftKeyDown()
                && state == RegionMode.SelectState.EDITING
                && mode != RegionMode.DELETE) {
            handleRight(level, player, stack, tag, mode, state);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ---- Shift+右键 --------------------------------------------------

    private void handleShiftRight(Level level, Player player, ItemStack stack,
                                  CompoundTag tag, RegionMode mode,
                                  RegionMode.SelectState state) {
        BlockPos feet = player.blockPosition();

        switch (state) {
            case IDLE -> {
                // 进入模式 + 编辑状态（仅对活动模式有效）
                switch (mode) {
                    case RECTANGLE -> {
                        setSelectState(tag, RegionMode.SelectState.EDITING);
                        tag.putLong(KEY_CORNER1, feet.asLong());
                        tag.remove(KEY_CORNER2);
                        player.displayClientMessage(
                                Component.translatable(MSG + "edit_enter"), true);
                    }
                    // case CIRCLE -> { ... }  // 圆形模式已禁用
                    // case UNION, COMPLEMENT -> { ... }  // 已禁用 — 空操作
                    case DELETE -> {
                        // 删除模式无编辑流程 — 通过左键处理
                        setSelectState(tag, RegionMode.SelectState.IDLE);
                    }
                    default -> { /* 并集、补集 — 尚未实现 */ }
                }
            }

            case EDITING -> {
                // 放弃 — 尚未记录第二个点
                clearEditState(tag);
                setSelectState(tag, RegionMode.SelectState.IDLE);
                player.displayClientMessage(
                        Component.translatable(MSG + "edit_cancelled"), true);
            }

            case PREVIEW -> {
                // 确认并保存
                if (saveCurrentRegion(tag, player)) {
                    clearEditState(tag);
                    setSelectState(tag, RegionMode.SelectState.IDLE);
                    player.displayClientMessage(
                            Component.translatable(MSG + "edit_saved"), true);
                }
            }
        }
    }

    // ---- 普通右键 --------------------------------------------------

    private void handleRight(Level level, Player player, ItemStack stack,
                              CompoundTag tag, RegionMode mode,
                              RegionMode.SelectState state) {
        BlockPos feet = player.blockPosition();

        switch (state) {
            case EDITING -> {
                // 完成选择 → 进入PREVIEW状态
                switch (mode) {
                    case RECTANGLE -> {
                        tag.putLong(KEY_CORNER2, feet.asLong());
                        setSelectState(tag, RegionMode.SelectState.PREVIEW);
                        player.displayClientMessage(
                                Component.translatable(MSG + "edit_corner"), true);
                    }
                    // case CIRCLE -> { ... }  // 圆形模式已禁用
                    // case UNION, COMPLEMENT -> { ... }  // 已禁用
                    case DELETE -> { /* 空操作 */ }
                    default -> { /* 并集、补集 — 尚未实现 */ }
                }
            }

            case PREVIEW -> {
                // 重新编辑 — 清除第二个点，返回EDITING状态
                switch (mode) {
                    case RECTANGLE -> {
                        tag.remove(KEY_CORNER2);
                        setSelectState(tag, RegionMode.SelectState.EDITING);
                        player.displayClientMessage(
                                Component.translatable(MSG + "edit_reenter"), true);
                    }
                    // case CIRCLE -> { ... }  // 圆形模式已禁用
                    // case UNION, COMPLEMENT -> { ... }  // 已禁用
                    case DELETE -> { /* 空操作 */ }
                    default -> { /* 并集、补集 — 尚未实现 */ }
                }
            }

            case IDLE -> { /* 空操作 — 不在选择流程中 */ }
        }
    }

    // ---- 模式切换（由网络数据包调用） --------------------------

    /**
     * 向前或向后切换模式一步。
     * 由 {@code ModeScrollPacket} 在服务端调用。
     */
    public void cycleModeAndUpdate(ItemStack stack, boolean forward) {
        // 仅在非编辑状态下切换
        if (getSelectState(stack) != RegionMode.SelectState.IDLE) return;

        RegionMode current = getMode(stack);
        RegionMode next = forward ? current.next() : current.prev();
        stack.getOrCreateTag().putInt(KEY_MODE, next.getId());
    }

    public void cycleMenuSelection(ItemStack stack, boolean forward) {
        if (getSelectState(stack) != RegionMode.SelectState.IDLE) return;

        switch (getEffectiveMenuLayer(stack.getTag())) {
            case REGION_MODE -> cycleModeAndUpdate(stack, forward);
            case FIELD_TYPE -> stack.getOrCreateTag().putString(KEY_SELECTED_FIELD_TYPE,
                    cycleEnum(getSelectedFieldType(stack), forward).key());
            case WORK_ACTION -> stack.getOrCreateTag().putString(KEY_SELECTED_WORK_ACTION,
                    cycleEnum(getSelectedWorkAction(stack), forward).key());
        }
    }

    public void advanceMenuLayer(ItemStack stack) {
        if (getSelectState(stack) != RegionMode.SelectState.IDLE) return;
        if (!canOpenWorkZoneMenu(stack)) {
            stack.getOrCreateTag().putInt(KEY_MENU_LAYER, WhipMenuLayer.REGION_MODE.id());
            return;
        }

        stack.getOrCreateTag().putInt(KEY_MENU_LAYER, getEffectiveMenuLayer(stack.getTag()).next().id());
    }

    public static boolean canOpenWorkZoneMenu(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return getSelectState(tag) == RegionMode.SelectState.IDLE && canOpenWorkZoneMenu(tag);
    }

    private static boolean canOpenWorkZoneMenu(@Nullable CompoundTag tag) {
        return getMode(tag) != RegionMode.DELETE;
    }

    private static WorkFieldType cycleEnum(WorkFieldType current, boolean forward) {
        WorkFieldType[] values = WorkFieldType.values();
        int next = (current.ordinal() + (forward ? 1 : -1) + values.length) % values.length;
        return values[next];
    }

    private static WorkAction cycleEnum(WorkAction current, boolean forward) {
        WorkAction[] values = WorkAction.values();
        int next = (current.ordinal() + (forward ? 1 : -1) + values.length) % values.length;
        return values[next];
    }

    // ---- 保存 -------------------------------------------------------------

    /**
     * 将当前预览的区域保存到持久的区域列表中，
     * 根据当前模式应用相应的逻辑。
     */
    private boolean saveCurrentRegion(CompoundTag tag, Player player) {
        RegionMode mode = getMode(tag);

        return switch (mode) {
            case RECTANGLE -> saveRectangle(tag, player);
            case DELETE -> false;
            default -> false;
        };
    }
    // --- 矩形保存（并集语义） ---

    private boolean saveRectangle(CompoundTag tag, Player player) {
        BlockPos c1 = BlockPos.of(tag.getLong(KEY_CORNER1));
        BlockPos c2 = BlockPos.of(tag.getLong(KEY_CORNER2));
        BlockPos[] newRange = CuboidGeometry.normalize(c1, c2);
        CuboidRegion region = new CuboidRegion(newRange[0], newRange[1]);
        WorkFieldType fieldType = getSelectedFieldType(tag);
        WorkAction action = getSelectedWorkAction(tag);

        try {
            region.checkAreaCap();
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal("§c" + e.getMessage()), false);
            return false;
        }

        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        List<RangeEntry> existing = loadEntries(ranges, tag);

        List<BlockPos[]> existingRanges = new ArrayList<>();
        for (RangeEntry e : existing) {
            existingRanges.add(e.range);
        }
        if (WorkZoneConflictChecker.firstIntersection(newRange, existingRanges).isPresent()) {
            setSelectState(tag, RegionMode.SelectState.EDITING);
            player.displayClientMessage(Component.translatable(MSG + "region_conflict"), true);
            return false;
        }

        // 查找与新区域相交的已有分组
        int targetGroup = -1;
        for (RangeEntry e : existing) {
            if (CuboidGeometry.intersection(newRange, e.range) != null) {
                targetGroup = e.group;
                break;
            }
        }

        if (targetGroup < 0) {
            // 与任何已有分组均无重叠 → 创建新的独立分组
            targetGroup = getNextGroupId(tag);
            incNextGroupId(tag);
        }

        // 收集目标分组中的所有区域 + 新区域
        List<BlockPos[]> groupRanges = new ArrayList<>();
        for (RangeEntry e : existing) {
            if (e.group == targetGroup) {
                groupRanges.add(e.range);
            }
        }
        groupRanges.add(newRange);

        // 合并重叠/相邻的区域以消除内部边界
        List<BlockPos[]> merged = CuboidGeometry.merge(groupRanges);

        // 重建：保留非目标分组，然后添加合并结果
        List<RangeEntry> result = new ArrayList<>();
        for (RangeEntry e : existing) {
            if (e.group != targetGroup) {
                result.add(e);
            }
        }
        for (BlockPos[] r : merged) {
            result.add(new RangeEntry(r, targetGroup, fieldType, action));
        }

        writeEntries(tag, result);

        return true;
    }

    // --- 圆形保存（已禁用） ---
    /*
    private void saveCircle(CompoundTag tag, Player player) {
        BlockPos center = BlockPos.of(tag.getLong(KEY_CENTER));
        double radius = tag.getDouble(KEY_RADIUS);
        if (radius <= 0) return;

        int cylMinY = tag.contains(KEY_CYL_MIN_Y) ? tag.getInt(KEY_CYL_MIN_Y) : center.getY();
        int cylMaxY = tag.contains(KEY_CYL_MAX_Y) ? tag.getInt(KEY_CYL_MAX_Y) : center.getY();

        CircleRegion region = new CircleRegion(center, radius, cylMinY, cylMaxY);

        try {
            region.checkAreaCap();
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal("§c" + e.getMessage()), false);
            return;
        }

        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        List<RangeEntry> existing = loadEntries(ranges, tag);
        int newGroupId = getNextGroupId(tag);

        // 保存为单个条目的列表（圆形以带类型的NBT条目存储）
        CompoundTag entry = RegionSerializer.serialize(region, newGroupId);
        ranges.add(entry);
        tag.put(KEY_RANGES, ranges);
        incNextGroupId(tag);
    }
    */

    // --- 并集保存（已禁用） ---
    /*
    private void saveUnion(CompoundTag tag, Player player) {
        BlockPos c1 = BlockPos.of(tag.getLong(KEY_CORNER1));
        BlockPos c2 = BlockPos.of(tag.getLong(KEY_CORNER2));
        BlockPos[] newRange = CuboidGeometry.normalize(c1, c2);

        CuboidRegion region = new CuboidRegion(newRange[0], newRange[1]);
        try {
            region.checkAreaCap();
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal("§c" + e.getMessage()), false);
            return;
        }

        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        List<RangeEntry> existing = loadEntries(ranges, tag);

        // 查找与新区域相交的第一个已有分组
        int targetGroup = -1;
        for (RangeEntry e : existing) {
            if (CuboidGeometry.intersection(newRange, e.range) != null) {
                targetGroup = e.group;
                break;
            }
        }

        if (targetGroup < 0) {
            // 无交集 — 添加为新的独立分组
            targetGroup = getNextGroupId(tag);
            incNextGroupId(tag);
        }

        // 收集目标分组中的所有区域 + 新区域
        List<BlockPos[]> groupRanges = new ArrayList<>();
        for (RangeEntry e : existing) {
            if (e.group == targetGroup) {
                groupRanges.add(e.range);
            }
        }
        groupRanges.add(newRange);

        // 合并重叠/相邻的区域以消除内部边界
        List<BlockPos[]> merged = CuboidGeometry.merge(groupRanges);

        // 重建：保留非目标分组，然后添加合并结果
        List<RangeEntry> result = new ArrayList<>();
        for (RangeEntry e : existing) {
            if (e.group != targetGroup) {
                result.add(e);
            }
        }
        for (BlockPos[] r : merged) {
            result.add(new RangeEntry(r, targetGroup));
        }

        writeEntries(tag, result);

        player.displayClientMessage(
                Component.translatable(MSG + "union_added"), true);
    }
    */

    // --- 补集保存（已禁用） ---
    /*
    private void saveComplement(CompoundTag tag, Player player) {
        BlockPos c1 = BlockPos.of(tag.getLong(KEY_CORNER1));
        BlockPos c2 = BlockPos.of(tag.getLong(KEY_CORNER2));
        BlockPos[] eraseRange = CuboidGeometry.normalize(c1, c2);

        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        List<RangeEntry> existing = loadEntries(ranges, tag);

        boolean erased = false;
        List<RangeEntry> result = new ArrayList<>();

        for (RangeEntry e : existing) {
            BlockPos[] I = CuboidGeometry.intersection(e.range, eraseRange);
            if (I != null) {
                erased = true;
                // 从该条目中减去擦除区域 → 保留剩余片段
                for (BlockPos[] sub : CuboidGeometry.subtract(e.range, I)) {
                    result.add(new RangeEntry(sub, e.group));
                }
            } else {
                result.add(e);
            }
        }

        writeEntries(tag, result);

        if (erased) {
            player.displayClientMessage(
                    Component.translatable(MSG + "complement_erased"), true);
        }
    }
    */

    // ---- 公共区域访问 -----------------------------------------------

    /**
     * 以 {@link Region} 对象列表的形式返回所有已保存的区域。
     * 用于客户端渲染和服务端包含检查。
     */
    public static List<Region> getRegions(ItemStack stack) {
        List<Region> result = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) return result;
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ranges.size(); i++) {
            Region r = RegionSerializer.deserialize(ranges.getCompound(i));
            if (r != null) result.add(r);
        }
        return result;
    }

    public static List<WorkZoneProfile> getWorkZones(ItemStack stack) {
        List<WorkZoneProfile> result = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) return result;
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ranges.size(); i++) {
            CompoundTag entry = ranges.getCompound(i);
            Region region = RegionSerializer.deserialize(entry);
            if (region != null) {
                result.add(new WorkZoneProfile(
                        region,
                        RegionSerializer.getGroupId(entry),
                        RegionSerializer.getFieldType(entry),
                        RegionSerializer.getWorkAction(entry)
                ));
            }
        }
        return result;
    }

    /**
     * @deprecated 请改用 {@link #getRegions(ItemStack)}。
     * 保留用于过渡期；仅返回矩形包围盒列表。
     */
    @Deprecated
    public static List<BlockPos[]> getRanges(ItemStack stack) {
        List<BlockPos[]> result = new ArrayList<>();
        for (Region r : getRegions(stack)) {
            result.add(new BlockPos[]{r.getMinimumPoint(), r.getMaximumPoint()});
        }
        return result;
    }

    /**
     * 查找包含 {@code pos} 的区域（如果存在）。
     * 由删除工具使用。
     */
    public static Region findContainingRegion(ItemStack stack, BlockPos pos) {
        for (Region r : getRegions(stack)) {
            if (r.contains(pos)) return r;
        }
        return null;
    }

    /**
     * 查找包含 {@code pos} 的区域的NBT索引和分组ID。
     * 如果未找到则返回 {@code [-1, -1]}。
     */
    public static int[] findRegionIndex(ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return new int[]{-1, -1};
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ranges.size(); i++) {
            Region r = RegionSerializer.deserialize(ranges.getCompound(i));
            if (r != null && r.contains(pos)) {
                int group = RegionSerializer.getGroupId(ranges.getCompound(i));
                return new int[]{i, group};
            }
        }
        return new int[]{-1, -1};
    }

    /**
     * 删除与指定分组ID匹配的所有区域条目。
     */
    public static void deleteByGroup(ItemStack stack, int targetGroup) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        for (int i = ranges.size() - 1; i >= 0; i--) {
            if (RegionSerializer.getGroupId(ranges.getCompound(i)) == targetGroup) {
                ranges.remove(i);
            }
        }
        tag.put(KEY_RANGES, ranges);
    }

    // ---- NBT 辅助方法 --------------------------------------------------------
    // 只读 getter 一律接受 @Nullable tag：tag 为 null 时返回与"键缺失"完全相同的
    // 默认值（getInt 缺键本来就是 0），因此语义不变，但不会给物品凭空挂上空 tag。

    static RegionMode getMode(@Nullable CompoundTag tag) {
        int id = tag == null ? 0 : tag.getInt(KEY_MODE);
        return RegionMode.fromId(id);
    }

    public static RegionMode getMode(ItemStack stack) {
        return getMode(stack.getTag());
    }

    static RegionMode.SelectState getSelectState(@Nullable CompoundTag tag) {
        int id = tag == null ? 0 : tag.getInt(KEY_SELECT_STATE);
        return RegionMode.SelectState.fromId(id);
    }

    public static RegionMode.SelectState getSelectState(ItemStack stack) {
        return getSelectState(stack.getTag());
    }

    static WhipMenuLayer getMenuLayer(@Nullable CompoundTag tag) {
        return WhipMenuLayer.fromId(tag == null ? 0 : tag.getInt(KEY_MENU_LAYER));
    }

    public static WhipMenuLayer getMenuLayer(ItemStack stack) {
        return getEffectiveMenuLayer(stack.getTag());
    }

    private static WhipMenuLayer getEffectiveMenuLayer(@Nullable CompoundTag tag) {
        if (!canOpenWorkZoneMenu(tag)) {
            return WhipMenuLayer.REGION_MODE;
        }
        return getMenuLayer(tag);
    }

    static void setSelectState(CompoundTag tag, RegionMode.SelectState state) {
        tag.putInt(KEY_SELECT_STATE, state.getId());
    }

    /** 获取鞭子当前选中的功能区地类。 */
    public static WorkFieldType getSelectedFieldType(ItemStack stack) {
        return getSelectedFieldType(stack.getTag());
    }

    static WorkFieldType getSelectedFieldType(@Nullable CompoundTag tag) {
        String key = tag == null ? "" : tag.getString(KEY_SELECTED_FIELD_TYPE);
        if (key.isEmpty()) {
            return WorkZoneSerializer.DEFAULT_FIELD_TYPE;
        }
        return WorkFieldType.fromKey(key);
    }

    public static void setSelectedFieldType(ItemStack stack, WorkFieldType fieldType) {
        stack.getOrCreateTag().putString(KEY_SELECTED_FIELD_TYPE, fieldType.key());
    }

    public static WorkAction getSelectedWorkAction(ItemStack stack) {
        return getSelectedWorkAction(stack.getTag());
    }

    static WorkAction getSelectedWorkAction(@Nullable CompoundTag tag) {
        String key = tag == null ? "" : tag.getString(KEY_SELECTED_WORK_ACTION);
        if (key.isEmpty()) {
            return WorkZoneSerializer.DEFAULT_ACTION;
        }
        return WorkAction.fromKey(key);
    }

    public static void setSelectedWorkAction(ItemStack stack, WorkAction action) {
        stack.getOrCreateTag().putString(KEY_SELECTED_WORK_ACTION, action.key());
    }

    /** 获取鞭子当前选中的耕牛 UUID（玩家右键过的牛）。 */
    public static UUID getSelectedOx(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(KEY_SELECTED_OX)) {
            return tag.getUUID(KEY_SELECTED_OX);
        }
        return null;
    }

    /** 设置鞭子当前选中的耕牛 UUID。 */
    public static void setSelectedOx(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putUUID(KEY_SELECTED_OX, uuid);
    }

    static void clearEditState(CompoundTag tag) {
        tag.remove(KEY_CORNER1);
        tag.remove(KEY_CORNER2);
        tag.remove(KEY_CENTER);
        tag.remove(KEY_RADIUS);
    }

    private static int getNextGroupId(CompoundTag tag) {
        return tag.getInt(KEY_NEXT_GROUP_ID);
    }

    private static void incNextGroupId(CompoundTag tag) {
        tag.putInt(KEY_NEXT_GROUP_ID, tag.getInt(KEY_NEXT_GROUP_ID) + 1);
    }

    // ---- 区域条目读写 ----------------------------------------------------

    private List<RangeEntry> loadEntries(ListTag ranges, CompoundTag rootTag) {
        List<RangeEntry> result = new ArrayList<>();
        int nextId = rootTag.getInt(KEY_NEXT_GROUP_ID);

        for (int i = 0; i < ranges.size(); i++) {
            CompoundTag r = ranges.getCompound(i);
            // 旧格式条目没有类型字段；隐式视为矩形。
            // 我们直接从旧格式读取 c1/c2。
            if (!r.contains(RegionSerializer.KEY_TYPE) || "cuboid".equals(r.getString(RegionSerializer.KEY_TYPE))) {
                if (!r.contains(KEY_C1) || !r.contains(KEY_C2)) continue;
                BlockPos c1 = BlockPos.of(r.getLong(KEY_C1));
                BlockPos c2 = BlockPos.of(r.getLong(KEY_C2));
                int group;
                if (r.contains(KEY_GROUP)) {
                    group = r.getInt(KEY_GROUP);
                    if (group >= nextId) nextId = group + 1;
                } else {
                    group = nextId++;
                }
                result.add(new RangeEntry(
                        CuboidGeometry.normalize(c1, c2),
                        group,
                        RegionSerializer.getFieldType(r),
                        RegionSerializer.getWorkAction(r)
                ));
            } else {
                // 圆形条目 — 保持原样；它们是非矩形区域
                int group = RegionSerializer.getGroupId(r);
                if (group >= nextId) nextId = group + 1;
                // 圆形以 NBT 形式存储，写入时重新序列化。
                // loadEntries 仅处理矩形以进行交集数学计算。
                // 圆形条目在此处跳过，保留在原始 NBT 中。
            }
        }

        rootTag.putInt(KEY_NEXT_GROUP_ID, nextId);
        return result;
    }

    private void writeEntries(CompoundTag tag, List<RangeEntry> entries) {
        ListTag out = new ListTag();
        for (RangeEntry re : entries) {
            CompoundTag entry = RegionSerializer.serialize(
                    new CuboidRegion(re.range[0], re.range[1]),
                    re.group,
                    re.fieldType,
                    re.action
            );
            out.add(entry);
        }
        tag.put(KEY_RANGES, out);
    }

    // ---- 内部数据类 ------------------------------------------------

    private record RangeEntry(BlockPos[] range, int group, WorkFieldType fieldType, WorkAction action) {}
}
