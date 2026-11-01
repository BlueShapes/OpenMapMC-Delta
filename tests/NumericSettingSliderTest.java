// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class NumericSettingSliderTest {
    public static void main(String[] args) throws Exception {
        testInitialValueAndTooltip();
        testNativeKeyboardGateAndPreciseStep();
        testDisabledSliderDoesNotChange();
        testDraggingAccumulatesAndReleaseSnaps();
        System.out.println("Numeric setting slider tests passed.");
    }

    private static void testInitialValueAndTooltip() {
        AtomicReference<Double> committed = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        NumericSettingSlider slider = slider("Transparency", 0.92, 0.1, 1.0, 0.01, false,
                value -> String.format(java.util.Locale.ROOT, "%.2f", value), committed, writes);
        equal(0.92, committed.get(), "initialization preserves saved value");
        equal(0, writes.get(), "initialization does not write settings");
        equal("Transparency: 0.92", slider.getMessage().getString(), "committed initial message");
        equal("Transparency: 0.92", slider.tooltip().message().getString(), "current-value tooltip");
    }

    private static void testNativeKeyboardGateAndPreciseStep() {
        AtomicReference<Double> committed = new AtomicReference<>(0.925);
        AtomicInteger writes = new AtomicInteger();
        NumericSettingSlider slider = slider("Scale", committed.get(), 0.1, 1.0, 0.01, false,
                value -> String.format(java.util.Locale.ROOT, "%.3f", value), committed, writes);
        double initialProgress = slider.progress();
        check(!pressKey(slider, 262), "arrow key should retain native focus navigation before selection");
        equal(0.925, committed.get(), "unselected slider value");
        equal(initialProgress, slider.progress(), "unselected slider thumb");

        check(pressKey(slider, 32), "selection key should enter slider edit mode");
        check(pressKey(slider, 262), "right arrow should be handled in edit mode");
        equal(0.935, committed.get(), "keyboard changes an off-grid value by one step");
        equal(1, writes.get(), "keyboard setter count");
        equal("Scale: 0.935", slider.getMessage().getString(), "keyboard message uses committed value");
        check(pressKey(slider, 257), "Enter should leave slider edit mode");
        check(!pressKey(slider, 262), "right arrow should resume native focus navigation");
        equal(0.935, committed.get(), "navigation preserves edited value");
        equal(1, writes.get(), "navigation does not write settings");
    }

    private static void testDisabledSliderDoesNotChange() {
        AtomicReference<Double> committed = new AtomicReference<>(0.5);
        AtomicInteger writes = new AtomicInteger();
        NumericSettingSlider slider = slider("Scale", 0.5, 0.0, 1.0, 0.01, false,
                value -> Double.toString(value), committed, writes);
        slider.active = false;
        check(!pressKey(slider, 262), "inactive slider should ignore arrows");
        equal(0.5, committed.get(), "inactive slider value");
        slider.active = true;
        slider.visible = false;
        check(!pressKey(slider, 262), "hidden slider should ignore arrows");
        equal(0, writes.get(), "disabled slider setter count");
    }

    private static void testDraggingAccumulatesAndReleaseSnaps() throws Exception {
        AtomicReference<Double> committed = new AtomicReference<>(5.0);
        AtomicInteger writes = new AtomicInteger();
        NumericSettingSlider slider = slider("Count", 5.0, 0.0, 10.0, 1.0, false,
                value -> Integer.toString((int) value), committed, writes);
        slider.moveThumbTo(0.51);
        slider.moveThumbTo(0.54);
        check(slider.progress() > 0.53, "small drag motions should accumulate before the next step");
        slider.moveThumbTo(0.56);
        equal(6.0, committed.get(), "drag commits the next step");
        equal(0.56, slider.progress(), "drag keeps pointer progress until release");
        equal(1, writes.get(), "mouse setter only runs for changed values");

        release(slider);
        equal(0.6, slider.progress(), "release snaps thumb to committed value");
    }

    private static NumericSettingSlider slider(String label, double initial, double minimum, double maximum,
                                                double step, boolean logarithmic,
                                                java.util.function.DoubleFunction<String> formatter,
                                                AtomicReference<Double> committed, AtomicInteger writes) {
        committed.set(initial);
        return new NumericSettingSlider(0, 0, 200, 20, label,
                new SettingSliderRange(minimum, maximum, step, logarithmic), committed::get,
                value -> { committed.set(value); writes.incrementAndGet(); }, formatter, Component.empty());
    }

    private static boolean pressKey(NumericSettingSlider slider, int key) {
        try {
            Method legacy = NumericSettingSlider.class.getDeclaredMethod("keyPressed", int.class, int.class, int.class);
            return (boolean) legacy.invoke(slider, key, 0, 0);
        } catch (NoSuchMethodException modernApi) {
            try {
                Method modern = NumericSettingSlider.class.getDeclaredMethod("keyPressed", KeyEvent.class);
                return (boolean) modern.invoke(slider, new KeyEvent(key));
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void release(NumericSettingSlider slider) throws Exception {
        try {
            Method legacy = NumericSettingSlider.class.getDeclaredMethod("onRelease", double.class, double.class);
            legacy.invoke(slider, 0.0, 0.0);
        } catch (NoSuchMethodException modernApi) {
            Method modern = NumericSettingSlider.class.getDeclaredMethod("onRelease", MouseButtonEvent.class);
            modern.invoke(slider, new MouseButtonEvent());
        }
    }

    private static void equal(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected '" + expected + "', got '" + actual + "'");
    }

    private static void equal(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void equal(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
