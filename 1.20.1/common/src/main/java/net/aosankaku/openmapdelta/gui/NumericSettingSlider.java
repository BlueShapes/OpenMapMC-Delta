// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/** A slider that commits only user-selected values and supports precise arrow-key adjustment. */
public final class NumericSettingSlider extends AbstractSliderButton {
    private final String label;
    private final SettingSliderRange range;
    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final DoubleFunction<String> formatter;
    private final Component tooltipHint;
    private double displayedValue;
    private int pendingKeyboardDirection;

    public NumericSettingSlider(int x, int y, int width, int height, String label,
                                SettingSliderRange range, DoubleSupplier getter, DoubleConsumer setter,
                                DoubleFunction<String> formatter, Component tooltipHint) {
        super(x, y, width, height, Component.empty(), range.toProgress(getter.getAsDouble()));
        this.label = label;
        this.range = range;
        this.getter = getter;
        this.setter = setter;
        this.formatter = formatter;
        this.tooltipHint = tooltipHint;
        this.displayedValue = getter.getAsDouble();
        refreshMessage();
    }

    @Override
    protected void updateMessage() {
        // AbstractSliderButton can dispatch this virtually during construction, before fields exist.
        if (range != null && formatter != null) {
            displayedValue = getter.getAsDouble();
            refreshMessage();
        }
    }

    @Override
    protected void applyValue() {
        double current = getter.getAsDouble();
        double selected = pendingKeyboardDirection == 0
                ? range.fromProgress(value) : range.stepBy(current, pendingKeyboardDirection);
        if (Double.compare(selected, current) != 0) setter.accept(selected);
        displayedValue = getter.getAsDouble();
        if (pendingKeyboardDirection != 0) value = range.toProgress(displayedValue);
        refreshMessage();
    }

    private void refreshMessage() {
        String text = label.isEmpty() ? formatter.apply(displayedValue)
                : label + ": " + formatter.apply(displayedValue);
        setMessage(Component.literal(text));
        Component tooltip = Component.literal(text);
        if (!tooltipHint.getString().isEmpty()) tooltip = tooltip.copy().append("\n").append(tooltipHint);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) return false;
        pendingKeyboardDirection = keyCode == 263 ? -1 : keyCode == 262 ? 1 : 0;
        try {
            return super.keyPressed(keyCode, scanCode, modifiers);
        } finally {
            pendingKeyboardDirection = 0;
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        snapThumbToCommittedValue();
    }

    private void snapThumbToCommittedValue() {
        displayedValue = getter.getAsDouble();
        value = range.toProgress(displayedValue);
        refreshMessage();
    }

    /** Refresh a value changed through a paired direct-entry field without applying a slider edit. */
    public void syncToGetter() {
        displayedValue = getter.getAsDouble();
        value = range.toProgress(displayedValue);
        refreshMessage();
    }
}
