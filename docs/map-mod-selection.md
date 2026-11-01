# Map mod selection

When OpenMapMC Delta is installed with JourneyMap or Xaero's Minimap, a warning on
the title screen lets you choose one map mod to enable. The choice is remembered
until a detected map mod is added, removed, or changes version. **Keep current
settings** leaves the map configurations as they are and acknowledges the warning.

You can choose again from **Map Settings → Integrations → Choose a map mod**.
The settings key remains available when OpenMapMC Delta is disabled.

The selection changes client configuration files and cached settings. Installed
JARs, worlds, waypoint files, and explored map data remain in place.

- OpenMapMC Delta uses `mapModEnabled` in `config/openmapdelta/config.json` to stop its
  maps, waypoint overlays, scanning, and client web hosting.
- JourneyMap disables both minimap presets, mapping, world waypoint overlays,
  and web hosting in its global and existing world-specific configuration files.
- Xaero's Minimap disables the minimap, minimap/world waypoints, and tracked-player
  world overlays in its legacy configuration or current local configuration profile.
  Other manually selected profiles retain their settings.

JourneyMap's fullscreen map can still be opened manually to inspect existing data.
Server-side mod features and server policies are unchanged.

When OpenMap is selected, its external-player integration can still read the
positions received by the installed JourneyMap/Xaero networking code. Hiding a
map's display does not unload its mod. Removing or disabling its JAR does remove
that provider. Public BlueMap positions can instead be configured independently
under **Integrations → BlueMap player positions**; see [Multiplayer](multiplayer.md).

Only the visibility and mapping flags needed for this selection are edited.
If a configuration cannot be safely updated, the selection is not recorded and
the warning stays open so you can retry or keep the current settings.
If the acknowledgement cannot be saved, **Continue without saving** still lets
you reach the title menu. The warning can appear again on the next launch.
