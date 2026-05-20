package com.Wx_Wr.Kaleidoscope_Agriculture_Evolution.rope.math;

import net.minecraft.world.phys.Vec3;

public class CatenaryMath {

    public static Vec3[] computeCurvePoints(Vec3 start, Vec3 end, double sagFactor, int segments) {
        Vec3[] points = new Vec3[segments + 1];
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double totalLength = Math.sqrt(horizontalDist * horizontalDist + dy * dy);

        if (totalLength < 0.001) {
            points[0] = start;
            for (int i = 1; i <= segments; i++) {
                points[i] = end;
            }
            return points;
        }

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double x = start.x + dx * t;
            double z = start.z + dz * t;
            double baseY = start.y + dy * t;
            double sag = sagFactor * horizontalDist * 4.0 * t * (1.0 - t);
            double y = baseY - sag;
            points[i] = new Vec3(x, y, z);
        }

        return points;
    }
}
