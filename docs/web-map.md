# Web map

While a world is open, the client hosts a read-only map at `http://localhost:8901`.
It displays terrain explored by this client, the active surface/cave layer,
waypoints, and known player positions. A server with OpenMapMC Delta supplies distant
players; otherwise positions are limited to players tracked by Minecraft.
The host stops when leaving the world or closing the client. Another service on
the chosen port is preserved, and a bind failure is reported in the game log.

After hosting starts successfully, each world connection receives one clickable
local web map URL in chat. Dimension changes, respawning, and configuration
reloads do not repeat the message. Reconnecting permits another announcement.
Wildcard listeners advertise localhost, and IPv6 addresses use URL brackets.

The game server can also announce a shared, operator-configured public URL on
each player's login. This uses normal chat, including for unmodded players. Add
these fields to the server's `config/openmapdelta/server.json` and restart it:

```json
{
  "allowCaveMap": true,
  "webMapEnabled": true,
  "webMapUrl": "https://map.example.com/"
}
```

The shared announcement defaults to disabled. Supply the URL of the web map
service operated for that server; the setting controls its login announcement.
An empty, malformed, non-HTTP(S), or credential-bearing URL is not announced.
Local hosting remains controlled by the separate `webmap.json` settings below.

In Minecraft 1.20.1, 1.21.1, and 26.2, **Map settings → Integrations → Server settings**
also exposes the public announcement switch and URL to server operators
(permission level 2+) and the singleplayer/LAN owner. Edits save automatically
and apply without restarting. Enabling or changing the URL immediately sends the
new link to current players. These controls configure the server's URL notice;
the client HTTP listener and authentication continue to use `webmap.json`.

Click a player icon or a row in the searchable player list to see XYZ and the
dimension. The list can be collapsed. Drag to pan, scroll or use +/− to zoom,
up to 16×, and use Recenter to return to the local player's location. Downscaled
terrain uses image filtering and smaller markers rather than oversized dots.
Chunk boundaries follow the world's 16-block grid and use two-pixel lines.
The bottom-right Show/Hide grid button toggles them; this preference is saved
in the browser when local storage is available.
Player markers use their Minecraft skin face and hat layer. Waypoints display
their selected station symbol, mob sprite, or rendered item model; an unavailable
icon falls back to the waypoint's colored marker.

Configuration is generated at `config/openmapdelta/webmap.json`:

```json
{
  "enabled": true,
  "bindAddress": "127.0.0.1",
  "port": 8901,
  "requirePassword": false,
  "password": ""
}
```

To require a password, set `requirePassword` to `true` and configure `password`,
or supply the `ODYSSEY_WEB_PASSWORD` environment variable. An empty required
password prevents hosting. Local configuration changes are checked once per
second. Set `enabled` to `false` to stop hosting.

Authentication covers terrain, player, and icon APIs. Sessions have no server-side
expiry and use a persistent HttpOnly, SameSite cookie that is renewed while
viewing. The browser can still clear cookies according to its own policies.
`webmap-session.key` retains the signing key across restarts. Changing the
password or removing that key revokes previous sessions. Keep both config and
key private. The default listener is local only; exposing it to a network
requires changing `bindAddress`. Use a password and a HTTPS reverse proxy when
making it reachable beyond the computer, since the built-in endpoint uses HTTP.

The map bundles its scripts and styles and contacts no external map service.
Game data is copied on the client thread before HTTP workers access it. Cave
data is suppressed when the server disables cave maps.
Icon images are bounded immutable snapshots identified by opaque keys; HTTP
requests cannot select arbitrary files, textures, item IDs, or remote URLs.

Run real HTTP/authentication checks without starting Minecraft:

```powershell
.\scripts\test-web-map.ps1 -VersionRoot 1.20.1 -JdkHome $env:JAVA_HOME
```

`test-web-map-browser.cjs` additionally runs headless interaction checks with
Playwright against seeded data. Its fixture and browser are stopped afterwards.
Compile with `test-web-map.ps1 -CompileOnly`, put the returned classpath in
`ODYSSEY_WEB_TEST_CLASSPATH`, and set `JAVA_HOME` and `ODYSSEY_PLAYWRIGHT` as needed.
For an existing browser binary, set `ODYSSEY_BROWSER_EXECUTABLE`.

After compiling the 1.20.1 Forge implementation, `test-web-capture-state.ps1`
checks item capture using a hidden GLFW context and Minecraft's render-target
classes. It verifies captured pixels and restoration of framebuffer, viewport,
texture, shader, scissor, mask, and blend state on success and renderer failure.
It uses a stub item renderer and does not start the Minecraft client.
