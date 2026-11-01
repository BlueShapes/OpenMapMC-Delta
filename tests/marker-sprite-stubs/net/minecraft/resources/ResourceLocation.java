package net.minecraft.resources;

import java.util.Objects;

public final class ResourceLocation {
    private final String namespace;
    private final String path;

    public ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceLocation location
                && namespace.equals(location.namespace) && path.equals(location.path);
    }

    @Override public int hashCode() { return Objects.hash(namespace, path); }
    @Override public String toString() { return namespace + ":" + path; }
}
