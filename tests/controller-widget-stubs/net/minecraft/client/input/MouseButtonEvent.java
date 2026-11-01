package net.minecraft.client.input;

public record MouseButtonEvent(double x, double y, ButtonInfo buttonInfo) {
    public record ButtonInfo(int button) { }
}
