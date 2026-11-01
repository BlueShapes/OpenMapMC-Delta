package net.minecraft.client.input;

public final class MouseButtonEvent {
    private final double x, y;
    private final ButtonInfo buttonInfo;
    public MouseButtonEvent(double x, double y, int button) { this.x=x; this.y=y; this.buttonInfo=new ButtonInfo(button); }
    public double x() { return x; }
    public double y() { return y; }
    public ButtonInfo buttonInfo() { return buttonInfo; }
    public record ButtonInfo(int button) {}
}
