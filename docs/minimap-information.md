# Minimap information and markers

Open **Map Settings → Minimap → Minimap information** to select each of the six
slots. Wide entries (coordinates, IDs, names and nearest-player/waypoint details)
are available only above or below the map. The four corner slots accept compact
values. Search the translated choices or their English identifiers; select
**None** to leave a slot empty.

The defaults are biome name above, full XYZ coordinates below, a 24-hour game
clock at the lower right, and elapsed game days at the lower left. The upper
corners start empty. Information uses white text on a dark backing and stays
within the GUI viewport. Wide entries can extend beyond the terrain map to keep
names and coordinates readable. **Hide in screens** hides the map and all its
information, including coordinates, in game menus and other screens. Chat keeps
the map and information visible. Inventory/container screens hide both.
**Show coordinates** controls coordinate entries.

Advancement and other toast notifications appear in front of the minimap,
including its markers and information panels. Native call order and depth or
strata were inspected for Minecraft 1.20.1, 1.21.1, 26.1.2 and 26.2; see the
version-specific evidence. Only 26.2
needed a minimap hook change. On 26.2 the fullscreen map keeps its existing
position in front of notifications. No setting change is needed.

On Minecraft 1.21.1, the four corner panels use a common row height and equal
upper/lower insets. Text is centered vertically inside each backing. In
**Map Settings → Minimap → Corner text alignment**, choose **Align to map edges**
(the default) or **Center in each column**. The second option gives the left
and right pairs their own fixed centers, so upper/lower labels stay centered
together as their text changes. Changes are saved immediately. The central
top/bottom slots retain their placement. Text origins and backing edges align
to physical pixels; the existing minimum text size and ellipsis for long
entries still apply.

**Align to map edges** uses the actual minimap's left and right edges, with
equal inward padding for both sides, even when the information frame is wider
than the map. Corner labels are shortened to fit their half of the map; a label
is omitted if even an ellipsis cannot fit. **Center in each column** continues
to use the information frame's column centers.

Game-time choices use **daytime**, including the day counter. The ordinary
12/24-hour clock follows Minecraft's 06:00 sunrise offset. Tick counts and
converted durations use daytime modulo 24,000. **Seconds per tick** controls
the custom minutes:seconds and seconds choices (default `0.05`). It does not
change Minecraft's tick rate. **Speed average (seconds)** selects the horizontal
movement averaging window (default five real seconds); teleports and dimension
changes start a new motion sample. FPS uses a five-second average. Session time
continues across dimension changes and resets when leaving the world/server.

TPS uses integrated-server timing in singleplayer and an explicitly marked
estimate from vanilla server time packets in multiplayer. Missing or stale data
shows `—`. Latency converts the connection's round-trip ping to simulation ticks
(50 ms on 1.20.1; the configured tick rate on newer versions).
RAM is JVM used heap divided by maximum heap. Free inventory slots count the
36 main/hotbar slots, excluding armor and offhand. Sleep availability describes
the time and dimension rules; a particular bed can still be obstructed or have
monsters nearby. Nearest players use same-dimension loaded players and Odyssey
Map's optional server player snapshots; unloaded players require server support.

**Minimap → Local player icon** defaults to **Vanilla green pointer**. The pointer
and **Triangle** turn with the player's facing direction. **Player head** stays
upright unless **Rotate player head** is enabled. All three use **Local player
icon scale**, which defaults to `0.6`; **Waypoint icon/name scale** defaults to
`0.65`. **Markers → Nearest waypoint names** defaults to five
ordinary waypoints, ranked by horizontal distance. Their icons remain visible.
Destinations and location notifications always show emphasized names and icons,
and do not consume the ordinary name limit. Choose **All** (`minimapLabelLimit:
-1`) for unlimited names, or zero for icons only on ordinary waypoints.

**Minimap → Border style** selects a solid or dotted border. The default is solid
gray (`#FF808080`). **Performance → Border color** accepts `#RRGGBB` or
`#AARRGGBB`; six-digit colors are opaque. Thickness remains independently
configurable. World destination labels use high-contrast outlines; beacon
visibility follows the beam's full vertical span when looking up or down.

Waypoint labels stay close to their points rather than moving away to avoid
player faces or other icons. Labels draw in front of those icons, with a black
background at 50% opacity. Distant groups retain their count and stacked names.
World-view group names are ordered from bottom to top by nearest distance by
default; **Markers → Cluster names, bottom to top** also offers farthest-first
and name order. This setting changes the rows, not the group's chosen icon.
