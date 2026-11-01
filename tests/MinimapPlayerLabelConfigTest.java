// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

import net.aosankaku.openmapdelta.platform.services.IPlatformHelper;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MinimapPlayerLabelConfigTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path config = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE,
                "inside labels default to none");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.getDefault() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE,
                "inside labels persist none as their default");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY,
                "outside labels default to distance only");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.getDefault() == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY,
                "outside labels persist distance only as their default");

        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\"showCompass\":false,\"minimapPlayerIcon\":\"TRIANGLE\"}");
        OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY);
        OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE,
                "an older file without the new inside key uses its default");
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY,
                "an older file without the new outside key uses its default");
        check(!OpenMapDeltaConfig.SHOW_COMPASS.get()
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_ICON.get() == OpenMapDeltaConfig.MinimapPlayerIcon.TRIANGLE,
                "an older file still loads unrelated settings");

        roundTripsAllModePairs(config);
        nestedAndFlatCompatibility(config);
        invalidValuesFallBackIndependently(config);

        System.out.println("Minimap player label config tests passed (" + assertions + " assertions).");
    }

    private static void roundTripsAllModePairs(Path config) throws Exception {
        for (OpenMapDeltaConfig.MinimapPlayerLabelMode inside : OpenMapDeltaConfig.MinimapPlayerLabelMode.values()) {
            for (OpenMapDeltaConfig.MinimapPlayerLabelMode outside : OpenMapDeltaConfig.MinimapPlayerLabelMode.values()) {
                OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.set(inside);
                OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.set(outside);
                OpenMapDeltaConfig.save();
                String saved = Files.readString(config);
                check(saved.contains("\"minimapPlayerLabelInside\": \"" + inside.name() + "\""),
                        "inside mode saves under its flat key");
                check(saved.contains("\"minimapPlayerLabelOutside\": \"" + outside.name() + "\""),
                        "outside mode saves under its flat key");
                OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY);
                OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY);
                OpenMapDeltaConfig.load();
                check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == inside
                                && OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == outside,
                        "every inside/outside mode pair survives save and reload");
            }
        }
    }

    private static void nestedAndFlatCompatibility(Path config) throws Exception {
        Files.writeString(config, "{\"minimapPlayerLabelInside\":\"name_and_distance\",\"minimapPlayerLabelOutside\":\"NAME_ONLY\"}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_AND_DISTANCE
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY,
                "the combined inside mode loads case-insensitively beside an independent outside mode");

        Files.writeString(config, "{\"map\":{\"minimapPlayerLabelInside\":\"NONE\",\"minimapPlayerLabelOutside\":\"name_and_distance\"}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_AND_DISTANCE,
                "the combined outside mode loads from legacy nested keys beside an independent inside mode");

        Files.writeString(config, "{\"map\":{\"minimapPlayerLabelInside\":\"NAME_ONLY\",\"minimapPlayerLabelOutside\":\"NONE\"}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE,
                "legacy nested map keys load");

        Files.writeString(config, "{\"minimapPlayerLabelInside\":\"DISTANCE_ONLY\",\"minimapPlayerLabelOutside\":\"NAME_ONLY\",\"map\":{\"minimapPlayerLabelInside\":\"NONE\",\"minimapPlayerLabelOutside\":\"DISTANCE_ONLY\"}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE
                        && OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY,
                "nested map values take precedence over flat keys");
    }

    private static void invalidValuesFallBackIndependently(Path config) throws Exception {
        String[] invalidValues = {"\"unknown\"", "1", "true", "null", "[]", "{}"};
        for (String invalid : invalidValues) {
            Files.writeString(config, "{\"minimapPlayerLabelInside\":" + invalid
                    + ",\"minimapPlayerLabelOutside\":\"NAME_ONLY\",\"showCompass\":false}");
            OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY);
            OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE);
            OpenMapDeltaConfig.SHOW_COMPASS.set(true);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE,
                    "invalid inside mode falls back to its own default: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY,
                    "valid outside mode loads beside an invalid inside mode: " + invalid);
            check(!OpenMapDeltaConfig.SHOW_COMPASS.get(), "later settings still load after invalid mode: " + invalid);

            Files.writeString(config, "{\"minimapPlayerLabelInside\":\"NAME_ONLY\",\"minimapPlayerLabelOutside\":" + invalid + "}");
            OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NONE);
            OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.set(OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_INSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.NAME_ONLY,
                    "valid inside mode loads beside an invalid outside mode: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_PLAYER_LABEL_OUTSIDE.get() == OpenMapDeltaConfig.MinimapPlayerLabelMode.DISTANCE_ONLY,
                    "invalid outside mode falls back to its own default: " + invalid);
        }
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    public static final class TestPlatform implements IPlatformHelper {
        public String getPlatformName() { return "test"; }
        public boolean isModLoaded(String modId) { return false; }
        public boolean isDevelopmentEnvironment() { return true; }
        public Path getConfigDir() { return Path.of(System.getProperty("minimap.test.configDir")); }
        public Path getGameDir() { return Path.of(System.getProperty("minimap.test.gameDir")); }
    }
}
