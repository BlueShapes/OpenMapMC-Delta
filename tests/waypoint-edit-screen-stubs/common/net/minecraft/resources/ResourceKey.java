package net.minecraft.resources;

public final class ResourceKey<T> {
    private final String id;
    public ResourceKey(String id) { this.id = id; }
    public static <T> ResourceKey<T> create(String id) { return new ResourceKey<>(id); }
    public String location() { return id; }
    public String identifier() { return id; }
    @Override public String toString() { return id; }
}
