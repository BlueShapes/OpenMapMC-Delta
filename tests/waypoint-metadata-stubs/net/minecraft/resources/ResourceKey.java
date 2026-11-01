package net.minecraft.resources;

public final class ResourceKey<T> {
    private final String value;
    private ResourceKey(String value) { this.value = value; }
    public static <T> ResourceKey<T> create(Object registry, ResourceLocation location) { return new ResourceKey<>(location.toString()); }
    public static <T> ResourceKey<T> create(Object registry, Identifier identifier) { return new ResourceKey<>(identifier.toString()); }
    public ResourceLocation location() { return ResourceLocation.tryParse(value); }
    public Identifier identifier() { return Identifier.tryParse(value); }
    @Override public boolean equals(Object other) { return other instanceof ResourceKey<?> key && value.equals(key.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
