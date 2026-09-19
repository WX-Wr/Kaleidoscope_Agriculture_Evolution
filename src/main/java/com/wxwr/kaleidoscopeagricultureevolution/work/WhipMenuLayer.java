package com.wxwr.kaleidoscopeagricultureevolution.work;

public enum WhipMenuLayer {
    REGION_MODE(0, "region_mode"),
    FIELD_TYPE(1, "field_type"),
    WORK_ACTION(2, "work_action");

    private final int id;
    private final String key;

    WhipMenuLayer(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public WhipMenuLayer next() {
        WhipMenuLayer[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static WhipMenuLayer fromId(int id) {
        for (WhipMenuLayer layer : values()) {
            if (layer.id == id) {
                return layer;
            }
        }
        return REGION_MODE;
    }
}
