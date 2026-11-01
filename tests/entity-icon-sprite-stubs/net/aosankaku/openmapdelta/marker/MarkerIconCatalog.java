package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.Identifier;

public final class MarkerIconCatalog {
    public record Texture(Identifier location, int u, int v, int width, int height,
                          int textureWidth, int textureHeight) {}
    public static long generation;
    public static long resourceGeneration() { return generation; }
}
