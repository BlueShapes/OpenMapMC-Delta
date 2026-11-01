package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;

public class Minecraft {
    private static Minecraft instance = new Minecraft();
    public ClientLevel level;
    public LocalPlayer player;
    private final TextureManager textureManager = new TextureManager();

    public static Minecraft getInstance() { return instance; }
    public static void setInstance(Minecraft minecraft) { instance = minecraft; }
    public TextureManager getTextureManager() { return textureManager; }
}
