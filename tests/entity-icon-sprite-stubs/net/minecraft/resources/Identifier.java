package net.minecraft.resources;

public record Identifier(String value) {
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return new Identifier(namespace + ":" + path);
    }
    @Override public String toString() { return value; }
}
