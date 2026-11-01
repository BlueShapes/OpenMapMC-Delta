// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.client;

import java.util.Locale;

/** Minecraft daytime formatting and small, dependency-free helpers for minimap information. */
public final class MinimapInfoMath {
    public static final long TICKS_PER_DAY = 24_000L;

    public record Clock(int hour, int minute) {}

    private MinimapInfoMath() {}

    public static long normalizedDayTick(long daytime) {
        return Math.floorMod(daytime, TICKS_PER_DAY);
    }

    public static long worldDay(long daytime) {
        return Math.floorDiv(daytime, TICKS_PER_DAY);
    }

    /** Daytime zero is 06:00, matching the in-game sun and the vanilla clock. */
    public static Clock clock24(long daytime) {
        long clockTick = Math.floorMod(normalizedDayTick(daytime) + 6_000L, TICKS_PER_DAY);
        return new Clock((int) (clockTick / 1_000L), (int) ((clockTick % 1_000L) * 60L / 1_000L));
    }

    public static Clock clock12(long daytime) {
        Clock clock = clock24(daytime);
        int hour = clock.hour() % 12;
        return new Clock(hour == 0 ? 12 : hour, clock.minute());
    }

    /** Treats each daytime tick as the configured number of seconds and displays MM:SS. */
    public static String customClock(long daytime, double secondsPerTick) {
        double tickSeconds = Double.isFinite(secondsPerTick) && secondsPerTick > 0.0 ? secondsPerTick : 0.05;
        long seconds = (long) Math.floor(normalizedDayTick(daytime) * tickSeconds);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String formatClock(Clock clock) {
        return String.format(Locale.ROOT, "%02d:%02d", clock.hour(), clock.minute());
    }

    public static String formatClock12(long daytime, String period) {
        return formatClock(clock12(daytime)) + " " + period;
    }

    public static String formatDuration(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3_600L;
        long minutes = safeSeconds / 60L % 60L;
        long remainder = safeSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
}
