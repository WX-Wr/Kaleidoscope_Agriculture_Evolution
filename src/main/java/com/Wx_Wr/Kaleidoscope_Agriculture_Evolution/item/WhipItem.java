package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.item;

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

import java.util.ArrayList;
import java.util.List;

public class WhipItem extends Item {

    private static final String KEY_EDITING = "editing";
    private static final String KEY_CORNER1 = "corner1";
    private static final String KEY_CORNER2 = "corner2";
    private static final String KEY_RANGES = "ranges";
    private static final String KEY_C1 = "c1";
    private static final String KEY_C2 = "c2";
    private static final String KEY_GROUP = "group";
    private static final String KEY_NEXT_GROUP_ID = "nextGroupId";

    public WhipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        CompoundTag tag = stack.getOrCreateTag();

        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                boolean editing = !tag.getBoolean(KEY_EDITING);
                if (!editing) {
                    if (tag.contains(KEY_CORNER1) && tag.contains(KEY_CORNER2)) {
                        saveRange(tag, player);
                    }
                    tag.remove(KEY_CORNER1);
                    tag.remove(KEY_CORNER2);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_exit"), true);
                } else {
                    tag.putLong(KEY_CORNER1, player.blockPosition().asLong());
                    tag.remove(KEY_CORNER2);
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_enter"), true);
                }
                tag.putBoolean(KEY_EDITING, editing);
            } else {
                if (tag.getBoolean(KEY_EDITING)) {
                    tag.putLong(KEY_CORNER2, player.blockPosition().asLong());
                    player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_corner"), true);
                }
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || level.isClientSide) return InteractionResult.PASS;

        CompoundTag tag = stack.getOrCreateTag();

        if (tag.getBoolean(KEY_EDITING) && !player.isShiftKeyDown()) {
            tag.putLong(KEY_CORNER2, context.getClickedPos().asLong());
            player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.edit_corner"), true);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * Save a new range, computing symmetric difference with any intersecting
     * existing ranges. Each sub-range carries a group ID so pieces from the
     * same original range can be identified.
     */
    private void saveRange(CompoundTag tag, Player player) {
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);

        BlockPos[] newRange = normalizeRange(
                BlockPos.of(tag.getLong(KEY_CORNER1)),
                BlockPos.of(tag.getLong(KEY_CORNER2))
        );

        // Assign group IDs, backfilling any existing ranges that lack one
        List<RangeEntry> existing = loadEntries(ranges, tag);
        int newGroupId = tag.getInt(KEY_NEXT_GROUP_ID);
        tag.putInt(KEY_NEXT_GROUP_ID, newGroupId + 1);

        // Pieces of the new range still to be placed
        List<RangeEntry> incoming = new ArrayList<>();
        incoming.add(new RangeEntry(newRange, newGroupId));

        // Existing ranges get processed through the split too
        List<RangeEntry> result = new ArrayList<>();
        boolean trimmed = false;

        for (RangeEntry e : existing) {
            List<RangeEntry> eRemaining = new ArrayList<>();
            eRemaining.add(e);
            List<RangeEntry> nextIncoming = new ArrayList<>();

            for (RangeEntry piece : incoming) {
                List<RangeEntry> eNext = new ArrayList<>();
                List<RangeEntry> pRemaining = new ArrayList<>();
                pRemaining.add(piece);

                for (RangeEntry ep : eRemaining) {
                    List<RangeEntry> pNext = new ArrayList<>();
                    for (RangeEntry pp : pRemaining) {
                        BlockPos[] I = getIntersection(pp.range, ep.range);
                        if (I != null) {
                            trimmed = true;
                            for (BlockPos[] sub : subtractRange(pp.range, I)) {
                                pNext.add(new RangeEntry(sub, pp.group));
                            }
                            for (BlockPos[] sub : subtractRange(ep.range, I)) {
                                eNext.add(new RangeEntry(sub, ep.group));
                            }
                        } else {
                            pNext.add(pp);
                            eNext.add(ep);
                        }
                    }
                    pRemaining = pNext;
                }
                eRemaining = eNext;
                nextIncoming.addAll(pRemaining);
            }

            result.addAll(eRemaining);
            incoming = nextIncoming;
        }

        result.addAll(incoming);

        // Write back
        ListTag out = new ListTag();
        for (RangeEntry re : result) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(KEY_C1, re.range[0].asLong());
            entry.putLong(KEY_C2, re.range[1].asLong());
            entry.putInt(KEY_GROUP, re.group);
            out.add(entry);
        }
        tag.put(KEY_RANGES, out);

        if (trimmed) {
            player.displayClientMessage(Component.translatable("message.kaleidoscope_agriculture_evolution.range_trimmed"), true);
        }
    }

    /**
     * Load range entries from NBT, assigning group IDs to any entries that lack one.
     */
    private List<RangeEntry> loadEntries(ListTag ranges, CompoundTag rootTag) {
        List<RangeEntry> result = new ArrayList<>();
        int nextId = rootTag.getInt(KEY_NEXT_GROUP_ID);

        for (int i = 0; i < ranges.size(); i++) {
            CompoundTag r = ranges.getCompound(i);
            BlockPos c1 = BlockPos.of(r.getLong(KEY_C1));
            BlockPos c2 = BlockPos.of(r.getLong(KEY_C2));
            int group;
            if (r.contains(KEY_GROUP)) {
                group = r.getInt(KEY_GROUP);
                if (group >= nextId) {
                    nextId = group + 1;
                }
            } else {
                group = nextId++;
            }
            result.add(new RangeEntry(normalizeRange(c1, c2), group));
        }

        rootTag.putInt(KEY_NEXT_GROUP_ID, nextId);
        return result;
    }

    public static List<BlockPos[]> getRanges(ItemStack stack) {
        List<BlockPos[]> result = new ArrayList<>();
        CompoundTag tag = stack.getOrCreateTag();
        ListTag ranges = tag.getList(KEY_RANGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < ranges.size(); i++) {
            CompoundTag range = ranges.getCompound(i);
            result.add(new BlockPos[]{
                    BlockPos.of(range.getLong(KEY_C1)),
                    BlockPos.of(range.getLong(KEY_C2))
            });
        }
        return result;
    }

    // --- Geometry helpers ---

    private static BlockPos[] normalizeRange(BlockPos a, BlockPos b) {
        return new BlockPos[]{
                new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))
        };
    }

    private static BlockPos[] getIntersection(BlockPos[] a, BlockPos[] b) {
        BlockPos[] na = normalizeRange(a[0], a[1]);
        BlockPos[] nb = normalizeRange(b[0], b[1]);

        int ix1 = Math.max(na[0].getX(), nb[0].getX());
        int iy1 = Math.max(na[0].getY(), nb[0].getY());
        int iz1 = Math.max(na[0].getZ(), nb[0].getZ());
        int ix2 = Math.min(na[1].getX(), nb[1].getX());
        int iy2 = Math.min(na[1].getY(), nb[1].getY());
        int iz2 = Math.min(na[1].getZ(), nb[1].getZ());

        if (ix1 > ix2 || iy1 > iy2 || iz1 > iz2) return null;

        return new BlockPos[]{new BlockPos(ix1, iy1, iz1), new BlockPos(ix2, iy2, iz2)};
    }

    /**
     * Subtract the intersection from a range. Returns the parts of the range that
     * lie outside the intersection, as up to 6 rectangular sub-ranges.
     */
    private static List<BlockPos[]> subtractRange(BlockPos[] range, BlockPos[] intersection) {
        List<BlockPos[]> result = new ArrayList<>();

        BlockPos[] nr = normalizeRange(range[0], range[1]);
        BlockPos[] ni = normalizeRange(intersection[0], intersection[1]);

        int ax1 = nr[0].getX(), ay1 = nr[0].getY(), az1 = nr[0].getZ();
        int ax2 = nr[1].getX(), ay2 = nr[1].getY(), az2 = nr[1].getZ();
        int ix1 = ni[0].getX(), iy1 = ni[0].getY(), iz1 = ni[0].getZ();
        int ix2 = ni[1].getX(), iy2 = ni[1].getY(), iz2 = ni[1].getZ();

        // X-axis: left and right of intersection
        if (ax1 < ix1) {
            result.add(new BlockPos[]{new BlockPos(ax1, ay1, az1), new BlockPos(ix1 - 1, ay2, az2)});
        }
        if (ax2 > ix2) {
            result.add(new BlockPos[]{new BlockPos(ix2 + 1, ay1, az1), new BlockPos(ax2, ay2, az2)});
        }

        int mx1 = Math.max(ax1, ix1);
        int mx2 = Math.min(ax2, ix2);

        // Y-axis: below and above intersection (within middle X band)
        if (ay1 < iy1) {
            result.add(new BlockPos[]{new BlockPos(mx1, ay1, az1), new BlockPos(mx2, iy1 - 1, az2)});
        }
        if (ay2 > iy2) {
            result.add(new BlockPos[]{new BlockPos(mx1, iy2 + 1, az1), new BlockPos(mx2, ay2, az2)});
        }

        int my1 = Math.max(ay1, iy1);
        int my2 = Math.min(ay2, iy2);

        // Z-axis: south and north of intersection (within middle XY band)
        if (az1 < iz1) {
            result.add(new BlockPos[]{new BlockPos(mx1, my1, az1), new BlockPos(mx2, my2, iz1 - 1)});
        }
        if (az2 > iz2) {
            result.add(new BlockPos[]{new BlockPos(mx1, my1, iz2 + 1), new BlockPos(mx2, my2, az2)});
        }

        return result;
    }

    // --- Internal data class ---

    private record RangeEntry(BlockPos[] range, int group) {}
}