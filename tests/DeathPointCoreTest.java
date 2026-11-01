// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.List;
import java.util.UUID;

public final class DeathPointCoreTest {
    private static int assertionCount;
    public static void main(String[] args) throws ReflectiveOperationException {
        var cheap63 = new DeathPointPolicy.Item("minecraft:oak_log", 63, List.of("minecraft:logs"), false);
        check(!DeathPointPolicy.shouldCreateSmart(List.of(cheap63), 10, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "63 cheap items and level 10 are suppressed");
        check(DeathPointPolicy.shouldCreateSmart(List.of(cheap63, new DeathPointPolicy.Item("minecraft:stick", 1, List.of(), false)), 10, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "64 cheap items create a point");
        check(DeathPointPolicy.shouldCreateSmart(List.of(), 11, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "level 11 creates a point");
        check(DeathPointPolicy.shouldCreateSmart(List.of(new DeathPointPolicy.Item("minecraft:diamond", 1, List.of(), false)), 0, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "one valuable item creates a point");
        check(DeathPointPolicy.shouldCreateSmart(List.of(new DeathPointPolicy.Item("minecraft:leather_helmet", 1, List.of(), true)), 0, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "dyed enchanted or named cheap items are valuable");
        check(!DeathPointPolicy.shouldCreateSmart(List.of(new DeathPointPolicy.Item("mod:token", 1, List.of(), false)), 0, "mod:token"), "custom cheap item IDs are honored");
        var data = new DeathPointData(true, 100, true, false);
        check(data.displayLabel("Death").equals("Death [5:00]"), "timer starts immediately");
        data.update(119);
        check(data.displayLabel("Renamed").equals("Renamed [5:00]"), "rename changes only base and rounds countdown upward");
        data.update(120);
        check(data.displayLabel("Renamed").equals("Renamed [4:59]"), "game tick countdown advances at 20 ticks per second");
        data.update(6099);
        check(data.color() == DeathPointData.RED, "last tick before expiry stays red");
        check(data.update(6100), "expiry reports a state transition once");
        check(data.color() == DeathPointData.BLACK && data.displayLabel("Renamed").equals("Renamed"), "five minute expiry turns black and ends countdown");
        check(!data.update(6100), "paused ticks never repeatedly expire");
        check(!data.shouldRemove(12099, 300) && data.shouldRemove(12100, 300), "default second five minute boundary removes black point");
        check(!data.shouldRemove(999999, 0), "zero black retention disables removal");
        var grave = new DeathPointData(true, 100, false, false);
        grave.update(7000);
        check(grave.color() == DeathPointData.RED && grave.displayLabel("Grave").equals("Grave"), "grave and keep-inventory points have no countdown without drops");
        grave.observeItems(); grave.update(7000);
        check(grave.isExpired(), "actual drops enable timer based on death game time even with a grave");
        var cached = new DeathPointData(true, 100, false, true);
        cached.restore(); cached.setCached(true);
        check(!cached.isCached(), "explicit restoration survives repeated server reports");
        UUID litter = UUID.randomUUID(); cached.setExistingItems(List.of(litter));
        check(cached.isExistingItem(litter) && !cached.isExistingItem(UUID.randomUUID()), "preexisting litter is excluded from actual death drops");
        cached.setExistingItemCounts(java.util.Map.of(litter, 12));
        check(!cached.isDeathItem(litter, 12) && !cached.isDeathItem(litter, 5), "unchanged or collected pre-existing litter never starts timer");
        check(cached.isDeathItem(litter, 13), "death drops merged into baseline item entity trigger timer");
        cached.observeDeathItem(litter);
        check(cached.isDeathItem(litter, 12) && cached.isDeathItem(litter, 1) && !cached.isDeathItem(litter, 0), "observed merged death entity must fully disappear even after partial pickup");
        var collected = new DeathPointData(true, 100, true, false);
        collected.collectByOthers(400);
        check(collected.color() == DeathPointData.BLACK && !collected.shouldRemove(6399, 300) && collected.shouldRemove(6400, 300), "other-player collection becomes black with its own retention clock");
        var basic = new DeathPointData(false, 100, false, false); basic.collectByOthers(400); basic.expire();
        check(basic.color() == DeathPointData.RED, "basic deathpoints always remain red");
        check(!DeathPointPolicy.canRecover(true, true, 100, false, false, false, false), "unobserved empty area cannot remove a new point");
        check(!DeathPointPolicy.canRecover(true, true, 99, true, false, false, false), "initial grace avoids spawn race");
        check(!DeathPointPolicy.canRecover(true, false, 100, true, false, false, false), "unloaded areas cannot confirm recovery");
        check(!DeathPointPolicy.canRecover(true, true, 100, true, false, true, true), "grave keeps point while death items are gone");
        check(DeathPointPolicy.canRecover(true, true, 100, true, false, true, false), "observed death items and grave gone permit recovery");
        check(!DeathPointPolicy.finishConfirmedRecovery(true, true, false, 0, 100), "authoritative recovery waits for owner dimension");
        check(!DeathPointPolicy.finishConfirmedRecovery(true, true, true, 256.01, 100), "authoritative recovery waits until within 16 blocks");
        check(!DeathPointPolicy.finishConfirmedRecovery(true, false, true, 0, 100), "authoritative recovery waits until owner is alive");
        check(DeathPointPolicy.finishConfirmedRecovery(true, true, true, 256, 100), "authoritative recovery finishes at owner approach boundary");
        UUID owner = UUID.randomUUID();
        var restoredA = new DeathPointData(true, 100, false, true); restoredA.setPlayerId(owner); restoredA.restore();
        var cachedB = new DeathPointData(true, 200, false, true); cachedB.setPlayerId(owner);
        restoredA.setCached(true);
        check(!DeathPointPolicy.replaceCached(restoredA, cachedB) && cachedB.isCached(), "replaying restored death A preserves newer cached death B");
        var oldReplay = new DeathPointData(true, 150, false, true); oldReplay.setPlayerId(owner);
        check(!DeathPointPolicy.replaceCached(oldReplay, cachedB), "replaying older hidden cache cannot delete newer cache");
        var newDeath = new DeathPointData(true, 300, false, true); newDeath.setPlayerId(owner);
        check(DeathPointPolicy.replaceCached(newDeath, cachedB), "newest suppressed death replaces previous cache");
        newDeath.setPlayerId(UUID.randomUUID());
        check(!DeathPointPolicy.replaceCached(newDeath, cachedB), "cache replacement stays account scoped");
        check(DeathPointPolicy.visibleToOwner(owner, owner), "owner can see own deathpoints");
        check(!DeathPointPolicy.visibleToOwner(owner, UUID.randomUUID()) && !DeathPointPolicy.visibleToOwner(null, owner), "other accounts and missing owner cannot expose deathpoints");
        check(DeathPointPolicy.isGraveEntity("corpse:corpse") && !DeathPointPolicy.isGraveEntity("minecraft:armor_stand"), "Corpse entity grave has explicit identity");
        check(!DeathPointPolicy.shouldCreateSmart(List.of(new DeathPointPolicy.Item("minecraft:warped_roots", 1, List.of(), false)), 0, DeathPointPolicy.DEFAULT_CHEAP_ITEMS), "nether roots default to cheap");
        authoritativeItemTimers();
        keepInventoryRecovery();
        System.out.println("Deathpoint core tests passed (" + assertionCount + " boundary and policy checks).");
    }
    private static void keepInventoryRecovery() {
        var keeper = new DeathPointData(true, 100, false, true);
        keeper.setKeepInventory(true);
        keeper.motion(0, 160, 0);
        check(!DeathPointPolicy.canRecover(keeper, true, true, 100, false, false),
                "returning to a hidden keepInventory death does not consume the restoration cache");
        keeper.restore();
        check(DeathPointPolicy.canRecover(keeper, true, true, 100, false, false),
                "restored aerial creative keepInventory death clears on return without observed drops or a grave");
        check(!keeper.observedItems() && !keeper.observedGrave() && keeper.motion(0, 0, 0).originY() == 160,
                "no-loot recovery proof does not fabricate drops, a grave, or a new death location");
        check(!DeathPointPolicy.canRecover(keeper, true, true, 99, false, false),
                "no-loot recovery waits for the five-second spawn grace");
        check(!DeathPointPolicy.canRecover(keeper, false, true, 100, false, false),
                "no-loot recovery still requires the owner to approach");
        check(!DeathPointPolicy.canRecover(keeper, true, false, 100, false, false),
                "unloaded, moving or not-yet-stable areas cannot prove no-loot recovery");
        check(!DeathPointPolicy.canRecover(keeper, true, true, 100, true, false),
                "actual drops keep a keepInventory marker while they remain");
        check(!DeathPointPolicy.canRecover(keeper, true, true, 100, false, true),
                "actual grave or protected corpse keeps a keepInventory marker while it remains");
        var visible = new DeathPointData(true, 100, false, false);
        visible.setKeepInventory(true);
        check(DeathPointPolicy.canRecover(visible, true, true, 100, false, false),
                "keepInventory deaths created visibly use the same empty-return recovery rule");
        var unknown = new DeathPointData(true, 100, false, false);
        check(!DeathPointPolicy.canRecover(unknown, true, true, 100, false, false),
                "an unknown gamerule and an unobserved empty area do not prove recovery");
        unknown.setKeepInventory(false);
        check(!DeathPointPolicy.canRecover(unknown, true, true, 100, false, false),
                "ordinary grave-mod deaths still require actual loot or grave observations");
        var basic = new DeathPointData(false, 100, false, false);
        basic.setKeepInventory(true);
        check(!DeathPointPolicy.canRecover(basic, true, true, 100, false, false),
                "basic deathpoints do not inherit smart no-loot automatic deletion");
        check(DeathPointPolicy.canRecover(true, true, 100, false, false, false, false, true),
                "authoritative keepInventory reports can confirm recovery with no tracked resources");
        check(!DeathPointPolicy.canRecover(true, true, 99, false, false, false, false, true),
                "authoritative no-loot recovery also waits for the spawn grace");
        check(!DeathPointPolicy.canRecover(true, false, 100, false, false, false, false, true),
                "incomplete or unloaded server resource tracking blocks no-loot confirmation");
        check(!DeathPointPolicy.canRecover(true, true, 100, false, true, false, false, true),
                "the authoritative drop ledger protects actual keepInventory drops");
        check(!DeathPointPolicy.canRecover(true, true, 100, false, false, false, true, true),
                "unobserved but present graves protect loot despite the keepInventory rule");
        var legacyCache = new DeathPointData(true, 100, false, true);
        legacyCache.restore();
        check(DeathPointPolicy.canRecover(legacyCache, true, true, 100, false, false),
                "restored legacy keepInventory caches without explicit gamerule metadata also clear on return");
        keeper.observeItems();
        check(!DeathPointPolicy.canRecover(keeper, true, true, 100, true, false)
                        && DeathPointPolicy.canRecover(keeper, true, true, 100, false, false),
                "keepInventory recovery follows actual items until they are gone");
    }
    private static void authoritativeItemTimers() throws ReflectiveOperationException {
        var corpse = new DeathPointData(true, 100, false, true);
        check(!corpse.hasItemTimer() && corpse.itemTimerRemainingTicks(100) == -1, "unknown server timer keeps fallback defaults");
        corpse.setItemTimer(600, true, false, 1000);
        check(corpse.displayLabel("Renamed corpse").equals("Renamed corpse [0:30]"), "server thirty-second resource timer appears immediately with current rename");
        check(corpse.deathTick() == 100 && corpse.isCached(), "finite resource sample changes neither death time nor hidden cache");
        corpse.update(1019);
        check(corpse.displayLabel("Renamed corpse").equals("Renamed corpse [0:30]"), "authoritative timer rounds partial seconds upward");
        corpse.update(1020);
        check(corpse.displayLabel("Renamed corpse").equals("Renamed corpse [0:29]"), "ticking server sample predicts at most one second");
        corpse.update(100000);
        check(corpse.itemTimerRemainingTicks(100000) == 580 && !corpse.isExpired(), "missed samples never catch up beyond twenty game ticks or infer expiry");
        check(corpse.itemTimerRemainingTicks(999) == 600, "sample prediction never runs backward against an earlier game time");

        corpse.setItemTimer(600, false, false, 2000);
        corpse.update(100000);
        check(corpse.displayLabel("Paused corpse").equals("Paused corpse [0:30]") && corpse.itemTimerRemainingTicks(100000) == 600,
                "paused corpse sample remains unchanged regardless client elapsed time");
        corpse.setItemTimer(12000, true, false, 3000);
        check(corpse.displayLabel("Configured corpse").equals("Configured corpse [10:00]"), "live force-duration sample replaces earlier timer without resetting death timestamp");
        check(corpse.deathTick() == 100, "live timer configuration never changes recorded death time");

        var drops = new DeathPointData(true, 100, true, false);
        drops.observeItems();
        drops.setItemTimer(600, true, false, 1000);
        check(drops.displayLabel("Items").equals("Items [0:30]"), "authoritative item age overrides fallback five minutes even when actual drops were observed");
        drops.update(7000);
        check(!drops.isExpired() && drops.itemTimerRemainingTicks(7000) == 580, "authoritative finite sample prevents fallback death-time expiry");
        drops.setItemTimer(-1, true, false, 7000);
        drops.update(100000);
        check(drops.hasItemTimer() && drops.itemTimerRemainingTicks(100000) == -1 && drops.displayLabel("Items").equals("Items"),
                "known infinite item lifetime suppresses all countdowns despite drop observations");
        check(!drops.isExpired() && drops.color() == DeathPointData.RED, "known infinite lifetime cannot expire through fallback timer");
        drops.clearItemTimer();
        check(!drops.hasItemTimer() && drops.itemTimerRemainingTicks(100000) == -1, "clearing a timer distinguishes unknown fallback from known infinite lifetime");
        drops.update(6100);
        check(drops.isExpired() && drops.shouldRemove(12100, 300), "old server fallback still expires at death plus five minutes with original retention boundary");
        drops.setItemTimer(600, false, false, 7000);
        check(!drops.isExpired() && drops.color() == DeathPointData.RED && drops.displayLabel("Items").equals("Items [0:30]"),
                "first accurate resource sample corrects an expiry previously inferred by local fallback");
        check(!drops.shouldRemove(100000, 300), "corrected fallback expiry clears its old black retention timestamp");

        var nearZero = new DeathPointData(true, 100, false, false);
        nearZero.setItemTimer(10, true, false, 1000);
        nearZero.update(1020);
        check(nearZero.displayLabel("Waiting").equals("Waiting [0:00]") && nearZero.color() == DeathPointData.RED,
                "prediction reaching zero waits red for proof that all resources are lost");
        check(!nearZero.isExpired() && !nearZero.shouldRemove(100000, 300), "predicted zero cannot start black retention or remove a deathpoint");
        nearZero.setItemTimer(0, false, true, 9000);
        check(nearZero.isExpired() && nearZero.color() == DeathPointData.BLACK && nearZero.displayLabel("Waiting").equals("Waiting"),
                "confirmed resource expiry makes smart point black and hides the countdown");
        check(!nearZero.shouldRemove(14999, 300) && nearZero.shouldRemove(15000, 300),
                "confirmed resource expiry starts configurable black retention at receiving game tick");
        check(!nearZero.shouldRemove(100000, 0), "zero retention disables authoritative expiry removal");
        nearZero.setItemTimer(0, false, true, 10000);
        check(nearZero.shouldRemove(15000, 300), "duplicate expiry samples never restart black retention");
        nearZero.setItemTimer(6000, true, false, 11000);
        check(!nearZero.isExpired() && nearZero.color() == DeathPointData.RED && !nearZero.shouldRemove(50000, 300),
                "new authoritative live items revive an existing black point and clear stale retention");
        nearZero.setItemTimer(-1, false, false, 12000);
        check(!nearZero.isExpired() && nearZero.displayLabel("Revived").equals("Revived"),
                "revived indefinite loot stays red without countdown");
        nearZero.setItemTimer(0, false, true, 13000);
        check(!nearZero.shouldRemove(18999, 300) && nearZero.shouldRemove(19000, 300),
                "subsequent true loss starts a new black retention period");
        var collected = new DeathPointData(true, 100, true, false);
        collected.collectByOthers(9000);
        collected.setItemTimer(6000, true, false, 10000);
        check(collected.isCollectedByOthers() && collected.color() == DeathPointData.BLACK && collected.shouldRemove(15000, 300),
                "live samples do not erase confirmed collection attribution or its original retention");

        var basic = new DeathPointData(false, 100, false, false);
        basic.setItemTimer(0, true, true, 9000);
        basic.update(100000);
        check(!basic.isExpired() && basic.color() == DeathPointData.RED && basic.displayLabel("Basic").equals("Basic"),
                "basic deathpoints never show or expire through authoritative item timer");
        corpse.makeBasic();
        check(!corpse.hasItemTimer() && corpse.itemTimerRemainingTicks(100000) == -1 && corpse.displayLabel("Basic").equals("Basic"),
                "disabling smart behavior clears all authoritative timer primitives");

        var sampled = new DeathPointData(true, 100, false, false);
        sampled.setItemTimer(600, false, false, 5000);
        var reloaded = new DeathPointData(true, 100, false, false);
        for (String name : List.of("itemTimerKnown", "itemTimerRemainingTicks", "itemTimerSampleGameTick", "itemTimerTicking", "itemTimerExpired")) {
            var field = DeathPointData.class.getDeclaredField(name);
            field.setAccessible(true);
            check(field.getType().isPrimitive() && !java.lang.reflect.Modifier.isTransient(field.getModifiers()),
                    "authoritative timer field persists as a primitive: " + name);
            field.set(reloaded, field.get(sampled));
        }
        reloaded.update(100000);
        check(reloaded.hasItemTimer() && reloaded.itemTimerRemainingTicks(100000) == 600 && reloaded.displayLabel("Reloaded").equals("Reloaded [0:30]"),
                "primitive timer sample survives persistence without transient display state");
        var legacy = new DeathPointData(true, 100, true, false);
        var absentRemaining = DeathPointData.class.getDeclaredField("itemTimerRemainingTicks"); absentRemaining.setAccessible(true); absentRemaining.setLong(legacy, 0);
        legacy.update(120);
        check(!legacy.hasItemTimer() && legacy.itemTimerRemainingTicks(120) == -1 && legacy.displayLabel("Legacy").equals("Legacy [4:59]"),
                "absent presence flag keeps older zero-initialized persisted primitives in fallback mode");
        var expiredSample = new DeathPointData(true, 100, false, false);
        sampled.setItemTimer(0, false, true, 9000);
        for (String name : List.of("itemTimerKnown", "itemTimerRemainingTicks", "itemTimerSampleGameTick", "itemTimerTicking", "itemTimerExpired")) {
            var field = DeathPointData.class.getDeclaredField(name); field.setAccessible(true); field.set(expiredSample, field.get(sampled));
        }
        expiredSample.update(10000);
        check(expiredSample.isExpired() && !expiredSample.shouldRemove(14999, 300) && expiredSample.shouldRemove(15000, 300),
                "persisted confirmed expiry reconstructs black retention from its authoritative sample time");
        boolean invalidRejected = false;
        try { legacy.setItemTimer(-2, true, false, 100); } catch (IllegalArgumentException expected) { invalidRejected = true; }
        check(invalidRejected && !legacy.hasItemTimer(), "malformed negative timer cannot alter existing state");
    }
    private static void check(boolean ok, String message) { assertionCount++; if (!ok) throw new AssertionError(message); }
}
