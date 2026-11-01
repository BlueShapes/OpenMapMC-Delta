package net.minecraft.world.level;

import net.minecraft.resources.ResourceKey;

public final class Level {
    private final ResourceKey<Level> dimension;
    public Level(ResourceKey<Level> dimension) { this.dimension = dimension; }
    public ResourceKey<Level> dimension() { return dimension; }
}
