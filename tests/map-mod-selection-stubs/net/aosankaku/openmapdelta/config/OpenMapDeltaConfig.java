// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.config;

/** Minimal runtime stand-in for the two cached Odyssey settings edited by selection. */
public final class OpenMapDeltaConfig {
    public static final Setting MAP_MOD_ENABLED = new Setting(true);
    public static final Setting ENABLED = new Setting(false);

    private OpenMapDeltaConfig() {}

    public static final class Setting {
        private boolean value;

        public Setting(boolean value) { this.value = value; }
        public Boolean get() { return value; }
        public void set(Boolean value) { this.value = value; }
    }
}
