// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.compat.ControllerSupport;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.CaveLayerSteps;
import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.minecraft.client.gui.components.AbstractSliderButton;
import java.util.Locale;
import net.aosankaku.openmapdelta.gui.MapSettingsScreen;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.navigation.NavigationRequestPolicy;
import net.aosankaku.openmapdelta.marker.MarkerRenderer;
import net.aosankaku.openmapdelta.client.RuntimeClientState;
import net.aosankaku.openmapdelta.render.FullscreenMapRenderer;
import net.aosankaku.openmapdelta.render.FullscreenMapZoom;
import net.aosankaku.openmapdelta.render.FullscreenMapProjection;
import net.aosankaku.openmapdelta.util.DimensionUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.aosankaku.openmapdelta.client.LocationSharing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class FullscreenMapScreen extends Screen {
    private static final int TOP_BAR_H = 46;
    private static final int MIN_MAP_DIM = 64;

    private double panX;
    private double panZ;
    private double zoom = 1;
    private FullscreenMapPanel mapPanel;
    private MarkerListWidget markerList;
    private Button markerPreviousButton;
    private Button markerNextButton;
    private Button showAllWaypointsButton;
    private Button hideAllWaypointsButton;
    private EditBox searchBox;
    private boolean sidebarExpanded;
    private boolean controllerListFocusMode;
    private MarkerListWidget.ControllerRowIdentity controllerListFocusIdentity;
    private boolean displayedHudHidden;
    private boolean f1Held;
    private Button sidebarToggle;
    private Button settingsButton;
    private Button gridButton;
    private Button zoomOutButton;
    private Button zoomInButton;
    private boolean displayedGridState;
    private LayerSlider layerSlider;
    private Button recenterButton;
    private ClientLevel centeredLevel;
    private ResourceKey<Level> centeredDimension;
    private ContextMenuWidget contextMenu;
    private boolean contextMenuOpen;
    private boolean overlayMenuOpen;
    private ResourceKey<Level> contextDimension;
    private int contextX;
    private int contextY;
    private int contextZ;
    private UUID contextMarkerId;
    private boolean contextNavigationFallback;
    private int contextAnchorX;
    private int contextAnchorY;

    public FullscreenMapScreen() {
        super(Component.translatable("screen.odysseymap.fullscreen"));
        zoom = OpenMapDeltaConfig.FULLSCREEN_DEFAULT_ZOOM.get();
    }

    public double getPanX() { return panX; }
    public double getPanZ() { return panZ; }
    public double getZoom() { return zoom; }
    public FullscreenMapPanel getMapPanel() { return mapPanel; }

    public double getBlocksPerPixel() {
        return FullscreenMapZoom.blocksPerPixel(RuntimeClientState.getZoomBlocksPerPixel(), zoom);
    }

    public void addPan(double dx, double dz) { panX += dx; panZ += dz; }

    /** Optional JDK-only reflection contract for controller integrations. */
    public String getControllerProfileId() { return "openmapdelta:fullscreen"; }

    public boolean handleControllerAction(String action, double cursorGuiX, double cursorGuiY) {
        synchronizeHudMode();
        FullscreenControllerPolicy.Action resolved = FullscreenControllerPolicy.resolve(
                action, contextMenuOpen, isControllerListFocused());
        return switch (resolved) {
            case RECENTER -> { recenter(); yield true; }
            case TOGGLE_WAYPOINTS -> { toggleControllerWaypoints(); yield true; }
            case CLOSE -> { onClose(); yield true; }
            case CONTEXT -> {
                if (displayedHudHidden || contextMenuOpen || mapPanel == null || !isInsideMap(cursorGuiX, cursorGuiY)) yield false;
                mapPanel.cancelDrag();
                openContextMenu(cursorGuiX, cursorGuiY);
                yield true;
            }
            case MENU_PREVIOUS -> { if (contextMenu != null) contextMenu.moveSelection(-1); yield true; }
            case MENU_NEXT -> { if (contextMenu != null) contextMenu.moveSelection(1); yield true; }
            case LIST_PREVIOUS -> { if (markerList != null) markerList.moveControllerSelection(-1); updateMarkerPaging(); yield true; }
            case LIST_NEXT -> { if (markerList != null) markerList.moveControllerSelection(1); updateMarkerPaging(); yield true; }
            case ZOOM_IN -> { adjustZoom(1); FullscreenMapRenderer.invalidate(); yield true; }
            case ZOOM_OUT -> { adjustZoom(-1); FullscreenMapRenderer.invalidate(); yield true; }
            case NATIVE_CLICK -> false;
        };
    }

    public boolean panControllerMap(double guiPixelsX, double guiPixelsY) {
        synchronizeHudMode();
        if (contextMenuOpen) return true;
        double blocksPerPixel = getBlocksPerPixel();
        double dx = FullscreenControllerPolicy.panWorldDelta(guiPixelsX, blocksPerPixel);
        double dz = FullscreenControllerPolicy.panWorldDelta(guiPixelsY, blocksPerPixel);
        double nextX = panX + dx;
        double nextZ = panZ + dz;
        if (Double.isFinite(nextX)) panX = nextX;
        if (Double.isFinite(nextZ)) panZ = nextZ;
        return true;
    }

    public double[] getControllerFocusPoint() {
        synchronizeHudMode();
        if (displayedHudHidden) return null;
        ControllerNavigationLayout.Point point = null;
        if (contextMenuOpen && contextMenu != null) point = contextMenu.getControllerFocusPoint();
        else if (isControllerListFocused()) {
            point = markerList.getControllerFocusPoint();
        }
        if (point == null || ControllerNavigationLayout.visibleUnique(List.of(point), width, height).isEmpty()) return null;
        return new double[]{point.x(), point.y()};
    }

    private void toggleControllerWaypoints() {
        if (displayedHudHidden) return;
        if (contextMenuOpen) closeContextMenu();
        if (sidebarExpanded) {
            controllerListFocusMode = false;
            controllerListFocusIdentity = null;
            if (markerList != null) markerList.setControllerFocused(false);
            toggleSidebar();
            setFocused(null);
        } else {
            toggleSidebar();
            if (markerList != null) {
                markerList.setControllerFocused(true);
                controllerListFocusMode = true;
                controllerListFocusIdentity = markerList.getControllerRowIdentity();
                setFocused(markerList);
            }
        }
    }

    public void adjustZoom(double scrollDelta) {
        zoom = FullscreenMapZoom.adjust(RuntimeClientState.getZoomBlocksPerPixel(), zoom, scrollDelta);
    }

    @Override
    protected void init() {
        if (controllerListFocusMode && markerList != null && markerList.isControllerFocused()) {
            controllerListFocusIdentity = markerList.getControllerRowIdentity();
        }
        displayedHudHidden = isHudHidden();
        String searchText = searchBox == null ? "" : searchBox.getValue();
        Set<WaypointListModel.Category> collapsedCategories = markerList == null ? Set.of() : markerList.getCollapsedCategories();
        MarkerManager.get().retainLocationNotices(this);
        contextMenuOpen = false;
        overlayMenuOpen = false;
        if (minecraft != null && minecraft.player != null && minecraft.level != null
                && (centeredLevel != minecraft.level || !minecraft.level.dimension().equals(centeredDimension))) {
            panX = minecraft.player.getX();
            panZ = minecraft.player.getZ();
            centeredLevel = minecraft.level;
            centeredDimension = minecraft.level.dimension();
        }

        var bounds = FullscreenMapChrome.bounds(width, height, getSidebarWidth(), displayedHudHidden);
        int mapX = bounds.x(), mapY = bounds.y();
        int mapW = bounds.width(), mapH = bounds.height();

        addRenderableOnly(this::renderPanel);

        // Register input before the map, then draw the button above it.
        recenterButton = addWidget(Button.builder(Component.translatable("gui.odysseymap.recenter"), b -> recenter())
                .bounds(0, 0, 60, 18).build());
        mapPanel = new FullscreenMapPanel(mapX, mapY, mapW, mapH, this);
        addRenderableWidget(mapPanel);

        int sidebarW = getSidebarLayoutWidth();
        int sidebarX = width - sidebarW + 4;
        int contentW = Math.max(1, sidebarW - 8);
        int halfW = Math.max(1, (contentW - 4) / 2);

        searchBox = new EditBox(font, sidebarX, mapY, contentW, 14, Component.translatable("gui.odysseymap.search"));
        searchBox.setHint(Component.translatable("gui.odysseymap.search"));
        searchBox.setResponder(text -> {
            if (markerList != null) markerList.setFilter(text);
        });

        int listY = mapY + 40;
        int listH = Math.max(0, mapH - 60);
        markerList = new MarkerListWidget(sidebarX, listY, contentW, listH,
                this::onWaypointSelected, this::editWaypoint, this::deleteWaypoint);
        markerList.setCollapsedCategories(collapsedCategories);
        markerList.setFilter(searchText);
        searchBox.setValue(searchText);
        showAllWaypointsButton = Button.builder(Component.translatable("gui.odysseymap.show_all_waypoints"), b -> markerList.setAllVisible(true))
                .bounds(sidebarX, mapY + 18, halfW, 18).build();
        hideAllWaypointsButton = Button.builder(Component.translatable("gui.odysseymap.hide_all_waypoints"), b -> markerList.setAllVisible(false))
                .bounds(sidebarX + halfW + 4, mapY + 18, contentW - halfW - 4, 18).build();
        markerPreviousButton = Button.builder(Component.literal("^"), b -> pageMarkers(-1))
                .createNarration(narration -> Component.translatable("gui.odysseymap.markers_previous"))
                .bounds(sidebarX, mapY + mapH - 18, halfW, 18).build();
        markerNextButton = Button.builder(Component.literal("v"), b -> pageMarkers(1))
                .createNarration(narration -> Component.translatable("gui.odysseymap.markers_next"))
                .bounds(sidebarX + halfW + 4, mapY + mapH - 18, contentW - halfW - 4, 18).build();
        markerPreviousButton.setTooltip(Tooltip.create(Component.translatable("gui.odysseymap.markers_previous")));
        markerNextButton.setTooltip(Tooltip.create(Component.translatable("gui.odysseymap.markers_next")));

        settingsButton = addRenderableWidget(Button.builder(Component.translatable("gui.odysseymap.settings"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new MapSettingsScreen(this));
            }
        }).bounds(4, 2, 50, 18).build());

        layerSlider = addRenderableWidget(new LayerSlider(58, 2, Math.max(50, Math.min(150, width - (OpenMapDeltaConfig.FULLSCREEN_SHOW_ZOOM_BUTTONS.get() ? 190 : 150))), 18));
        addRenderableOnly(recenterButton);
        zoomOutButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            adjustZoom(-1);
            FullscreenMapRenderer.invalidate();
        }).createNarration(defaultNarration -> Component.translatable("key.odysseymap.zoom_out"))
                .bounds(width - 120, 2, 18, 18).build());
        zoomInButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            adjustZoom(1);
            FullscreenMapRenderer.invalidate();
        }).createNarration(defaultNarration -> Component.translatable("key.odysseymap.zoom_in"))
                .bounds(width - 100, 2, 18, 18).build());
        zoomOutButton.setTooltip(Tooltip.create(Component.translatable("key.odysseymap.zoom_out")));
        zoomInButton.setTooltip(Tooltip.create(Component.translatable("key.odysseymap.zoom_in")));
        gridButton = addRenderableWidget(Button.builder(Component.empty(), b -> openOverlayMenu())
                .createNarration(defaultNarration -> layersLabel().copy())
                .bounds(width - 80, 2, 38, 18).build());
        updateGridButton();
        updateHeaderControls();

        sidebarToggle = addRenderableWidget(Button.builder(Component.literal("<"), b -> toggleSidebar())
                .createNarration(defaultNarration -> sidebarToggleLabel().copy())
                .bounds(width - 40, 2, 18, 18).build());

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(width - 20, 2, 18, 18).build());

        contextMenu = new ContextMenuWidget(Math.max(1, width - 8), this::performContextAction);
        addRenderableWidget(contextMenu);
        applySidebarState();
        if (controllerListFocusMode && sidebarExpanded && !displayedHudHidden && markerList != null) {
            markerList.setControllerFocused(true, controllerListFocusIdentity);
            setFocused(markerList);
        }
        if (displayedHudHidden) {
            for (var child : children()) {
                if (child instanceof AbstractWidget widget && widget != mapPanel) widget.visible = widget.active = false;
            }
            setFocused(null);
        }
    }

    private boolean isHudHidden() { return minecraft != null && minecraft.options.hideGui; }

    private void synchronizeHudMode() {
        if (displayedHudHidden == isHudHidden()) return;
        clearWidgets();
        init();
        FullscreenMapRenderer.invalidate();
    }

    private Component layersLabel() { return Component.translatable("gui.odysseymap.layers"); }

    private boolean anyMapLayerShown() {
        return OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID.get()
                || OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get()
                || OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get();
    }

    private void updateGridButton() {
        displayedGridState = anyMapLayerShown();
        if (gridButton != null) gridButton.setTooltip(Tooltip.create(layersLabel()));
    }

    private List<ContextOption> layerOptions() {
        List<ContextOption> options = new ArrayList<>(3);
        options.add(layerOption("chunk_grid", OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID.get(), ContextAction.TOGGLE_CHUNK_GRID));
        options.add(layerOption("mtr_rails", OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get(), ContextAction.TOGGLE_MTR_RAILS));
        options.add(layerOption("create_rails", OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get(), ContextAction.TOGGLE_CREATE_RAILS));
        return options;
    }

    private static ContextOption layerOption(String key, boolean enabled, ContextAction action) {
        Component checked = Component.literal(enabled ? "[x] " : "[ ] ")
                .append(Component.translatable("gui.odysseymap.layers." + key));
        return new ContextOption(checked, action);
    }

    private void openOverlayMenu() {
        if (contextMenu == null || gridButton == null) return;
        controllerListFocusMode = false;
        controllerListFocusIdentity = null;
        if (markerList != null) markerList.setControllerFocused(false);
        if (mapPanel != null) mapPanel.cancelDrag();
        contextDimension = null;
        contextMarkerId = null;
        contextNavigationFallback = false;
        contextAnchorX = gridButton.getX() + gridButton.getWidth() / 2;
        contextAnchorY = gridButton.getY() + gridButton.getHeight();
        contextMenu.showAt(contextAnchorX, contextAnchorY, layersLabel(), layerOptions(), width, height);
        contextMenuOpen = true;
        overlayMenuOpen = true;
        setFocused(contextMenu);
    }

    private void toggleOverlay(ContextAction action) {
        if (!overlayMenuOpen) return;
        switch (action) {
            case TOGGLE_CHUNK_GRID -> OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID.set(!OpenMapDeltaConfig.FULLSCREEN_SHOW_GRID.get());
            case TOGGLE_MTR_RAILS -> OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.set(!OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get());
            case TOGGLE_CREATE_RAILS -> OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.set(!OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get());
            default -> { return; }
        }
        OpenMapDeltaConfig.save();
        FullscreenMapRenderer.invalidate();
        updateGridButton();
        contextMenu.updateOptions(layerOptions());
    }

    private void updateHeaderControls() {
        boolean showZoom = OpenMapDeltaConfig.FULLSCREEN_SHOW_ZOOM_BUTTONS.get();
        if (zoomOutButton != null) zoomOutButton.visible = zoomOutButton.active = showZoom;
        if (zoomInButton != null) zoomInButton.visible = zoomInButton.active = showZoom;
        if (!showZoom && (getFocused() == zoomOutButton || getFocused() == zoomInButton)) setFocused(gridButton);
        if (layerSlider != null) layerSlider.setWidth(Math.max(50, Math.min(150, width - (showZoom ? 190 : 150))));
        if (displayedGridState != anyMapLayerShown()) updateGridButton();
    }

    private int getSidebarWidth() {
        return sidebarExpanded ? getSidebarLayoutWidth() : 0;
    }

    private int getSidebarLayoutWidth() {
        return WaypointListModel.sidebarWidth(width);
    }

    private Component sidebarToggleLabel() {
        return Component.translatable(sidebarExpanded
                ? "gui.odysseymap.hide_sidebar" : "gui.odysseymap.show_sidebar");
    }

    private void toggleSidebar() {
        sidebarExpanded = !sidebarExpanded;
        if (!sidebarExpanded) {
            controllerListFocusMode = false;
            controllerListFocusIdentity = null;
            if (markerList != null) markerList.setControllerFocused(false);
        }
        // Update existing widgets so pan, zoom, search and selection survive the toggle.
        applySidebarState();
        FullscreenMapRenderer.invalidate();
    }

    private void applySidebarState() {
        mapPanel.setWidth(FullscreenMapChrome.bounds(width, height, getSidebarWidth(), displayedHudHidden).width());
        if (!sidebarExpanded && (getFocused() == searchBox || getFocused() == markerList
                || getFocused() == markerPreviousButton || getFocused() == markerNextButton
                || getFocused() == showAllWaypointsButton || getFocused() == hideAllWaypointsButton)) {
            setFocused(sidebarToggle);
        }
        searchBox.visible = searchBox.active = sidebarExpanded;
        markerList.visible = markerList.active = sidebarExpanded;
        showAllWaypointsButton.visible = showAllWaypointsButton.active = sidebarExpanded;
        hideAllWaypointsButton.visible = hideAllWaypointsButton.active = sidebarExpanded;
        markerPreviousButton.visible = markerNextButton.visible = sidebarExpanded;
        updateMarkerPaging();
        if (sidebarExpanded) {
            addRenderableWidget(searchBox);
            addRenderableWidget(markerList);
            addRenderableWidget(showAllWaypointsButton);
            addRenderableWidget(hideAllWaypointsButton);
            addRenderableWidget(markerPreviousButton);
            addRenderableWidget(markerNextButton);
        } else {
            // Remove hidden widgets from input routing as well as rendering.
            removeWidget(searchBox);
            removeWidget(markerList);
            removeWidget(showAllWaypointsButton);
            removeWidget(hideAllWaypointsButton);
            removeWidget(markerPreviousButton);
            removeWidget(markerNextButton);
        }
        sidebarToggle.setMessage(Component.literal(sidebarExpanded ? ">" : "<"));
        sidebarToggle.setTooltip(Tooltip.create(sidebarToggleLabel()));
        updateRecenterButton();
    }

    /** Cursor targets follow the drawn rows rather than the list widget's outer box. */
    public List<ControllerNavigationLayout.Point> getControllerNavigationPoints() {
        synchronizeHudMode();
        if (displayedHudHidden) return List.of(new ControllerNavigationLayout.Point(width / 2.0, height / 2.0));
        if (contextMenuOpen && contextMenu != null) {
            return ControllerNavigationLayout.visibleUnique(ControllerNavigationLayout.contextRows(
                    contextMenu.getX(), contextMenu.getY(), contextMenu.getWidth(), contextMenu.getHeight(),
                    contextMenu.popupPadding, contextMenu.rowHeight, contextMenu.options.size()), width, height);
        }
        updateRecenterButton();
        updateHeaderControls();
        updateMarkerPaging();
        List<ControllerNavigationLayout.Point> points = new ArrayList<>();
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.active && widget.visible
                    && widget != mapPanel && widget != markerList && widget != contextMenu) {
                points.add(new ControllerNavigationLayout.Point(widget.getX() + widget.getWidth() / 2.0,
                        widget.getY() + widget.getHeight() / 2.0));
            }
        }
        if (mapPanel != null && mapPanel.visible && mapPanel.active) {
            points.add(new ControllerNavigationLayout.Point(mapPanel.getX() + mapPanel.getWidth() / 2.0,
                    mapPanel.getY() + mapPanel.getHeight() / 2.0));
        }
        if (markerList != null) points.addAll(markerList.getControllerNavigationPoints());
        return ControllerNavigationLayout.visibleUnique(points, width, height);
    }

    private void pageMarkers(int direction) {
        if (markerList == null) return;
        markerList.scrollRows(direction * Math.max(1, ControllerNavigationLayout.visibleRows(markerList.getHeight())));
        updateMarkerPaging();
    }

    private void updateMarkerPaging() {
        if (markerPreviousButton == null || markerNextButton == null || markerList == null) return;
        markerPreviousButton.active = sidebarExpanded && markerList.canScrollRows(-1);
        markerNextButton.active = sidebarExpanded && markerList.canScrollRows(1);
    }

    private void updateRecenterButton() {
        if (recenterButton == null || mapPanel == null) return;
        recenterButton.setX(Math.max(mapPanel.getX(),
                mapPanel.getX() + mapPanel.getWidth() - recenterButton.getWidth() - 4));
        recenterButton.setY(mapPanel.getY() + mapPanel.getHeight() - recenterButton.getHeight() - 4);
        boolean offCenter = minecraft != null && minecraft.level != null && minecraft.player != null
                && (Math.abs(panX - minecraft.player.getX()) > 1.0E-6
                || Math.abs(panZ - minecraft.player.getZ()) > 1.0E-6);
        recenterButton.visible = recenterButton.active = offCenter;
        if (!offCenter && getFocused() == recenterButton) setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        synchronizeHudMode();
        if (displayedHudHidden) return button != 0 || super.mouseClicked(mouseX, mouseY, button);
        if (contextMenuOpen) {
            if (contextMenu.containsPoint(mouseX, mouseY)) {
                setFocused(contextMenu);
                return contextMenu.mouseClicked(mouseX, mouseY, button);
            }
            closeContextMenu();
            return true;
        }
        if (controllerListFocusMode && !isInsideControllerList(mouseX, mouseY)) releaseControllerListFocus();
        if (button == 1 && isInsideMap(mouseX, mouseY)) {
            mapPanel.cancelDrag();
            openContextMenu(mouseX, mouseY);
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (isInsideControllerList(mouseX, mouseY) && markerList != null && markerList.isControllerFocused()) {
            controllerListFocusMode = true;
            controllerListFocusIdentity = markerList.getControllerRowIdentity();
            setFocused(markerList);
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return contextMenuOpen || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return contextMenuOpen || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return contextMenuOpen || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 290 && minecraft != null) {
            if (f1Held) return true;
            f1Held = true;
            minecraft.options.hideGui = !minecraft.options.hideGui;
            synchronizeHudMode();
            return true;
        }
        if (!contextMenuOpen) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == 256) {
            closeContextMenu();
        } else if (keyCode == 265) {
            contextMenu.moveSelection(-1);
        } else if (keyCode == 264) {
            contextMenu.moveSelection(1);
        } else if (keyCode == 257 || keyCode == 335 || keyCode == 32) {
            contextMenu.activateSelection();
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 290) { f1Held = false; return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private boolean isInsideMap(double mouseX, double mouseY) {
        return mapPanel != null && mouseX >= mapPanel.getX() && mouseX < mapPanel.getX() + mapPanel.getWidth()
                && mouseY >= mapPanel.getY() && mouseY < mapPanel.getY() + mapPanel.getHeight();
    }

    private boolean isControllerListFocused() {
        return controllerListFocusMode && !displayedHudHidden && sidebarExpanded && markerList != null
                && markerList.isControllerFocused() && getFocused() == markerList;
    }

    private boolean isInsideControllerList(double mouseX, double mouseY) {
        return sidebarExpanded && markerList != null && markerList.active && markerList.visible
                && mouseX >= markerList.getX() && mouseX < (double) markerList.getX() + markerList.getWidth()
                && mouseY >= markerList.getY() && mouseY < (double) markerList.getY() + markerList.getHeight();
    }

    private void releaseControllerListFocus() {
        controllerListFocusMode = false;
        controllerListFocusIdentity = null;
        if (markerList != null) markerList.setControllerFocused(false);
        if (markerList != null && getFocused() == markerList) setFocused(null);
    }

    private void openContextMenu(double mouseX, double mouseY) {
        if (minecraft == null || minecraft.level == null || contextMenu == null) return;
        controllerListFocusMode = false;
        controllerListFocusIdentity = null;
        if (markerList != null) markerList.setControllerFocused(false);
        overlayMenuOpen = false;
        contextDimension = minecraft.level.dimension();
        contextAnchorX = Mth.floor(mouseX);
        contextAnchorY = Mth.floor(mouseY);
        contextX = screenToWorldX(contextAnchorX);
        contextZ = screenToWorldZ(contextAnchorY);
        contextY = resolveMapY(contextX, contextZ);
        Marker hit = MarkerRenderer.findFullscreenSelectableMarker(minecraft,
                mapPanel.getX(), mapPanel.getY(), mapPanel.getWidth(), mapPanel.getHeight(),
                panX, panZ, getBlocksPerPixel(), mouseX, mouseY);
        contextMarkerId = hit == null ? null : hit.getId();
        contextNavigationFallback = hit != null && (hit.getType() == MarkerType.NAVIGATION || hit.getType() == MarkerType.NAVIGATION_POINT);
        Marker marker = getContextMarker();
        List<ContextOption> options = new ArrayList<>();
        if (marker != null) {
            var markerPosition = MarkerRenderer.fullscreenMarkerPosition(minecraft,
                    mapPanel.getX(), mapPanel.getY(), mapPanel.getWidth(), mapPanel.getHeight(),
                    panX, panZ, getBlocksPerPixel(), marker);
            contextAnchorX = markerPosition.x();
            contextAnchorY = markerPosition.y();
            contextX = marker.getX();
            contextZ = marker.getZ();
            contextY = marker.hasKnownY() ? marker.getY() : resolveMapY(contextX, contextZ);
            boolean player = marker.getType() == MarkerType.PLAYER;
            if (LocationSharing.canTeleport(minecraft)) addOption(options, "teleport", ContextAction.TELEPORT);
            if (player) addOption(options, "message", ContextAction.MESSAGE);
            if (MarkerManager.get().canManageWaypoint(marker)) {
                addOption(options, "edit", ContextAction.EDIT);
                addOption(options, "delete", ContextAction.DELETE);
            } else {
                addOption(options, "create", ContextAction.CREATE);
                addOption(options, "temporary", ContextAction.TEMPORARY);
            }
            addOption(options, marker.getType() == MarkerType.DEATH ? "request_help" : "notify", ContextAction.NOTIFY);
            if (marker.getType() != MarkerType.LOCATION_NOTICE && minecraft.player != null
                    && NavigationRequestPolicy.canStart(marker.getId(), minecraft.player.getUUID())) {
                boolean navigating = MarkerManager.get().isNavigating(marker.getId());
                addOption(options, navigating ? "stop_navigation" : "navigate",
                        navigating ? ContextAction.STOP_NAVIGATION : ContextAction.NAVIGATE);
            }
        } else {
            contextMarkerId = null;
            addOption(options, "create", ContextAction.CREATE);
            addOption(options, "temporary", ContextAction.TEMPORARY);
            if (LocationSharing.canTeleport(minecraft)) addOption(options, "teleport", ContextAction.TELEPORT);
            addOption(options, "notify", ContextAction.NOTIFY);
            addOption(options, "navigate", ContextAction.NAVIGATE);
        }
        if (SharedWaypointClient.canCreate()) addOption(options, "create_shared", ContextAction.CREATE_SHARED);
        if (MarkerManager.get().getNavigationCount() > 0)
            addOption(options, "stop_all_navigation", ContextAction.STOP_ALL_NAVIGATION);
        Component coordinates = Component.literal("X: " + contextX + "  Y: " + contextY + "  Z: " + contextZ);
        contextMenu.showAt(contextAnchorX, contextAnchorY, coordinates, options, width, height);
        contextMenuOpen = true;
        setFocused(contextMenu);
    }

    private static void addOption(List<ContextOption> options, String key, ContextAction action) {
        options.add(new ContextOption(Component.translatable("gui.odysseymap.context." + key), action));
    }

    private int resolveMapY(int x, int z) {
        if (minecraft != null && minecraft.level != null) {
            var active = OpenMapDeltaClient.getActiveTileCache();
            int height = active == null ? Tile.UNKNOWN_HEIGHT : active.getHeight(minecraft.level.dimension(), x, z);
            if (height != Tile.UNKNOWN_HEIGHT) return height + 1;
            if (minecraft.level.hasChunkAt(new BlockPos(x, 0, z))) {
                return minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }
            return minecraft.level.getSeaLevel();
        }
        return 64;
    }

    private int resolveSurfaceHeight(int x, int z) {
        if (minecraft == null || minecraft.level == null) return Tile.UNKNOWN_HEIGHT;
        if (minecraft.level.hasChunkAt(new BlockPos(x, 0, z))) {
            return minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        }
        var cache = OpenMapDeltaClient.getTileCache();
        return cache == null ? Tile.UNKNOWN_HEIGHT : cache.getHeight(minecraft.level.dimension(), x, z);
    }

    private final class LayerSlider extends AbstractSliderButton {
        private int minimum;
        private int maximum;
        private int lastStep;

        private LayerSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 1.0);
            sync();
        }

        private boolean needsSync() {
            int min = minecraft != null && minecraft.level != null ? minecraft.level.getMinBuildHeight() : -64;
            int max = minecraft != null && minecraft.level != null ? minecraft.level.getMaxBuildHeight() - 1 : 319;
            return minimum != min || maximum != max || active != ClientServerState.isCaveAllowed()
                    || lastStep != OpenMapDeltaConfig.CAVE_LAYER_STEP.get()
                    || CaveLayerSteps.fromSlider(value, minimum, maximum, lastStep) != OpenMapDeltaClient.getSelectedMapLayer();
        }

        private void sync() {
            minimum = minecraft != null && minecraft.level != null ? minecraft.level.getMinBuildHeight() : -64;
            maximum = minecraft != null && minecraft.level != null ? minecraft.level.getMaxBuildHeight() - 1 : 319;
            active = ClientServerState.isCaveAllowed();
            int layer = OpenMapDeltaClient.getSelectedMapLayer();
            lastStep = OpenMapDeltaConfig.CAVE_LAYER_STEP.get();
            value = CaveLayerSteps.toSlider(layer, minimum, maximum, lastStep);
            updateMessage();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (stepWithKey(keyCode)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean stepWithKey(int key) {
            if (!active || (key != 263 && key != 262)) return false;
            int step = OpenMapDeltaConfig.CAVE_LAYER_STEP.get();
            int bands = CaveLayerSteps.layerCount(minimum, maximum, step) - 1;
            if (bands == 0) return true;
            double selected = CaveLayerSteps.toSlider(OpenMapDeltaClient.getSelectedMapLayer(), minimum, maximum, step);
            value = Mth.clamp(selected + (key == 262 ? 1.0 : -1.0) / bands, 0.0, 1.0);
            applyValue();
            updateMessage();
            return true;
        }

        @Override
        protected void updateMessage() {
            int step = OpenMapDeltaConfig.CAVE_LAYER_STEP.get();
            int selected = CaveLayerSteps.fromSlider(value, minimum, maximum, step);
            setMessage(!ClientServerState.isCaveAllowed() ? Component.translatable("gui.odysseymap.cave_disabled")
                    : selected == CaveLayerSteps.SURFACE ? Component.translatable("gui.odysseymap.surface")
                    : Component.translatable("gui.odysseymap.cave_layer_range", selected,
                            CaveLayerSteps.bandEnd(selected, minimum, maximum, step)));
        }

        @Override
        protected void applyValue() {
            int step = OpenMapDeltaConfig.CAVE_LAYER_STEP.get();
            int selected = CaveLayerSteps.fromSlider(value, minimum, maximum, step);
            OpenMapDeltaClient.setSelectedMapLayer(selected);
            value = CaveLayerSteps.toSlider(selected, minimum, maximum, step);
        }
    }

    private Marker getContextMarker() {
        if (contextMarkerId == null) return null;
        // An edited or hidden source can be replaced by a navigation snapshot at its old position.
        Marker marker = contextNavigationFallback ? MarkerManager.get().getMapMarkers(contextDimension).stream()
                .filter(candidate -> candidate.getId().equals(contextMarkerId)).findFirst().orElse(null)
                : MarkerManager.get().getMapMarker(contextMarkerId);
        return marker != null && marker.getDimension().equals(contextDimension) ? marker : null;
    }

    private void closeContextMenu() {
        contextMenuOpen = false;
        overlayMenuOpen = false;
        if (contextMenu != null) contextMenu.hide();
        setFocused(null);
    }

    private void performContextAction(ContextAction action) {
        if (action == ContextAction.TOGGLE_CHUNK_GRID || action == ContextAction.TOGGLE_MTR_RAILS
                || action == ContextAction.TOGGLE_CREATE_RAILS) {
            toggleOverlay(action);
            return;
        }
        closeContextMenu();
        if (minecraft == null || minecraft.level == null || contextDimension == null
                || !minecraft.level.dimension().equals(contextDimension)) return;
        switch (action) {
            case CREATE -> {
                if (minecraft.player == null) return;
                Marker source = getContextMarker();
                String name = source != null && !source.getType().isEditableWaypoint()
                        ? source.getLabel() : Component.translatable("odysseymap.marker.default_name",
                                MarkerManager.get().getWaypoints().size() + 1).getString();
                minecraft.setScreen(WaypointEditScreen.forCreate(
                        this, contextDimension, contextX, contextY, contextZ, name));
            }
            case CREATE_SHARED -> {
                if (!SharedWaypointClient.canCreate() || minecraft.player == null) return;
                String name = Component.translatable("odysseymap.marker.default_name",
                        MarkerManager.get().getSharedWaypoints().size() + 1).getString();
                minecraft.setScreen(WaypointEditScreen.forSharedCreate(
                        this, contextDimension, contextX, contextY, contextZ, name));
            }
            case TEMPORARY -> {
                MarkerManager.get().addTemporaryWaypoint(minecraft, contextDimension, contextX, contextY, contextZ);
                refreshMarkerList();
            }
            case TELEPORT -> {
                if (!LocationSharing.canTeleport(minecraft)) return;
                Marker marker = contextMarkerId == null ? null : getContextMarker();
                if (contextMarkerId != null && marker == null) return;
                if (marker != null && marker.getType() == MarkerType.PLAYER) {
                    LocationSharing.teleportToPlayer(minecraft, marker.getId());
                    return;
                }
                int x = marker == null ? contextX : marker.getX();
                int z = marker == null ? contextZ : marker.getZ();
                int y = marker == null || !marker.hasKnownY() ? resolveMapY(x, z) : marker.getY();
                LocationSharing.teleport(minecraft, x, y, z);
            }
            case MESSAGE -> {
                Marker marker = getContextMarker();
                if (marker != null && marker.getType() == MarkerType.PLAYER)
                    minecraft.setScreen(new PlayerMessageScreen(this, marker.getId()));
            }
            case NOTIFY -> {
                Marker marker = getContextMarker();
                UUID playerId = marker != null && marker.getType() == MarkerType.PLAYER ? marker.getId() : null;
                minecraft.setScreen(new PlayerSelectScreen(this, contextDimension, contextX, contextY, contextZ, playerId,
                        marker != null && marker.getType() == MarkerType.DEATH));
            }
            case NAVIGATE -> {
                Marker marker = contextMarkerId == null ? null : getContextMarker();
                switch (NavigationRequestPolicy.contextDestination(contextMarkerId != null, marker != null)) {
                    case SELECTED_MARKER -> MarkerManager.get().startNavigation(minecraft, marker);
                    case MAP_COORDINATE -> {
                        String label = "X: " + contextX + " Y: " + contextY + " Z: " + contextZ;
                        Marker destination = new Marker(UUID.randomUUID(), MarkerType.NAVIGATION_POINT,
                                contextDimension, contextX, contextY, contextZ, label, MarkerType.NAVIGATION.defaultColor);
                        MarkerManager.get().startNavigation(minecraft, destination);
                    }
                    case STALE_SELECTION -> { }
                }
            }
            case STOP_NAVIGATION -> {
                if (contextMarkerId != null) MarkerManager.get().stopNavigation(contextMarkerId);
            }
            case STOP_ALL_NAVIGATION -> MarkerManager.get().stopAllNavigation();
            case EDIT -> {
                Marker marker = getContextMarker();
                if (marker != null && MarkerManager.get().canEditAppearance(marker))
                    minecraft.setScreen(WaypointEditScreen.forEdit(this, marker));
            }
            case DELETE -> {
                Marker marker = getContextMarker();
                if (marker != null && MarkerManager.get().canManageWaypoint(marker)) {
                    MarkerManager.get().removeWaypoint(marker.getId());
                    refreshMarkerList();
                }
            }
        }
    }

    private enum ContextAction { CREATE, CREATE_SHARED, TEMPORARY, TELEPORT, MESSAGE, NOTIFY, EDIT, DELETE, NAVIGATE, STOP_NAVIGATION, STOP_ALL_NAVIGATION, TOGGLE_CHUNK_GRID, TOGGLE_MTR_RAILS, TOGGLE_CREATE_RAILS }

    private static final class ContextOption {
        private final Component label;
        private final ContextAction action;

        private ContextOption(Component label, ContextAction action) {
            this.label = label;
            this.action = action;
        }
    }

    private static final class ContextMenuWidget extends AbstractWidget {
        private static final int PADDING = 4;
        private static final int ROW_HEIGHT = 20;
        private final int maximumWidth;
        private final Consumer<ContextAction> onAction;
        private List<ContextOption> options = List.of();
        private Component header = Component.empty();
        private int selectedIndex;
        private int popupPadding = PADDING;
        private int rowHeight = ROW_HEIGHT;

        private ContextMenuWidget(int maximumWidth, Consumer<ContextAction> onAction) {
            super(0, 0, Math.max(1, maximumWidth), 1, Component.empty());
            this.maximumWidth = Math.max(1, maximumWidth);
            this.onAction = onAction;
            this.visible = false;
            this.active = false;
        }

        private void showAt(int anchorX, int anchorY, Component header, List<ContextOption> options, int screenWidth, int screenHeight) {
            this.options = List.copyOf(options);
            this.header = header;
            int rows = this.options.size() + 1;
            int textWidth = this.options.stream().mapToInt(option -> Minecraft.getInstance().font.width(option.label)).max().orElse(0);
            textWidth = Math.max(textWidth, Minecraft.getInstance().font.width(header));
            setWidth(Math.min(maximumWidth, Math.max(120, textWidth + 20)));
            int availableHeight = ContextMenuPlacement.availableHeight(anchorX, anchorY, getWidth(), screenWidth, screenHeight);
            if (availableHeight < rows * 9) availableHeight = Math.max(1, screenHeight - 8);
            var layout = ContextMenuPlacement.rows(rows, availableHeight);
            popupPadding = layout.padding();
            rowHeight = layout.rowHeight();
            setHeight(layout.height());
            var position = ContextMenuPlacement.place(anchorX, anchorY, getWidth(), getHeight(), screenWidth, screenHeight);
            setX(position.x());
            setY(position.y());
            this.selectedIndex = 0;
            this.visible = true;
            this.active = true;
            updateMessage();
        }

        private void hide() {
            this.visible = false;
            this.active = false;
            this.options = List.of();
        }

        private void updateOptions(List<ContextOption> updatedOptions) {
            int previous = selectedIndex;
            options = List.copyOf(updatedOptions);
            selectedIndex = options.isEmpty() ? 0 : Math.min(previous, options.size() - 1);
            updateMessage();
        }

        private boolean containsPoint(double mouseX, double mouseY) {
            return visible && mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        private ControllerNavigationLayout.Point getControllerFocusPoint() {
            if (!active || !visible || options.isEmpty()) return null;
            List<ControllerNavigationLayout.Point> points = ControllerNavigationLayout.contextRows(
                    getX(), getY(), getWidth(), getHeight(), popupPadding, rowHeight, options.size());
            return selectedIndex >= 0 && selectedIndex < points.size() ? points.get(selectedIndex) : null;
        }

        private void moveSelection(int delta) {
            if (options.isEmpty()) return;
            selectedIndex = Math.floorMod(selectedIndex + delta, options.size());
            updateMessage();
        }

        private void activateSelection() {
            if (!options.isEmpty()) onAction.accept(options.get(selectedIndex).action);
        }

        private void updateMessage() {
            if (!options.isEmpty()) setMessage(Component.empty().append(header).append(" ").append(options.get(selectedIndex).label));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || !visible) return false;
            if (!containsPoint(mouseX, mouseY)) return false;
            if (button == 0) {
                double listTop = getY() + popupPadding + rowHeight;
                if (mouseY < listTop || mouseY >= listTop + options.size() * rowHeight) return true;
                int row = (int) ((mouseY - listTop) / rowHeight);
                if (row >= 0 && row < options.size()) {
                    selectedIndex = row;
                    updateMessage();
                    onAction.accept(options.get(row).action);
                }
            }
            return true;
        }

        private Component fitLabel(Component label, int measuredWidth) {
            Minecraft mc = Minecraft.getInstance();
            String text = label.getString();
            int maxWidth = Math.max(0, getWidth() - 18);
            if (measuredWidth <= maxWidth) return label;
            while (!text.isEmpty() && mc.font.width(text + "…") > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            return Component.literal(text + "…");
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        public void drawPopup(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!visible || options.isEmpty()) return;
            graphics.fill(getX() + 2, getY() + 2, getX() + getWidth() + 2, getY() + getHeight() + 2, 0x90000000);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xF0181820);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFF707080);
            Minecraft mc = Minecraft.getInstance();
            int headerY = getY() + popupPadding;
            graphics.drawString(mc.font, fitLabel(header, mc.font.width(header)), getX() + 8,
                    headerY + Math.max(0, (rowHeight - 9) / 2), 0xFFBFCADA, false);
            graphics.fill(getX() + 2, headerY + rowHeight - 1, getX() + getWidth() - 2,
                    headerY + rowHeight, 0xFF505060);
            for (int i = 0; i < options.size(); i++) {
                int rowY = getY() + popupPadding + (i + 1) * rowHeight;
                boolean hover = mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= rowY && mouseY < rowY + rowHeight;
                boolean selected = i == selectedIndex;
                if (hover || selected) graphics.fill(getX() + 2, rowY, getX() + getWidth() - 2, rowY + rowHeight,
                        hover ? 0xFF3A5268 : 0xFF2A3A4C);
                graphics.drawString(mc.font, fitLabel(options.get(i).label, mc.font.width(options.get(i).label)), getX() + 8,
                        rowY + Math.max(0, (rowHeight - 9) / 2),
                        selected ? 0xFFFFFFFF : 0xFFE0E0E0, false);
            }
        }

    }

    public void focusLocation(ResourceKey<Level> dimension, int x, int z) {
        if (minecraft != null && minecraft.level != null && minecraft.level.dimension().equals(dimension)) {
            panX = x;
            panZ = z;
            FullscreenMapRenderer.invalidate();
        }
    }

    private void recenter() {
        if (minecraft != null && minecraft.player != null) {
            panX = minecraft.player.getX();
            panZ = minecraft.player.getZ();
            FullscreenMapRenderer.invalidate();
        }
        updateRecenterButton();
    }

    private void deleteWaypoint(Marker marker) {
        if (minecraft == null || marker == null || !MarkerManager.get().canManageWaypoint(marker)) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) MarkerManager.get().removeWaypoint(marker.getId());
            refreshMarkerList();
            minecraft.setScreen(this);
        }, Component.translatable("gui.odysseymap.delete_waypoint_title"),
                Component.translatable("gui.odysseymap.delete_waypoint_message", marker.getLabel()),
                Component.translatable("gui.odysseymap.context.delete"), Component.translatable("gui.odysseymap.cancel")));
    }

    private void editWaypoint(Marker marker) {
        if (minecraft != null && MarkerManager.get().canEditAppearance(marker)) {
            minecraft.setScreen(WaypointEditScreen.forEdit(this, marker));
        }
    }

    public void refreshMarkerList() {
        if (markerList != null) markerList.refresh();
        updateMarkerPaging();
    }

    private void onWaypointSelected(Marker marker) {
        if (marker != null) {
            panX = marker.getX();
            panZ = marker.getZ();
            FullscreenMapRenderer.invalidate();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        synchronizeHudMode();
        if (displayedHudHidden) {
            if (mapPanel != null) mapPanel.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (markerList != null) markerList.refreshIfNeeded();
        updateRecenterButton();
        updateHeaderControls();
        updateMarkerPaging();
        if (layerSlider != null && layerSlider.needsSync()) layerSlider.sync();
        super.render(graphics, mouseX, mouseY, partialTick);

        if (OpenMapDeltaConfig.MAP_SHOW_COORDINATES.get()) {
            int worldX = screenToWorldX(mouseX);
            int worldZ = screenToWorldZ(mouseY);
            int worldY = resolveSurfaceHeight(worldX, worldZ);
            String yText = worldY == Tile.UNKNOWN_HEIGHT ? "?" : Integer.toString(worldY);
            if (mapPanel != null) {
                graphics.drawString(font, "X: " + worldX + "  Y: " + yText + "  Z: " + worldZ,
                        mapPanel.getX(), mapPanel.getY() + mapPanel.getHeight() - 10, 0xFFCCCCCC, true);
            }
        }

        String zoomText = String.format(Locale.ROOT, "%.2f×", 1.0 / getBlocksPerPixel());
        int zoomTextX = width - (OpenMapDeltaConfig.FULLSCREEN_SHOW_ZOOM_BUTTONS.get() ? 124 : 84) - font.width(zoomText);
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFFFF);
        if (minecraft != null && minecraft.level != null) {
            String dim = DimensionUtil.dimensionId(minecraft.level.dimension());
            graphics.drawCenteredString(font, dim, width / 2, 35, 0xFFAAAABB);
        }
        int headerLeft = layerSlider == null ? 58 : layerSlider.getX() + layerSlider.getWidth() + 4;
        if (zoomTextX >= headerLeft) graphics.drawString(font, zoomText, zoomTextX, 6, 0xFFAAAAAA, false);

        drawControllerHints(graphics);
        drawGridButton(graphics);
        queueWaypointTooltip();
        if (contextMenuOpen && contextMenu != null) {
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0, 0.0, 400.0);
            try {
                contextMenu.drawPopup(graphics, mouseX, mouseY);
                if (!overlayMenuOpen) drawContextAnchor(graphics);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    private void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0000000);

        if (mapPanel != null) {
            int px = mapPanel.getX();
            int py = mapPanel.getY();
            int pw = mapPanel.getWidth();
            int ph = mapPanel.getHeight();
            graphics.fill(px - 1, py - 1, px + pw + 1, py, 0xFF505060);
            graphics.fill(px - 1, py + ph, px + pw + 1, py + ph + 1, 0xFF505060);
            graphics.fill(px - 1, py, px, py + ph, 0xFF505060);
            graphics.fill(px + pw, py, px + pw + 1, py + ph, 0xFF505060);
        }

        if (sidebarExpanded) {
            graphics.fill(width - getSidebarLayoutWidth(), TOP_BAR_H, width, height, 0xE0181820);
            graphics.vLine(width - getSidebarLayoutWidth(), TOP_BAR_H, height, 0xFF404050);
        }
    }

    /** Defer row hints so vanilla positions them by the cursor above the other widgets. */
    private void queueWaypointTooltip() {
        if (contextMenuOpen || markerList == null || minecraft == null) return;
        Component tooltip = markerList.getHoveredTooltip();
        if (tooltip == null) return;
        int wrapWidth = Math.max(1, Math.min(200, width - 24));
        setTooltipForNextRenderPass(font.split(tooltip, wrapWidth), DefaultTooltipPositioner.INSTANCE, true);
    }

    private void drawControllerHints(GuiGraphics graphics) {
        if (mapPanel == null || contextMenuOpen) return;
        List<Component> hints = ControllerSupport.mapHints();
        int rows = Math.min(hints.size(), Math.max(0, (mapPanel.getHeight() - 24) / 10));
        int availableWidth = mapPanel.getWidth() - 8;
        if (rows == 0 || availableWidth <= 0) return;
        int x = mapPanel.getX() + 4;
        int y = mapPanel.getY() + 4;
        for (int row = 0; row < rows; row++) {
            String text = font.plainSubstrByWidth(hints.get(row).getString(), availableWidth);
            graphics.fill(x - 2, y - 1, x + font.width(text) + 2, y + 9, 0xA0000000);
            graphics.drawString(font, text, x, y, 0xFFCCCCCC, true);
            y += 10;
        }
    }

    private void drawGridButton(GuiGraphics graphics) {
        if (gridButton == null) return;
        int x = gridButton.getX() + (gridButton.getWidth() - 13) / 2;
        int y = gridButton.getY() + (gridButton.getHeight() - 13) / 2;
        int color = anyMapLayerShown() ? 0xFF55FFFF : 0xFF999999;
        for (int offset = 0; offset <= 12; offset += 4) {
            graphics.fill(x + offset, y, x + offset + 1, y + 13, color);
            graphics.fill(x, y + offset, x + 13, y + offset + 1, color);
        }
    }

    private void drawContextAnchor(GuiGraphics graphics) {
        // A hollow white ring with a dark edge stays visible on both terrain and icons.
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -5; dx <= 5; dx++) {
                int radiusSquared = dx * dx + dy * dy;
                if (radiusSquared < 5 || radiusSquared > 25) continue;
                int color = radiusSquared >= 9 && radiusSquared <= 16 ? 0xFFFFFFFF : 0xFF101010;
                graphics.fill(contextAnchorX + dx, contextAnchorY + dy,
                        contextAnchorX + dx + 1, contextAnchorY + dy + 1, color);
            }
        }
    }

    private int screenToWorldX(int screenX) {
        if (mapPanel == null) return Mth.floor(panX);
        return FullscreenMapProjection.world(mapPanel.getX(), mapPanel.getWidth(),
                panX, screenX, getBlocksPerPixel());
    }

    private int screenToWorldZ(int screenY) {
        if (mapPanel == null) return Mth.floor(panZ);
        return FullscreenMapProjection.world(mapPanel.getY(), mapPanel.getHeight(),
                panZ, screenY, getBlocksPerPixel());
    }

    @Override
    public void onClose() {
        MarkerManager.get().releaseLocationNotices(this);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
