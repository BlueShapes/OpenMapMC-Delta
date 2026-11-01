// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

public enum MinimapInfoType {
    NONE(false, false), LEFT_SHORT(false, false), LEFT_LONG(false, false), RIGHT_SHORT(false, false),
    RIGHT_LONG(false, false), TOP_LINE(true, false), BOTTOM_LINE(true, false);

    private final boolean wide;
    private final boolean coordinates;
    MinimapInfoType(boolean wide, boolean coordinates) { this.wide = wide; this.coordinates = coordinates; }
    public boolean isWide() { return wide; }
    public boolean isCoordinates() { return coordinates; }
}
