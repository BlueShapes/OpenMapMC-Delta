package net.minecraft.resources;

public final class ResourceLocation {
    private final String value;
    private ResourceLocation(String value) { this.value = value; }
    public static ResourceLocation tryParse(String value) {
        return value != null && value.contains(":") ? new ResourceLocation(value) : null;
    }
    @Override public String toString() { return value; }
    @Override public boolean equals(Object other) { return other instanceof ResourceLocation id && value.equals(id.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
