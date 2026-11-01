package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.state.BlockState;

public final class LevelChunkSection {
    private final PalettedContainer<BlockState> states = new PalettedContainer<>();
    public boolean hasOnlyAir() { return states.hasOnlyAir(); }
    public PalettedContainer<BlockState> getStates() { return states; }
}
