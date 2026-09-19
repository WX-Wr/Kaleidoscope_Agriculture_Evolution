package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.google.gson.JsonParseException;

public enum WorkFieldType {
    DRY_FIELD("dry_field"),
    PADDY_FIELD("paddy_field");

    private final String key;

    WorkFieldType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WorkFieldType fromKey(String key) {
        for (WorkFieldType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        throw new JsonParseException("Unknown work field type: " + key);
    }
}
