package xaero.hud.minimap.common.config.option;

import xaero.minimap.XaeroMinimap.Option;

/** Reflected Xaero option holder with current option IDs. */
public final class MinimapProfiledConfigOptions {
    public static final Option DISPLAY_MINIMAP = new Option("display_minimap");
    public static final Option WAYPOINTS_IN_WORLD = new Option("waypoints_in_world");
    public static final Option WAYPOINTS_ON_MINIMAP = new Option("waypoints_on_minimap");
    public static final Option TRACKED_PLAYERS_IN_WORLD = new Option("tracked_players_in_world");
    private MinimapProfiledConfigOptions() {}
}
