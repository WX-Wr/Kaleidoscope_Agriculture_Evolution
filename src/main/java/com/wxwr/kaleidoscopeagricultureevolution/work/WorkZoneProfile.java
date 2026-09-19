package com.wxwr.kaleidoscopeagricultureevolution.work;

import com.wxwr.kaleidoscopeagricultureevolution.region.Region;

public record WorkZoneProfile(
        Region region,
        int groupId,
        WorkFieldType fieldType,
        WorkAction action) {
}
