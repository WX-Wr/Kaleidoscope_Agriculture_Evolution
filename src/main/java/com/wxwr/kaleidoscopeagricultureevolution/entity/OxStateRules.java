package com.wxwr.kaleidoscopeagricultureevolution.entity;

final class OxStateRules {

    private OxStateRules() {}

    static boolean keepsBoundEntityConverted(PlowOxEntity.OxState state) {
        return state != PlowOxEntity.OxState.WAITING
                && state != PlowOxEntity.OxState.SELECT_CORNER
                && state != PlowOxEntity.OxState.IDLE
                && state != PlowOxEntity.OxState.ERROR;
    }

    static boolean farmerShouldIdle(PlowOxEntity.OxState state) {
        return state == PlowOxEntity.OxState.IDLE
                || state == PlowOxEntity.OxState.WAITING
                || state == PlowOxEntity.OxState.FINISHING
                || state == PlowOxEntity.OxState.ERROR;
    }
}
