// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.List;

/** Availability of all death loot; a countdown reaching zero alone never proves loss. */
public final class DeathLootTimer {
    private DeathLootTimer() {}

    public record Resource(long remainingTicks, boolean ticking) {
        public Resource {
            if (remainingTicks < -1 || remainingTicks == 0 || remainingTicks > 2L * Integer.MAX_VALUE + 1
                    || ticking && remainingTicks < 0) throw new IllegalArgumentException("Invalid live resource lifetime");
        }
    }

    public static DeathPointUpdate.ItemTimer sample(List<Resource> resources, boolean protectedUnknown,
            boolean unavailable, boolean hadTrackedResources, DeathPointUpdate.ItemTimer previous) {
        if (protectedUnknown || resources.stream().anyMatch(resource -> resource.remainingTicks() < 0))
            return new DeathPointUpdate.ItemTimer(-1, false, false);
        long longest = 0;
        boolean ticking = false;
        for (Resource resource : resources) {
            if (resource.remainingTicks() > longest) {
                longest = resource.remainingTicks(); ticking = resource.ticking();
            } else if (resource.remainingTicks() == longest) ticking &= resource.ticking();
        }
        if (unavailable) {
            // A last-seen resource cannot disappear while its chunk is unavailable. Preserve that sample,
            // but accept a newly extended lifetime from another loaded tracked item.
            long remaining = previous == null || previous.remainingTicks() < 0 ? -1
                    : Math.max(previous.remainingTicks(), longest);
            return new DeathPointUpdate.ItemTimer(remaining, false, false);
        }
        if (!resources.isEmpty()) return new DeathPointUpdate.ItemTimer(longest, ticking, false);
        return hadTrackedResources ? new DeathPointUpdate.ItemTimer(0, false, true) : null;
    }
}
