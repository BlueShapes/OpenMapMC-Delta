# Multiplayer, cave layers, and markers

OpenMapMC Delta can be installed on the client, the server, or both. The server does
not require other players to install it. Clients can join servers without it.
With OpenMapMC Delta on both sides, the server sends a complete player snapshot once
per second, including XYZ and dimensions. Tracked Minecraft players update every
client tick and override the snapshot. Minimap and fullscreen player markers can
be configured independently of waypoint visibility. Without the server mod,
published positions from compatible map integrations are also available.

## External player positions

All four Minecraft versions support **Map Settings → Integrations → External
player positions** (enabled by default). With JourneyMap or Xaero's Minimap
installed, OpenMap reads their received player positions even when their maps
and overlays have been hidden by the map-mod selector. No extra OpenMap server
installation is needed. The server must already publish positions through a
compatible tracker, such as JourneyMap's expanded radar or Xaero's server/add-on
tracking. Disabling or removing the other mod's JAR also removes that provider;
this integration does not implement its private wire protocol independently.

**BlueMap player positions** in the same settings tab works without any other
client map mod. While connected to the relevant multiplayer server and dimension,
enter its public BlueMap website URL (remove the fragment/query), the exact map
ID, and its relative live-data root (normally `maps`). The latter two values can
be found in that site's `settings.json`. Save once per Minecraft dimension;
OpenMap does not guess a web map's dimension from its name. **Remove** deletes
only the current server/dimension binding. Settings are stored in
`config/openmapdelta/remote-players.json` and are never copied between servers.
The website URL must use HTTP(S), without embedded credentials. The relative
live-data root supports ordinary path components within that website.

BlueMap is polled every three seconds in the background. Empty full responses
remove players immediately; `foreign` players are omitted because their
coordinates belong to another world. Responses and player counts are bounded,
redirects are rejected, each request has a six-second total deadline, and active
requests are canceled when their binding or connection changes. Failed requests
expire their last successful snapshot after 15 seconds. JourneyMap/Xaero stores
are read once per second; their own packet handling determines whether stored
positions remain current. Logout, world changes and disabling this integration
clear its snapshots.

Nearby tracked entities retain priority. An accepted OpenMap server snapshot is
authoritative, including an empty player list, and keeps its existing cave policy.
Otherwise the external priority is JourneyMap, Xaero, then BlueMap for duplicate
UUIDs. The positions feed the existing minimap/fullscreen markers, nearest-player
information, selected-player tracking and local Web map. The usual player
visibility and minimap border-marker settings still apply. No integration can
show a remote position that its server has not published, including players
hidden by that server's privacy settings.

Use matching Minecraft versions and compatible loaders. Minecraft 1.20.1 Forge
uses a separate optional channel from Fabric because their message envelopes
differ. A mixed Forge/Fabric 1.20.1 connection uses tracked-player fallback;
server-wide synchronization requires the matching loader. Newer Fabric and
NeoForge versions share the snapshot format and negotiate support before sending.

## Minimap player labels

Under **Map Settings → Minimap**, **Players inside map** and **Players outside
map** independently choose **Name only**, **Distance only**, **Name and distance**,
or **None** for other players.

| Player position | Default label |
| --- | --- |
| Inside the minimap | None |
| Outside the minimap | Distance only |

The choice uses the actual circular or square map boundary before an off-map
icon is clamped to the border. Changing the border-marker offset does not change
which label setting applies. The settings also apply to selected player targets
and work independently of the general marker-distance toggle. Player icons and
selection highlights remain visible when labels are hidden. Existing player
visibility and border-marker switches still control whether markers are drawn.
Fullscreen, world and Web-map labels keep their existing behavior.

## Nearby entities

**Map Settings → Markers → Show Entities** controls nearby client-loaded
entities on both the minimap and fullscreen map. In 26.2, new configurations
enable this by default; older versions default to off. Existing saved choices
are preserved, so enable this setting if an older configuration has it off.
Mobs use the available face icons. In 26.2, fish and catalog-missing entities
(including mod entities) use cached images from their registered renderer: a safely
identified head when available, otherwise the complete entity. Fish use species
sprites while generation is pending; unavailable renderers use a colored marker.
Non-square images keep their aspect ratio. See [entity icon rendering](entity-icon-rendering.md)
for cache behavior and compatibility limits. Entity icons are smaller than normal map icons, have
no automatic labels, and do not consume the fullscreen waypoint limit or depend
on the waypoint visibility switch.

The icons fade with the absolute height difference from the player:

| Height difference | Opacity |
| --- | --- |
| Less than 4 blocks | 100% |
| 4–7 blocks | 80% |
| 8–11 blocks | 60% |
| 12–15 blocks | 40% |
| 16 blocks or more | 20% |

