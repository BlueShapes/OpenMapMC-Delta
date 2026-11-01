package net.minecraft.client;

import net.minecraft.client.renderer.texture.TextureManager;

public final class Minecraft {
    private final TextureManager textureManager = new TextureManager();
    public TextureManager getTextureManager() { return textureManager; }
}
