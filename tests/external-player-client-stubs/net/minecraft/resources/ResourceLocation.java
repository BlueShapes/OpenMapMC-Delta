package net.minecraft.resources;

public record ResourceLocation(String namespace, String path) {
    public static ResourceLocation parse(String value) {
        int separator = value.indexOf(':');
        return new ResourceLocation(value.substring(0, separator), value.substring(separator + 1));
    }
    public String getNamespace() { return namespace; }
    public String getPath() { return path; }
    @Override public String toString() { return namespace + ":" + path; }
}
