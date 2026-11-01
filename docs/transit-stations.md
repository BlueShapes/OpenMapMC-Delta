# Automatic transit station waypoints

The Integrations settings tab enables Create (including Steam 'n' Rails) and
Minecraft Transit Railway independently. The matching `config.json` options are
`createStationWaypoints` and `mtrStationWaypoints`, both enabled by default.
The tab also offers Create station grouping radius choices of 0, 10, 25, 50,
75, 100, 150, 200, 500, and 1000 blocks. `createStationGroupingRadius` defaults
to 50 and accepts any integer from 0 through 1,000,000 in JSON. A value of 0
disables grouping while preserving upstream station-ID deduplication. Changing
the setting refreshes the station snapshot on the next tick.

Station markers refresh every 5 seconds from the transit mod's synchronized
client data. They are session-only and do not alter saved personal waypoints.
Create station blocks with the same exact name and canonical dimension string
are grouped into connected components. Two blocks are connected when their
three-dimensional Euclidean distance is no greater than the configured grouping
radius, including the boundary; connections are transitive, so a chain of nearby
stations forms one component. Each component has one waypoint. MTR station
areas also have one waypoint, even when the area contains several eligible
platforms; the Create radius does not affect MTR grouping.

Each waypoint is placed at the point minimizing the sum of Euclidean distances
to its member Create station blocks or eligible MTR platform rail midpoints. The
geometric median is rounded to integer block coordinates, so the displayed point
has block precision. Marker identities use the lexicographically smallest
upstream Create station UUID in the component or the MTR station-area ID, scoped
by source and dimension. Enumeration order, block movement, or a shared rename
does not change the Create marker ID while its representative station remains a
member. Adding a member with a smaller UUID, removing the representative, or a
split or merge can change the group ID even when some other members remain.
Separate remote Create components and different MTR station areas keep separate
markers even when their names match.

Disconnecting or changing client levels clears the snapshot. A Create waypoint
is removed when its component disappears; an MTR waypoint is removed when the
area has no eligible platforms on the next refresh.

Create 0.5.1 and 6 use the client railway graph's `STATION` edge points. A point
must have a station block position and connected nodes in a matching dimension.
For loaded blocks, the station block entity, station identity, and linked track
are also validated. Unloaded stations rely on Create's synchronized graph and
do not force chunk loads. Steam 'n' Rails extends these Create station objects,
so its stations use the same integration and setting.

MTR 3 uses `mtr.client.ClientData`: a platform needs its cached station-area
membership, a valid saved rail, and a midpoint inside the station area. MTR 4
uses `MinecraftClientData.getInstance()`: a platform needs an assigned `area`,
a valid saved platform rail, and a midpoint within that area. Eligible platform
midpoints are grouped by station-area ID before the center is calculated. The
area name labels its waypoint. Empty station rectangles and unassigned, invalid,
or out-of-area platforms do not contribute to the center. Only data available in
MTR's active dimension is displayed.

MTR display names are split at the first `||`; content after that separator is
metadata and is hidden. Names before it are split at `|`, trimmed, and deduplicated.
The first name is shown as the main label and remaining language aliases appear in
full-width parentheses separated by `・`, for example `上南|Kaminami||private`
is displayed as `上南（Kaminami）`.

Station API reflection lookups are cached per upstream class and argument types.
The reader keeps only the latest immutable MTR center snapshot, and the catalog
keeps only the latest immutable grouping result; label, membership, position,
dimension, or grouping-radius changes invalidate the matching snapshot. The
waypoint manager constructs per-dimension marker lists only at station refresh
time and selects the currently enabled source lists when asked to render.

MTR 4.0.5 validates a saved rail with `isInvalidSavedRail(Data)`. The reader passes
the synchronized client data to this method, and supports the newer no-argument
form when the data-argument method is absent. A failed validation is still
propagated to the integration's error handler rather than bypassed.

The integrations have no required dependency on either mod. An incompatible
public API is skipped and logged once, without guessing names or station
positions. Source contracts were checked against these upstream sources:

- [Create 6.0.8 StationBlockEntity](https://github.com/Creators-of-Create/Create/blob/mc1.20.1-6.0.8/src/main/java/com/simibubi/create/content/trains/station/StationBlockEntity.java)
- [Create 0.5.1 GlobalStation](https://github.com/Creators-of-Create/Create/blob/mc1.20.1/0.5.1/src/main/java/com/simibubi/create/content/trains/station/GlobalStation.java)
- [Steam 'n' Rails station integration](https://github.com/Layers-of-Railways/Railway/blob/1.20/dev/common/src/main/java/com/railwayteam/railways/mixin/MixinStationBlockEntity.java)
- [MTR 3 ClientData](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/3.x.x/common/src/main/java/mtr/client/ClientData.java)
- [MTR 3 DataCache](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/3.x.x/common/src/main/java/mtr/data/DataCache.java)
- [MTR 4.0.6 MinecraftClientData](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/4.0.6/fabric/src/main/java/org/mtr/mod/client/MinecraftClientData.java)
- [MTR core SavedRailBase](https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/blob/master/src/main/java/org/mtr/core/data/SavedRailBase.java)
- [MTR core identities](https://github.com/Minecraft-Transit-Railway/Transport-Simulation-Core/blob/master/src/main/java/org/mtr/core/data/NameColorDataBase.java)

Run `scripts/test-transit-stations.ps1` for dependency-free checks of the public
API readers, platform validation, station-area aggregation, geometric centers,
duplicate platform IDs, and stable marker IDs. Pass `-MtrJar <path-to-MTR-4-jar>`
to also check the real core data model in that JAR without starting Minecraft:
multiple platforms and their midpoint centers, station-area identity, renaming,
reassignment, missing rails, station bounds changes, and deletion.

Run `scripts/benchmark-transit-stations.ps1` for a headless warm benchmark of
100, 1,000, and 10,000 same-name Create stations in sparse, dense, and chain
layouts, plus MTR stations with the same number of eligible platforms. Create
timings include reflection-based station reading and catalog grouping; MTR timings
include reflection-based platform reading and station-center calculation. The
unchanged rows replay the same snapshot and measure the cache-hit path. It reports
median and p95 time for each case and does not start Minecraft.

One Java 17 run with 3 warmups and 11 measured samples produced these results
(milliseconds, `median / p95`; timings vary by machine):

| Detection path | Count | Changed snapshot | Unchanged snapshot |
| --- | ---: | ---: | ---: |
| Create sparse | 100 | 0.89 / 3.39 | 0.83 / 1.28 |
| Create sparse | 1,000 | 3.85 / 6.14 | 0.81 / 1.09 |
| Create sparse | 10,000 | 14.61 / 21.04 | 2.61 / 3.09 |
| Create dense | 100 | 0.87 / 1.17 | — |
| Create dense | 1,000 | 1.57 / 1.94 | — |
| Create dense | 10,000 | 4.92 / 6.93 | — |
| Create chain | 100 | 0.88 / 1.70 | — |
| Create chain | 1,000 | 3.49 / 4.32 | — |
| Create chain | 10,000 | 9.89 / 15.21 | — |
| MTR platforms | 100 | 1.19 / 1.84 | 0.42 / 1.45 |
| MTR platforms | 1,000 | 1.76 / 3.61 | 1.85 / 2.64 |
| MTR platforms | 10,000 | 5.11 / 6.06 | 3.36 / 4.94 |

Create benchmarks use generated Create-compatible public API fixtures. MTR
benchmarks use generated MTR-compatible fixtures; the separate installed-JAR
test verifies behavior against MTR 4.0.5 without timing or starting Minecraft.
