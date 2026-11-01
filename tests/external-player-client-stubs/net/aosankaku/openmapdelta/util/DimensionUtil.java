package net.aosankaku.openmapdelta.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DimensionUtil {
    private DimensionUtil() { }
    public static String dimensionId(ResourceKey<Level> key) { return key.location().toString(); }
}
