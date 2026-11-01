package net.minecraft.world.level.material;

public final class MapColor {
    public static final MapColor NONE = new MapColor("none", 0x000000);
    public static final MapColor WATER = new MapColor("water", 0x3040D0);
    public static final MapColor STONE = new MapColor("stone", 0x686868);
    public static final MapColor DIRT = new MapColor("dirt", 0x876044);
    public static final MapColor GRASS = new MapColor("grass", 0x59A332);
    public static final MapColor PLANT = new MapColor("plant", 0x55AA44);
    public static final MapColor BEDROCK = new MapColor("bedrock", 0x272727);
    public static final MapColor NETHERRACK = new MapColor("netherrack", 0x812929);
    public static final MapColor LAVA = new MapColor("lava", 0xF07010);
    public static final MapColor OBSIDIAN = new MapColor("obsidian", 0x20153B);
    public static final MapColor SOUL_SOIL = new MapColor("soul_soil", 0x463532);
    public static final MapColor TEST_OVERRIDE = new MapColor("test_override", 0x12ABEF);
    public final String name;
    public final int rgb;
    private MapColor(String name, int rgb) { this.name = name; this.rgb = rgb; }
    @Override public String toString() { return name; }

    public enum Brightness {
        LOWEST(135), LOW(180), NORMAL(220), HIGH(255);
        public final int modifier;
        Brightness(int modifier) { this.modifier = modifier; }
    }
}
