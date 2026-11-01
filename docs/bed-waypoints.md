# Last sleep waypoint

The last qualifying sleep is saved in `config/openmapdelta/waypoints.json`, scoped
to the current world or server. Returning to that world restores the waypoint.
Sleeping in another dimension moves the world's existing sleep waypoint rather
than creating a second one. Other worlds' waypoints are retained.

The waypoint uses a red bed item icon and stores the bed's exact height. It appears
in the waypoint list and supports the usual visibility, favorite, name, color,
icon, category and coordinate controls. Those preferences survive subsequent
sleep and reconnects. The **Show Beds** setting controls its visibility separately
from ordinary waypoints. World icons and beacons use the ordinary world waypoint
settings. Hiding beds does not stop recording them.

**Settings → Markers → Record sleep without setting spawn** enables recording
sleeping bags and other beds that do not establish a respawn point. It defaults
to **off** (`recordNonSpawnSleep: false`). A sleep that does not qualify leaves
the previous waypoint unchanged. The Japanese setting is
**スポーンを設定しない睡眠も記録**.

Tracking observes successful sleeping state on every loader, so unsuccessful bed
interactions do not create a waypoint. Singleplayer checks the actual server
player's respawn point. On remote servers, Minecraft does not synchronize that
personal point; tracking uses the standard spawn confirmation and vanilla bed
rules. Modded beds without a standard confirmation are treated conservatively
and require the opt-in setting. No server installation is required.

## Regression evidence

The user journeys were derived from the reported loss after server re-entry,
missing world display, generic icon, and unwanted non-spawn sleep records.

`scripts/test-sleep-points.ps1` exercises notification ordering, default skipping,
opt-in recording, waking before confirmation, duplicate notifications, stale
confirmation expiry, session reset, and changes of bed/dimension. It also runs
the waypoint metadata tests, which check persistence of BED type, height, world
scope, icon, visibility and appearance, plus editing of hidden beds.

The initial RED evidence was captured before production edits:

- `test-waypoint-metadata.ps1` compiled and failed at
  `bed gets a distinct default icon`.
- `test-sleep-points.ps1` compiled the new test and failed because the required
  `SleepPointSession` implementation did not yet exist.
- Commit `a615f57` preserves those reproducer tests.

Additional RED/GREEN tests caught icon clearing, unrelated recent anchor
notifications, and alternating anchor responses. The interaction evidence tests
check charging, empty and exploding anchors, version-specific hand rules,
unchanged confirmed spawns, bounded queues, expiry, and delayed replies.

Final checks passed:

- `scripts/test-sleep-points.ps1 -VersionRoot 1.20.1` (sleep/session/evidence and
  waypoint metadata tests).
- `scripts/test-waypoint-metadata.ps1` for all four version roots, including
  the 1.21.1 Smart Deathpoint metadata/deletion checks.
- `scripts/test-locales.ps1` (64 catalogs).
- 1.20.1 `:forge:build :fabric:compileJava`.
- 1.21.1 `:common:compileJava :fabric:compileJava :neoforge:compileJava`.
- 26.1.2 and 26.2 `:common:compileJava`.

The pure sleep session/evidence classes are identical across all four roots.
The latest evidence correction was compiled by the Java 17 standalone test and
included in the final Forge rebuild. Existing newer-version Minecraft API
compilation results remain valid. The 1.21.1 Fabric compile has an existing
`ThreadDeath` removal warning in `ControllerNavigationBridge`.

These standalone checks do not launch Minecraft; live server/mod compatibility
and actual rendered pixels are outside their coverage. No percentage coverage
tool is configured for this standalone Java test runner.
