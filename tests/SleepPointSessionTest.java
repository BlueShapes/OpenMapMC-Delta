// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

public final class SleepPointSessionTest {
    private static final SleepPointSession.Point BED = new SleepPointSession.Point("minecraft:overworld", 10, 65, 20);

    public static void main(String[] args) {
        SleepPointSession session = new SleepPointSession();
        check(session.tick(BED, false, false) == null, "non-spawn sleep is ignored by default");
        session.confirmSpawn(BED);
        check(BED.equals(session.tick(BED, false, false)), "late server spawn confirmation records the bed");
        check(session.tick(BED, true, true) == null, "sleep is recorded once");
        session.confirmSpawn(BED);
        session.tick(null, false, false);
        check(session.tick(BED, false, false) == null, "late notification for a recorded sleep cannot leak");
        session.tick(null, false, false);
        check(BED.equals(session.tick(BED, true, false)), "same spawn bed records again without a new chat message");

        session.reset();
        session.confirmSpawn(BED);
        session.tick(null, false, false);
        check(BED.equals(session.tick(BED, false, false)), "spawn confirmation may precede sleeping metadata");

        session.reset();
        check(BED.equals(session.tick(BED, false, true)), "opt-in records sleeping bags without a spawn");
        session.reset();
        check(session.tick(BED, false, false) == null, "reset removes another server's spawn confirmation");
        session.tick(null, false, false);
        session.confirmSpawn(BED);
        check(BED.equals(session.tick(null, false, false)), "confirmation shortly after waking still resolves the sleep");

        session.reset();
        session.confirmSpawn(BED);
        for (int i = 0; i < 50; i++) session.tick(null, false, false);
        check(session.tick(BED, false, false) == null, "stale spawn confirmation cannot mark a sleeping bag");
        for (int i = 0; i < 50; i++) session.tick(null, false, false);
        session.confirmSpawn(BED);
        check(session.tick(null, false, false) == null, "old sleep cannot consume an unrelated respawn-anchor message");

        session.reset();
        SleepPointSession.Point anchor = new SleepPointSession.Point("minecraft:overworld", 11, 65, 20);
        session.confirmSpawn(anchor);
        session.tick(null, false, false);
        check(session.tick(BED, false, false) == null,
                "a recent nearby respawn anchor cannot qualify a sleeping bag");
        session.confirmSpawn(BED);
        check(BED.equals(session.tick(BED, false, false)), "a confirmation must refer to the actual sleep position");

        session.reset();
        session.tick(BED, false, false);
        SleepPointSession.Point other = new SleepPointSession.Point("minecraft:the_nether", 10, 40, 20);
        check(other.equals(session.tick(other, false, true)), "changed sleep position is a distinct sleep");
        System.out.println("Sleep point session tests passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
