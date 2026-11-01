package net.minecraft.client;

import net.minecraft.client.player.LocalPlayer;

public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public LocalPlayer player = new LocalPlayer();
    private Minecraft() {}
    public static Minecraft getInstance() { return INSTANCE; }
}
