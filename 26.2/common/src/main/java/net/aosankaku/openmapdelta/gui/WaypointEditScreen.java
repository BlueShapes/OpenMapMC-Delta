// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.aosankaku.openmapdelta.marker.MarkerIconRenderer;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class WaypointEditScreen extends Screen {
    public static final int[] PRESET_COLORS = {
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
            0xFF55FFFF, 0xFF5555FF, 0xFFAA55FF, 0xFFFFFFFF,
            0xFFFF55AA, 0xFFAAAAAA
    };

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 234;
    private static final int PAD = 10;
    private static final int LABEL_H = 10;
    private static final int LABEL_GAP = 4;
    private static final int ROW_GAP = 6;
    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_PAD = 4;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;

    private final Screen parent;
    private final boolean editMode;
    private final boolean sharedMode;
    private final UUID editId;
    private final ResourceKey<Level> dimension;

    private EditBox nameBox;
    private EditBox categoryBox;
    private String draftCategory = "";
    private boolean stationMode;
    private boolean deathMode;
    private int categoryLabelY;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox hexColorBox;
    private int selectedColor;
    private String selectedIcon = "";
    private Button iconButton;
    private Button iconPreviewButton;
    private int swatchX;
    private int swatchY;

    private int panelLeft;
    private int panelTop;
    private int nameLabelY;
    private int coordLabelY;
    private int colorLabelY;

    private int pendingX;
    private int pendingY;
    private int pendingZ;
    private String draftName;
    private String draftX;
    private String draftY;
    private String draftZ;
    private String draftColor;
    private WaypointEditDraft baselineDraft;

    public static WaypointEditScreen forCreate(Screen parent, ResourceKey<Level> dimension,
                                               int x, int y, int z, String defaultName) {
        return new WaypointEditScreen(parent, false, null, dimension, x, y, z,
                defaultName, MarkerType.WAYPOINT.defaultColor, false);
    }

    public static WaypointEditScreen forSharedCreate(Screen parent, ResourceKey<Level> dimension,
                                                    int x, int y, int z, String defaultName) {
        return new WaypointEditScreen(parent, false, null, dimension, x, y, z,
                defaultName, MarkerType.SHARED_WAYPOINT.defaultColor, true);
    }

    public static WaypointEditScreen forEdit(Screen parent, Marker marker) {
        WaypointEditScreen screen = new WaypointEditScreen(parent, true, marker.getId(), marker.getDimension(),
                marker.getX(), marker.hasKnownY() ? marker.getY() : 64, marker.getZ(),
                marker.getLabel(), marker.getColor(), marker.isShared());
        screen.selectedIcon = marker.getIconId();
        screen.draftCategory = marker.getCategory();
        screen.stationMode = marker.getType() == MarkerType.TRANSIT_STATION;
        screen.deathMode = marker.getType() == MarkerType.DEATH;
        screen.baselineDraft = screen.captureDraft();
        return screen;
    }

    private WaypointEditScreen(Screen parent, boolean editMode, UUID editId,
                               ResourceKey<Level> dimension, int x, int y, int z,
                               String defaultName, int defaultColor, boolean sharedMode) {
        super(Component.translatable(sharedMode && !editMode ? "screen.odysseymap.shared_waypoint_create"
                : editMode ? "screen.odysseymap.waypoint_edit" : "screen.odysseymap.waypoint_create"));
        this.parent = parent;
        this.editMode = editMode;
        this.sharedMode = sharedMode;
        this.editId = editId;
        this.dimension = dimension;
        this.selectedColor = defaultColor;
        this.pendingX = x;
        this.pendingY = y;
        this.pendingZ = z;
        this.draftName = defaultName;
        this.draftX = String.valueOf(x);
        this.draftY = String.valueOf(y);
        this.draftZ = String.valueOf(z);
        this.draftColor = String.format("#%06X", defaultColor & 0xFFFFFF);
        this.baselineDraft = captureDraft();
    }

    @Override
    protected void init() {
        if (nameBox != null) captureDraft();
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
        int y = panelTop + 24;

        addRenderableOnly(this::renderPanel);

        nameLabelY = y;
        y += LABEL_H;
        nameBox = new EditBox(font, panelLeft + PAD, y, PANEL_W - PAD * 2, FIELD_H, Component.empty());
        nameBox.setMaxLength(Math.max(256, draftName.length()));
        nameBox.setValue(draftName);
        addRenderableWidget(nameBox);
        y += FIELD_H + ROW_GAP;

        categoryLabelY = y;
        y += LABEL_H;
        categoryBox = new EditBox(font, panelLeft + PAD, y, PANEL_W - PAD * 2, FIELD_H,
                Component.translatable("gui.odysseymap.waypoint_category"));
        categoryBox.setMaxLength(Math.max(64, draftCategory.length()));
        categoryBox.setValue(draftCategory);
        categoryBox.setHint(Component.translatable(stationMode ? "gui.odysseymap.category_stations" : "gui.odysseymap.category_uncategorized"));
        addRenderableWidget(categoryBox);
        y += FIELD_H + ROW_GAP;

        coordLabelY = y;
        y += LABEL_H;
        int boxW = (PANEL_W - PAD * 2 - SWATCH_PAD * 2) / 3;
        xBox = new EditBox(font, panelLeft + PAD, y, boxW, FIELD_H, Component.empty());
        xBox.setMaxLength(Math.max(11, draftX.length()));
        xBox.setValue(draftX);
        addRenderableWidget(xBox);

        yBox = new EditBox(font, panelLeft + PAD + boxW + SWATCH_PAD, y, boxW, FIELD_H, Component.empty());
        yBox.setMaxLength(Math.max(11, draftY.length()));
        yBox.setValue(draftY);
        addRenderableWidget(yBox);

        zBox = new EditBox(font, panelLeft + PAD + (boxW + SWATCH_PAD) * 2, y, boxW, FIELD_H, Component.empty());
        zBox.setMaxLength(Math.max(11, draftZ.length()));
        zBox.setValue(draftZ);
        addRenderableWidget(zBox);
        y += FIELD_H + ROW_GAP;

        colorLabelY = y;
        y += LABEL_H;
        swatchX = panelLeft + PAD;
        swatchY = y;
        y += SWATCH_SIZE + LABEL_GAP;

        hexColorBox = new EditBox(font, panelLeft + PAD, y, 90, FIELD_H, Component.empty());
        hexColorBox.setValue(draftColor);
        hexColorBox.setMaxLength(9);
        addRenderableWidget(hexColorBox);
        iconPreviewButton = addRenderableWidget(Button.builder(Component.empty(), button -> openIconPicker())
                .createNarration(defaultNarration -> iconSelectionLabel().copy())
                .bounds(panelLeft + PAD + 100, y, 20, FIELD_H).build());
        iconButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.waypoint_icon"), button -> openIconPicker())
                .bounds(panelLeft + PAD + 124, y, PANEL_W - PAD * 2 - 124, FIELD_H).build());
        Tooltip selectedIconTooltip = Tooltip.create(iconSelectionLabel());
        iconPreviewButton.setTooltip(selectedIconTooltip);
        iconButton.setTooltip(selectedIconTooltip);

        if (stationMode) {
            nameBox.setEditable(false);
            xBox.setEditable(false);
            yBox.setEditable(false);
            zBox.setEditable(false);
            Tooltip sourceTooltip = Tooltip.create(Component.translatable("gui.odysseymap.station_read_only"));
            nameBox.setTooltip(sourceTooltip);
            xBox.setTooltip(sourceTooltip);
            yBox.setTooltip(sourceTooltip);
            zBox.setTooltip(sourceTooltip);
        }
        if (deathMode) {
            xBox.setEditable(false);
            yBox.setEditable(false);
            zBox.setEditable(false);
            hexColorBox.setEditable(false);
            iconButton.active = false;
            iconPreviewButton.active = false;
            Tooltip tooltip = Tooltip.create(Component.translatable("gui.odysseymap.death_read_only"));
            xBox.setTooltip(tooltip);
            yBox.setTooltip(tooltip);
            zBox.setTooltip(tooltip);
            hexColorBox.setTooltip(tooltip);
        }
        int btnW = (PANEL_W - PAD * 2 - SWATCH_PAD) / 2;
        int btnY = panelTop + PANEL_H - BTN_H - 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.save"), b -> save())
                .bounds(panelLeft + PAD, btnY, btnW, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.cancel"), b -> cancel())
                .bounds(panelLeft + PAD + btnW + SWATCH_PAD, btnY, btnW, BTN_H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        MarkerIconCatalog.Entry selectedEntry = MarkerIconCatalog.find(selectedIcon);
        int iconX = iconPreviewButton.getX() + iconPreviewButton.getWidth() / 2;
        int iconY = iconPreviewButton.getY() + iconPreviewButton.getHeight() / 2;
        if (selectedEntry == null) {
            graphics.fill(iconX - 3, iconY - 3, iconX + 3, iconY + 3, selectedColor | 0xFF000000);
        } else {
            MarkerIconRenderer.drawEntry(graphics, selectedEntry, iconX, iconY, 16);
        }

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            int sx = swatchX + i * (SWATCH_SIZE + SWATCH_PAD);
            int sy = swatchY;
            int c = PRESET_COLORS[i];
            graphics.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, 0xFF000000 | (c & 0xFFFFFF));
            if ((c & 0xFFFFFF) == (selectedColor & 0xFFFFFF)) {
                graphics.outline(sx - 1, sy - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xFFFFFFFF);
            }
        }
    }

    private void renderPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xF0101018);
        graphics.outline(panelLeft, panelTop, PANEL_W, PANEL_H, 0xFF606070);

        graphics.centeredText(font, title, panelLeft + PANEL_W / 2, panelTop + 8, 0xFFFFFFFF);
        drawLabel(graphics, "screen.odysseymap.waypoint_name", nameLabelY);
        drawLabel(graphics, "gui.odysseymap.waypoint_category", categoryLabelY);
        drawLabel(graphics, "screen.odysseymap.waypoint_coords", coordLabelY);
        drawLabel(graphics, "screen.odysseymap.waypoint_color", colorLabelY);
    }

    private void drawLabel(GuiGraphicsExtractor graphics, String key, int y) {
        graphics.text(font, Component.translatable(key), panelLeft + PAD, y, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 0 && !deathMode) {
            double mouseX = event.x();
            double mouseY = event.y();
            for (int i = 0; i < PRESET_COLORS.length; i++) {
                int sx = swatchX + i * (SWATCH_SIZE + SWATCH_PAD);
                int sy = swatchY;
                if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= sy && mouseY < sy + SWATCH_SIZE) {
                    selectedColor = PRESET_COLORS[i];
                    hexColorBox.setValue(String.format("#%06X", selectedColor & 0xFFFFFF));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private Component iconSelectionLabel() {
        MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(selectedIcon);
        Component name = entry == null ? Component.translatable("gui.odysseymap.icon_none") : entry.label();
        return Component.translatable("gui.odysseymap.waypoint_icon").append(": ").append(name);
    }

    private void openIconPicker() {
        if (minecraft == null || deathMode) return;
        captureDraft();
        pendingX = parseCoord(xBox.getValue(), pendingX);
        pendingY = parseCoord(yBox.getValue(), pendingY);
        pendingZ = parseCoord(zBox.getValue(), pendingZ);
        applyHexColor();
        minecraft.gui.setScreen(new MarkerIconPickerScreen(this, selectedIcon, id -> selectedIcon = id));
    }

    private void save() {
        WaypointEditDraft submitted = captureDraft();
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) name = Component.translatable("odysseymap.marker.waypoint").getString();
        applyHexColor();
        int x = parseCoord(xBox.getValue(), pendingX);
        int y = parseCoord(yBox.getValue(), pendingY);
        int z = parseCoord(zBox.getValue(), pendingZ);

        if (stationMode && editId != null) {
            Marker current = MarkerManager.get().getWaypoint(editId);
            if (current != null) {
                var changes = submitted.appearanceChangesComparedTo(baselineDraft);
                if (!changes.color()) selectedColor = current.getColor();
                if (!changes.icon()) selectedIcon = current.getIconId();
                if (!changes.category()) draftCategory = current.getCategory();
            }
        }
        if (sharedMode) {
            final boolean queued;
            try {
                if (editMode && editId != null) {
                    queued = MarkerManager.get().saveSharedWaypoint(editId, dimension, x, y, z, name,
                            selectedColor | 0xFF000000, selectedIcon, draftCategory);
                } else {
                    queued = SharedWaypointClient.send(SharedWaypointRequest.create(dimension.identifier().toString(),
                            x, y, z, name, selectedColor | 0xFF000000, selectedIcon, draftCategory));
                }
            } catch (IllegalArgumentException invalidInput) {
                if (minecraft != null && minecraft.gui != null) minecraft.gui.hud.getChat().addClientSystemMessage(
                        Component.translatable("odysseymap.shared.invalid"));
                return;
            }
            if (!queued) {
                if (minecraft != null && minecraft.gui != null) minecraft.gui.hud.getChat().addClientSystemMessage(
                        Component.translatable("odysseymap.shared.unavailable"));
                return;
            }
            returnToParent();
            return;
        }
        Marker saved;
        if (editMode && editId != null) {
            MarkerManager.get().updateWaypoint(editId, name, selectedColor | 0xFF000000, x, y, z);
            saved = MarkerManager.get().getWaypoint(editId);
        } else {
            saved = MarkerManager.get().addWaypoint(dimension, x, y, z, name, selectedColor | 0xFF000000);
        }
        if (saved != null) MarkerManager.get().saveWaypointAppearance(saved, selectedIcon, draftCategory);
        returnToParent();
    }

    private void applyHexColor() {
        String hex = hexColorBox.getValue().trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try { selectedColor = 0xFF000000 | (Integer.parseInt(hex, 16) & 0xFFFFFF); }
        catch (NumberFormatException ignored) {}
    }

    private static int parseCoord(String text, int fallback) {
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private WaypointEditDraft captureDraft() {
        if (nameBox != null) draftName = nameBox.getValue();
        if (categoryBox != null) draftCategory = categoryBox.getValue();
        if (xBox != null) draftX = xBox.getValue();
        if (yBox != null) draftY = yBox.getValue();
        if (zBox != null) draftZ = zBox.getValue();
        if (hexColorBox != null) draftColor = hexColorBox.getValue();
        return new WaypointEditDraft(draftName, draftX, draftY, draftZ, draftColor, selectedIcon, draftCategory);
    }

    private void cancel() {
        if (minecraft == null) return;
        if (!captureDraft().isDirtyComparedTo(baselineDraft)) {
            returnToParent();
            return;
        }
        minecraft.gui.setScreen(new ConfirmScreen(discard -> {
            if (minecraft == null) return;
            if (discard) returnToParent();
            else minecraft.gui.setScreen(this);
        }, Component.translatable("gui.odysseymap.discard_changes.title"),
                Component.translatable("gui.odysseymap.discard_changes.message"),
                Component.translatable("gui.odysseymap.discard_changes.confirm"),
                Component.translatable("gui.odysseymap.discard_changes.keep_editing")));
    }

    private void returnToParent() {
        if (minecraft != null) {
            if (parent instanceof FullscreenMapScreen fullscreen) {
                fullscreen.refreshMarkerList();
            }
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() { cancel(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
