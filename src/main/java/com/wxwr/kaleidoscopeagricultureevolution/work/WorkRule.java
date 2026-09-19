package com.wxwr.kaleidoscopeagricultureevolution.work;

import java.util.List;

public record WorkRule(
        WorkFieldType fieldType,
        WorkAction action,
        WorkToolRequirement tool,
        List<WorkItemRequirement> required,
        List<WorkItemRequirement> optional,
        String pathRule) {
}
