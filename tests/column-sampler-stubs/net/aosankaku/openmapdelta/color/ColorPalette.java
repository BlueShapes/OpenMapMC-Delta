package net.aosankaku.openmapdelta.color;

import net.minecraft.world.level.material.MapColor;

public final class ColorPalette {
    private ColorPalette() {}
    public static MapColor.Brightness terrainBrightness(int depth, int x, int z) { return MapColor.Brightness.NORMAL; }
    public static MapColor.Brightness waterBrightness(int depth, int x, int z) { return MapColor.Brightness.NORMAL; }
    public static int mapColorToRgb(MapColor color, MapColor.Brightness brightness) {
        return color == null ? 0 : 0xFF000000 | color.rgb;
    }
}
