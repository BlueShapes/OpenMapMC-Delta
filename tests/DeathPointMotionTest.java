// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

public final class DeathPointMotionTest {
    private static final class Terrain implements DeathPointMotion.Terrain {
        boolean loaded = true;
        double floor = 64.5;
        int collisions;
        public boolean loaded(double x, double y, double z, double dy) { return loaded; }
        public double collide(double x, double y, double z, double dy) {
            collisions++;
            return Math.max(floor - y, dy);
        }
        public double minimumY() { return -128; }
    }

    public static void main(String[] args) {
        var terrain = new Terrain();
        var motion = new DeathPointMotion(-2, 100, 3);
        check(motion.tick(100, 1, terrain), "aerial point begins falling");
        near(motion.y(), 99.96, "item gravity on first tick");
        near(motion.renderY(0.5), 99.98, "icon interpolates between physics ticks");
        near(motion.velocityY(), -0.0392, "item drag on first tick");
        check(!motion.tick(100, 1, terrain), "paused game time cannot advance physics");
        motion.tick(101, 1, terrain);
        near(motion.y(), 99.8808, "fall accelerates each tick");
        double beforePause = motion.y();
        terrain.loaded = false;
        int beforeCollisions = terrain.collisions;
        motion.tick(10000, 20, terrain);
        near(motion.y(), beforePause, "unloaded chunk pauses movement");
        check(terrain.collisions == beforeCollisions, "unloaded chunk never queries collision");
        terrain.loaded = true;
        motion.tick(10001, 1, terrain);
        check(beforePause - motion.y() < 0.2, "loading chunk does not catch up unobserved time");
        for (long tick = 10002; tick < 10200; tick++) motion.tick(tick, 1, terrain);
        near(motion.y(), 64.5, "partial-block collision lands at exact surface");
        near(motion.velocityY(), 0, "landing stops gravity speed");
        check(!motion.falling(), "landed point permits recovery");
        check(motion.originX() == -2 && motion.originY() == 100 && motion.originZ() == 3,
                "landing preserves immutable death coordinates");
        check(motion.blockX() == -2 && motion.blockY() == 64, "negative coordinates use floor");

        motion.follow(7.25, 70.75, -4.25, 0.02, 10200);
        near(motion.x(), 7.25, "real item supplies horizontal scatter");
        near(motion.y(), 70.75, "real floating/modded item overrides predicted ground");
        check(motion.originY() == 100 && motion.blockZ() == -5, "following keeps identity and negative position");
        double finiteY = motion.y();
        check(!motion.follow(0, Double.NaN, 0, 0, 10201), "nonfinite observed position rejected");
        near(motion.y(), finiteY, "invalid observation cannot corrupt location");
        var terminal = new DeathPointMotion(0, 200, 0);
        terminal.follow(0.5, 64, 0.5, -1, 300);
        terminal.freeze(300);
        near(terminal.renderY(0.1), 64, "terminal report snaps to recovered location");
        near(terminal.renderY(0.9), 64, "frozen point cannot repeat interpolation every tick");

        var bounded = new DeathPointMotion(0, 100, 0);
        bounded.tick(1, 1, terrain);
        bounded.tick(100000, 20, terrain);
        check(bounded.y() > 90, "server prediction bounded to one second after reconnect");
        terrain.floor = -1000;
        var voidPoint = new DeathPointMotion(0, -127, 0);
        for (int tick = 0; tick < 200; tick++) voidPoint.tick(tick, 1, terrain);
        near(voidPoint.y(), -128, "void stops at item destruction boundary");
        near(voidPoint.velocityY(), 0, "void boundary cannot accumulate speed indefinitely");

        var noLoot = new DeathPointData(true, 100, false, true);
        var noLootMotion = noLoot.motion(1000, 100, 0);
        check(!noLoot.canPredictItemMotion(), "hidden keepInventory death without drops never predicts falling loot");
        noLootMotion.follow(1000.5, -128, 0.5, -1, 200);
        check(noLoot.resetUnobservedMotion(201), "old incorrectly fallen no-loot cache is repaired");
        near(noLootMotion.y(), 100, "no-loot repair restores the immutable death Y");
        near(noLootMotion.renderY(0.5), 100, "repaired no-loot marker does not interpolate from the void");
        check(!noLoot.resetUnobservedMotion(202), "unchanged no-loot repair does not repeatedly dirty storage");
        noLoot.restore();
        check(!noLoot.canPredictItemMotion() && noLoot.displayLabel("Restored").equals("Restored"),
                "restoration enables display without enabling gravity or countdown");
        check(noLoot.keepsInventory(), "legacy cache remains identifiable after restoration without new metadata");
        noLoot.setTimerExpected(false);
        check(!noLoot.canPredictItemMotion(), "repeated keepInventory reports leave restored point stationary");
        noLoot.observeItems();
        noLoot.setTimerExpected(false);
        check(noLoot.canPredictItemMotion(), "actual drops override keepInventory motion suppression");
        noLootMotion.follow(1004.25, 80.5, -3.75, -0.2, 203);
        check(!noLoot.resetUnobservedMotion(204), "actual death drops keep their observed position");
        near(noLootMotion.y(), 80.5, "real loot movement is preserved after a restore");
        noLoot.update(204);
        check(noLootMotion.originY() == 100 && noLoot.displayLabel("Restored").equals("Restored [4:55]"),
                "following restored death loot preserves identity and starts its original timer");
        var protectedGrave = new DeathPointData(true, 100, false, false);
        var graveMotion = protectedGrave.motion(1, 100, 2);
        protectedGrave.setKeepInventory(false);
        check(protectedGrave.canPredictItemMotion(), "grave-mod countdown suppression does not pin non-keepInventory deaths in the air");
        graveMotion.follow(1.5, 80, 2.5, -0.2, 202);
        check(!protectedGrave.resetUnobservedMotion(203), "a not-yet-observed grave death keeps its predicted falling location");
        protectedGrave.observeGrave();
        graveMotion.follow(1.5, 80, 2.5, -0.2, 203);
        check(!protectedGrave.resetUnobservedMotion(204), "known grave position is never repaired as a no-loot cache");
        near(graveMotion.y(), 80, "actual falling corpse position is retained without an item timer");
        var visibleKeeper = new DeathPointData(true, 100, false, false);
        visibleKeeper.setKeepInventory(true);
        check(!visibleKeeper.canPredictItemMotion(), "visible keepInventory deaths also stay at the origin without drops");
        visibleKeeper.observeItems();
        check(visibleKeeper.canPredictItemMotion(), "actual drops enable visible keepInventory motion");
        var correctedRule = new DeathPointData(true, 100, false, true);
        correctedRule.restore();
        correctedRule.setKeepInventory(false);
        check(correctedRule.canPredictItemMotion(), "an authoritative false gamerule overrides a legacy cache inference");

        var data = new DeathPointData(true, 100, true, false);
        var oldCorpse = java.util.UUID.randomUUID();
        data.setExistingGraves(java.util.List.of(oldCorpse));
        check(!data.isNewGrave(oldCorpse) && data.isNewGrave(java.util.UUID.randomUUID()),
                "death before/after corpse baseline excludes older bodies");
        var timerMotion = data.motion(0, 100, 0);
        data.observeGraveBlock(0, 64, 0);
        timerMotion.follow(5.5, 64, 0.5, 0, 100);
        check(java.util.Arrays.equals(data.graveBlock(), new int[]{0, 64, 0}),
                "flowing items cannot move the observed static grave anchor");
        data.clearGraveBlock();
        check(data.graveBlock() == null && data.observedGrave(), "removed grave clears anchor but retains recovery evidence");
        timerMotion.tick(101, 1, new Terrain());
        data.update(120);
        check(data.displayLabel("Renamed").equals("Renamed [4:59]"), "falling and rename retain original countdown");
        data.makeBasic();
        check(!data.isSmart() && data.displayLabel("Renamed").equals("Renamed"), "turning smart off ends motion eligibility and countdown");
        System.out.println("Deathpoint motion tests passed (gravity, collision, pause, corpse baseline, frozen rendering and timer checks).");
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0e-7, message + ": " + actual);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
