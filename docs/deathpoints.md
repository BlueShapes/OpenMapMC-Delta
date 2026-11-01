# Smart Deathpoints (Minecraft 1.20.1 / 1.21.1 / 26.2)

1.20.1 の Fabric / Forge と、1.21.1・26.2 の Fabric / NeoForge 版で利用できます。マップ設定の「死亡地点」
タブから有効化、残り時間、消失後の保持時間などを変更できます。
クライアント単独でも動作しますが、サーバーにも導入すると keepInventory、
アイテムの寿命、他プレイヤーによる回収を正確に扱えます。

This feature is integrated in Minecraft 1.20.1 Fabric/Forge and Minecraft 1.21.1 / 26.2 Fabric/NeoForge.
The client supports local death tracking without the server mod. Installing the
mod on the server additionally enables authoritative inventory rules, item
lifetimes and recovery reports, sent only to the owner of each death.

Deathpoints and smart deathpoints start enabled. Open **Map Settings → Deaths**
to change them. Smart deathpoints require deathpoints to be enabled, and create
one deathpoint for each death. Turning smart deathpoints off creates ordinary
red deathpoints instead and makes existing smart points ordinary red points.

Deathpoints save the actual death coordinates, dimension, world/server and player
identity. Switching accounts does not expose another player's deathpoints.
They can be renamed or manually deleted in the waypoint list or map context menu.
Renaming preserves the timer. Manual deletion is permanent and also destroys
that point's recovery timer; the restore button cannot restore a manually deleted
point. Deathpoints have a red cross with a black outline and an individual world
beacon. They are excluded from numbered waypoint clusters on the minimap and in
the world. Beam opacity pulses between 25% and 100% by default.

Black retention uses a slider from 0 through 86,400 seconds; 0 means forever.
Beam minimum and maximum opacity use 0–100% sliders. Arrow keys adjust retention
by one second or opacity by one percentage point. Opening the screen preserves
saved custom values, and the sliders retain their explanatory tooltips.

Smart deathpoints show the remaining availability of death items beside their
name. Vanilla drops normally last five minutes. With server support, the point
turns black once tracked loot is confirmed gone. It disappears after another five minutes by
default. Set the black retention time to `0` to retain black points indefinitely.
With server support, timers sample each item's actual age and its Forge/NeoForge
lifespan, including lifetime extensions. Unloaded chunks pause the last known
remaining time. Reaching an estimated deadline alone never proves item loss:
the server checks that no tracked loot remains. Without server support, the
five-minute world-time timer remains an approximation: it turns the point black
after 6,000 world ticks even if the death chunk is unloaded. The client cannot
know those unloaded items' actual age or disappearance. Pausing the world pauses
both timers.
If the server verifies available loot again, a still-existing expired point returns to
red and its old black retention is cleared. Deleted points remain permanently
deleted regardless of later server reports.

After an aerial death, smart deathpoints fall with item gravity and stop on the
actual block shape, including slabs and stairs. The world icon interpolates
between ticks. Observed death items supply their real position, including their
horizontal scatter and water movement; server support follows a tracked death
item even after the owner respawns elsewhere. Without an observed item, prediction
stops at fluid surfaces and pauses at unloaded chunks. It never loads chunks.
Ordinary deathpoints remain fixed. The original death coordinates are preserved
for identifying the death; moving or landing does not reset the countdown,
rename the point, or undo deletion. Recovery uses the moving/landed location.
Recognized falling Corpse entities are followed too. Corpses already present
before this death are excluded. Recovery remembers an observed static grave
block's exact location until its loaded block is confirmed gone, even if
accompanying drops move away.

