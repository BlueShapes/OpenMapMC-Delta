// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.config.MinimapInfoSlot;
import net.aosankaku.openmapdelta.config.MinimapInfoType;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Six independently configurable slots with a searchable, paged choice list. */
public final class MinimapInfoScreen extends Screen {
    private final Screen parent;
    private MinimapInfoSlot selectedSlot = MinimapInfoSlot.TOP;
    private final List<Button> slotButtons = new ArrayList<>();
    private final List<Button> choices = new ArrayList<>();
    private List<MinimapInfoType> filtered = List.of();
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;
    private int panelX;
    private int panelWidth;
    private int rows;

    public MinimapInfoScreen(Screen parent) {
        super(Component.translatable("screen.odysseymap.minimap_info"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        slotButtons.clear();
        choices.clear();
        panelWidth = Math.min(420, Math.max(120, width - 16));
        panelX = (width - panelWidth) / 2;
        rows = Math.max(1, (height - 166) / 22);
        int slotWidth = (panelWidth - 8) / 3;
        MinimapInfoSlot[] slots = {MinimapInfoSlot.TOP_LEFT, MinimapInfoSlot.TOP, MinimapInfoSlot.TOP_RIGHT,
                MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoSlot.BOTTOM, MinimapInfoSlot.BOTTOM_RIGHT};
        for (int i = 0; i < slots.length; i++) {
            MinimapInfoSlot slot = slots[i];
            Button button = Button.builder(slotLabel(slot), ignored -> {
                selectedSlot = slot;
                page = 0;
                updateSlots();
                filter();
            }).bounds(panelX + (i % 3) * (slotWidth + 4), 24 + (i / 3) * 22, slotWidth, 20).build();
            slotButtons.add(addRenderableWidget(button));
        }
        search = addRenderableWidget(new EditBox(font, panelX, 72, panelWidth, 18,
                Component.translatable("gui.odysseymap.search")));
        search.setHint(Component.translatable("gui.odysseymap.search"));
        search.setResponder(text -> { page = 0; filter(); });
        int footer = height - 70;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> { page--; rebuild(); })
                .bounds(panelX, footer, 26, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> { page++; rebuild(); })
                .bounds(panelX + panelWidth - 26, footer, 26, 18).build());
        int controlWidth = (panelWidth - 8) / 2;
        addRenderableWidget(new NumericSettingSlider(panelX, height - 40, controlWidth, 18,
                Component.translatable("gui.odysseymap.setting.info_tick_seconds").getString(),
                new SettingSliderRange(0.001, 3600.0, 0.001, true),
                () -> OpenMapDeltaConfig.INFO_TICK_SECONDS.get(), value -> {
                    if (Double.compare(OpenMapDeltaConfig.INFO_TICK_SECONDS.get(), value) != 0) {
                        OpenMapDeltaConfig.INFO_TICK_SECONDS.set(value);
                        OpenMapDeltaConfig.save();
                    }
                }, MinimapInfoScreen::formatTickSeconds,
                Component.translatable("gui.odysseymap.info.tick_seconds_hint")));
        addRenderableWidget(new NumericSettingSlider(panelX + (panelWidth + 8) / 2, height - 40, controlWidth, 18,
                Component.translatable("gui.odysseymap.setting.info_speed_seconds").getString(),
                new SettingSliderRange(1, 60, 1, false),
                () -> OpenMapDeltaConfig.INFO_SPEED_SECONDS.get(), value -> {
                    int nextValue = (int) Math.round(value);
                    if (OpenMapDeltaConfig.INFO_SPEED_SECONDS.get() != nextValue) {
                        OpenMapDeltaConfig.INFO_SPEED_SECONDS.set(nextValue);
                        OpenMapDeltaConfig.save();
                    }
                }, value -> Integer.toString((int) Math.round(value)),
                Component.translatable("gui.odysseymap.info.speed_seconds_hint")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 45, height - 20, 90, 18).build());
        updateSlots();
        filter();
    }

    private Component slotLabel(MinimapInfoSlot slot) {
        return Component.literal(Component.translatable(slot.translationKey()).getString() + ": "
                + Component.translatable(OpenMapDeltaConfig.infoSlot(slot).get().translationKey()).getString());
    }

    private void updateSlots() {
        MinimapInfoSlot[] slots = {MinimapInfoSlot.TOP_LEFT, MinimapInfoSlot.TOP, MinimapInfoSlot.TOP_RIGHT,
                MinimapInfoSlot.BOTTOM_LEFT, MinimapInfoSlot.BOTTOM, MinimapInfoSlot.BOTTOM_RIGHT};
        for (int i = 0; i < slotButtons.size(); i++) {
            Button button = slotButtons.get(i);
            button.setMessage(slotLabel(slots[i]));
            button.setTooltip(Tooltip.create(button.getMessage()));
            button.active = selectedSlot != slots[i];
        }
    }

    private void filter() {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered = Arrays.stream(MinimapInfoType.values()).filter(selectedSlot::accepts)
                .filter(type -> query.isEmpty() || Component.translatable(type.translationKey()).getString()
                        .toLowerCase(Locale.ROOT).contains(query) || type.name().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        rebuild();
    }

    private void rebuild() {
        for (Button button : choices) removeWidget(button);
        choices.clear();
        int perPage = rows * 2;
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / perPage)));
        int columnWidth = (panelWidth - 4) / 2;
        for (int i = 0; i < perPage && page * perPage + i < filtered.size(); i++) {
            MinimapInfoType type = filtered.get(page * perPage + i);
            Component name = Component.translatable(type.translationKey());
            Button button = Button.builder(name, ignored -> {
                OpenMapDeltaConfig.infoSlot(selectedSlot).set(type);
                OpenMapDeltaConfig.save();
                updateSlots();
                rebuild();
            }).bounds(panelX + (i % 2) * (columnWidth + 4), 96 + (i / 2) * 22, columnWidth, 20).build();
            button.active = OpenMapDeltaConfig.infoSlot(selectedSlot).get() != type;
            button.setTooltip(Tooltip.create(name));
            choices.add(addRenderableWidget(button));
        }
        previous.active = page > 0;
        next.active = (page + 1) * perPage < filtered.size();
    }

    private static String formatTickSeconds(double value) {
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("\\.?0+$", "");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.odysseymap.info.slot_hint"), width / 2,
                height - 64, 0xFFCCCCCC);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
