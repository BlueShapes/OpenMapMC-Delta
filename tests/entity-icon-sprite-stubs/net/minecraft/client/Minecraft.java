package net.minecraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureManager;
import java.util.ArrayDeque;
import java.util.Queue;

public final class Minecraft {
    public ClientLevel level;
    public LocalPlayer player;
    private final TextureManager textureManager;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private static Minecraft instance;
    public Minecraft(TextureManager textureManager) { this.textureManager = textureManager; instance = this; }
    public TextureManager getTextureManager() { return textureManager; }
    public void execute(Runnable runnable) { pending.add(runnable); }
    public void flushTasks() { while (!pending.isEmpty()) pending.remove().run(); }
    public static Minecraft getInstance() { return instance; }
}
