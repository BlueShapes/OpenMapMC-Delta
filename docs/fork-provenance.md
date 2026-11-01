# Fork provenance and compatibility identifiers

OpenMapMC Delta is an independently branded fork of [Odyssey Map](https://github.com/MeherBenSalem/Minimap), originally authored by NAIZO. It retains the Apache License 2.0. The upstream copyright notice remains in `NOTICE`; this fork's changes are credited to AoSankaku there and in modified Java source files.

The distribution and repository name remains **OpenMapMC Delta**. The in-game and Web display name is **OpenMap Delta**. The runtime MOD ID and resource namespace are `openmapdelta`, and the Java package/group is `net.aosankaku.openmapdelta`. Release archive names retain the `openmapmcdelta` basename, and each version root keeps its `openmapmcdelta` Gradle project name. Version numbering remains `1.3.0` for this fork point.

Existing player data and optional-server compatibility depend on several identifiers that remain intentionally unchanged. On the first start, OpenMap Delta migrates settings from `config/odysseymap/**` to `config/openmapdelta/**` and moves the tile cache from `config/odysseymap/cache/**` to `<game directory>/openmapdelta/**`. Existing files in the new location win conflicts, missing files are imported, and the original settings tree remains as a backup. A completion marker prevents later starts from restoring files that users remove. If migration encounters an unsafe path or I/O error, startup stops before loading or writing defaults so the old data remains available for retry.

- Client configuration, web-map settings and session key, map-provider selection state, waypoints, station appearance, and related files now use `config/openmapdelta/**` after migration. The settings retain their file names and contents.
- Tile files keep their existing world/dimension directories, identities, and encoding while moving directly under the game-directory `openmapdelta/**` cache root; no extra `cache` segment is added.
- The world identity and shared-waypoint files remain `data/odysseymap-world-id` and `data/odysseymap-shared-waypoints.json`.
- In the 1.20.1 and 1.21.1 Smart Deathpoints builds, server recovery tracking remains in `data/odysseymap-death-recovery.json`. Client deletion receipts remain in `config/openmapdelta/deathpoint-deletions.json`; retain them when migrating waypoint data so manually deleted deaths cannot reappear.
- Existing station icon selections remain `symbol:odysseymap:mtr_station` and `symbol:odysseymap:create_station`; their textures now load from the new `openmapdelta` asset namespace.
- Transit waypoint identity hashes keep the `odysseymap:transit:` seed.
- The local web map keeps the `odyssey_session` cookie and `ODYSSEY_WEB_PASSWORD` environment variable, including support for existing browser sessions and server setup.
- The browser chunk-grid preference remains `odysseymap.chunkGridVisible`.
- The map-provider selection state keeps the legacy `odysseymap` provider key while runtime detection checks `openmapdelta`.
- Optional-server channel namespaces, chat payloads such as `[Odyssey Map:<uuid>]` and `[Odyssey Map ACK:<uuid>]` (including the existing 54-character ACK form), and shared-waypoint feedback translation keys `odysseymap.shared.*` remain compatible with existing Odyssey Map servers and clients.
- Other translation and keybinding identifiers under `odysseymap` stay stable so user key assignments and saved translations continue to work.

These identifiers are compatibility details, not the current game/Web display name. New in-game and Web text uses OpenMap Delta branding; distribution and provenance materials continue to use OpenMapMC Delta.
