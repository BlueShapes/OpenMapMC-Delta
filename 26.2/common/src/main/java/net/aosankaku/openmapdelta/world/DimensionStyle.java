// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import net.minecraft.world.level.Level;

public final class DimensionStyle {
    private DimensionStyle() {}

    public static boolean hasCeiling(Level level) {
        return level.dimensionType().hasCeiling();
    }

    public static int voidBackgroundArgb() { return 0xFF0A0A12; }
}
