package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class MarkerIconCatalog {
    public record Texture(Identifier location, int u, int v, int width, int height,
                          int textureWidth, int textureHeight) {}
    public record Entry(String id, ItemStack item, Texture texture) {}
    private static final Texture FACE = new Texture(Identifier.parse("minecraft:textures/entity/zombie/zombie.png"),
            8, 8, 8, 8, 64, 64);
    private static final Texture SYMBOL = new Texture(Identifier.parse("test:textures/opaque.png"),
            0, 0, 16, 16, 16, 16);
    private MarkerIconCatalog() {}
    public static Entry find(String id) {
        if ("mob:minecraft:cod".equals(id)) return new Entry(id, new ItemStack(true), SYMBOL);
        if ("mob:minecraft:cat".equals(id)) return new Entry(id, new ItemStack(true),
                new Texture(Identifier.parse("minecraft:textures/entity/cat/cat_tabby.png"), 5, 5, 6, 4, 64, 32));
        if ("mob:minecraft:villager".equals(id)) return new Entry(id, new ItemStack(true),
                new Texture(Identifier.parse("minecraft:textures/entity/villager/villager.png"), 8, 8, 8, 10, 64, 64));
        if ("mob:minecraft:zombie".equals(id)) return new Entry(id, new ItemStack(true), FACE);
        if ("symbol:test:opaque".equals(id)) return new Entry(id, new ItemStack(true), SYMBOL);
        if ("mob:minecraft:missing-texture".equals(id)) return new Entry(id, new ItemStack(true), null);
        return null;
    }
    public static Texture resolveTexture(Entry entry) { return entry.texture(); }
}
