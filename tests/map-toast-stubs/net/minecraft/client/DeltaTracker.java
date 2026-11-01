package net.minecraft.client;
public class DeltaTracker {
    public int calls;
    public float getGameTimeDeltaPartialTick(boolean paused) {
        if (paused) throw new AssertionError("Unexpected paused interpolation");
        calls++;
        return 0.25f;
    }
}
