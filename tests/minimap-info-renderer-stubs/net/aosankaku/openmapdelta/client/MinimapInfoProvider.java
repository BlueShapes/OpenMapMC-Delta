// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import net.minecraft.client.Minecraft;
import net.aosankaku.openmapdelta.config.MinimapInfoType;

import java.util.EnumMap;
import java.util.Map;

public final class MinimapInfoProvider {
    private static final Map<MinimapInfoType, String> TEXT = new EnumMap<>(MinimapInfoType.class);
    private MinimapInfoProvider() {}
    public static String text(Minecraft mc, MinimapInfoType type) { return TEXT.get(type); }
    public static void setText(MinimapInfoType type, String value) { TEXT.put(type, value); }
}
