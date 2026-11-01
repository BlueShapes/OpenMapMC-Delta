// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.util;

import net.minecraft.util.Mth;

public final class MathUtil {
    private MathUtil() {}

    public static double distanceXZ(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Mth.sqrt((float) (dx * dx + dz * dz));
    }
}