When the owner approaches a smart point within 16 blocks and the observed drops
and grave are gone, the client posts a chat message and removes the point. The
client waits until the surrounding chunks are loaded and observes drops or a grave
before assuming recovery. It never treats an unloaded area as empty.
For a known keepInventory death with no drops (including Creative `/kill`), a
visible or restored smart point also disappears when its owner returns to the
loaded, empty location. The same five-second grace and proximity checks apply.
Actual items or a grave still prevent removal. A hidden keepInventory cache is
never consumed by this approach check; it remains available for restoration.

Grave mods and keepInventory normally suppress the countdown. Actual death drops
start it even in those cases. With smart deathpoints enabled, keepInventory deaths
are hidden by default, but the last hidden death is cached for this world and can
be restored with **Restore last deathpoint**. This is a one-time action; the button
becomes unavailable after restoration and a chat message confirms it. Cached and
restored keepInventory points stay at the original death coordinates until actual
death items or a corpse are observed. Old no-loot predictions are corrected to
the original coordinates. The corresponding
toggle enables creation in keepInventory worlds. On a server without OpenMap Delta,
the client infers keepInventory from retained inventory because that game rule is
not sent to ordinary clients. A death with no items and zero XP provides no
reliable evidence, so exact detection in that case requires server support.

Smart deathpoints skip insignificant deaths: every item must be easy to obtain,
the total count must be less than 64, and the experience level at death must be
10 or less. Any other item or level 11 creates a point. Decorated items, including
dyed leather armor and enchanted or named equipment, count as valuable.

The default easy-item list includes log, plank, sapling, dirt and sand item tags,
sticks, wheat seeds, gravel, leaf litter, roots, wooden tools and undyed leather
armor. Edit the comma-separated item IDs and `#item_tag` values in the Deaths tab
or `config/openmapdelta/config.json` (`smartDeathpointCheapItems`). IDs absent in
older Minecraft versions are harmless. An empty list makes every nonempty stack
valuable.

## Server support

When OpenMap Delta is also installed on the server, the server reports the true
keepInventory rule and death inventory and follows death drops through partial
pickups and item merging. When other players collect all tracked drops, the owner
receives their names and the point becomes black. Ordinary deathpoints remain
red. Without server support, the client cannot attribute pickups to other players.
Only the owner receives these reports. Tracking is saved with the server world.

Grave detection recognizes the `openmapdelta:graves` and legacy `odysseymap:graves` block tags, grave/tomb/corpse
block names and explicit block IDs in the server's `graveBlockIds` configuration.
Corpse entities (`corpse:corpse`) are also observed.
Custom grave implementations may need an entry or tag. Client-only recovery uses
recognized grave block names and observed item entities, so it is conservative
when a server hides item or grave state.

### Corpse loot lifetime

With OpenMap Delta on the server, the optional Corpse integration reads the
loaded server `corpse.despawn.force_time` setting and the corpse's actual age.
The default `-1` (and `0`) leaves a full corpse indefinitely, so it has no
countdown. `corpse.skeleton_time` only changes the body: skeletons still contain
recoverable items and do not turn the deathpoint black. The empty-body retention
setting `corpse.despawn.time` does not extend the lifetime of already recovered
loot. The optional integration reads the installed Corpse API at runtime.

When forced body removal is enabled, the timer includes that remaining delay
and the expected lifetime of its contents after they spill onto the ground.
The actual spawned item UUIDs are then tracked through movement, merging,
pickups and their real expiration. The point stays red while any loot remains
recoverable. Config changes are reread; an unavailable optional API suppresses
the estimate instead of assuming cosmetic body removal destroys the loot.
The exact server-only corpse age cannot be inferred on a client-only install.

## Asking for help

Right-clicking a deathpoint changes **Notify** to **Ask for help**. The message
picker places **Please pick up my items for me**, **Please help me with nearby
enemies**, and **Please help me cross the difficult terrain** first, in bold.
The initial notification duration is five minutes. These templates are also
available for ordinary location notifications.
Singleplayer notices still show the sender's local marker and chat message.
Location notices accept sky and void death coordinates; the teleport action
continues to reject coordinates outside the current world's build height.
