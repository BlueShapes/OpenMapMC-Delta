package net.minecraft.client.renderer.texture;

import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TextureManager {
    public final Map<Identifier, DynamicTexture> textures = new LinkedHashMap<>();
    public int releaseCount;
    public boolean failNextUploadAndClose;
    public void register(Identifier id, DynamicTexture texture) {
        if (failNextUploadAndClose) {
            texture.throwOnUpload = true;
            texture.throwOnClose = true;
            failNextUploadAndClose = false;
        }
        textures.put(id, texture);
    }
    public void release(Identifier id) {
        DynamicTexture texture = textures.remove(id);
        if (texture != null) { releaseCount++; texture.close(); }
    }
    public int registeredCount() { return textures.size(); }
}
