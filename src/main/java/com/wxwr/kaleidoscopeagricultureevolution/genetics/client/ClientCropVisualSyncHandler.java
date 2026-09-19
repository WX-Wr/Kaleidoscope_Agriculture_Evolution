package com.wxwr.kaleidoscopeagricultureevolution.genetics.client;

import com.wxwr.kaleidoscopeagricultureevolution.network.CropVisualSyncPacket;

public final class ClientCropVisualSyncHandler {
    private ClientCropVisualSyncHandler() {
    }

    public static void handle(CropVisualSyncPacket msg) {
        ClientGenomeData.setVisual(msg.pos(), msg.present(), msg.highYieldCrop(), msg.yieldGrade());
    }
}
