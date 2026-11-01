package net.minecraft.client.renderer.texture;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextureManager {
    private final Map<ResourceLocation, DynamicTexture> registered = new LinkedHashMap<>();
    private int registerCount;

    public void register(ResourceLocation location, DynamicTexture texture) {
        registerCount++;
        registered.put(location, texture);
    }

    public void release(ResourceLocation location) { registered.remove(location); }
    public int registerCount() { return registerCount; }
    public Map<ResourceLocation, DynamicTexture> registeredTextures() { return registered; }
}
