// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.UUID;

/** Vanilla locator-bar UUID fallback, independent of Minecraft and java.desktop APIs. */
public final class PlayerIconColor {
    private static final float BRIGHTNESS = 0.9F;

    private PlayerIconColor() {}

    /** Uses the UUID hash's low 24 bits, preserving hue/saturation at vanilla's fixed brightness. */
    public static int fromUuid(UUID id) {
        int hash = id.hashCode();
        int red = (hash >>> 16) & 255;
        int green = (hash >>> 8) & 255;
        int blue = hash & 255;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        float saturation = max == 0 ? 0 : (max - min) / (float) max;
        if (saturation == 0) return rgb(BRIGHTNESS, BRIGHTNESS, BRIGHTNESS);

        float delta = max - min;
        float redDistance = (max - red) / delta;
        float greenDistance = (max - green) / delta;
        float blueDistance = (max - blue) / delta;
        float hue = red == max ? blueDistance - greenDistance
                : green == max ? 2 + redDistance - blueDistance : 4 + greenDistance - redDistance;
        hue /= 6;
        if (hue < 0) hue += 1;

        float sector = (hue - (float) Math.floor(hue)) * 6;
        float fraction = sector - (float) Math.floor(sector);
        float p = BRIGHTNESS * (1 - saturation);
        float q = BRIGHTNESS * (1 - saturation * fraction);
        float t = BRIGHTNESS * (1 - saturation * (1 - fraction));
        return switch ((int) sector) {
            case 0 -> rgb(BRIGHTNESS, t, p);
            case 1 -> rgb(q, BRIGHTNESS, p);
            case 2 -> rgb(p, BRIGHTNESS, t);
            case 3 -> rgb(p, q, BRIGHTNESS);
            case 4 -> rgb(t, p, BRIGHTNESS);
            default -> rgb(BRIGHTNESS, p, q);
        };
    }

    private static int rgb(float red, float green, float blue) {
        return 0xFF000000 | Math.round(red * 255) << 16 | Math.round(green * 255) << 8 | Math.round(blue * 255);
    }
}
