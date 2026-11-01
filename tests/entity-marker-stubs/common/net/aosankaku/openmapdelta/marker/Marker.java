package net.aosankaku.openmapdelta.marker;

public final class Marker {
    private final String iconId;
    private final int color;
    public Marker(String iconId, int color) { this.iconId = iconId; this.color = color; }
    public String getIconId() { return iconId; }
    public int getColor() { return color; }
}
