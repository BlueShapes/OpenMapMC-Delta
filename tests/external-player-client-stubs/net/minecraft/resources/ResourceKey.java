package net.minecraft.resources;

public final class ResourceKey<T> {
    private final ResourceLocation location;
    private ResourceKey(ResourceLocation location) { this.location = location; }
    public static <T> ResourceKey<T> create(String value) { return new ResourceKey<>(ResourceLocation.parse(value)); }
    public ResourceLocation location() { return location; }
}
