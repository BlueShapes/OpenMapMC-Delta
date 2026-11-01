package xaero.minimap;

import java.nio.file.Path;

/** Minimal modern Xaero entry point/profile surface matching the reflected API. */
public final class XaeroMinimap {
    public static XaeroMinimap instance;
    private final Path configSubFolder;
    private final Profile profile = new Profile("default");
    private final LegacySettings settings = new LegacySettings();

    public XaeroMinimap(Path configSubFolder) { this.configSubFolder = configSubFolder; instance = this; }
    public HudConfigs getHudConfigs() { return new HudConfigs(profile); }
    public Path getConfigSubFolder() { return configSubFolder; }
    public LegacySettings getSettings() { return settings; }

    public static final class LegacySettings {
        private boolean showWaypoints = true;
        private boolean showIngameWaypoints = true;
        public boolean displayTrackedPlayersInWorld = true;
        public boolean showWaypoints() { return showWaypoints; }
        public boolean showIngameWaypoints() { return showIngameWaypoints; }
        public boolean displayTrackedPlayersInWorld() { return displayTrackedPlayersInWorld; }
    }

    public static final class HudConfigs {
        private final Profile profile;
        HudConfigs(Profile profile) { this.profile = profile; }
        public ClientConfigManager getClientConfigManager() { return new ClientConfigManager(profile); }
    }

    public static final class ClientConfigManager {
        private final Profile profile;
        ClientConfigManager(Profile profile) { this.profile = profile; }
        public Profile getCurrentProfile() { return profile; }
    }

    public static final class Profile {
        private final String id;
        private final java.util.Map<String, Boolean> values = new java.util.HashMap<>();
        private String failOption;
        Profile(String id) {
            this.id = id;
            values.put("display_minimap", true);
            values.put("waypoints_in_world", true);
            values.put("waypoints_on_minimap", true);
            values.put("tracked_players_in_world", true);
        }
        public String getId() { return id; }
        public Boolean get(Option option) { return values.get(option.getId()); }
        public void set(Option option, Boolean value) {
            if (option.getId().equals(failOption) && !value) {
                failOption = null;
                throw new IllegalStateException("one-shot test failure");
            }
            values.put(option.getId(), value);
        }
        public void failNextFalse(String option) { failOption = option; }
        public boolean value(String option) { return values.get(option); }
    }

    public static final class Option {
        private final String id;
        public Option(String id) { this.id = id; }
        public String getId() { return id; }
    }
}
