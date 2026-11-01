// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.nio.file.Files;
import java.nio.file.Path;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;

/** Exercises config defaults, persistence, and safe loading for minimap corner alignment. */
public final class MinimapInfoAlignmentConfigTest {
    public static void main(String[] args) throws Exception {
        Path config = new MinimapSizingTest.TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE,
                "map-edge alignment remains the default for old configs");

        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.CENTER);
        OpenMapDeltaConfig.save();
        check(Files.readString(config).contains("\"infoCornerAlignment\": \"CENTER\""),
                "alignment is saved as the flat infoCornerAlignment key");
        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.CENTER,
                "flat setting survives save and reload");

        Files.writeString(config, "{\"map\":{\"infoCornerAlignment\":\"CENTER\"}}");
        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.CENTER,
                "nested map setting is accepted for old config files");

        Files.writeString(config, "{\"infoCornerAlignment\":\"CENTER\",\"map\":{\"infoCornerAlignment\":\"MAP_EDGE\"}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE,
                "nested map setting takes precedence when both spellings exist");

        for (String invalid : new String[]{"\"unknown\"", "1", "true", "null", "[]", "{}"}) {
            Files.writeString(config, "{\"infoCornerAlignment\":" + invalid + "}");
            OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.CENTER);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE,
                    "invalid alignment falls back to the default: " + invalid);
        }

        Files.writeString(config, "{\"enabled\":true}");
        OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.set(OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.INFO_CORNER_ALIGNMENT.get() == OpenMapDeltaConfig.InfoCornerAlignment.MAP_EDGE,
                "older config without the setting keeps the default");
        System.out.println("Minimap corner alignment config tests passed.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
