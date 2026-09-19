package com.wxwr.kaleidoscopeagricultureevolution.entity.ai;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Serialization helpers for the synchronized remaining route preview. */
public final class PlowPathData {
    private PlowPathData() {
    }

    public static List<BlockPos> parse(String data) {
        List<BlockPos> points = new ArrayList<>();
        if (data == null || data.isEmpty()) {
            return points;
        }

        for (String entry : data.split(";")) {
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                points.add(new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        return points;
    }
}
