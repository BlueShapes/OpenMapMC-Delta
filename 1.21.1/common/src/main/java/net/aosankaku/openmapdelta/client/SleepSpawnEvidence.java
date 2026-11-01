// Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Associates standard spawn notifications with block interactions, including delayed anchor replies. */
public final class SleepSpawnEvidence {
    private static final long WINDOW_NANOS = 5_000_000_000L;
    private record Anchor(SleepPointSession.Point point, long time) {}
    private final Deque<Anchor> anchors = new ArrayDeque<>();
    private SleepPointSession.Point interaction;
    private long interactionTime;
    private SleepPointSession.Point confirmed;
    private int discardedAnchorReplies;
    private long lastAnchorTime;

    public void interact(SleepPointSession.Point point, boolean anchorAttempt, long now) {
        expire(now);
        interaction = point;
        interactionTime = now;
        SleepPointSession.Point expectedSpawn = anchors.isEmpty() ? confirmed : anchors.peekLast().point();
        if (anchorAttempt && point != null && !point.equals(expectedSpawn)) {
            if (anchors.size() >= 16) {
                anchors.removeFirst();
                discardedAnchorReplies++;
            }
            anchors.addLast(new Anchor(point, now));
            lastAnchorTime = now;
        }
    }

    public SleepPointSession.Point confirm(long now) {
        expire(now);
        if (discardedAnchorReplies > 0) {
            discardedAnchorReplies--;
            return null;
        }
        SleepPointSession.Point point = !anchors.isEmpty() ? anchors.removeFirst().point()
                : interaction != null && now - interactionTime <= WINDOW_NANOS ? interaction : null;
        if (point != null) confirmed = point;
        return point;
    }

    private void expire(long now) {
        while (!anchors.isEmpty() && now - anchors.peekFirst().time() > WINDOW_NANOS) anchors.removeFirst();
        if (now - lastAnchorTime > WINDOW_NANOS) discardedAnchorReplies = 0;
    }

    /** Vanilla's charge/fuel/hand rules, evaluated before client prediction mutates the block. */
    public static boolean anchorAttempt(int charges, boolean mainHand, boolean currentFuel,
                                        boolean offhandFuel, boolean supportsOffhandSpawn,
                                        boolean environmentAllowsSpawn) {
        if (charges <= 0 || !environmentAllowsSpawn || (!mainHand && !supportsOffhandSpawn)) return false;
        if (currentFuel && charges < 4) return false;
        return !(mainHand && !currentFuel && offhandFuel && (supportsOffhandSpawn || charges < 4));
    }

    public void reset() {
        anchors.clear();
        interaction = null;
        confirmed = null;
        discardedAnchorReplies = 0;
    }
}
