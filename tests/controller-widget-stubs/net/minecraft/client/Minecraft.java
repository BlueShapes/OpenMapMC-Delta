package net.minecraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.world.level.Level;

public final class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public final Font font = new Font();
    public Level level;
    private Minecraft() { }
    public static Minecraft getInstance() { return INSTANCE; }
}
