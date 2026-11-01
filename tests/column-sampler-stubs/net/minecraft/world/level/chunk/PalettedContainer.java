package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PalettedContainer<T> {
    private final BlockState[] states;
    public PalettedContainer() {
        states = new BlockState[4096];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());
    }
    private PalettedContainer(BlockState[] states) { this.states = states; }
    public PalettedContainer<T> copy() { return new PalettedContainer<>(states.clone()); }
    @SuppressWarnings("unchecked")
    public T get(int x, int y, int z) { return (T) states[(y & 15) << 8 | (z & 15) << 4 | (x & 15)]; }
    public void set(int x, int y, int z, BlockState state) { states[(y & 15) << 8 | (z & 15) << 4 | (x & 15)] = state; }
    public boolean hasOnlyAir() {
        for (BlockState state : states) if (!state.isAir()) return false;
        return true;
    }
}
