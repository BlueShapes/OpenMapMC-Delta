// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.util;

import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/** Reserves the ellipsis, then asks the font for a source-preserving prefix exactly once. */
public final class MapTextFitting {
    private MapTextFitting() {}

    public static String fit(String text, int maxWidth, ToIntFunction<String> width,
                             BiFunction<String, Integer, String> prefixByWidth) {
        if (text == null || text.isEmpty() || maxWidth < 0) return "";
        if (width.applyAsInt(text) <= maxWidth) return text;
        int ellipsisWidth = width.applyAsInt("…");
        if (ellipsisWidth > maxWidth) return "";
        return prefixByWidth.apply(text, maxWidth - ellipsisWidth) + "…";
    }
}
