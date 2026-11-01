package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class Block {
    private final BlockState state;
    public Block(String name, MapColor color) { this(name, color, false); }
    public Block(String name, MapColor color, boolean air) { state = new BlockState(name, color, air, false, false); }
    public BlockState defaultBlockState() { return state; }
}
