package com.wxwr.kaleidoscopeagricultureevolution.region;

import com.wxwr.kaleidoscopeagricultureevolution.work.WorkAction;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkFieldType;
import com.wxwr.kaleidoscopeagricultureevolution.work.WorkZoneSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * 将 {@link Region} 对象序列化到 NBT / 从 NBT 反序列化。
 *
 * <p>存储格式（向后兼容）：
 * <pre>{@code
 *   // Cuboid —— 旧存档中 type 字段可能不存在（默认为 "cuboid"）
 *   { type:"cuboid", c1:long, c2:long, group:int }
 *
 *   // Circle
 *   { type:"circle", center:long, radiusX:double, radiusZ:double,
 *     minY:int, maxY:int, group:int }
 * }</pre>
 */
public final class RegionSerializer {

    // NBT 键
    public static final String KEY_TYPE     = "type";
    public static final String KEY_C1       = "c1";
    public static final String KEY_C2       = "c2";
    public static final String KEY_GROUP    = "group";
    public static final String KEY_CENTER   = "center";
    public static final String KEY_RADIUS_X = "radiusX";
    public static final String KEY_RADIUS_Z = "radiusZ";
    public static final String KEY_MIN_Y    = "minY";
    public static final String KEY_MAX_Y    = "maxY";
    public static final String KEY_FIELD_TYPE = WorkZoneSerializer.KEY_FIELD_TYPE;
    public static final String KEY_WORK_ACTION = WorkZoneSerializer.KEY_WORK_ACTION;

    static final String TYPE_CUBOID  = "cuboid";
    static final String TYPE_CIRCLE  = "circle";

    private RegionSerializer() {}

    // ---- 序列化 ----------------------------------------------------------

    /**
     * 将一个 Region 及其组 ID 写入 NBT。
     */
    public static CompoundTag serialize(Region region, int groupId) {
        return serialize(region, groupId, WorkZoneSerializer.DEFAULT_FIELD_TYPE, WorkZoneSerializer.DEFAULT_ACTION);
    }

    public static CompoundTag serialize(Region region, int groupId, WorkFieldType fieldType, WorkAction action) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_GROUP, groupId);
        WorkZoneSerializer.write(tag, fieldType, action);

        if (region instanceof CuboidRegion cr) {
            tag.putString(KEY_TYPE, TYPE_CUBOID);
            tag.putLong(KEY_C1, cr.getPos1().asLong());
            tag.putLong(KEY_C2, cr.getPos2().asLong());
        } else if (region instanceof CircleRegion cir) {
            tag.putString(KEY_TYPE, TYPE_CIRCLE);
            tag.putLong(KEY_CENTER, cir.getCenterPos().asLong());
            tag.putDouble(KEY_RADIUS_X, cir.getRadiusX());
            tag.putDouble(KEY_RADIUS_Z, cir.getRadiusZ());
            tag.putInt(KEY_MIN_Y, cir.getMinY());
            tag.putInt(KEY_MAX_Y, cir.getMaxY());
        } else {
            // 回退方案：通过边界框按 cuboid 处理
            tag.putString(KEY_TYPE, TYPE_CUBOID);
            tag.putLong(KEY_C1, region.getMinimumPoint().asLong());
            tag.putLong(KEY_C2, region.getMaximumPoint().asLong());
        }

        return tag;
    }

    public static void writeWorkZone(CompoundTag tag, WorkFieldType fieldType, WorkAction action) {
        WorkZoneSerializer.write(tag, fieldType, action);
    }

    // ---- 反序列化 --------------------------------------------------------

    /**
     * 从 NBT 读取 Region。
     *
     * <p>如果 {@code type} 字段不存在，该条目被视为
     * {@link CuboidRegion}（与 1.3 之前的存档向后兼容）。
     */
    @Nullable
    public static Region deserialize(CompoundTag tag) {
        String type = tag.getString(KEY_TYPE);
        if (type.isEmpty()) {
            type = TYPE_CUBOID; // 旧版存档
        }

        return switch (type) {
            case TYPE_CUBOID -> {
                if (!tag.contains(KEY_C1) || !tag.contains(KEY_C2)) yield null;
                yield new CuboidRegion(
                        BlockPos.of(tag.getLong(KEY_C1)),
                        BlockPos.of(tag.getLong(KEY_C2))
                );
            }
            case TYPE_CIRCLE -> {
                if (!tag.contains(KEY_CENTER)) yield null;
                double rx = tag.contains(KEY_RADIUS_X) ? tag.getDouble(KEY_RADIUS_X) : 1;
                double rz = tag.contains(KEY_RADIUS_Z) ? tag.getDouble(KEY_RADIUS_Z) : rx;
                int minY = tag.contains(KEY_MIN_Y) ? tag.getInt(KEY_MIN_Y) : 0;
                int maxY = tag.contains(KEY_MAX_Y) ? tag.getInt(KEY_MAX_Y) : 255;
                yield new CircleRegion(
                        BlockPos.of(tag.getLong(KEY_CENTER)),
                        rx, rz, minY, maxY
                );
            }
            default -> null;
        };
    }

    /**
     * 从已保存的范围标签中读取组 ID。
     * 如果不存在则返回 0（向后兼容）。
     */
    public static int getGroupId(CompoundTag tag) {
        return tag.contains(KEY_GROUP) ? tag.getInt(KEY_GROUP) : 0;
    }

    public static WorkFieldType getFieldType(CompoundTag tag) {
        return WorkZoneSerializer.readFieldType(tag);
    }

    public static WorkAction getWorkAction(CompoundTag tag) {
        return WorkZoneSerializer.readAction(tag);
    }
}
