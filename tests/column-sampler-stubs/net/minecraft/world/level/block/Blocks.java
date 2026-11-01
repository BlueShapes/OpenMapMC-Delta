package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

public final class Blocks {
    public static final Block AIR = new Block("air", MapColor.NONE, true);
    public static final Block BEDROCK = new Block("bedrock", MapColor.BEDROCK);
    public static final Block DIRT = new Block("dirt", MapColor.DIRT);
    public static final Block GRASS_BLOCK = new Block("grass block", MapColor.GRASS);
    public static final Block STONE = new Block("stone", MapColor.STONE);
    public static final Block NETHERRACK = new Block("netherrack", MapColor.NETHERRACK);
    public static final Block OBSIDIAN = new Block("obsidian", MapColor.OBSIDIAN);
    public static final Block SOUL_SOIL = new Block("soul_soil", MapColor.SOUL_SOIL);
    public static final Block WATER = new Block("water", MapColor.WATER);
    public static final Block LAVA = new Block("lava", MapColor.LAVA);
    private Blocks() {}

    static {
        fluids(WATER.defaultBlockState(), LAVA.defaultBlockState());
    }

    private static void fluids(BlockState water, BlockState lava) {
        water.setFluid(new FluidState(water));
        lava.setFluid(new FluidState(lava));
    }
}
