# World cache identity

Map tiles are stored per server or local save, world identity, and dimension.
Surface and cave layers keep their existing separate storage. Server addresses
use a canonical host and port: `example.com`, `EXAMPLE.COM`, and
`example.com:25565` select the same cache. Nondefault ports remain independent.
Hostnames are not resolved to IPs, so shared hosting and DNS aliases are not
accidentally merged.

The client reads the seed-derived 64-bit value already supplied by vanilla
Minecraft. Different values at the same address select separate cache
directories, including superflat worlds. This does not reveal the original
seed and does not scan chunks or compare editable terrain.

When the server also has OpenMapMC Delta, it maintains a UUID in
`<world>/data/odysseymap-world-id` and sends it through a separate optional
channel. This identifies independently created worlds even with the same
seed. Integrated servers use the same file. The identity is loaded once per
server instance and preserved across restarts. A malformed or unreadable
identity file is preserved. UUID-capable connections keep map sampling in memory
until a valid identity arrives, so they never read or overwrite a previously
visited world's seed-only cache. If the identity file is unreadable, this session
remains memory-only rather than guessing its saved world.
Deleting or replacing a whole world folder generates a new UUID. Copying the
folder together with its identity file preserves that identity. A retained
identity file with a changed seed still selects a separate cache.

The legacy player-position payload is unchanged. Vanilla clients and older
Odyssey clients are not sent the new identity payload. Received identities
are scoped to the actual client connection, survive dimension travel, and
clear on disconnect. Forge and NeoForge check negotiated channel support;
Fabric uses an optional login query and completes its response only after
recording the originating connection's support notice. Unsupported clients
can decline this query and still join. If the UUID arrives after sampling begins,
the memory-only session switches to its distinct directory and rejects stale
loads. Receivers capture their originating connection so queued callbacks from
an earlier login cannot assign another world's ID to the current connection.

Existing address-only caches remain untouched in their old directories. They
do not contain a seed or UUID, so their original world cannot be confirmed and
they are not imported automatically. The first visit after this update builds
a new identified cache. Returning to each identified world reuses its cache.

Without server-side support, worlds with the same seed-derived value cannot
be distinguished reliably. This also applies to servers that deliberately
replace that value. Terrain and bedrock matching can confuse edits with world
replacement and needs overlapping loaded terrain, so it is not used as an
automatic reason to discard or reassign cache data.

`scripts/test-world-identity.ps1` checks address equivalence, UUID persistence,
bounded codecs, stale connection rejection, and preservation of both another
world's cache and legacy cache bytes. The tile-cache tests cover final writes
and asynchronous generation isolation. `scripts/test-multiplayer.cjs` checks
real Forge and Fabric connections for vanilla, older, and current map clients,
including the optional Fabric login barrier before PLAY.
