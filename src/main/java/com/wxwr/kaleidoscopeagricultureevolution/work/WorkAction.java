package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.google.gson.JsonParseException;

public enum WorkAction {
    TILL("till"),
    SOW("sow"),
    FERTILIZE("fertilize");

    private final String key;

    WorkAction(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static WorkAction fromKey(String key) {
        for (WorkAction action : values()) {
            if (action.key.equals(key)) {
                return action;
            }
        }
        throw new JsonParseException("Unknown work action: " + key);
    }
}
