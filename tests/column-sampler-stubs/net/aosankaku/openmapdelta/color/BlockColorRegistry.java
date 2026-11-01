package net.aosankaku.openmapdelta.color;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class BlockColorRegistry {
    private BlockColorRegistry() {}
    public static int resolveRgb(Level level, BlockPos pos, BlockState state, MapColor map, MapColor.Brightness brightness) {
        if (map == null || map == MapColor.NONE) return 0;
        return ColorPalette.mapColorToRgb(map, brightness);
    }
}
