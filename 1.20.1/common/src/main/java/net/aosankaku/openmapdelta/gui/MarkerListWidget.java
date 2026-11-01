// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerIconRenderer;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.render.WaypointOwnerFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class MarkerListWidget extends AbstractWidget {
    public record ControllerRowIdentity(String markerId, WaypointListModel.Category category) {}
    private static final long DOUBLE_CLICK_MS = 400;
    private static final int ROW_H = WaypointListModel.ROW_HEIGHT;
    private static final int STAR_W = WaypointListModel.STAR_WIDTH;
    private static final int EYE_W = WaypointListModel.EYE_WIDTH;
    private static final int DELETE_W = WaypointListModel.DELETE_WIDTH;
    private final List<Marker> allMarkers = new ArrayList<>();
    private final Set<WaypointListModel.Category> collapsedCategories = new HashSet<>();
    private List<WaypointListModel.Row<Marker>> rows = List.of();
    private final Consumer<Marker> onSelect, onEdit, onDelete;
    private int scroll;
    private Marker selected;
    private boolean controllerFocused;
    private int controllerRow = -1;
    private String controllerMarkerId;
    private WaypointListModel.Category controllerCategory;
    private long lastClickTime, nextRefresh;
    private int lastClickIndex = -1;
    private String filterText = "";
    private Component hoveredTooltip;

    public MarkerListWidget(int x, int y, int width, int height,
                            Consumer<Marker> onSelect, Consumer<Marker> onEdit, Consumer<Marker> onDelete) {
        super(x, y, width, height, Component.translatable("gui.odysseymap.waypoints"));
        this.onSelect = onSelect;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        refresh();
    }
    public void refresh() {
        allMarkers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) allMarkers.addAll(MarkerManager.get().getListedWaypointsInDimension(mc.level.dimension()));
        else allMarkers.addAll(MarkerManager.get().getWaypoints());
        if (selected != null) selected = allMarkers.stream().filter(m -> m.getId().equals(selected.getId())).findFirst().orElse(null);
        applyFilter();
        nextRefresh = MarkerManager.monotonicMillis() + 1000;
    }
    /** Keep synchronized stations current without interrupting a pending double click. */
    public void refreshIfNeeded() {
        long now = MarkerManager.monotonicMillis();
        if (active && visible && now >= nextRefresh && now - lastClickTime > DOUBLE_CLICK_MS) refresh();
    }
    public void setFilter(String text) {
        filterText = text == null ? "" : text.toLowerCase(Locale.ROOT);
        scroll = 0;
        applyFilter();
    }
    public Set<WaypointListModel.Category> getCollapsedCategories() { return Set.copyOf(collapsedCategories); }
    public void setCollapsedCategories(Collection<WaypointListModel.Category> categories) {
        collapsedCategories.clear();
        if (categories != null) collapsedCategories.addAll(categories);
        applyFilter();
    }
    private static WaypointListModel.Category category(Marker m) {
        return new WaypointListModel.Category(m.getCategory(), m.getType() == MarkerType.TRANSIT_STATION);
    }
    private Component categoryLabel(WaypointListModel.Category category) {
        if (!category.name().isEmpty()) return Component.literal(category.name());
        return Component.translatable(category.stations() ? "gui.odysseymap.category_stations" : "gui.odysseymap.category_uncategorized");
    }
    private List<Marker> categoryMembers(WaypointListModel.Category category) {
        return WaypointListModel.members(allMarkers, MarkerListWidget::category, category);
    }
    private void applyFilter() {
        resetLastClick();
        int previousControllerRow = controllerRow;
        rememberControllerRow();
        rows = WaypointListModel.rows(allMarkers, MarkerListWidget::category, Comparator
                .comparing(Marker::isFavorite).reversed().thenComparing(m -> m.getLabel().toLowerCase(Locale.ROOT)),
                m -> filterText.isEmpty() || m.getLabel().toLowerCase(Locale.ROOT).contains(filterText)
                        || categoryLabel(category(m)).getString().toLowerCase(Locale.ROOT).contains(filterText), collapsedCategories);
        scroll = ControllerNavigationLayout.clampScroll(scroll, rows.size(), getHeight());
        controllerRow = findControllerRow(controllerMarkerId, controllerCategory);
        if (controllerRow < 0 && !rows.isEmpty()) {
            controllerRow = Math.max(0, Math.min(previousControllerRow, rows.size() - 1));
        }
        if (controllerFocused) revealControllerRow();
        rememberControllerRow();
    }
    public Marker getSelected() { return selected; }
    public ControllerRowIdentity getControllerRowIdentity() {
        if (controllerRow < 0 || controllerRow >= rows.size()) return null;
        WaypointListModel.Row<Marker> row = rows.get(controllerRow);
        return new ControllerRowIdentity(row.marker() == null ? null : row.marker().getId().toString(), row.category());
    }
    public void setControllerFocused(boolean focused) {
        setControllerFocused(focused, null);
    }
    public void setControllerFocused(boolean focused, ControllerRowIdentity identity) {
        controllerFocused = focused && active && visible;
        if (!controllerFocused) return;
        controllerRow = identity == null ? -1 : findControllerRow(identity.markerId(), identity.category());
        if (controllerRow < 0) controllerRow = selected == null ? -1 : findControllerRow(selected.getId().toString(), null);
        if (controllerRow < 0 && !rows.isEmpty()) controllerRow = 0;
        revealControllerRow();
        rememberControllerRow();
        resetLastClick();
    }
    public boolean isControllerFocused() { return controllerFocused && active && visible; }
    public void moveControllerSelection(int delta) {
        if (!isControllerFocused()) return;
        int next = FullscreenControllerPolicy.moveRow(controllerRow, delta, rows.size());
        if (next != controllerRow) {
            controllerRow = next;
            revealControllerRow();
            rememberControllerRow();
            resetLastClick();
        }
    }
    public ControllerNavigationLayout.Point getControllerFocusPoint() {
        if (!isControllerFocused() || controllerRow < 0 || controllerRow >= rows.size()) return null;
        int visibleRows = ControllerNavigationLayout.visibleRows(getHeight());
        if (visibleRows == 0 || controllerRow < scroll || (long) controllerRow >= (long) scroll + visibleRows) return null;
        WaypointListModel.Row<Marker> row = rows.get(controllerRow);
        List<Double> targets = WaypointListModel.targetXs(width, row.isHeader(), false);
        int labelTarget = row.isHeader() ? 1 : 2;
        if (targets.size() <= labelTarget) return null;
        return new ControllerNavigationLayout.Point(getX() + targets.get(labelTarget),
                getY() + (controllerRow - scroll) * ROW_H + ROW_H / 2.0);
    }
    private void revealControllerRow() {
        scroll = FullscreenControllerPolicy.revealRow(controllerRow, scroll, rows.size(), getHeight());
    }
    private void rememberControllerRow() {
        if (controllerRow < 0 || controllerRow >= rows.size()) {
            controllerMarkerId = null;
            controllerCategory = null;
            return;
        }
        WaypointListModel.Row<Marker> row = rows.get(controllerRow);
        controllerMarkerId = row.marker() == null ? null : row.marker().getId().toString();
        controllerCategory = row.category();
    }
    private int findControllerRow(String markerId, WaypointListModel.Category category) {
        if (markerId != null) {
            for (int i = 0; i < rows.size(); i++) {
                Marker marker = rows.get(i).marker();
                if (marker != null && marker.getId().toString().equals(markerId)) return i;
            }
        }
        if (category != null) {
            for (int i = 0; i < rows.size(); i++) {
                WaypointListModel.Row<Marker> row = rows.get(i);
                if (row.isHeader() && row.category().equals(category)) return i;
            }
        }
        return -1;
    }
    public Component getHoveredTooltip() { return active && visible ? hoveredTooltip : null; }
    public void setAllVisible(boolean visible) {
        OpenMapDeltaConfig.SHOW_SHARED_WAYPOINTS.set(visible);
        OpenMapDeltaConfig.save();
        refresh();
        MarkerManager.get().setWaypointsVisible(allMarkers, visible);
        refresh();
    }
    public List<ControllerNavigationLayout.Point> getControllerNavigationPoints() {
        if (!active || !visible) return List.of();
        List<ControllerNavigationLayout.Point> points = new ArrayList<>();
        int end = Math.min(rows.size(), scroll + getHeight() / ROW_H);
        for (int index = scroll; index < end; index++) {
            var row = rows.get(index);
            for (double x : WaypointListModel.targetXs(width, row.isHeader(),
                    row.marker() != null && MarkerManager.get().canManageWaypoint(row.marker()))) {
                points.add(new ControllerNavigationLayout.Point(getX() + x, getY() + (index - scroll) * ROW_H + ROW_H / 2.0));
            }
        }
        return List.copyOf(points);
    }
    public void scrollRows(int amount) {
        long requested = (long) scroll + amount;
        int next = ControllerNavigationLayout.clampScroll((int) Math.max(0L, Math.min(Integer.MAX_VALUE, requested)), rows.size(), getHeight());
        if (next != scroll) {
            scroll = next;
            if (controllerFocused) {
                int visibleRows = ControllerNavigationLayout.visibleRows(getHeight());
                if (visibleRows == 0 || rows.isEmpty()) controllerRow = -1;
                else {
                    long lastVisibleRow = (long) scroll + visibleRows - 1;
                    controllerRow = (int) Math.min((long) rows.size() - 1,
                            Math.max((long) scroll, Math.min((long) controllerRow, lastVisibleRow)));
                }
                rememberControllerRow();
            }
            resetLastClick();
        }
    }
    public boolean canScrollRows(int direction) {
        return direction < 0 ? scroll > 0 : direction > 0 && scroll < ControllerNavigationLayout.maxScroll(rows.size(), getHeight());
    }
    private void resetLastClick() { lastClickIndex = -1; lastClickTime = 0; }
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Component tooltip = null;
        for (int i = 0; i < getHeight() / ROW_H && i + scroll < rows.size(); i++) {
            WaypointListModel.Row<Marker> row = rows.get(i + scroll);
            Marker m = row.marker();
            int y = getY() + i * ROW_H;
            boolean hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + ROW_H;
            graphics.fill(getX(), y, getX() + width, y + ROW_H,
                    row.isHeader() ? 0xFF303044 : m == selected ? 0xFF446688 : hover ? 0xFF333333 : 0xFF222222);
            if (row.isHeader()) {
                List<Marker> members = categoryMembers(row.category());
                boolean shown = members.stream().allMatch(Marker::isVisible);
                boolean collapsed = collapsedCategories.contains(row.category());
                drawCategoryArrow(graphics, getX() + 4, y + 4, collapsed);
                drawEye(graphics, getX() + 18, y + 5, shown);
                String label = categoryLabel(row.category()).getString() + " (" + members.size() + ")";
                graphics.drawString(mc.font, mc.font.plainSubstrByWidth(label, Math.max(0, width - 36)), getX() + 34, y + 3, 0xFFE0E0FF, false);
                if (hover) {
                    WaypointListModel.Action action = WaypointListModel.actionAt(mouseX - getX(), width, 0, true, false);
                    tooltip = action == WaypointListModel.Action.VISIBILITY
                            ? Component.translatable(shown ? "gui.odysseymap.hide_category" : "gui.odysseymap.show_category", categoryLabel(row.category()))
                            : Component.translatable(collapsed ? "gui.odysseymap.expand_category" : "gui.odysseymap.collapse_category", categoryLabel(row.category()));
                }
                continue;
            }
            int x = getX() + 2;
            graphics.drawString(mc.font, m.isFavorite() ? "\u2605" : "\u2606", x, y + 3, m.isFavorite() ? 0xFFFFD700 : 0xFF666666, false);
            x += STAR_W;
            drawEye(graphics, x + 1, y + 5, m.isVisible());
            x += EYE_W;
            MarkerIconRenderer.draw(graphics, m, x + 5, y + 8, 10, false);
            x += 12;
            if (m.getOwnerId() != null) {
                WaypointOwnerFace.draw(graphics, m, x + 4, y + 7, 8);
                x += 10;
            }
            String label = mc.font.plainSubstrByWidth(m.getDisplayLabel(), Math.max(0, width - (x - getX()) - DELETE_W - 2));
            graphics.drawString(mc.font, label, x, y + 3, m.isVisible() ? 0xFFFFFFFF : 0xFF888888, false);
            boolean deletable = MarkerManager.get().canManageWaypoint(m);
            drawTrash(graphics, getX() + width - DELETE_W + 4, y + 3, deletable);
            if (hover) {
                WaypointListModel.Action action = WaypointListModel.actionAt(mouseX - getX(), width, 0, false, deletable);
                tooltip = switch (action) {
                    case VISIBILITY -> Component.translatable(m.isVisible() ? "gui.odysseymap.hide_waypoint" : "gui.odysseymap.show_waypoint");
                    case DELETE -> Component.translatable("gui.odysseymap.context.delete");
                    case NONE -> Component.translatable("gui.odysseymap.station_read_only");
                    default -> m.isShared() && !MarkerManager.get().canManageWaypoint(m)
                            ? Component.translatable("gui.odysseymap.shared_read_only", m.getOwnerName())
                            : Component.literal(m.getLabel()).append("\n").append(Component.translatable("gui.odysseymap.waypoint_edit_hint"));
                };
            }
        }
        if (rows.isEmpty()) graphics.drawString(mc.font, Component.translatable("gui.odysseymap.no_waypoints"), getX() + 4, getY() + 4, 0xFFAAAAAA, false);
        hoveredTooltip = tooltip;
    }
    private static void drawCategoryArrow(GuiGraphics graphics, int x, int y, boolean collapsed) {
        if (collapsed) {
            graphics.fill(x, y, x + 1, y + 7, 0xFFE0E0FF);
            graphics.fill(x + 1, y + 1, x + 2, y + 6, 0xFFE0E0FF);
            graphics.fill(x + 2, y + 2, x + 3, y + 5, 0xFFE0E0FF);
            graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFFE0E0FF);
        } else {
            graphics.fill(x, y, x + 7, y + 1, 0xFFE0E0FF);
            graphics.fill(x + 1, y + 1, x + 6, y + 2, 0xFFE0E0FF);
            graphics.fill(x + 2, y + 2, x + 5, y + 3, 0xFFE0E0FF);
            graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFFE0E0FF);
        }
    }
    /** Pixel icons remain recognizable with every font and resource pack. */
    private static void drawEye(GuiGraphics graphics, int x, int y, boolean visible) {
        int c = visible ? 0xFF99DDAA : 0xFF888888;
        graphics.fill(x + 3, y, x + 8, y + 1, c);
        graphics.fill(x + 1, y + 1, x + 3, y + 2, c);
        graphics.fill(x + 8, y + 1, x + 10, y + 2, c);
        graphics.fill(x, y + 2, x + 1, y + 4, c);
        graphics.fill(x + 10, y + 2, x + 11, y + 4, c);
        graphics.fill(x + 1, y + 4, x + 3, y + 5, c);
        graphics.fill(x + 8, y + 4, x + 10, y + 5, c);
        graphics.fill(x + 3, y + 5, x + 8, y + 6, c);
        if (visible) graphics.fill(x + 4, y + 2, x + 7, y + 4, c);
        else for (int i = 0; i < 7; i++) graphics.fill(x + i + 2, y + 6 - i, x + i + 4, y + 7 - i, c);
    }
    private static void drawTrash(GuiGraphics graphics, int x, int y, boolean enabled) {
        int c = enabled ? 0xFFCC8888 : 0xFF555555;
        graphics.fill(x + 2, y, x + 6, y + 1, c);
        graphics.fill(x, y + 2, x + 8, y + 3, c);
        graphics.fill(x + 1, y + 4, x + 2, y + 10, c);
        graphics.fill(x + 6, y + 4, x + 7, y + 10, c);
        graphics.fill(x + 2, y + 10, x + 6, y + 11, c);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        int index = ControllerNavigationLayout.rowAt(mouseX, mouseY, getX(), getY(), width, height, scroll, rows.size());
        if (index < 0 || (button != 0 && button != 1)) return false;
        controllerFocused = true;
        if (controllerRow != index) {
            controllerRow = index;
            rememberControllerRow();
            resetLastClick();
        }
        WaypointListModel.Row<Marker> row = rows.get(index);
        Marker m = row.marker();
        WaypointListModel.Action action = WaypointListModel.actionAt(mouseX - getX(), width, button,
                row.isHeader(), m != null && MarkerManager.get().canManageWaypoint(m));
        switch (action) {
            case FAVORITE -> { MarkerManager.get().toggleFavorite(m.getId()); refresh(); }
            case VISIBILITY -> {
                if (row.isHeader()) {
                    List<Marker> members = categoryMembers(row.category());
                    MarkerManager.get().setWaypointsVisible(members, !members.stream().allMatch(Marker::isVisible));
                } else MarkerManager.get().toggleVisibility(m.getId());
                refresh();
            }
            case TOGGLE_CATEGORY -> {
                if (row.isHeader()) {
                    if (!collapsedCategories.add(row.category())) collapsedCategories.remove(row.category());
                    applyFilter();
                }
            }
            case DELETE -> { resetLastClick(); onDelete.accept(m); }
            case EDIT -> {
                resetLastClick();
                if (MarkerManager.get().canEditAppearance(m)) onEdit.accept(m);
            }
            case SELECT -> {
                selected = m;
                onSelect.accept(m);
                long now = MarkerManager.monotonicMillis();
                if (index == lastClickIndex && now - lastClickTime < DOUBLE_CLICK_MS) {
                    resetLastClick();
                    if (MarkerManager.get().canEditAppearance(m)) onEdit.accept(m);
                }
                else { lastClickIndex = index; lastClickTime = now; }
            }
            case NONE -> resetLastClick();
        }
        return true;
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!active || !visible || delta == 0 || mouseX < getX() || mouseX >= (double) getX() + width
                || mouseY < getY() || mouseY >= (double) getY() + height) return false;
        scrollRows(delta > 0 ? -1 : 1);
        return true;
    }
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
        Component tooltip = getHoveredTooltip();
        if (tooltip != null) output.add(NarratedElementType.HINT, tooltip);
    }
}
