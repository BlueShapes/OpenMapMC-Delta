package net.minecraft.world.level.block.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

public final class BlockState {
    private final String name;
    private final MapColor mapColor;
    private final boolean air, leaves, sturdy;
    private FluidState fluid = FluidState.EMPTY;
    public BlockState(String name, MapColor mapColor, boolean air, boolean leaves, boolean sturdy) {
        this.name = name; this.mapColor = mapColor; this.air = air; this.leaves = leaves; this.sturdy = sturdy;
    }
    public MapColor mapColor() { return mapColor; }
    public boolean isAir() { return air; }
    public boolean is(Object tag) { return tag == BlockTags.LEAVES && leaves; }
    public FluidState getFluidState() { return fluid; }
    public boolean isFaceSturdy(Level level, BlockPos pos, Direction direction) { return sturdy; }
    public MapColor getMapColor(Level level, BlockPos pos) { return mapColor; }
    public void setFluid(FluidState fluid) { this.fluid = fluid; }
    @Override public String toString() { return name; }
}
