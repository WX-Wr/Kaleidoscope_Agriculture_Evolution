package com.wxwr.kaleidoscopeagricultureevolution.genetics.client;

import com.wxwr.kaleidoscopeagricultureevolution.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientGenomeData {

    private static final Map<Long, Character> posYieldGrade = new ConcurrentHashMap<>();
    private static final Set<Long> highYieldPositions = ConcurrentHashMap.newKeySet();

    public static void remove(BlockPos pos) {
        long key = pos.asLong();
        posYieldGrade.remove(key);
        highYieldPositions.remove(key);
    }

    /**
     * <b>状态：待定。</b>产量等级已随 {@code CropVisualSyncPacket} 同步到客户端并缓存，
     * 但<b>暂无消费者</b>（等待后续 UI 使用）。
     */
    public static char getYieldGrade(BlockPos pos) {
        if (!Config.isGeneticsEnabled()) return 'D';
        return posYieldGrade.getOrDefault(pos.asLong(), 'D');
    }

    public static boolean isHighYield(BlockPos pos) {
        if (!Config.isGeneticsEnabled()) return false;
        return highYieldPositions.contains(pos.asLong());
    }

    public static void setVisual(BlockPos pos, boolean present, boolean highYield, char yieldGrade) {
        if (!Config.isGeneticsEnabled()) {
            present = false;
            highYield = false;
        }

        long key = pos.asLong();
        if (!present) {
            remove(pos);
            markDirty(pos);
            return;
        }

        posYieldGrade.put(key, yieldGrade);
        if (highYield) {
            highYieldPositions.add(key);
        } else {
            highYieldPositions.remove(key);
        }

        markDirty(pos);
    }

    private static void markDirty(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static void clear() {
        posYieldGrade.clear();
        highYieldPositions.clear();
    }
}
