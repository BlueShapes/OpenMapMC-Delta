// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DimensionUtil {
    private DimensionUtil() {}

    public static String dimensionId(ResourceKey<Level> dimension) {
        ResourceLocation loc = dimension.location();
        return loc.getNamespace() + ":" + loc.getPath();
    }
}
