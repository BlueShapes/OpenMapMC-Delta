# Transit waypoint icons

Automatic Create stations use `symbol:odysseymap:create_station`; MTR stations use
`symbol:odysseymap:mtr_station`. Both symbols are bundled with OpenMapMC Delta and can
also be selected for an ordinary waypoint without either transit mod installed.

The picker includes registered mod items and minecarts, rendered with their GUI
item models. Search supports ordinary names, `@mod` for a mod name or ID, `&id`
for an item ID, `#tag`, `$tooltip`, quoted phrases, spaces for AND, `|` for OR,
and `-term` to exclude a match. Search syntax and matching tooltip text are
highlighted. These are supported JEI-style search forms; they do not require JEI.

A waypoint retains its selected item ID when that item is unavailable. It uses
its colored marker until the item becomes available again after restoring the
mod or updating resources. This also applies to Web map icons.

The MTR red circle and white M is the upstream station logo, copied unchanged
from `assets/mtr/textures/block/sign/logo.png`. Its MIT license and Jonathan Ho
copyright are included in each distributable as `MTR-STATION-ICON-LICENSE.txt`.
[Upstream asset](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/master/fabric/src/main/resources/assets/mtr/textures/block/sign/logo.png)
and [license](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway/blob/master/LICENSE).

The Create symbol is original generated artwork. It uses a simple brass gear,
teal locomotive front, cream windows, and a warm headlamp on a transparent
background. It is saved in each version's
`common/src/main/resources/assets/openmapdelta/textures/marker/create_station.png`.
Generation used the built-in `imagegen` tool, followed by this simplification
prompt in response to the player's preference:

> Make the icon much simpler for a tiny Minecraft map marker, with a strict
> 24-by-24 logical pixel grid and nearest-neighbor pixel-art shapes. Use a brass
> gear surrounding a simple Create-inspired locomotive front: dark teal body,
> two cream windows, warm headlamp, and dark wheels. Limit the palette to dark
> charcoal, teal, brass, cream, and brown. Omit gradients, bevels, textures,
> bolts, scenery, and text. Keep the background transparent.

The native generated PNG is 1024 by 1024. Game and browser renderers scale it
with nearest-neighbor filtering to preserve the simple pixel-art appearance.
