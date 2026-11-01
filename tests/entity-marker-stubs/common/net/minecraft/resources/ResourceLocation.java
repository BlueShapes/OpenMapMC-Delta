package net.minecraft.resources;

public final class ResourceLocation {
    private final String value;
    private ResourceLocation(String value) { this.value = value; }
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace + ":" + path);
    }
    @Override public String toString() { return value; }
}
