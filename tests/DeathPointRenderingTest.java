// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;
public final class DeathPointRenderingTest {
    public static void main(String[] args) {
        deathBeaconLimits();
        System.out.println("Deathpoint beacon tests passed.");
    }
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
    private static void deathBeaconLimits() {
        check(Math.abs(WaypointRenderMath.deathBeaconPulse(0, 0, .25, 1) - 1) < .00001,
                "death beam begins at full opacity");
        check(Math.abs(WaypointRenderMath.deathBeaconPulse(20, 0, .25, 1) - .25) < .00001,
                "death beam reaches configured opacity floor");
        for (long tick = -40; tick < 80; tick++) {
            float custom = WaypointRenderMath.deathBeaconPulse(tick, .5F, .4, .7);
            check(custom >= .4F && custom <= .7F, "custom death beam limits bound each phase");
        }
        check(WaypointRenderMath.deathBeaconPulse(20, 0, .8, .2) >= .2F,
                "inverted config limits remain valid");
        check(Float.isFinite(WaypointRenderMath.deathBeaconPulse(Long.MAX_VALUE, Float.NaN, Double.NaN, Double.POSITIVE_INFINITY)),
                "malformed config and interpolation remain finite");
    }

}
