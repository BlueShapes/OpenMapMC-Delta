package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.config.ControllerHintMode;

/** Focused executable tests for the fullscreen controller-hint visibility policy. */
public final class ControllerHintSessionTest {
    private static int checks;

    public static void main(String[] args) {
        firstTimeStartsOnFirstEligibleDrawAndMarksSeenOnce();
        firstTimeExpiresAtBoundaryAndDoesNotRestart();
        ineligibleStateDoesNotConsumeFirstTime();
        alreadySeenSuppressesANewSession();
        alwaysAndNeverIgnoreSeenState();
        modeChangesStartANewPolicySession();
        backwardClockDoesNotRestartTheWindow();
        check(ControllerHintMode.values()[0] == ControllerHintMode.FIRST_TIME,
                "FIRST_TIME is the default enum value");
        System.out.println("Controller hint session passed " + checks + " checks.");
    }

    private static void firstTimeStartsOnFirstEligibleDrawAndMarksSeenOnce() {
        ControllerHintSession session = new ControllerHintSession();

        ControllerHintSession.Decision beforeEligible = session.update(
                ControllerHintMode.FIRST_TIME, false, false, 1000);
        check(!beforeEligible.visible() && !beforeEligible.markSeen(),
                "an ineligible map does not start or consume the first-time hint");

        ControllerHintSession.Decision firstDraw = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 5000);
        check(firstDraw.visible() && firstDraw.markSeen(),
                "first eligible draw starts the window and requests persistence");

        ControllerHintSession.Decision subsequentDraw = session.update(
                ControllerHintMode.FIRST_TIME, true, true, 12000);
        check(subsequentDraw.visible() && !subsequentDraw.markSeen(),
                "persisting seen does not hide the active window or request persistence twice");
    }

    private static void firstTimeExpiresAtBoundaryAndDoesNotRestart() {
        ControllerHintSession session = new ControllerHintSession();
        ControllerHintSession.Decision start = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 20000);
        check(start.visible() && start.markSeen(), "first-time window begins on an eligible draw");

        ControllerHintSession.Decision justBeforeEnd = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 27999);
        check(justBeforeEnd.visible() && !justBeforeEnd.markSeen(),
                "hint remains visible until the eight-second boundary");

        ControllerHintSession.Decision atEnd = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 28000);
        check(!atEnd.visible() && !atEnd.markSeen(), "hint expires at exactly eight seconds");

        ControllerHintSession.Decision afterEnd = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 45000);
        check(!afterEnd.visible() && !afterEnd.markSeen(),
                "an expired window does not restart if persistence failed");
    }

    private static void alreadySeenSuppressesANewSession() {
        ControllerHintSession session = new ControllerHintSession();
        ControllerHintSession.Decision decision = session.update(
                ControllerHintMode.FIRST_TIME, true, true, 100);
        check(!decision.visible() && !decision.markSeen(),
                "a fresh screen session honors the persisted seen state");
    }

    private static void ineligibleStateDoesNotConsumeFirstTime() {
        ControllerHintSession session = new ControllerHintSession();
        ControllerHintSession.Decision absent = session.update(
                ControllerHintMode.FIRST_TIME, false, false, 100);
        ControllerHintSession.Decision hiddenThenEligible = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 9000);
        check(!absent.visible() && !absent.markSeen(),
                "controller absence, keyboard-only mode, or hidden map is ineligible");
        check(hiddenThenEligible.visible() && hiddenThenEligible.markSeen(),
                "the first eligible draw still starts the first-time window");

        ControllerHintSession.Decision hiddenDuringWindow = session.update(
                ControllerHintMode.FIRST_TIME, true, false, 11000);
        ControllerHintSession.Decision resumedAfterElapsedWindow = session.update(
                ControllerHintMode.FIRST_TIME, true, true, 18000);
        check(!hiddenDuringWindow.visible() && !hiddenDuringWindow.markSeen(),
                "ineligible frames hide an active hint");
        check(!resumedAfterElapsedWindow.visible() && !resumedAfterElapsedWindow.markSeen(),
                "time spent ineligible may elapse the already-started window");
    }

    private static void alwaysAndNeverIgnoreSeenState() {
        ControllerHintSession always = new ControllerHintSession();
        ControllerHintSession.Decision alwaysEligible = always.update(
                ControllerHintMode.ALWAYS, true, true, 0);
        ControllerHintSession.Decision alwaysIneligible = always.update(
                ControllerHintMode.ALWAYS, false, false, 1);
        check(alwaysEligible.visible() && !alwaysEligible.markSeen(),
                "ALWAYS shows on eligible frames without changing seen state");
        check(!alwaysIneligible.visible() && !alwaysIneligible.markSeen(),
                "ALWAYS still respects display eligibility");

        ControllerHintSession never = new ControllerHintSession();
        ControllerHintSession.Decision neverEligible = never.update(
                ControllerHintMode.NEVER, false, true, 0);
        check(!neverEligible.visible() && !neverEligible.markSeen(),
                "NEVER hides even when eligible and unseen");
    }

    private static void modeChangesStartANewPolicySession() {
        ControllerHintSession session = new ControllerHintSession();
        ControllerHintSession.Decision first = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 1000);
        ControllerHintSession.Decision always = session.update(
                ControllerHintMode.ALWAYS, true, true, 2000);
        ControllerHintSession.Decision backToFirstTime = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 9000);
        check(first.visible() && first.markSeen(), "initial FIRST_TIME mode starts its session");
        check(always.visible() && !always.markSeen(), "ALWAYS mode shows independently");
        check(backToFirstTime.visible() && backToFirstTime.markSeen(),
                "returning to FIRST_TIME with seen reset starts a fresh window");

        ControllerHintSession.Decision never = session.update(
                ControllerHintMode.NEVER, false, true, 10000);
        check(!never.visible() && !never.markSeen(), "NEVER mode hides immediately");
        ControllerHintSession.Decision firstAgain = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 12000);
        check(firstAgain.visible() && firstAgain.markSeen(),
                "a later mode change resets FIRST_TIME consumption too");
    }

    private static void backwardClockDoesNotRestartTheWindow() {
        ControllerHintSession session = new ControllerHintSession();
        ControllerHintSession.Decision start = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 10000);
        ControllerHintSession.Decision clockMovedBack = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 9000);
        ControllerHintSession.Decision originalDeadline = session.update(
                ControllerHintMode.FIRST_TIME, false, true, 18000);
        check(start.visible() && start.markSeen(), "initial clock value starts the window");
        check(clockMovedBack.visible() && !clockMovedBack.markSeen(),
                "a backward clock adjustment does not restart or consume another window");
        check(!originalDeadline.visible(), "the original deadline remains authoritative");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
