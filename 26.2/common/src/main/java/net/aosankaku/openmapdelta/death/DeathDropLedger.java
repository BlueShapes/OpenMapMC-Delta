// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.UUID;

/** Conservative accounting for death-owned items mixed with unrelated items in one entity. */
public final class DeathDropLedger {
    private DeathDropLedger() {}
    public record Drop(UUID id, int count, int x, int y, int z) {
        public Drop {
            if (id == null || count < 1 || count > 1_000_000 || Math.abs((long) x) > 30_000_000
                    || Math.abs((long) z) > 30_000_000 || y < -2048 || y > 4096)
                throw new IllegalArgumentException("Invalid tracked drop");
        }
        public Drop withCount(int value) { return new Drop(id, value, x, y, z); }
        /** Explicit decoding supports older Gson versions and rejects numeric narrowing in persisted state. */
        public static Drop fromJson(com.google.gson.JsonObject json) {
            try {
                return new Drop(UUID.fromString(json.get("id").getAsString()),
                        json.get("count").getAsBigDecimal().intValueExact(), json.get("x").getAsBigDecimal().intValueExact(),
                        json.get("y").getAsBigDecimal().intValueExact(), json.get("z").getAsBigDecimal().intValueExact());
            } catch (RuntimeException malformed) { throw new IllegalArgumentException("Invalid persisted death drop", malformed); }
        }
    }

    /** Treat unrelated mass as transferred first, so a partial pickup cannot claim a whole death stack. */
    public static int attributableAmount(int beforeTotal, int trackedTotal, int transferred) {
        if (beforeTotal < 0 || trackedTotal < 0 || transferred < 0)
            throw new IllegalArgumentException("Negative item accounting");
        int tracked = Math.min(beforeTotal, trackedTotal);
        int unrelated = beforeTotal - tracked;
        return Math.min(tracked, Math.max(0, Math.min(beforeTotal, transferred) - unrelated));
    }

    public static boolean completelyCollected(int remainingDrops, boolean lostWithoutPickup) {
        return remainingDrops == 0 && !lostWithoutPickup;
    }
}
