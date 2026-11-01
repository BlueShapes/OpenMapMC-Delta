// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numeric range mapping shared by settings sliders and dependency-free tests. */
public final class SettingSliderRange {
    private final double minimum;
    private final double maximum;
    private final double step;
    private final boolean logarithmic;
    private final double logMaximum;

    public SettingSliderRange(double minimum, double maximum, double step, boolean logarithmic) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step)
                || minimum >= maximum || step <= 0.0 || (logarithmic && minimum < 0.0)) {
            throw new IllegalArgumentException("Invalid slider range");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.logarithmic = logarithmic;
        this.logMaximum = logarithmic
                ? (minimum == 0.0 ? Math.log1p(maximum) : Math.log(maximum / minimum))
                : 0.0;
    }

    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public double step() { return step; }
    public boolean logarithmic() { return logarithmic; }

    /** Maps a committed value to the slider's normalized 0..1 position without snapping it. */
    public double toProgress(double value) {
        double clamped = clamp(value);
        if (clamped <= minimum) return 0.0;
        if (clamped >= maximum) return 1.0;
        if (!logarithmic) return (clamped - minimum) / (maximum - minimum);
        if (minimum == 0.0) return Math.log1p(clamped) / logMaximum;
        return Math.log(clamped / minimum) / logMaximum;
    }

    /** Maps a normalized slider position onto the nearest valid step. */
    public double fromProgress(double progress) {
        double normalized = Math.max(0.0, Math.min(1.0, progress));
        if (normalized <= 0.0) return minimum;
        if (normalized >= 1.0) return maximum;
        double value;
        if (!logarithmic) {
            value = minimum + normalized * (maximum - minimum);
        } else if (minimum == 0.0) {
            value = Math.expm1(normalized * logMaximum);
        } else {
            value = minimum * Math.exp(normalized * logMaximum);
        }
        return snap(value);
    }

    /** Clamps and rounds to the nearest step anchored at the minimum. */
    public double snap(double value) {
        double clamped = clamp(value);
        if (clamped <= minimum || clamped >= maximum) return clamped;
        BigDecimal low = BigDecimal.valueOf(minimum);
        BigDecimal increment = BigDecimal.valueOf(step);
        BigDecimal steps = BigDecimal.valueOf(clamped).subtract(low)
                .divide(increment, 0, RoundingMode.HALF_UP);
        double snapped = low.add(increment.multiply(steps)).doubleValue();
        return clamp(snapped);
    }

    /** Moves by one precise step while preserving a valid off-grid starting value. */
    public double stepBy(double value, int direction) {
        if (direction == 0) return clamp(value);
        BigDecimal changed = BigDecimal.valueOf(clamp(value)).add(
                BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(Integer.signum(direction))));
        return clamp(changed.doubleValue());
    }

    private double clamp(double value) {
        if (Double.isNaN(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
