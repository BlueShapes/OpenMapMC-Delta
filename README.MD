# OpenMapMC Delta

[Source, issues, and contributions](https://github.com/BlueShapes/OpenMapMC-Delta)

現在の機能要件とバージョン別の対応状況は
[機能・移植要件一覧](docs/fork-change-register.md)を参照してください。
The [feature and porting register](docs/fork-change-register.md) describes current
requirements and version-specific differences. Features implemented in one
version are not necessarily available in every other version.

OpenMapMC Delta is a fork of Odyssey Map by NAIZO. See [fork provenance and compatibility identifiers](docs/fork-provenance.md).

The distribution name remains OpenMapMC Delta. In-game and Web interfaces display OpenMap Delta.

Minecraft minimap with real-time terrain rendering, markers, waypoints, a
fullscreen map, and an optional local web map. Server installation is optional.
Formerly known as MiniMaps / Vanilla Minimaps.

## Supported versions

| Version root | Minecraft | Loaders | Java |
| --- | --- | --- | --- |
| `1.20.1/` | 1.20.1 | Fabric, Forge | 17 |
| `1.21.1/` | 1.21.1 | Fabric, NeoForge | 21 |
| `26.1.2/` | 26.1.2 | Fabric, NeoForge | 25 |
| `26.2/` | 26.2 | Fabric, NeoForge | 25 |

Each version root is an independent Gradle project.
Within a version root, Fabric and Forge/NeoForge use the same `common` source.
The `common` directories in different version roots are separate copies, so
changes must be ported explicitly between Minecraft versions.

## Features

- Real-time terrain minimap with smooth rotation
- Waypoints and markers
- Fullscreen map view
- Optional surface place names and editable areas with personal or server storage (26.2)
- In-game settings screen
- Optional Controllable / Controllable Delta map navigation and marker paging
- Six configurable minimap information slots and distance-limited waypoint names
- Import existing JourneyMap and Xaero's Minimap waypoints
- Choose an active map mod at startup when JourneyMap or Xaero's Minimap is installed
- Automatic Create / Steam 'n' Rails and Minecraft Transit Railway stations
- Create and MTR train tracks, including curves, with fullscreen layer toggles
- Surface and selectable cave layers, including independent automatic/manual minimap layers and server-owned cave-map policy
- Flat item and mob-face waypoint icons, with clear hover outlines
- Optional server-wide player positions and saveable location notifications
- Editable notification templates, coordinate copying, and optional structure context
- Fullscreen F1 map view and highlighted navigation to arbitrary locations
- Server-persisted shared waypoints with creator faces and owner/admin permissions
- Smart Deathpoints with loot timers, recovery tracking, and restoration in 1.20.1 / 1.21.1 / 26.2
- Local web map with searchable players and optional persistent password sessions
- Sixteen game-language catalogs

See [transit stations](docs/transit-stations.md), [transit tracks](docs/transit-rails.md), [web map](docs/web-map.md),
[surface areas and place names (26.2)](docs/surface-areas.md),
[minimap information](docs/minimap-information.md),
[terrain rendering and zoom quality](docs/map-rendering.md),
[multiplayer and cave layers](docs/multiplayer.md), and
[languages](docs/localization.md), and [controller support](docs/controller-support.md)
for configuration and compatibility details.

See [map mod selection](docs/map-mod-selection.md) for the startup warning and
configuration changes used to hide other maps.

See [Smart Deathpoints](docs/deathpoints.md) for death tracking, optional server
support, and the distinction between authoritative loot timers and client-only estimates.

## Managing waypoints

Open the fullscreen map sidebar to list waypoints in the current dimension,
including hidden waypoints and automatically discovered transit stations. The eye
button changes visibility while keeping the entry in the list. The trash button
at the right opens a deletion confirmation; automatic stations cannot be deleted.
Double-click or right-click a row to edit it.

Enter a category in the waypoint editor to group entries. Empty categories appear
under **Uncategorized**, or **Stations** for automatic stations. Click a category's
arrow or name to collapse or expand its entries. The category's eye button shows
all its members, or hides them when all are shown. **Show all** and
**Hide all** affect every listed waypoint in the current dimension, including
entries excluded by the search or collapsed categories. Category controls also
affect all members during a search. Station names and coordinates follow their
source; their category, visibility, favorite status, color and icon settings are
saved for the world.
Newly created waypoints and newly discovered stations start visible.

The [last sleep waypoint](docs/bed-waypoints.md) is saved for the current world or
server, uses a bed icon, and supports the same editing and world display controls.
Recording sleeps that do not set spawn is optional and disabled by default.

## Importing waypoints

When existing JourneyMap or Xaero's Minimap waypoint data is found, OpenMapMC Delta
shows a chat message with a clickable **Import Waypoints** link. The same screen
can be opened from **Map Settings → Import Waypoints**.
The settings key is configurable in Minecraft's controls. You can also open
settings from the fullscreen map.

Select the source world/server, then choose **Import**. Names, coordinates,
dimensions, colors, and visibility are preserved. JourneyMap's Nether coordinate
conversion is applied. Existing waypoints with the same name, dimension, and
coordinates are skipped, and source files are left untouched. Unchanged data is
only announced once per source dataset. Imported waypoints belong to the current
world/server and reappear when you return to it. Existing manually created
waypoints keep their previous behavior. Select the source world/server whose
waypoints you want to copy into the current world.

Supported data in the current Minecraft instance:

- JourneyMap 5: `journeymap/data/{sp|mp}/<world>/waypoints/*.json`
- JourneyMap 6: `journeymap/data/{sp|mp}/<world>/waypoints/WaypointData.dat`
- Xaero's Minimap: `xaero/minimap/<world>/dim%*/`, and legacy `XaeroWaypoints/`,
  `config/XaeroWaypoints/`, or `mods/XaeroWaypoints/` directories

For migration from another launcher instance, copy its waypoint data into the
corresponding directory of this instance and rescan in the import screen.
Malformed entries are skipped and reported in the screen.
The **Rescan** button also retries a failed scan. Leaving a world invalidates its
import screen and pending results. A failed save leaves the previous waypoint
list intact.

The formats follow [JourneyMap's waypoint documentation](https://teamjm.github.io/journeymap-docs/6.0.x/client/waypoints/)
and [Xaero's author's format description](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/2379024-xaeros-minimap-1-21-11-1-20-x-1-19-4-1-18-2-1-17-1?page=101).

Run the standalone reader regression tests without starting Minecraft:

```powershell
.\scripts\test-waypoint-import.ps1 -VersionRoot 26.2 -JdkHome $env:JAVA_HOME
```

The test script uses a cached Gson 2.10.1 JAR; pass `-GsonJar` to select another
compatible JAR. Java 17 or newer is required.

## Configuration

Fullscreen waypoint labels have a translucent black background. Their text defaults
to 0.75× size; Settings → Fullscreen → Waypoint text scale changes it from 0.25×
to 2.00×. The same value is stored as `fullscreenWaypointLabelScale` in
`config/openmapdelta/config.json`. Text backgrounds and clickable areas follow the
selected size. Below 0.50× zoom,
waypoint labels are hidden; Settings →
Fullscreen → Waypoint label minimum zoom provides a 0.00–2.00× slider. The exact
threshold remains visible, and 0.00× keeps labels visible at every zoom. Hidden
labels have no clickable text area; icons remain selectable. Player labels retain
their normal behavior.

Waypoints outside the visible map appear as icons on its edge; clicking those icons selects their
actual coordinates. The fullscreen zoom has two additional zoom-out steps (17 and
18). `playerIconMaxSize` caps fullscreen and web map player icons at 16 GUI/CSS
pixels by default, with a configurable range of 4–64.

`biomeGrassTint` defaults to enabled and colors grass and foliage using each
biome's grass color. Nether and End retain their original map colors. Tiles store
both the original and biome colors, allowing immediate toggling without deleting
explored terrain. Older tile caches remain readable; their biome colors become
available when those loaded chunks are scanned again.

`asyncMapGeneration` defaults to enabled. On machines with at least four logical
CPU cores and sampled CPU load below 80%, up to four workers process copied chunk
palettes, reserving two cores. Busy CPUs use client-thread sampling. Surface and
cave scans share a bounded worker pool; world-dependent color callbacks, cache
updates, and rendering remain on the client thread. Changed chunks and obsolete
world sessions invalidate worker results. Leaving the world stops the workers.
After teleporting, nearby chunks that arrive late are retried before farther
terrain. Player surroundings take priority even when the fullscreen map remains
panned elsewhere. Both scan paths apply blocks nearest the player first; chunks
with repeated packet updates temporarily use live sampling so they still finish.

Cancelling a changed waypoint opens a discard confirmation. Reaching a navigation
destination posts its name in the local player's chat. The fullscreen map's context
menu can navigate to an empty map position without saving a waypoint. The local
player cannot be selected as their own navigation destination.

Overlapping distant world waypoint icons share a white beacon and a dark circular
count badge centered between their screen positions, with eligible names stacked
above it. The center includes every member and is independent of which member
has display priority. Looking at any readable member focuses the whole cluster,
making its other readable names eligible too. Clusters with more than five
eligible names show five names and a localized remaining-count row above them;
navigation targets and location notices receive selection priority while the
displayed rows retain their configured order. Faded names are excluded from that
count. Looking away from the cluster hides ordinary names, while navigation
destinations and location notifications remain visible. Near and standalone
waypoints keep their existing per-waypoint gaze behavior.
Waypoints below **Name Display
Distance (m)** (`minimapWaypointNameDistance`, default 150) remain individual;
zero keeps every waypoint individual. Colliding text moves vertically without
moving the waypoint's icon. The minimap also groups distant icons and stacks their
names without distances, while individual labels retain the configured label limit.
World counts include overlapping distant members beyond the 64 rendered-group
limit. Navigation and notification targets retain their emphasis.

Run the focused generation and tile storage checks without starting Minecraft:

```powershell
.\scripts\test-map-generation.ps1 -JdkHome $env:JAVA_HOME
.\scripts\test-tile-cache.ps1 -VersionRoot 1.20.1 -JdkHome $env:JAVA_HOME
```

The minimap stays north-up by default. Its central player marker is an outlined
triangle twice as tall as it is wide that turns with the player's facing direction.
In heading-up mode the triangle points up while the map rotates. In the **Minimap**
tab, use **Player icon** to switch between the triangle and your player head;
this selection is separate from **Show Player Head**, which controls the fullscreen
and web maps. Player heads stay upright by default. Enable **Rotate player head** to turn the head
with your facing direction in north-up mode; in heading-up mode it points up.
The local player icon scale also sizes either minimap icon.

The minimap's default scale is `1.0`: its map and border fit within one third of
the shorter GUI dimension. `0.5` makes it half that size, and `2.0` doubles it.
Relative sizing stays consistent when Minecraft's GUI scale changes. Set
`minimapScale` in `config/openmapdelta/config.json` to a finite value from `0.25`
to `2.0`, or use **Minimap Size (x)** in the in-game settings. Existing
`minimapSize` entries no longer control display size; configurations without
`minimapScale` use `1.0`. Invalid scales fall back to `1.0` without preventing
other settings from loading. A minimap smaller than one GUI pixel is hidden.

Run the standalone sizing and config checks without starting Minecraft:

```powershell
.\scripts\test-minimap-sizing.ps1 -VersionRoot 26.2 -JdkHome $env:JAVA_HOME
```

The script uses cached Gson and SLF4J API JARs, or accepts `-GsonJar` and
`-Slf4jJar` paths. Java 17 or newer is sufficient for these checks on any root.

Numeric settings and location-notice durations use sliders. Drag to choose a
value, or use the left/right arrow keys for a single-step adjustment. Wide ranges,
including seconds per tick and station grouping distances, use a logarithmic
scale. Opening a settings screen preserves saved values until you adjust them.
Waypoint coordinates and hexadecimal colors retain direct text entry for precise
values. Run the standalone slider range checks with
`.\scripts\test-setting-sliders.ps1 -JdkHome $env:JAVA_HOME`.

Fullscreen map lines mark chunk boundaries at world X/Z multiples of 16 and
stay aligned with terrain while panning and zooming, including negative coordinates.
Every chunk boundary remains visible down to 0.15x zoom, with thinner lines as they
get closer together. Further out, the grid groups 2, 4, 8, or more chunks per line
to keep a similar density. Toggle it under **Settings → Fullscreen → Show Chunk Borders**.

Chunk border thickness can be changed under
**Settings → Fullscreen → Chunk Border Width**, or with `fullscreenGridThickness` in
`config/openmapdelta/config.json`. The default is `0.5` GUI pixels; values from `0.25`
to `4.0` are supported. Lines become thinner at dense zoom levels, and their width
is bounded to leave gaps. GUI scale also scales the lines. Existing configurations
without this setting use the default.

World waypoint icons, labels, and backdrops fade independently from beacon beams
as the local player approaches. Labels start fading at exactly 2.5 blocks by default
and are hidden at 1.25 blocks or closer. Beams retain their existing behavior:
fading starts at 10 blocks and ends at 5 blocks. Moving away reveals each part again.
Distance uses the player's three-dimensional position, so changing the camera view
does not change proximity visibility.

Change **Settings → Markers → Label Fade Start (m)**, or set `waypointLabelFadeDistance`
in `config/openmapdelta/config.json`. Its default is `2.5`; labels, icons, and backdrops
are hidden at half the selected value. **Beam Hide Within (m)** and `waypointHideDistance`
control only the beam, with the unchanged default `5` and fade start at twice that value.
Both settings accept finite fractional values from `0` to `128`; `0` disables that
part's proximity fade. Invalid values fall back to the respective default.
These rules apply to permanent waypoints, temporary waypoints, and notification
markers in the world. Temporary and notification beams pulse between 25% and 100%
before the proximity fade is applied, and a visible
beam remains rendered when its label is hidden. Saved waypoint data, other players'
visibility, and map markers remain unchanged. Temporary waypoint arrival/removal
and notification expiry still follow their existing rules.

Run the standalone viewport and chunk boundary geometry checks without starting Minecraft:

```powershell
.\scripts\test-fullscreen-map.ps1 -VersionRoot 1.20.1 -JdkHome $env:JAVA_HOME
```

## Building

Install the JDK listed in the version table and set `JAVA_HOME` to that JDK's
installation directory. The Gradle wrappers are included; a separate Gradle
installation is not needed. The first build downloads its Gradle distribution
and project dependencies.

Build the version and loader you are changing. For example, with `JAVA_HOME`
set to Java 25, build Minecraft 26.2 / NeoForge from the repository root:

**Windows (PowerShell)**

```powershell
Set-Location 26.2
.\gradlew.bat :neoforge:build --no-daemon
```

**Linux / macOS**

```sh
cd 26.2
./gradlew :neoforge:build --no-daemon
```

Use the matching tasks within each version root:

| Version root | JDK | Fabric | Forge / NeoForge |
| --- | --- | --- | --- |
| `1.20.1/` | 17 | `:fabric:build` | `:forge:build` |
| `1.21.1/` | 21 | `:fabric:build` | `:neoforge:build` |
| `26.1.2/` | 25 | `:fabric:build` | `:neoforge:build` |
| `26.2/` | 25 | `:fabric:build` | `:neoforge:build` |

Each loader's output is in `<version>/<loader>/build/libs/`. Install the regular
mod JAR matching your Minecraft version and loader, rather than a `-sources`,
`-javadoc`, or intermediate development JAR. Keep one enabled version of this
mod in the instance's `mods` directory.

### Building multiple versions

For the repository-root aggregate tasks, configure `JAVA_HOME_17`,
`JAVA_HOME_21`, and `JAVA_HOME_25` to your installed JDK directories. The root
build selects the matching variable for each version and falls back to
`JAVA_HOME` when a version-specific variable is absent. Run the root wrapper
with Java 21 or newer.

From the repository root, after configuring those environment variables:

```powershell
$env:JAVA_HOME = $env:JAVA_HOME_21
.\gradlew.bat buildAll --no-daemon
```

```sh
export JAVA_HOME="$JAVA_HOME_21"
./gradlew buildAll --no-daemon
```

`buildAll` builds all four version roots and collects regular mod JARs under
`all-jars/`. Root tasks `build1201`, `build1211`, `build1211NeoForge`, `build2612`,
and `build262` are available for smaller selections.

### Targeted checks

The `scripts/test-*.ps1` runners exercise specific areas without launching
Minecraft. With PowerShell and a suitable JDK configured, for example:

```powershell
.\scripts\test-fullscreen-map.ps1 -VersionRoot 26.2 -JdkHome $env:JAVA_HOME
```

Some runners also need cached build dependencies or a matching game JAR; their
parameters allow those paths to be supplied. See the relevant feature document
and script when selecting a check. A standalone check does not verify actual
GPU rendering or every external-mod combination.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
Project contributions are acknowledged in [CONTRIBUTORS.md](CONTRIBUTORS.md).

## Security

See [.github/SECURITY.md](.github/SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
