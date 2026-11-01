// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

import java.util.EnumMap;
import java.util.Map;

public final class OpenMapDeltaConfig {
    private static final Map<MinimapInfoSlot, Value<MinimapInfoType>> SLOTS = new EnumMap<>(MinimapInfoSlot.class);
    public static final Value<Boolean> SHOW_COORDINATES = new Value<>(true);
    public static final Value<InfoCornerAlignment> INFO_CORNER_ALIGNMENT = new Value<>(InfoCornerAlignment.MAP_EDGE);

    static {
        for (MinimapInfoSlot slot : MinimapInfoSlot.values()) SLOTS.put(slot, new Value<>(MinimapInfoType.NONE));
    }

    private OpenMapDeltaConfig() {}
    public static Value<MinimapInfoType> infoSlot(MinimapInfoSlot slot) { return SLOTS.get(slot); }

    public enum InfoCornerAlignment { MAP_EDGE, CENTER }

    public static final class Value<T> {
        private T value;
        private Value(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
}
