package com.wxwr.kaleidoscopeagricultureevolution.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class HoneyExtractorClientInteraction {
    private HoneyExtractorClientInteraction() {
    }

    /**
     * 用透明 Screen 接管摇蜜期间的输入。Screen 打开后，Minecraft 不再把鼠标移动当作转视角。
     */
    public static void start(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.options.keyUse.setDown(false);
            minecraft.setScreen(new HoneyExtractorScreen(pos));
        }
    }

    public static boolean isActive() {
        return Minecraft.getInstance().screen instanceof HoneyExtractorScreen;
    }

    public static boolean isActiveAt(BlockPos pos) {
        return Minecraft.getInstance().screen instanceof HoneyExtractorScreen screen
                && screen.getExtractorPos().equals(pos);
    }

    public static void stop(boolean notifyServer) {
        if (Minecraft.getInstance().screen instanceof HoneyExtractorScreen screen) {
            screen.closeFromInteraction(notifyServer);
        }
    }
}
