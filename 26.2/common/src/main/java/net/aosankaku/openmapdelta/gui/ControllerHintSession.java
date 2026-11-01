package net.aosankaku.openmapdelta.gui;

import java.util.Objects;
import net.aosankaku.openmapdelta.config.ControllerHintMode;

/** Pure visibility policy for fullscreen controller hints. */
public final class ControllerHintSession {
    public static final long DISPLAY_MILLIS = 8_000L;

    private ControllerHintMode mode;
    private boolean firstTimeStarted;
    private boolean firstTimeExpired;
    private boolean firstTimeSuppressed;
    private long firstTimeStartedAt;
    private long lastObservedTime;
    private boolean hasObservedTime;

    /**
     * Evaluates whether hints can be drawn for this frame.
     *
     * @param mode selected hint mode
     * @param seen whether this player has previously seen the first-time hints
     * @param eligible whether a nonempty controller hint can actually be drawn
     * @param nowMillis current time in milliseconds, supplied by the caller
     */
    public Decision update(ControllerHintMode mode, boolean seen, boolean eligible, long nowMillis) {
        Objects.requireNonNull(mode, "mode");
        boolean modeChanged = this.mode != mode;
        if (modeChanged) {
            this.mode = mode;
            resetFirstTimeState();
            lastObservedTime = nowMillis;
            hasObservedTime = true;
        } else if (!hasObservedTime || nowMillis > lastObservedTime) {
            lastObservedTime = nowMillis;
            hasObservedTime = true;
        }
        long effectiveNow = lastObservedTime;

        return switch (mode) {
            case ALWAYS -> new Decision(eligible, false);
            case NEVER -> new Decision(false, false);
            case FIRST_TIME -> updateFirstTime(seen, eligible, effectiveNow);
        };
    }

    private Decision updateFirstTime(boolean seen, boolean eligible, long nowMillis) {
        if (firstTimeStarted) {
            if (hasElapsed(firstTimeStartedAt, nowMillis, DISPLAY_MILLIS)) {
                firstTimeExpired = true;
            }
            return new Decision(eligible && !firstTimeExpired, false);
        }

        if (seen) {
            firstTimeSuppressed = true;
        }
        if (firstTimeSuppressed || !eligible) {
            return new Decision(false, false);
        }

        firstTimeStarted = true;
        firstTimeStartedAt = nowMillis;
        return new Decision(true, true);
    }

    private void resetFirstTimeState() {
        firstTimeStarted = false;
        firstTimeExpired = false;
        firstTimeSuppressed = false;
        firstTimeStartedAt = 0L;
    }

    private static boolean hasElapsed(long startMillis, long nowMillis, long durationMillis) {
        if (startMillis > Long.MAX_VALUE - durationMillis) {
            return false;
        }
        return nowMillis >= startMillis + durationMillis;
    }

    public record Decision(boolean visible, boolean markSeen) {}
}
