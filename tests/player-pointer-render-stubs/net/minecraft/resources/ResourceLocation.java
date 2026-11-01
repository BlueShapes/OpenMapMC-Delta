package net.minecraft.resources;

public final class ResourceLocation {
    private final String value;
    public ResourceLocation(String namespace, String path) { value = namespace + ":" + path; }
    public static ResourceLocation withDefaultNamespace(String path) { return new ResourceLocation("minecraft", path); }
    @Override public String toString() { return value; }
}
