// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.nio.file.Files;
import java.nio.file.Path;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;

/** Exercises real settings persistence and the dependency between the two deathpoint switches. */
public final class DeathPointConfigTest {
    public static void main(String[] args) throws Exception {
        Path path = new MinimapSizingTest.TestPlatform().getConfigDir().resolve("openmapdelta/config.json");
        check(OpenMapDeltaConfig.effectiveSmartDeathpoints(), "smart deathpoints are enabled by default");
        check(!OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get(), "keepInventory deaths are cached by default");
        check(OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.get() == 300, "default black retention is five minutes");
        OpenMapDeltaConfig.setDeathpointsEnabled(false);
        OpenMapDeltaConfig.setSmartDeathpointsEnabled(true);
        check(!OpenMapDeltaConfig.effectiveSmartDeathpoints() && !OpenMapDeltaConfig.SMART_DEATHPOINTS.get(),
                "smart mode requires deathpoint creation to be enabled");
        OpenMapDeltaConfig.setDeathpointsEnabled(true);
        OpenMapDeltaConfig.setSmartDeathpointsEnabled(true);
        OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.set(true);
        OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.set(.4);
        OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.set(.8);
        OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.set(0);
        OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.set("minecraft:stick,#minecraft:logs");
        OpenMapDeltaConfig.save();
        OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.set(false);
        OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.set(.25);
        OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.set(1.0);
        OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.set(300);
        OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.set("");
        OpenMapDeltaConfig.load();
        check(OpenMapDeltaConfig.effectiveSmartDeathpoints() && OpenMapDeltaConfig.SHOW_KEEP_INVENTORY_DEATHPOINTS.get(),
                "both enabled switches and keepInventory visibility survive save/reload");
        check(OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.get() == .4 && OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.get() == .8
                        && OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.get() == 0
                        && OpenMapDeltaConfig.SMART_DEATHPOINT_CHEAP_ITEMS.get().equals("minecraft:stick,#minecraft:logs"),
                "pulse limits, indefinite retention and custom cheap items survive save/reload");
        for (String invalid : new String[]{"-1", "1e309", "\"0.5\"", "null", "[]", "{}", "true"}) {
            OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.set(.4);
            OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.set(.8);
            OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.set(0);
            String settings = "\"deathBeaconMinOpacity\":" + invalid + ",\"deathBeaconMaxOpacity\":" + invalid
                    + ",\"smartDeathpointBlackRetentionSeconds\":" + invalid + ",\"showDeath\":false,\"smartDeathpoints\":true";
            Files.writeString(path, "{" + settings + "}");
            OpenMapDeltaConfig.load();
            check(OpenMapDeltaConfig.DEATH_BEACON_MIN_OPACITY.get() == .25 && OpenMapDeltaConfig.DEATH_BEACON_MAX_OPACITY.get() == 1.0
                            && OpenMapDeltaConfig.SMART_DEATHPOINT_BLACK_RETENTION_SECONDS.get() == 300,
                    "invalid deathpoint settings fall back safely: " + invalid);
            check(!OpenMapDeltaConfig.SMART_DEATHPOINTS.get(), "disabled deathpoints override loaded smart mode");
        }
        System.out.println("Deathpoint settings persistence and validation tests passed.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
