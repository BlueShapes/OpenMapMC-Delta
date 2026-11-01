package net.aosankaku.openmapdelta.config;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class BlockOverrideConfig {
    private static final Map<BlockState, MapColor> OVERRIDES = new HashMap<>();
    private BlockOverrideConfig() {}
    public static Map<BlockState, MapColor> getResolvedOverrides() { return OVERRIDES; }
}
