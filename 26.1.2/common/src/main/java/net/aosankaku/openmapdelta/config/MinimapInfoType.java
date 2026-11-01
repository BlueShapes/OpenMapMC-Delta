// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

import java.util.Locale;

/** Persisted information choices; wide entries are restricted to the top/bottom slots. */
public enum MinimapInfoType {
    NONE, WEATHER, AVERAGE_FPS, DAYTIME_12, DAYTIME_24, DAYTIME_CLOCK,
    DAYTIME_TICKS, DAYTIME_SECONDS, SLEEP, PLAYER_COUNT, X, Y, Z, WORLD_DAYS,
    TARGET_LIGHT, FOOT_LIGHT, HORIZONTAL_SPEED, SESSION_TIME, REAL_TIME,
    CHUNK_COORDINATES, CHUNK_LOCAL, TPS, LATENCY_TICKS, RAM, FREE_SLOTS,
    COORDINATES(true), DIMENSION_ID(true), DIMENSION_NAME(true), BIOME_ID(true),
    BIOME_NAME(true), TARGET_NAME(true), TARGET_ID(true), NEAREST_PLAYER(true),
    NEAREST_PLAYER_COORDINATES(true), NEAREST_WAYPOINT(true);

    private final boolean wide;
    MinimapInfoType() { this(false); }
    MinimapInfoType(boolean wide) { this.wide = wide; }
    public boolean isWide() { return wide; }
    public boolean isCoordinates() { return this == COORDINATES || this == X || this == Y || this == Z; }
    public String translationKey() { return "gui.odysseymap.info." + name().toLowerCase(Locale.ROOT); }
}
