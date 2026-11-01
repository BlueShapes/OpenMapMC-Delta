// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

public enum MinimapInfoSlot {
    TOP, TOP_LEFT, TOP_RIGHT, BOTTOM, BOTTOM_RIGHT, BOTTOM_LEFT;

    public boolean accepts(MinimapInfoType type) {
        return !type.isWide() || this == TOP || this == BOTTOM;
    }
}
