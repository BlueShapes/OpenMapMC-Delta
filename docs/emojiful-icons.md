# Emojiful waypoint icons

When Emojiful is installed, the waypoint icon picker adds an **Emojis** tab.
It lists Emojiful's live standard and custom emoji catalog, including world
recipe emojis. Search includes their names, aliases, alternate text, and
`@emojiful`. Listing names does not request their images; visible icons load
through Emojiful's own texture loader.

The selected emoji uses its current frame in game. The Web map captures the
available frame during its regular icon updates, with a shared image budget
and bounded GPU readback queues. It does not download images independently.

Selections persist as `emoji:emojiful:<SHA-256 of the first nonempty alias>`.
This preserves the choice across changes to Emojiful's internal names. If the
mod or a custom emoji is removed, or its image is loading or fails, the game
and Web map use the waypoint's colored marker. The saved choice remains and
resolves again when the same alias becomes available.

The waypoint does not archive the image selected at creation time. If Emojiful
loads a replacement image under the same first nonempty alias, the waypoint
uses that replacement. Changing that identity alias produces a different ID;
changing only an internal name does not. Reusing an alias for another emoji
also redirects existing waypoints to the new emoji.

Changing or deleting an image at its remote URL does not itself notify the map.
The map uses the frame Emojiful currently exposes and does not poll that URL
or force a fresh download. A previously loaded image may remain visible while
Emojiful retains it; an unavailable entry or an unready/failed frame falls
back to the colored marker. Catalog refreshes are limited to once per second,
so a catalog change need not appear immediately.

The integration has no compile-time dependency on Emojiful. Reflection reads
`Constants.EMOJI_LIST`, emoji metadata, `getResourceLocationForBinding()`,
and the uploaded flag on its image texture. It tolerates missing APIs and
refreshes metadata at most once per second. Resource reload and disconnect
invalidate OpenMapMC Delta's catalog without destroying Emojiful-owned textures.

The contract was checked against the installed Emojiful Forge 1.20.1 4.2.0
JAR and its [upstream API](https://github.com/InnovativeOnlineIndustries/Emojiful/blob/1.21/Common/src/main/java/com/hrznstudio/emojiful/api/Emoji.java).
`scripts/test-emojiful-icons.ps1` verifies lazy loading, missing classes,
uninstall/reinstall, replaced recipe entries, animated frame resolution, and
public/private upload flags using a small optional API fixture.

Selecting an emoji icon is separate from writing an alias inside a waypoint
name or other text. Emojiful Delta's display tokens preserve the raw aliases
used by this integration. The 26.2 compatibility update fixes alias clipping,
outline duplication, opacity, and bold advance when used with the matching
Emojiful Delta build (commit `055c710` or its descendants). Text halos retain
the emoji's advance but omit its image; the original colored image is drawn
once in the foreground. Native input fields show literal aliases while editing,
including after deferred GUI extraction, so cursor and selection offsets stay
aligned with the saved text. Atomic emoji wrapping remains active outside inputs.

The original investigation and
fix evidence distinguish the verified
26.2 behavior from unported versions and untested GPU/physical input behavior.
