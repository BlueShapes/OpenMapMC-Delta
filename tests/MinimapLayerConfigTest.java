// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import net.aosankaku.openmapdelta.config.MinimapLayerMode;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.platform.services.IPlatformHelper;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises minimap layer defaults, persistence, validation, and compatibility loading. */
public final class MinimapLayerConfigTest {
    public static void main(String[] args) throws Exception {
        Path config = new TestPlatform().getConfigDir().resolve("openmapdelta/config.json");

        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.SURFACE,
                "surface mode is the default");
        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.getDefault() == MinimapLayerMode.SURFACE,
                "surface mode is the persisted default");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == 64,
                "manual cave Y defaults to 64");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.getDefault() == 64,
                "manual cave Y persisted default is 64");

        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.PLAYER_Y);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(-4096);
        OpenMapDeltaConfig.save();
        String saved = Files.readString(config);
        check(saved.contains("\"minimapLayerMode\": \"PLAYER_Y\""),
                "layer mode saves as a flat minimapLayerMode key");
        check(saved.contains("\"minimapCaveY\": -4096"),
                "manual cave Y saves as a flat minimapCaveY key");
        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.SURFACE);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(64);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.PLAYER_Y,
                "layer mode survives save and reload");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == -4096,
                "manual cave Y survives save and reload");

        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(4096);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(64);
        check(rejects(() -> OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(-4097)),
                "manual cave Y rejects values below -4096");
        check(rejects(() -> OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(4097)),
                "manual cave Y rejects values above 4096");

        Files.writeString(config, "{\"map\":{\"minimapLayerMode\":\"MANUAL\",\"minimapCaveY\":-23}}");
        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.SURFACE);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(64);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.MANUAL,
                "nested map spelling loads the minimap layer mode");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == -23,
                "nested map spelling loads the manual cave Y");

        Files.writeString(config, "{\"minimapLayerMode\":\"PLAYER_Y\",\"minimapCaveY\":32,\"map\":{\"minimapLayerMode\":\"MANUAL\",\"minimapCaveY\":-14}}");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.MANUAL,
                "nested map mode takes precedence over the flat spelling");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == -14,
                "nested map Y takes precedence over the flat spelling");

        for (String invalid : new String[]{"\"unknown\"", "1", "true", "null", "[]", "{}"}) {
            Files.writeString(config, "{\"minimapLayerMode\":" + invalid + ",\"minimapCaveY\":73,\"showCompass\":false}");
            OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.MANUAL);
            OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(64);
            OpenMapDeltaConfig.SHOW_COMPASS.set(true);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.SURFACE,
                    "invalid layer mode falls back independently: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == 73,
                    "valid cave Y loads alongside an invalid layer mode: " + invalid);
            check(!OpenMapDeltaConfig.SHOW_COMPASS.get(),
                    "other settings still load alongside an invalid layer mode: " + invalid);
        }

        for (String invalid : new String[]{"\"64\"", "64.5", "true", "null", "[]", "{}", "4097", "-4097"}) {
            Files.writeString(config, "{\"minimapLayerMode\":\"PLAYER_Y\",\"minimapCaveY\":" + invalid + "}");
            OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.SURFACE);
            OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(128);
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == 64,
                    "invalid cave Y falls back to its default: " + invalid);
            check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.PLAYER_Y,
                    "valid layer mode loads alongside invalid cave Y: " + invalid);
        }

        Files.writeString(config, "{\"enabled\":true}");
        OpenMapDeltaConfig.MINIMAP_LAYER_MODE.set(MinimapLayerMode.SURFACE);
        OpenMapDeltaConfig.MINIMAP_CAVE_Y.set(64);
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.MINIMAP_LAYER_MODE.get() == MinimapLayerMode.SURFACE,
                "older config without the setting keeps the default mode");
        check(OpenMapDeltaConfig.MINIMAP_CAVE_Y.get() == 64,
                "older config without the setting keeps the default Y");

        System.out.println("Minimap layer config tests passed.");
    }

    private static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void check(boolean condition, String description) {
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
