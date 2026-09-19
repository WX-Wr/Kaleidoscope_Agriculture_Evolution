package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.google.gson.JsonParseException;
import com.wxwr.kaleidoscopeagricultureevolution.entity.PlowOxEntity;
import org.jetbrains.annotations.Nullable;

public enum WorkToolRequirement {
    NONE("none", null),
    PLOW("plow", PlowOxEntity.ToolType.PLOW),
    LOUCHE("louche", PlowOxEntity.ToolType.LOUCHE);

    private final String key;
    @Nullable
    private final PlowOxEntity.ToolType toolType;

    WorkToolRequirement(String key, @Nullable PlowOxEntity.ToolType toolType) {
        this.key = key;
        this.toolType = toolType;
    }

    public String key() {
        return key;
    }

    public boolean matches(@Nullable PlowOxEntity.ToolType mountedTool) {
        return this == NONE || mountedTool == toolType;
    }

    @Nullable
    public PlowOxEntity.ToolType toolType() {
        return toolType;
    }

    public static WorkToolRequirement fromKey(String key) {
        for (WorkToolRequirement requirement : values()) {
            if (requirement.key.equals(key)) {
                return requirement;
            }
        }
        throw new JsonParseException("Unknown work tool requirement: " + key);
    }
}