Only entities already tracked by the client within 64 blocks in three dimensions
can be shown. This feature does not obtain entities from unloaded chunks or add
entity positions to the server or external-map player protocols. Entities outside
the displayed map area are omitted rather than placed at its border.

## Shared waypoints

With OpenMapMC Delta on both the server and client, right-click the fullscreen map
and choose **Create shared waypoint**. A dedicated server or a world published
to LAN enables this action; a LAN host counts even before another player joins.
Each shared point belongs to the authenticated player who created it. Its name
starts with that player's face, including when the creator is offline. Only its
creator or a server administrator may edit or delete it. A LAN world's host is
also an administrator. The server checks these permissions for every change.

Shared points are saved in the world's
`data/odysseymap-shared-waypoints.json`, separate from personal client waypoints.
They survive server restarts and synchronize to players who join later. They
are visible by default. The eye button in the waypoint list hides or restores
an individual shared point for the current player. **Show shared waypoints**
in the map settings controls all shared points, including future additions.
Hiding a point does not delete it or change anyone else's map. Individual
visibility and favorites are stored locally for that player and world.

Servers without OpenMapMC Delta and clients without shared-waypoint support keep
their existing connection behavior. These clients cannot display shared points.
Shared requests have bounded names, icons, coordinates and payloads; the server
limits mutation frequency and stores at most 4096 shared points per world.
A failed save leaves the previous shared data intact and reports an error to
the requesting player.

The existing Notify action shares a location using Minecraft chat. A receiving
player can right-click its point in the fullscreen map and choose Create to save
the supplied name and XYZ as a personal waypoint. Notices remain selectable
while that fullscreen map session is open, including trips through settings,
import, or waypoint editing. Closing the map releases expired notices. Remote
notices are bounded to 64 entries.

The layer slider beside the top-left settings button switches between Surface
and cave Y bands. Bands contain eight blocks by default, with their lower edges
anchored to the dimension's minimum build height. For example, an Overworld
starting at Y -64 offers -64 through -57, then -56 through -49; the final band
may be shorter. The label shows both inclusive edges. The `caveLayerStep`
setting accepts 1 through 64 blocks. Changing it or entering a dimension with
different build-height bounds snaps the selected Y to the new band and refreshes
the active cave cache. Cave sampling finds the floor below a space near the band's
lower edge, including floors more than 64 blocks below it. Columns whose surface
is below the selected band show that actual surface. Sampling uses client-loaded
chunks and a separate cache, and does not request hidden
chunks from the server. The footer reports the
highest known surface Y at the cursor; `?` means that column has not been sampled.
Older cache files remain readable and acquire height data as columns are scanned.

In **Map Settings → Minimap → Minimap terrain layer**, choose **Surface** (the
default), **Follow player Y**, or **Manual**. Automatic mode follows the band
containing the player's feet. When the column above the player's eyes is clear,
automatic mode shows Surface, including at night or in rain. Any non-air block
overhead, including glass, leaves, or fluid, keeps the cave band selected.
Ceiling dimensions such as the Nether keep following the player's Y.
Moving inside a band under cover keeps the same layer.
**Manual cave layer Y** selects a band independently of the player. Both modes
use `caveLayerStep` and the current dimension's height bounds. These settings
are saved as `minimapLayerMode` and `minimapCaveY`; the fullscreen/Web selection
remains independent. Views of the same cave band share their sampled data.
Server cave restrictions also apply to the minimap, while the saved mode is
retained for when caves are allowed again.

In the Nether and other ceiling dimensions, Surface maps actual terrain and
lava below the roof. It also handles openings in the roof and excludes builds
above the logical ceiling. A column needs a space at least two air blocks high
to distinguish an interior surface from a tiny roof pocket. Previously saved
placeholder patterns update as loaded chunks are scanned again; exploration
history is preserved, and distant unvisited columns keep their cached data.

Server policy is generated at `config/openmapdelta/server.json`:

```json
{
  "allowCaveMap": true
}
```

Set `allowCaveMap` to `false` and restart the server to disable OpenMapMC Delta cave
layers for connected clients. Clients return to Surface and the web map suppresses
cave data. Invalid policy files fail closed. A server without this mod does not
send a cave policy. This controls OpenMapMC Delta's feature, rather than other map mods.

Waypoint editing includes an icon picker with search and categories. Flat vanilla
item icons and supported front-facing mob textures are available; blocks and
special model-rendered items such as banners and shields are excluded. Saved
waypoints retain the icon identifier. Incompatible or unavailable identifiers
fall back to the marker dot.

