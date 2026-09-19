package com.wxwr.kaleidoscopeagricultureevolution.work;

import net.minecraft.nbt.CompoundTag;

public final class WorkZoneSerializer {
    public static final String KEY_FIELD_TYPE = "fieldType";
    public static final String KEY_WORK_ACTION = "workAction";

    public static final WorkFieldType DEFAULT_FIELD_TYPE = WorkFieldType.DRY_FIELD;
    public static final WorkAction DEFAULT_ACTION = WorkAction.TILL;

    private WorkZoneSerializer() {
    }

    public static void write(CompoundTag tag, WorkFieldType fieldType, WorkAction action) {
        tag.putString(KEY_FIELD_TYPE, fieldType.key());
        tag.putString(KEY_WORK_ACTION, action.key());
    }

    public static WorkFieldType readFieldType(CompoundTag tag) {
        String key = tag.getString(KEY_FIELD_TYPE);
        if (key.isEmpty()) {
            return DEFAULT_FIELD_TYPE;
        }
        return WorkFieldType.fromKey(key);
    }

    public static WorkAction readAction(CompoundTag tag) {
        String key = tag.getString(KEY_WORK_ACTION);
        if (key.isEmpty()) {
            return DEFAULT_ACTION;
        }
        return WorkAction.fromKey(key);
    }
}
