package net.minecraft.client.player;

public class LocalPlayer {
    private final double x;
    private final double z;
    public LocalPlayer(double x, double z) { this.x = x; this.z = z; }
    public double getX() { return x; }
    public double getZ() { return z; }
}