The fullscreen map's top-right grid button toggles and saves chunk-border
visibility. Zoom buttons are hidden by default; enable Show zoom buttons in the
Fullscreen settings tab (`fullscreenShowZoomButtons`) to restore them alongside
the grid button. Mouse-wheel zoom remains available.

In 26.2, fullscreen detail zoom reaches **4×**, with **1× → 2× → 4×** steps.
Your own head is normally about 25% smaller than before. Moving the cursor toward
it shrinks it in stages to one world block wide (at least one GUI pixel when
zoomed out). The selection area shrinks with the head: its center still opens
your player menu, while adjacent ground remains available for placing waypoints.
Moving away restores the ordinary size. Other players keep their existing sizes.

26.2 の全画面マップは **4倍**まで拡大できます。自分の頭は通常約25%小さく、
マウスを近づけると段階的に1ブロック幅まで縮みます（縮小表示時は最低1 GUI pixel）。
選択範囲も頭に合わせて縮むため、頭の中心では自分を選択でき、その隣の地面では
ウェイポイントを作成できます。マウスを離すと元のサイズへ戻ります。
他のプレイヤーのサイズは維持します。

Fullscreen hover highlights use thin font outlines, preserving the fine strokes
of CJK fonts. Bright waypoint colors receive a black halo and dark colors
receive a white halo. Icon frames have both black and white borders so the hover
highlight remains visible over bright terrain and dark cave backgrounds.

Right-click a player, waypoint, temporary waypoint, or empty location in the fullscreen map and
choose Head here to add a navigation destination. Player destinations follow
their current position. Waypoint and temporary-waypoint destinations preserve
the position selected at that moment. The default limit is three simultaneous
destinations (`navigationMaxTargets`, configurable from 1 to 16). Entering the
arrival radius, five meters by default, removes that destination automatically;
set `navigationArrivalDistance` to `0` to disable automatic arrival. Use Stop
navigation to remove a destination manually. Right-click any map point and use
Stop all destinations to release every slot, including targets in another dimension.
An empty location also displays a highlighted temporary-style icon and beacon
in the world. It is session-only and disappears with the navigation target.
In Minecraft 1.20.1, 1.21.1, and 26.2, a selected player also has a world icon and beacon
that follow their latest known position until arrival or cancellation. This
explicit destination remains visible when ordinary player or waypoint markers
are hidden; unselected players do not gain world beacons.

Player location notifications offer Follow selected player, enabled by default.
With it enabled, the receiving point follows that selected player's position;
with it disabled, it keeps the shared XYZ snapshot. When fresh position data is
unavailable, a following point keeps its last known position. As with other
player positions, server-wide updates require support on both sides; otherwise
only client-tracked positions are available. The point's existing lifetime and
fullscreen-session retention still apply.

After choosing notification recipients, choose a message template or enter free
text on the next screen. This step applies to every location notification,
including an empty map location. The chat line copies its coordinates when
clicked. See [context actions](context-actions.md) for templates and the optional,
loaded-chunk-only biome/structure query.

Send message on a player's fullscreen context menu opens a translated private
message entry screen. It wraps the server's native private-message command
(`/msg` or `/w`) and preserves the command's normal permission checks. An offline
player or unavailable command produces a translated error.

## Player icons

Player markers on the minimap and fullscreen map default to each player's skin
face, including the hat layer, and remain upright. Each map's settings tab has
its own Player icon selector (face, triangle, or vanilla pointer) and Rotate
player head switch. The minimap player-size setting applies to other players
as well as yourself; fullscreen player icons share the configured maximum size.
Face and pointer rendering also works in safe render mode. Online player skins
use the tab-list skin when available and a UUID-based default while a skin is
being resolved. Saved icon choices are preserved; the former `showPlayerHead`
setting is imported only when `fullscreenPlayerIcon` is absent.

Vanilla pointers use the white map-player sprite, tinted with the same UUID
fallback color as vanilla's locator bar: the low 24 bits of `UUID.hashCode()`
are converted to HSV and their brightness is set to 0.9. This compact local
calculation requires no locator-bar API and behaves identically on every
supported Minecraft version. Colors are stable for a UUID, though different
players can have the same color. The green item-frame sprite is not used.
The fallback was checked against Mojang's [26.2 client binary](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar).
The [vanilla locator bar](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w17a)
also supports server and team overrides; OpenMapMC Delta's pointer color uses the
UUID fallback independently of those overrides.

When rotation is enabled, tracked players' icons follow their interpolated
facing direction. Heading-up minimaps account for the local player's map
rotation. Server position snapshots do not carry heading information, so icons
for distant, untracked players remain upright; this preserves compatibility
with existing servers. Both face rotation options default to `false`.

