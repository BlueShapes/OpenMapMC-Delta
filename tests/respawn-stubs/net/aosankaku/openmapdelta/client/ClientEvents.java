package net.aosankaku.openmapdelta.client;

import net.minecraft.client.player.LocalPlayer;

public final class ClientEvents {
    private LocalPlayer receivedPlayer;
    private String receivedDimension;
    private double receivedX;
    private double receivedZ;
    private int deathCount;

    public void onPlayerDeath(LocalPlayer player) {
        deathCount++;
        receivedPlayer = player;
        if (player != null) {
            receivedDimension = player.dimension();
            receivedX = player.x();
            receivedZ = player.z();
        }
    }

    public LocalPlayer receivedPlayer() { return receivedPlayer; }
    public String receivedDimension() { return receivedDimension; }
    public double receivedX() { return receivedX; }
    public double receivedZ() { return receivedZ; }
    public int deathCount() { return deathCount; }
}
