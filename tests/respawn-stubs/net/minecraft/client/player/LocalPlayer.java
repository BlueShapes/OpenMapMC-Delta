package net.minecraft.client.player;

public final class LocalPlayer {
    private final boolean dead;
    private final String dimension;
    private final double x;
    private final double z;

    public LocalPlayer(boolean dead, String dimension, double x, double z) {
        this.dead = dead;
        this.dimension = dimension;
        this.x = x;
        this.z = z;
    }

    public boolean isDeadOrDying() { return dead; }
    public String dimension() { return dimension; }
    public double x() { return x; }
    public double z() { return z; }
}
