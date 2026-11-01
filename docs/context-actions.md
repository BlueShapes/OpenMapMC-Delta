# Fullscreen map context actions

The Recenter button appears at the map area's bottom-right only while the map's
center differs from the local player's X/Z position. Clicking it centers the map
on the player and hides the button immediately. It follows the map edge when the
waypoint sidebar is opened or closed and when the screen is resized.

Right-click an empty map location to create a waypoint, create a temporary
waypoint, head there, teleport, or notify players. An empty-location destination
has a highlighted, temporary-style world icon and beacon until navigation stops
or the arrival radius is reached. It is never saved as a permanent waypoint.
Teleport appears only when the server's
permission-filtered command tree contains `tp` or `teleport`; the server checks
permission again when the command executes.

The popup header displays the selected target's X, Y, and Z coordinates. When
right-clicking a waypoint, it displays that waypoint's coordinates. The header
is informational; clicking it does not activate an action.

Right-click a visible permanent or temporary waypoint icon or label to teleport,
edit, or delete that waypoint. Hidden or culled waypoints cannot intercept map
clicks. Escape or an outside click dismisses the popup. Editing and recipient
selection preserve the map's pan and zoom.

Temporary waypoints use a three-dimensional five-block arrival sphere around
the selected block center (X/Z + 0.5, selected Y). Creation inside the sphere
requires leaving it before returning. Changing the target through the editor
reinitializes that arrival rule. These markers are session-only and are not
written to waypoint storage.

The recipient screen supports searching names, skin portraits, individual
checkboxes, multiple recipients, and selecting every currently online player.
The Duration button cycles through 5, 15, and 30 seconds, then 1, 2, 5, and
10 minutes. The default is 15 seconds; the choice survives screen resizing.
Players whose entities the client knows are sorted by distance to the local
player. Other online players, including those outside the entity tracking range
or in another dimension, appear afterward with “Distance unknown.” Selecting
all includes those other players and is independent of the search filter. The
sender is always notified implicitly and is excluded from the list, its counts,
search results, and selection controls. Clearing the selection or selecting no
other players sends a local-only notification to the sender.

**Next** opens the message screen after choosing recipients for any map target.
Choose a template or enter your own message; templates remain editable. Fixed
templates cover meeting up, asking someone to come, a possible base, a cave,
building and trading. A looked-at block adds its block name. Biome and structure
templates use the location being shared, not necessarily the sender's position.
Each applicable structure has its own choice. Incoming context results leave
the draft intact, and Back preserves the recipients, duration and follow option.

Structure information requires a compatible OpenMapMC Delta server. The optional
query checks only loaded chunks and never generates or force-loads terrain.
Missing references or bounded-query limits are shown as incomplete information;
known structure choices remain available. Clients on servers without this
support keep the fixed/block templates and can type a message freely. A biome
template is also available when that location is in a chunk already loaded by
the client in its current dimension.

The editor shows the remaining space in the bounded notification payload.
Messages must be a single line and cannot contain control or formatting codes.
Overlong messages disable Send instead of being truncated. The sender's and
recipient's yellow notification lines contain the message; clicking a line
copies the displayed `X Y Z` coordinates to the clipboard.

After a notification is accepted, the sender sees exactly one notification
marker. It uses the same icon and sender-name label as the recipient's marker on
the maps and in
the world, with the usual waypoint label visibility rules. The sender's
selected-duration timer starts when the notification is queued; a recipient's
timer starts on receipt. No private message is sent to the sender. A rejected
notification creates no marker, including when a remote batch exceeds the
pending queue's capacity.

Location sharing works without a server-side OpenMapMC Delta installation. It uses
vanilla private messages (`msg`, `tell`, or `w`). Both clients need this feature
to show notification markers; recipients without it still receive readable
coordinates. Plugins which replace private player messages with system messages
or change their chat type are not supported. The vanilla private message remains
in chat history; only the location marker disappears after the selected duration,
measured from receipt. Its world beacon fades smoothly in and out over a
two-second cycle. Its point and label do not pulse. All three world elements
also respect the local player's waypoint proximity fade.

Default 15-second notices with an empty message retain the original chat payload format. Other
durations append ` D:<seconds>s` and require an updated recipient to display
the marker. A nonempty message appends ` M:<message>` and requires an updated
recipient for marker decoding. Updated clients accept older notices with the
15-second default and no message.

Press F1 in the fullscreen map to hide its header, buttons, waypoint list and
coordinate footer and fill the entire screen with the map. Entering the map
while F1 is already enabled uses the same layout. Dragging and wheel zoom still
work. Press F1 again to restore the controls; pan and zoom are retained. The F1
state also remains active when returning to the world.

Receivers process only private player messages already accepted by Minecraft.
Sender identity comes from the accepted player message and its matching profile,
not the payload. Payloads have a 220-character limit, canonical UUID, valid
resource identifier, integral coordinates inside Minecraft's horizontal world
bounds, an advertised server dimension, and valid height when that dimension is
currently loaded. Optional durations must be integral seconds from 5 through 600.
Duplicate notices do not restart the expiry timer. A notice
never moves an open fullscreen map to coordinates in another dimension.

Broadcasts resolve recipients by UUID at delivery time, skip disconnected
players, and send at most once per 40 observed server game ticks and 1.1 seconds.
A backwards server clock waits for 40 new ticks. The pending queue is limited to
4096 messages; an overflowing selection is rejected as a whole, with feedback.
Logout clears the queue and all temporary/notification markers.

Run the small dependency-free regression check with:

```powershell
.\scripts\test-context-actions.ps1 -VersionRoot 1.20.1 -JdkHome 'C:\Program Files\Java\jdk-17'
```

Minecraft GUI behavior and Forge production refmap/JAR registration must also
be checked in the final integrated build. World-render integration can include
temporary and notification markers through
`MarkerManager.getRenderableWaypointsInDimension` and
`MarkerType.isWorldWaypoint`, while leaving editable persistent sidebar queries
and saved waypoint data unchanged.
