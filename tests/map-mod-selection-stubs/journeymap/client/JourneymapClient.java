package journeymap.client;

import java.io.File;
import java.nio.file.Path;

/** Minimal shape-compatible JourneyMap runtime fixture for reflection/API tests. */
public final class JourneymapClient {
    private static JourneymapClient instance;
    private final CoreProperties core;
    private final MiniMapProperties mini1;
    private final MiniMapProperties mini2;
    private final WaypointProperties waypoint;
    private final WebMapProperties web;

    public JourneymapClient(Path settingsDirectory) {
        instance = this;
        core = new CoreProperties(settingsDirectory.resolve("journeymap.core.config"));
        mini1 = new MiniMapProperties(settingsDirectory.resolve("journeymap.minimap.config"));
        mini2 = new MiniMapProperties(settingsDirectory.resolve("journeymap.minimap2.config"));
        waypoint = new WaypointProperties(settingsDirectory.resolve("journeymap.waypoint.config"));
        web = new WebMapProperties(settingsDirectory.resolve("journeymap.webmap.config"));
    }

    public static JourneymapClient getInstance() { return instance; }
    public CoreProperties getCoreProperties() { return core; }
    public MiniMapProperties getMiniMapProperties1() { return mini1; }
    public MiniMapProperties getMiniMapProperties2() { return mini2; }
    public WaypointProperties getWaypointProperties() { return waypoint; }
    public WebMapProperties getWebMapProperties() { return web; }

    public static class BooleanField {
        private boolean value;
        private boolean failNextFalse;
        private final String key;
        private final boolean defaultValue;

        public BooleanField(boolean value, boolean defaultValue, String key) {
            this.value = value;
            this.defaultValue = defaultValue;
            this.key = key;
        }
        public Boolean get() { return value; }
        public Boolean getDefaultValue() { return defaultValue; }
        public String getKey() { return key; }
        public BooleanField set(Boolean value) {
            if (!value && failNextFalse) {
                failNextFalse = false;
                throw new IllegalStateException("one-shot test failure");
            }
            this.value = value;
            return this;
        }
        public void failNextFalse() { failNextFalse = true; }
    }

    public static class CoreProperties {
        public final BooleanField mappingEnabled = new BooleanField(true, true, "");
        private final File file;
        CoreProperties(Path file) { this.file = file.toFile(); }
        public File getFile() { return file; }
    }

    public static class MiniMapProperties {
        public final BooleanField enabled = new BooleanField(true, true, "jm.minimap.enable_minimap");
        private final File file;
        MiniMapProperties(Path file) { this.file = file.toFile(); }
        public File getFile() { return file; }
    }

    public static class WaypointProperties {
        public final BooleanField beaconEnabled = new BooleanField(true, true, "jm.waypoint.enable_beacons");
        public final BooleanField renderWaypointsWorld = new BooleanField(true, true,
                "jm.waypoint.world.render_enabled");
        private final File file;
        WaypointProperties(Path file) { this.file = file.toFile(); }
        public File getFile() { return file; }
    }

    public static class WebMapProperties {
        public final BooleanField enabled = new BooleanField(true, false, "jm.webmap.enable");
        private final File file;
        WebMapProperties(Path file) { this.file = file.toFile(); }
        public File getFile() { return file; }
    }
}
