package net.minecraft.world.level.material;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class FluidState {
    public static final FluidState EMPTY = new FluidState(null);
    private final BlockState legacy;
    public FluidState(BlockState legacy) { this.legacy = legacy; }
    public boolean isEmpty() { return legacy == null; }
    public BlockState createLegacyBlock() { return legacy == null ? Blocks.AIR.defaultBlockState() : legacy; }
}