## Live server settings (Minecraft 1.20.1, 1.21.1, and 26.2)

Open **Map settings → Integrations → Server settings** on a server with the
updated mod installed. Operators with permission level 2 or higher and the
singleplayer/LAN owner can edit cave-map availability, public Web map URL
announcements, and the world-wide shared waypoint limit. Permissions are checked
on every change and refreshed while connected.

Changes save automatically and take effect without restarting or reconnecting.
Text input waits briefly for typing to stop. The status shows pending, saved,
invalid input, conflict, or a storage failure. A conflicting administrator edit
reloads the current server values to avoid overwriting another admin's work.

`maxSharedWaypoints` in `config/openmapdelta/server.json` defaults to `4096` and
accepts integers from `0` through `4096`. Zero blocks new shared waypoints.
Lowering the limit preserves existing points and allows their editing/deletion.
The cave policy is immediately sent to all compatible clients. A newly enabled
or changed public URL is announced to current participants as well as later joins.

The settings protocol uses new optional channels. Older or unmodded peers can
still connect; existing map-policy and shared-waypoint formats remain unchanged.
Only authorized clients receive the editable values. Corrupt configuration is
preserved and disables editing until repaired. Unknown configuration fields and
grave-block IDs survive successful edits.

## Verification

`test-server-config.ps1` covers bounded settings codecs, live permissions,
concurrent admin conflicts, atomic persistence/rollback, URL announcements,
and shared waypoint limit changes using the real server services with API stubs.
`test-server-config-client.ps1` covers connection isolation, coalesced writes,
rate-limit retry, conflicts, and request timeouts without starting Minecraft.

`test-shared-waypoint-server.ps1` covers the shared request/snapshot codecs,
ownership policy, exact JSON values and preservation of corrupt stores.
`test-shared-waypoints.ps1` covers atomic multi-page assembly, connection resets,
permission updates, and per-world/player preference saving and reloading.
`test-neoforge-shared-waypoint-registration.ps1` checks channel registration;
its `-Native -VersionRoot <version> -JdkHome <jdk> -GradleCommand <gradle>` mode
invokes the actual NeoForge registry after compiling the selected loader and
checks both shared waypoints and optional location-context queries.
`test-web-shared-waypoints.cjs` checks creator faces and label alignment on the
shipped web map scripts without starting a browser.

`test-shared-waypoints-network.cjs 27191 forge exercise <state-file>` exercises
the isolated Forge fixture's real connections: creator identity, permissions,
late joins, offline creators, failed-save rollback and optional channels.
After stopping and restarting that fixture, use `restore` with the same state
file to verify persisted ownership and deletion. This fixture binds loopback,
uses offline mode with compression disabled, and is never a public server.

`test-location-context-protocol.ps1` checks strict context codecs and bounded
structure aggregation, including overlapping starts, full XYZ bounds and
incomplete references. `test-location-context-network.cjs 27191` checks the
isolated Forge fixture's real context replies, exact coordinates/tokens, rate
limiting, unavailable dimensions/chunks, malformed-request recovery and vanilla
peer isolation. `test-player-notices.ps1` also checks message compatibility and
the client context bridge. `test-fullscreen-chrome.ps1` checks normal/F1 viewport
geometry without starting Minecraft.

Standalone scripts cover bounded snapshot codecs and policy resets
(`test-server-state.ps1`), notification actions and lifetime
(`test-context-actions.ps1`), terrain caches (`test-tile-cache.ps1`), viewport
geometry (`test-fullscreen-map.ps1`), and icon selection (`test-marker-icons.ps1`).
`test-player-icons.ps1` verifies vanilla UUID color vectors, 10,000 UUIDs against
an independent RGB/HSV oracle, rotated face corner selection, and navigation
edge placement. `test-minimap-sizing.ps1` checks independent map icon and head
rotation preferences, persistence, malformed values, and legacy preference import.
`test-cave-layers.ps1` verifies cave-band coverage and slider conversion for all
configured step sizes, negative heights, partial bands, and bounded invalid input.

`test-multiplayer.cjs` connects two protocol-763 test clients to an isolated
Minecraft 1.20.1 Forge server. It checks unmodded login, optional-channel
negotiation, both players' XYZ snapshots, and delivery only to subscribed clients.
Use an offline-mode fixture on loopback with compression disabled. This test is
not intended for public or authenticated servers.

Final batch verification compiles all eight loader/version targets and builds
the Forge 1.20.1 distributable. Forge 47.4.0 dedicated-server startup and optional
player synchronization are tested over real network connections. Graphical
Minecraft client interaction and MTR station discovery in an actual MTR world
remain manual checks; reflection fixtures and upstream contracts cover the
station readers without requiring those mods to start.
