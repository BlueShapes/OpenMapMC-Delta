package xaero.hud.minimap;

/** Legacy Xaero minimap HUD module control. */
public final class BuiltInHudModules {
    public static final HudModule MINIMAP = new HudModule();
    private BuiltInHudModules() {}

    public static final class HudModule {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
}
