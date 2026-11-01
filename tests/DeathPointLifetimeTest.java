// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Exercises optional reflection with an absent mod, both Corpse owner APIs and loader/vanilla item APIs. */
public final class DeathPointLifetimeTest {
    public static final class Value { public int value; public int get() { return value; } }
    public static final class Spec { public boolean loaded = true; public boolean isLoaded() { return loaded; } }
    public static final class Config {
        public Value corpseForceDespawnTime = new Value();
        public Value corpseSkeletonTime = new Value();
        public Value corpseDespawnTime = new Value();
        public Spec spec = new Spec();
        public Spec getConfigSpec() { return spec; }
    }
    public static final class Death {
        public List<?> items = List.of("diamond");
        public UUID owner = UUID.randomUUID();
        public List<?> getAllItems() { return items; }
        public UUID getPlayerUUID() { return owner; }
    }
    public static class Corpse {
        private int age;
        public boolean empty;
        public Death death = new Death();
        public boolean isEmpty() { return empty; }
        public Death getDeath() { return death; }
        public Optional<UUID> getCorpseUUID() { return Optional.of(death.owner); }
    }
    public static final class NewCorpse extends Corpse { public UUID getPlayerUuid() { return death.owner; } }

    public static void main(String[] args) {
        boolean loader = args[0].equals("loader");
        var corpse = new Corpse();
        var config = new Config();
        config.corpseForceDespawnTime.value = -1;
        config.corpseSkeletonTime.value = 0;
        corpse.age = 72000;
        var snapshot = CorpseCompat.read(corpse, config);
        check(snapshot != null && snapshot.remainingForceTicks() == -1 && !snapshot.empty(),
                "instant skeleton and old corpse still protect recoverable loot indefinitely");
        config.corpseForceDespawnTime.value = 0;
        check(CorpseCompat.read(corpse, config).remainingForceTicks() == -1, "zero force expiry is disabled in Corpse");
        corpse.age = 100;
        config.corpseForceDespawnTime.value = 200;
        snapshot = CorpseCompat.read(corpse, config);
        check(snapshot.remainingForceTicks() == 101 && snapshot.items().equals(List.of("diamond")), "age and configured force expiry are read exactly");
        long afterSpill = snapshot.remainingForceTicks() + ItemLifetimeCompat.futureLifetime(new ItemStack(9000), new Level());
        check(afterSpill == 101 + (loader ? 9000 : 6000), "body expiry leaves the subsequent ground-item lifetime");
        config.corpseForceDespawnTime.value = 400;
        check(CorpseCompat.read(corpse, config).remainingForceTicks() == 301, "runtime server config changes are reread");
        config.corpseDespawnTime.value = 20;
        check(CorpseCompat.read(corpse, config).remainingForceTicks() == 301, "empty-body retention does not shorten availability of full inventory");
        corpse.age = 400;
        check(CorpseCompat.read(corpse, config).remainingForceTicks() == 1, "Corpse expires only when next age exceeds force config");
        corpse.empty = true;
        corpse.death.items = List.of();
        check(CorpseCompat.read(corpse, config).empty(), "empty inventory can be distinguished from retained cosmetic body");
        config.spec.loaded = false;
        check(CorpseCompat.read(corpse, config) == null, "unloaded server config cannot supply an authoritative deadline");
        check(CorpseCompat.snapshot(corpse) == null && !CorpseCompat.isCorpse(corpse), "missing optional mod remains safe");
        check(CorpseCompat.owner(corpse).equals(corpse.death.owner), "older Optional owner API works without Corelib linkage");
        var newer = new NewCorpse();
        check(CorpseCompat.owner(newer).equals(newer.death.owner), "new owner API works");
        check(CorpseCompat.owner(new Object()) == null && CorpseCompat.read(new Object(), config) == null, "unsupported mod API remains unknown");

        var item = new ItemEntity(100, 9000);
        check(ItemLifetimeCompat.remaining(item) == (loader ? 8900 : 5900), "loader lifespan and vanilla fallback sampled");
        item.setAge(200);
        item.setLifetime(12000);
        check(ItemLifetimeCompat.remaining(item) == (loader ? 11800 : 5800), "expiration-event extensions are reread from actual live entity");
        check(ItemLifetimeCompat.remaining(-32768, 6000) == -1, "unlimited lifetime sentinel recognized");
        check(ItemLifetimeCompat.remaining(-6000, 6000) == 12000, "extended lifetime is finite");
        check(ItemLifetimeCompat.remaining(6000, 6000) == 1, "a live item at nominal expiry never proves resource loss");
        check(ItemLifetimeCompat.remaining(0, -1) == 1, "negative loader lifespan remains live until actual removal");
        System.out.println("DeathPointLifetimeTest passed (" + args[0] + ")");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
