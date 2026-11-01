package net.minecraft.client.input;

public final class KeyEvent {
    private final int key;

    public KeyEvent(int key) { this.key = key; }
    public int key() { return key; }
    public boolean isSelection() { return key == 32 || key == 257; }
    public boolean isLeft() { return key == 263; }
    public boolean isRight() { return key == 262; }
}
