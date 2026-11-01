# Server event log contract

Shared waypoint events use the existing `OpenMapDelta` server logger at
INFO level. The message body is the literal prefix `OPENMAPMC_EVENT ` followed by
one compact JSON object. The logging backend may add its usual timestamp,
thread, level and logger decoration before the message body.

The following message was captured from a headless test of the actual server
service and store (test player identity; not a live Minecraft server log):

```text
OPENMAPMC_EVENT {"schema_version":1,"event":"shared_waypoint_created","waypoint_id":"2572c5e1-964b-421f-9ae7-314eec1cf7ee","creator_uuid":"26fed6e8-78dd-4d84-b6d5-16aa15f13016","creator_name":"Alice","name":"拠点","dimension":"minecraft:overworld","x":123,"y":64,"z":-456}
```

| Field | JSON type | Meaning |
| --- | --- | --- |
| `schema_version` | integer | `1` |
| `event` | string | `shared_waypoint_created`, `shared_waypoint_updated`, or `shared_waypoint_deleted` |
| `waypoint_id` | string | UUID of the shared waypoint |
| `creator_uuid` | string | Saved owner's UUID (also the creating player for CREATE) |
| `creator_name` | string | Saved owner's name |
| `name` | string | Saved waypoint label |
| `dimension` | string | Resource identifier such as `minecraft:overworld` |
| `x`, `y`, `z` | number | Finite saved coordinates; currently integer block coordinates |
| `actor_uuid` | string | UPDATE/DELETE only: UUID of the authenticated player performing the operation |
| `actor_name` | string | UPDATE/DELETE only: Performing player's server-side name |

Each event is emitted once after its operation has successfully saved the store
and published the new server state. CREATE keeps its existing ten fields without
adding actor fields. UPDATE describes the successfully saved updated waypoint.
DELETE describes the last saved waypoint immediately before deletion, and is
emitted only after deletion has been persisted. An administrator editing or
deleting another player's waypoint appears in `actor_*`; `creator_*` continues
to identify the saved owner.

Failed requests, permission denials, rate limiting, invalid data and storage
failures emit no event. Reading existing data on restart, sending snapshots,
and creating personal waypoints also emit no event.

The UPDATE and DELETE examples below were captured from separate test
operations in the 1.21.1 headless fixture. Alice owns both waypoints; Admin
performs the operations. The UPDATE includes its new saved dimension and
coordinates; the DELETE retains its waypoint's last saved content.

```text
OPENMAPMC_EVENT {"schema_version":1,"event":"shared_waypoint_updated","waypoint_id":"795b4a04-16d4-4bab-80e7-bc09becd8e01","creator_uuid":"df365d2f-2b59-4f73-a982-c37049e801ce","creator_name":"Alice","name":"admin edit","dimension":"minecraft:the_end","x":101,"y":70,"z":-202,"actor_uuid":"7f854c4c-2e80-43ff-b088-d3770c0b344e","actor_name":"Admin"}
OPENMAPMC_EVENT {"schema_version":1,"event":"shared_waypoint_deleted","waypoint_id":"0ef7a200-5c08-406b-b13f-15b114ac4f19","creator_uuid":"df365d2f-2b59-4f73-a982-c37049e801ce","creator_name":"Alice","name":"second point","dimension":"minecraft:overworld","x":7,"y":68,"z":9,"actor_uuid":"7f854c4c-2e80-43ff-b088-d3770c0b344e","actor_name":"Admin"}
```

Run `scripts/test-shared-waypoint-events.ps1 -VersionRoot <version> -JdkHome <jdk>`
to capture synthetic samples in a local test directory.

Strings are escaped by the JSON library. Existing validation rejects CR/LF and
other control characters in names. Quotes, backslashes and Unicode line
separators cannot introduce a second log record. No credentials or secrets are
included. Consumers should parse JSON after the prefix, match the event and
schema version, and tolerate additional fields. The shared packet and world
store formats are unchanged.

The implementation and verification status for each Minecraft version is
recorded under F43 in [the porting register](fork-change-register.md).
