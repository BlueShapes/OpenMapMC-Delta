package net.minecraft.client;
public final class Minecraft {
    private static final Minecraft INSTANCE=new Minecraft();
    public Object screen;
    public final Gui gui=new Gui();
    public static Minecraft getInstance() { return INSTANCE; }
    public final class Gui { public Object screen() { return screen; } }
}
