package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class MarkerIconCatalog {
    public record Texture(ResourceLocation location, int u, int v, int width, int height,
                          int textureWidth, int textureHeight) {}
    public record Entry(String id, ItemStack item, Texture texture) {}
    private static final Texture FACE = new Texture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png"),
            8, 8, 8, 8, 64, 64);
    private static final Texture SYMBOL = new Texture(ResourceLocation.fromNamespaceAndPath("test", "textures/opaque.png"),
            0, 0, 16, 16, 16, 16);
    private MarkerIconCatalog() {}
    public static Entry find(String id) {
        if ("mob:minecraft:zombie".equals(id)) return new Entry(id, new ItemStack(true), FACE);
        if ("symbol:test:opaque".equals(id)) return new Entry(id, new ItemStack(true), SYMBOL);
        if ("mob:minecraft:missing-texture".equals(id)) return new Entry(id, new ItemStack(true), null);
        return null;
    }
    public static Texture resolveTexture(Entry entry) { return entry.texture(); }
}
