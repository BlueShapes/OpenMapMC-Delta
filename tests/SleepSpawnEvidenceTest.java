// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

public final class SleepSpawnEvidenceTest {
    public static void main(String[] args) {
        var anchor = new SleepPointSession.Point("minecraft:overworld", 1, 64, 2);
        var bed = new SleepPointSession.Point("minecraft:overworld", 3, 64, 2);
        SleepSpawnEvidence evidence = new SleepSpawnEvidence();
        evidence.interact(anchor, true, 0);
        evidence.interact(bed, false, 1);
        check(anchor.equals(evidence.confirm(2)), "delayed anchor response stays associated with anchor");
        check(bed.equals(evidence.confirm(3)), "the following bed confirmation resolves its own location");
        evidence.interact(anchor, true, 4);
        check(anchor.equals(evidence.confirm(5)), "a changed spawn can return to the anchor");
        evidence.interact(anchor, true, 6);
        evidence.interact(bed, false, 7);
        check(bed.equals(evidence.confirm(8)), "same confirmed anchor creates no expected response");
        evidence.reset();
        evidence.interact(anchor, false, 0);
        evidence.interact(bed, false, 1);
        check(bed.equals(evidence.confirm(2)), "charging or empty-anchor interaction cannot consume bed confirmation");
        check(evidence.confirm(6_000_000_000L) == null, "old interaction cannot claim an unrelated notification");
        evidence.reset();
        check(evidence.confirm(0) == null, "session reset removes previous server interactions");
        var otherAnchor = new SleepPointSession.Point("minecraft:overworld", 8, 64, 2);
        evidence.interact(anchor, true, 0);
        evidence.interact(otherAnchor, true, 1);
        evidence.interact(anchor, true, 2);
        evidence.interact(bed, false, 3);
        check(anchor.equals(evidence.confirm(4)), "first alternating anchor response is preserved");
        check(otherAnchor.equals(evidence.confirm(5)), "second alternating anchor response is preserved");
        check(anchor.equals(evidence.confirm(6)), "returning to an outstanding anchor expects another response");
        evidence.interact(otherAnchor, true, 7);
        evidence.interact(anchor, true, 8);
        evidence.interact(bed, false, 9);
        check(otherAnchor.equals(evidence.confirm(10)), "pending new spawn takes precedence over previous confirmation");
        check(anchor.equals(evidence.confirm(11)), "return to previously confirmed anchor still expects response");
        evidence.reset();
        for (int i = 0; i < 17; i++) {
            evidence.interact(new SleepPointSession.Point("minecraft:overworld", 50 + i, 64, 2), true, i);
        }
        evidence.interact(bed, false, 17);
        check(evidence.confirm(18) == null, "overflowed anchor response cannot claim the latest bed");
        for (int i = 1; i < 17; i++) {
            check(new SleepPointSession.Point("minecraft:overworld", 50 + i, 64, 2).equals(evidence.confirm(18 + i)),
                    "bounded anchor queue preserves the number and order of expected responses");
        }
        for (boolean oldVersion : new boolean[] {true, false}) {
            check(!SleepSpawnEvidence.anchorAttempt(0, true, false, false, oldVersion, true), "empty anchor skips");
            check(!SleepSpawnEvidence.anchorAttempt(1, true, true, false, oldVersion, true), "charging skips");
            check(!SleepSpawnEvidence.anchorAttempt(1, true, false, true, oldVersion, true), "offhand charging skips");
            check(!SleepSpawnEvidence.anchorAttempt(1, true, false, false, oldVersion, false), "exploding anchor skips");
            check(SleepSpawnEvidence.anchorAttempt(1, true, false, false, oldVersion, true), "charged spawn anchor qualifies");
            check(SleepSpawnEvidence.anchorAttempt(4, true, true, false, oldVersion, true), "full anchor no longer charges");
        }
        check(!SleepSpawnEvidence.anchorAttempt(4, true, false, true, true, true), "old version defers offhand fuel even when full");
        check(SleepSpawnEvidence.anchorAttempt(4, true, false, true, false, true), "new version full anchor uses main hand");
        check(SleepSpawnEvidence.anchorAttempt(1, false, false, false, true, true), "old version can set spawn from offhand");
        check(!SleepSpawnEvidence.anchorAttempt(1, false, false, false, false, true), "new version offhand cannot set spawn");
        System.out.println("Sleep spawn evidence tests passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
