// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.aosankaku.openmapdelta.marker.MarkerIconRenderer;
import net.aosankaku.openmapdelta.marker.MarkerIconSearchQuery;
import net.aosankaku.openmapdelta.marker.MarkerIconSearchText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Searchable, paged item, mob, and built-in symbol icons. */
public final class MarkerIconPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelect;
    private final String selectedId;
    private final List<Button> iconButtons = new ArrayList<>();
    private List<MarkerIconCatalog.Entry> filtered = List.of();
    private EditBox search;
    private Button previous;
    private Button next;
    private int page;
    private int category;
    private boolean emojisAvailable;
    private long emojiRevision;
    private int columns;
    private int rows;
    private int gridX;
    private int gridY;

    public MarkerIconPickerScreen(Screen parent, String selectedId, Consumer<String> onSelect) {
        super(Component.translatable("screen.odysseymap.icon_picker"));
        this.parent = parent;
        this.selectedId = selectedId;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        columns = Math.max(1, Math.min(10, (width - 24) / 28));
        rows = Math.max(1, Math.min(6, (height - 100) / 28));
        gridX = (width - columns * 28) / 2;
        gridY = 64;
        emojisAvailable = MarkerIconCatalog.emojifulAvailable();
        emojiRevision = MarkerIconCatalog.emojifulRevision();
        search = addRenderableWidget(new EditBox(font, gridX, 24, columns * 28, 16,
                Component.translatable("gui.odysseymap.search")));
        search.setHint(Component.translatable("gui.odysseymap.icon_search_hint"));
        search.addFormatter((text, cursor) -> MarkerIconSearchText.syntax(text));
        search.setResponder(text -> { page = 0; filter(); });
        int categoryCount = emojisAvailable ? 5 : 4;
        for (int i = 0; i < categoryCount; i++) {
            int selected = i;
            String key = switch (i) {
                case 0 -> "gui.odysseymap.icons_all";
                case 1 -> "gui.odysseymap.icons_items";
                case 2 -> "gui.odysseymap.icons_mobs";
                case 3 -> "gui.odysseymap.icons_symbols";
                default -> "gui.odysseymap.icons_emojis";
            };
            addRenderableWidget(Button.builder(Component.translatable(key), button -> {
                category = selected; page = 0; filter();
            }).bounds(gridX + i * columns * 28 / categoryCount, 43,
                    Math.max(1, columns * 28 / categoryCount - 2), 18).build());
        }
        int footer = Math.min(height - 24, gridY + rows * 28 + 4);
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuild(); })
                .bounds(gridX, footer, 22, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuild(); })
                .bounds(gridX + columns * 28 - 22, footer, 22, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), button -> onClose())
                .bounds(width / 2 - 40, footer, 80, 18).build());
        filter();
        setInitialFocus(search);
    }

    private void filter() {
        MarkerIconSearchQuery.Query query = MarkerIconSearchQuery.parse(search.getValue());
        filtered = MarkerIconCatalog.entries().stream()
                .filter(entry -> entry.id().isEmpty() || category == 0
                        || category == 1 && entry.id().startsWith("item:")
                        || category == 2 && entry.id().startsWith("mob:")
                        || category == 3 && entry.id().startsWith("symbol:")
                        || category == 4 && entry.id().startsWith("emoji:"))
                .filter(entry -> MarkerIconSearchQuery.matches(query, entry.metadata())).toList();
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (emojisAvailable) {
            long currentRevision = MarkerIconCatalog.emojifulRevision();
            if (currentRevision != emojiRevision) {
                emojiRevision = currentRevision;
                page = 0;
                filter();
            }
        }
    }

    private void rebuild() {
        for (Button button : iconButtons) removeWidget(button);
        iconButtons.clear();
        int perPage = columns * rows;
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / perPage)));
        for (int i = 0; i < perPage && page * perPage + i < filtered.size(); i++) {
            MarkerIconCatalog.Entry entry = filtered.get(page * perPage + i);
            Button button = Button.builder(Component.empty(), ignored -> {
                onSelect.accept(entry.id());
                onClose();
            }).bounds(gridX + i % columns * 28, gridY + i / columns * 28, 26, 26).build();
            button.setTooltip(Tooltip.create(MarkerIconSearchText.tooltip(entry, search.getValue())));
            iconButtons.add(addRenderableWidget(button));
        }
        previous.active = page > 0;
        next.active = (page + 1) * perPage < filtered.size();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int perPage = columns * rows;
        for (int i = 0; i < iconButtons.size(); i++) {
            Button button = iconButtons.get(i);
            MarkerIconCatalog.Entry entry = filtered.get(page * perPage + i);
            MarkerIconRenderer.drawEntry(graphics, entry, button.getX() + 13, button.getY() + 13, 16);
            if (entry.id().equals(selectedId)) graphics.outline(button.getX(), button.getY(), 26, 26, 0xFFFFFFFF);
        }
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
