// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Portable persisted icon identity; missing or malformed icons fall back to the colored marker. */
public final class MarkerIconId {
    private MarkerIconId() {}
    public static String normalize(String id) {
        if (id == null || id.length() > 160) return "";
        if (id.equals("symbol:odysseymap:death")) return "symbol:openmapdelta:death";
        if (id.equals("symbol:odysseymap:death_black")) return "symbol:openmapdelta:death_black";
        if (id.startsWith("emoji:")) {
            return id.matches("emoji:emojiful:[0-9a-f]{64}") ? id : "";
        }
        if (!id.startsWith("item:") && !id.startsWith("mob:") && !id.startsWith("symbol:")) return "";
        String resource = id.substring(id.indexOf(':') + 1);
        return resource.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") ? id : "";
    }
}
