package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.Identifier;

/** A ready/failed capture service at the real map renderer boundary. */
public final class EntityIconSprites {
    public static MarkerIconCatalog.Texture resolve(Marker marker) {
        if ("mob:example:generated".equals(marker.getIconId()) || "mob:minecraft:cod".equals(marker.getIconId())) {
            return new MarkerIconCatalog.Texture(Identifier.parse("openmapdelta:generated/test"), 0, 0, 12, 6, 12, 6);
        }
        return null;
    }
}
