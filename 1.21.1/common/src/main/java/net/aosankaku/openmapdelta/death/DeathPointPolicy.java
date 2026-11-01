// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Minecraft-independent inventory significance and recovery rules. */
public final class DeathPointPolicy {
    public static final String DEFAULT_CHEAP_ITEMS = "#minecraft:logs,#minecraft:planks,#minecraft:saplings,#minecraft:dirt,#minecraft:sand,"
            + "minecraft:stick,minecraft:wheat_seeds,minecraft:gravel,minecraft:leaf_litter,"
            + "minecraft:mangrove_roots,minecraft:muddy_mangrove_roots,minecraft:hanging_roots,minecraft:crimson_roots,minecraft:warped_roots,"
            + "minecraft:wooden_sword,minecraft:wooden_pickaxe,minecraft:wooden_axe,minecraft:wooden_shovel,minecraft:wooden_hoe,"
            + "minecraft:leather_helmet,minecraft:leather_chestplate,minecraft:leather_leggings,minecraft:leather_boots,minecraft:leather_horse_armor";
    public record Item(String id, int count, Collection<String> tags, boolean valuable) {}
    private DeathPointPolicy() {}
    public static boolean shouldCreateSmart(Collection<Item> items, int experienceLevel, String allowlist) {
        if (experienceLevel > 10) return true;
        Set<String> cheap = new HashSet<>();
        for (String entry : allowlist.split(",")) if (!entry.isBlank()) cheap.add(entry.trim());
        long total = 0;
        for (Item item : items) {
            if (item.count() <= 0) continue;
            boolean allowed = cheap.contains(item.id());
            for (String tag : item.tags()) if (cheap.contains("#" + tag)) allowed = true;
            if (item.valuable() || !allowed) return true;
            total += item.count();
            if (total >= 64) return true;
        }
        return false;
    }
    public static boolean canRecover(boolean near, boolean loaded, long elapsedTicks,
                                     boolean observedItems, boolean itemsPresent,
                                     boolean observedGrave, boolean gravePresent) {
        return canRecover(near, loaded, elapsedTicks, observedItems, itemsPresent, observedGrave, gravePresent, false);
    }
    /** A known keepInventory death can finish on approach even when nothing was dropped. */
    public static boolean canRecover(boolean near, boolean loaded, long elapsedTicks,
                                     boolean observedItems, boolean itemsPresent,
                                     boolean observedGrave, boolean gravePresent, boolean keepInventory) {
        return near && loaded && elapsedTicks >= 100 && (observedItems || observedGrave || keepInventory)
                && !itemsPresent && !gravePresent;
    }
    /** Hidden caches remain available for restoration while actual drops can still be observed. */
    public static boolean canRecover(DeathPointData data, boolean near, boolean loaded, long elapsedTicks,
                                     boolean itemsPresent, boolean gravePresent) {
        return data != null && data.isSmart() && !data.isCached()
                && canRecover(near, loaded, elapsedTicks, data.observedItems(), itemsPresent,
                data.observedGrave(), gravePresent, data.keepsInventory());
    }
    public static boolean isGrave(String blockId) {
        return blockId != null && !blockId.startsWith("minecraft:")
                && (blockId.contains("grave") || blockId.contains("tomb") || blockId.contains("corpse"));
    }
    public static boolean finishConfirmedRecovery(boolean confirmed, boolean alive, boolean sameDimension,
                                                  double distanceSquared, long elapsedTicks) {
        return confirmed && alive && sameDimension && distanceSquared <= 256 && elapsedTicks >= 100;
    }
    public static boolean visibleToOwner(java.util.UUID owner, java.util.UUID currentPlayer) {
        return owner != null && owner.equals(currentPlayer);
    }
    public static boolean isGraveEntity(String entityId) { return "corpse:corpse".equals(entityId); }
    /** Replaying an explicitly restored or older death never consumes the latest hidden cache. */
    public static boolean replaceCached(DeathPointData incoming, DeathPointData existing) {
        return incoming != null && existing != null && incoming.isCached() && existing.isCached()
                && java.util.Objects.equals(incoming.playerId(), existing.playerId())
                && incoming.deathTick() >= existing.deathTick();
    }
}
