package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private final Map<Object, DynamicTexture> textures = new HashMap<>();
    private int nextDynamicId;

    public ResourceLocation register(String name, DynamicTexture texture) {
        ResourceLocation id;
        do {
            id = new ResourceLocation("dynamic", name + "_" + nextDynamicId++);
        } while (textures.containsKey(id));
        textures.put(id, texture);
        return id;
    }

    public void register(ResourceLocation id, DynamicTexture texture) { textures.put(id, texture); }
    public void register(Identifier id, DynamicTexture texture) { textures.put(id, texture); }

    public void release(ResourceLocation id) { releaseKey(id); }
    public void release(Identifier id) { releaseKey(id); }

    private void releaseKey(Object id) {
        DynamicTexture texture = textures.remove(id);
        if (texture != null) texture.close();
    }

    public int activeTextureCount() { return textures.size(); }
    public Map<Object, DynamicTexture> registeredTextures() { return Map.copyOf(textures); }
}
