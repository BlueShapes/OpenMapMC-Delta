// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.area.AreaDocument;
import net.aosankaku.openmapdelta.area.AreaEditHistory;
import net.aosankaku.openmapdelta.area.AreaGeometryOps;
import net.aosankaku.openmapdelta.area.AreaManager;
import net.aosankaku.openmapdelta.area.AreaRenderGeometry;
import net.aosankaku.openmapdelta.area.MapArea;
import net.aosankaku.openmapdelta.render.FullscreenMapRenderer;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A draft-first editor: geometry and metadata become persistent only after an explicit save. */
public final class AreaEditorScreen extends FullscreenMapScreen {
    private static final int PANEL_TOP = 46;
    private static final int PANEL_MARGIN = 8;
    private static final int[] COLORS = {0xFF46B8FF, 0xFF65D58A, 0xFFFFB347, 0xFFDA79FF, 0xFFFF6F76, 0xFFFFFF72};

    private enum RingMode { OUTER, HOLE, ISLAND }
    private static final class Draft {
        final AreaDocument base;
        final AreaEditHistory history;
        UUID selectedId;
        UUID mergeTargetId;
        String name = "";
        String childA = "";
        String childB = "";
        String parentFilter = "";
        String areaFilter = "";
        String pointX = "";
        String pointZ = "";
        boolean pointFieldsDirty;
        MapArea.Kind kind = MapArea.Kind.OTHER;
        MapArea.Shape shape = MapArea.Shape.POLYGON;
        MapArea.Style style = MapArea.Style.defaults(COLORS[0]);
        UUID parentId;

        Draft(AreaDocument base) {
            this.base = base;
            this.history = new AreaEditHistory(base);
        }

        AreaDocument document() { return history.current(); }
        boolean dirty() { return !document().equals(base); }
    }

    private final FullscreenMapScreen parentScreen;
    private final int returnLayer;
    private final EnumMap<MapArea.Scope, Draft> drafts = new EnumMap<>(MapArea.Scope.class);
    private MapArea.Scope scope = MapArea.Scope.PERSONAL;
    private MapArea.Kind kind = MapArea.Kind.OTHER;
    private MapArea.Shape shape = MapArea.Shape.POLYGON;
    private MapArea.Style style = MapArea.Style.defaults(COLORS[0]);
    private UUID selectedId;
    private UUID mergeTargetId;
    private boolean drawing;
    private boolean newFeature;
    private boolean replaceGeometry;
    private RingMode ringMode = RingMode.OUTER;
    private final ArrayList<MapArea.Point> pendingPoints = new ArrayList<>();
    private final ArrayList<List<MapArea.Point>> pendingPaths = new ArrayList<>();
    private MapArea.Point selectedPoint;
    private MapArea.Point movingFrom;
    private MapArea.Point movingTo;
    private boolean insertVertexArmed;
    private boolean removeVertexArmed;
    private boolean splitArmed;
    private boolean moveLabelArmed;
    private UUID pendingRequestId;
    private Component status;
    private int colorIndex;
    private int panelX;
    private int panelWidth;
    private int listY;
    private int listHeight;
    private int controlsY;
    private int footerY;
    private int controlScroll;
    private int bodyMaxOffset;
    private boolean loadingPointValues;
    private EditBox searchBox;
    private EditBox parentSearchBox;
    private EditBox nameBox;
    private EditBox pointXBox;
    private EditBox pointZBox;
    private EditBox childABox;
    private EditBox childBBox;
    private AreaListWidget areaList;
    private final ArrayList<AbstractWidget> editorActions = new ArrayList<>();
    private final ArrayList<AbstractWidget> bodyWidgets = new ArrayList<>();
    private final IdentityHashMap<AbstractWidget, Integer> bodyOffsets = new IdentityHashMap<>();
    private final IdentityHashMap<AbstractWidget, Boolean> bodyLogicalActive = new IdentityHashMap<>();
    private Button scopeButton;
    private Button kindButton;
    private Button shapeButton;
    private Button parentButton;
    private Button colorButton;
    private Button visibleButton;
    private Button labelsButton;
    private Button borderButton;
    private Button fillButton;
    private Button newButton;
    private Button applyButton;
    private Button deleteButton;
    private Button drawButton;
    private Button finishButton;
    private Button branchButton;
    private Button holeButton;
    private Button islandButton;
    private Button insertButton;
    private Button removePointButton;
    private Button movePointButton;
    private Button moveLabelButton;
    private Button splitButton;
    private Button mergeButton;
    private Button undoButton;
    private Button redoButton;
    private Button saveButton;
    private Button cancelButton;
    private boolean copiedView;

    public AreaEditorScreen(FullscreenMapScreen parent, int returnLayer) {
        this.parentScreen = parent;
        this.returnLayer = returnLayer;
        this.copiedView = parent != null;
    }

    private Draft draft() {
        return drafts.computeIfAbsent(scope, value -> new Draft(AreaManager.document(value)));
    }

    @Override
    protected void init() {
        editorActions.clear(); bodyWidgets.clear(); bodyOffsets.clear(); bodyLogicalActive.clear(); bodyMaxOffset = 0;
        super.init();
        if (copiedView && parentScreen != null) {
            addPan(parentScreen.getPanX() - getPanX(), parentScreen.getPanZ() - getPanZ());
            double target = parentScreen.getBlocksPerPixel();
            for (int i = 0; i < 48 && Math.abs(getBlocksPerPixel() - target) > 1.0e-6; i++) {
                adjustZoom(getBlocksPerPixel() > target ? 1 : -1);
            }
            copiedView = false;
        }
        AreaEditorLayout.Layout paneLayout = AreaEditorLayout.calculate(width, height);
        setWaypointSidebarAvailable(false);
        setFullscreenMapViewportWidth(paneLayout.mapViewportWidth());
        panelWidth = paneLayout.panelWidth();
        panelX = paneLayout.panelX();
        synchronizeFormFromDocument(true);
        int contentX = panelX + PANEL_MARGIN;
        int contentWidth = panelWidth - PANEL_MARGIN * 2;

        // This background is registered after the map and before the controls, so map input remains available outside it.
        addRenderableWidget(new PanelBackground(panelX, PANEL_TOP, panelWidth, Math.max(0, height - PANEL_TOP)));
        scopeButton = pinnedAction("gui.openmapdelta.area.scope", contentX, 51, contentWidth, 18, this::toggleScope);
        searchBox = new EditBox(font, contentX, 73, contentWidth, 18, Component.translatable("gui.openmapdelta.area.search"));
        searchBox.setMaxLength(128);
        searchBox.setValue(draft().areaFilter);
        if (areaList != null) areaList.setFilter(draft().areaFilter);
        searchBox.setResponder(value -> { draft().areaFilter = value; if (areaList != null) areaList.setFilter(value); });
        addRenderableWidget(searchBox);

        listY = paneLayout.listY();
        listHeight = paneLayout.listHeight();
        areaList = addRenderableWidget(new AreaListWidget(contentX, listY, contentWidth, listHeight,
                this::visibleAreas, this::selectArea, this::selectMergeTarget));
        controlsY = paneLayout.controlsTop();
        footerY = paneLayout.footerTop();
        int half = Math.max(1, (contentWidth - 4) / 2);
        int small = Math.max(1, (contentWidth - 8) / 3);
        int row = controlsY;

        String previousName = draft().name;
        nameBox = new EditBox(font, contentX, row, contentWidth, 18, Component.translatable("gui.openmapdelta.area.name"));
        nameBox.setMaxLength(MapArea.MAX_NAME_CODE_POINTS);
        nameBox.setValue(previousName);
        nameBox.setResponder(value -> draft().name = value);
        addBodyWidget(nameBox);
        row += 22;

        kindButton = action("gui.openmapdelta.area.kind", contentX, row, half, 18, this::cycleKind);
        shapeButton = action("gui.openmapdelta.area.shape", contentX + half + 4, row, contentWidth - half - 4, 18, this::cycleShape);
        row += 22;
        parentButton = action("gui.openmapdelta.area.parent", contentX, row, half, 18, this::cycleParent);
        colorButton = action("gui.openmapdelta.area.color", contentX + half + 4, row, contentWidth - half - 4, 18, this::cycleColor);
        row += 22;
        parentSearchBox = new EditBox(font, contentX, row, contentWidth, 18,
                Component.translatable("gui.openmapdelta.area.parent_search"));
        parentSearchBox.setMaxLength(128);
        parentSearchBox.setHint(Component.translatable("gui.openmapdelta.area.parent_search"));
        parentSearchBox.setValue(draft().parentFilter);
        parentSearchBox.setResponder(value -> draft().parentFilter = value);
        addBodyWidget(parentSearchBox);
        row += 22;
        visibleButton = action("gui.openmapdelta.area.visible", contentX, row, half, 18, () -> toggleStyle(0));
        labelsButton = action("gui.openmapdelta.area.labels", contentX + half + 4, row, contentWidth - half - 4, 18, () -> toggleStyle(1));
        row += 22;
        borderButton = action("gui.openmapdelta.area.border", contentX, row, half, 18, () -> toggleStyle(2));
        fillButton = action("gui.openmapdelta.area.fill", contentX + half + 4, row, contentWidth - half - 4, 18, () -> toggleStyle(3));
        row += 22;
        newButton = action("gui.openmapdelta.area.create", contentX, row, small, 18, this::createFeature);
        applyButton = action("gui.openmapdelta.area.apply", contentX + small + 4, row, small, 18, this::applyMetadata);
        deleteButton = action("gui.openmapdelta.area.delete", contentX + (small + 4) * 2, row,
                contentWidth - (small + 4) * 2, 18, this::deleteSelected);
        row += 22;
        drawButton = action("gui.openmapdelta.area.draw", contentX, row, half, 18, this::beginDrawing);
        finishButton = action("gui.openmapdelta.area.finish", contentX + half + 4, row, contentWidth - half - 4, 18, this::finishDrawing);
        row += 22;
        branchButton = action("gui.openmapdelta.area.branch", contentX, row, small, 18, this::beginBranch);
        holeButton = action("gui.openmapdelta.area.hole", contentX + small + 4, row, small, 18, this::beginHole);
        islandButton = action("gui.openmapdelta.area.island", contentX + (small + 4) * 2, row, contentWidth - (small + 4) * 2, 18, this::beginIslandOrSeparatePath);
        row += 22;

        pointXBox = new EditBox(font, contentX, row, Math.max(1, (contentWidth - 52) / 2), 18,
                Component.translatable("gui.openmapdelta.area.point_x"));
        pointZBox = new EditBox(font, contentX + (contentWidth - 52) / 2 + 4, row,
                Math.max(1, (contentWidth - 52) / 2), 18, Component.translatable("gui.openmapdelta.area.point_z"));
        pointXBox.setMaxLength(24); pointZBox.setMaxLength(24);
        setPointFieldValues(draft());
        pointXBox.setResponder(value -> { draft().pointX = value; if (!loadingPointValues) draft().pointFieldsDirty = true; });
        pointZBox.setResponder(value -> { draft().pointZ = value; if (!loadingPointValues) draft().pointFieldsDirty = true; });
        addBodyWidget(pointXBox); addBodyWidget(pointZBox);
        movePointButton = action("gui.openmapdelta.area.move_point", panelX + panelWidth - PANEL_MARGIN - 44, row, 44, 18, this::movePointFromFields);
        row += 22;
        int coordinateWidth = Math.max(1, contentWidth - 52);
        insertButton = action("gui.openmapdelta.area.insert_vertex", contentX, row, coordinateWidth / 2, 18, this::armInsertVertex);
        removePointButton = action("gui.openmapdelta.area.remove_vertex", contentX + coordinateWidth / 2 + 4, row,
                coordinateWidth - coordinateWidth / 2 - 4, 18, this::armRemoveVertex);
        moveLabelButton = action("gui.openmapdelta.area.move_label", contentX + coordinateWidth + 4, row, 44, 18, this::armMoveLabel);
        childABox = new EditBox(font, contentX, row + 22, half, 18, Component.translatable("gui.openmapdelta.area.child_a"));
        childBBox = new EditBox(font, contentX + half + 4, row + 22, contentWidth - half - 4, 18,
                Component.translatable("gui.openmapdelta.area.child_b"));
        childABox.setMaxLength(MapArea.MAX_NAME_CODE_POINTS); childBBox.setMaxLength(MapArea.MAX_NAME_CODE_POINTS);
        childABox.setValue(draft().childA); childBBox.setValue(draft().childB);
        childABox.setResponder(value -> draft().childA = value); childBBox.setResponder(value -> draft().childB = value);
        addBodyWidget(childABox); addBodyWidget(childBBox);
        row += 44;
        splitButton = action("gui.openmapdelta.area.split_children", contentX, row, half, 18, this::splitChildren);
        mergeButton = action("gui.openmapdelta.area.merge", contentX + half + 4, row, contentWidth - half - 4, 18, this::mergeAreas);
        row += 22;
        undoButton = action("gui.openmapdelta.area.undo", contentX, row, half, 18, this::undo);
        redoButton = action("gui.openmapdelta.area.redo", contentX + half + 4, row, contentWidth - half - 4, 18, this::redo);
        row += 22;
        saveButton = pinnedAction("gui.openmapdelta.area.save", contentX, footerY,
                Math.max(1, (contentWidth - 4) / 2), 18, this::save);
        int footerHalf = Math.max(1, (contentWidth - 4) / 2);
        cancelButton = pinnedAction("gui.openmapdelta.area.cancel", contentX + footerHalf + 4, footerY,
                contentWidth - footerHalf - 4, 18, this::onClose);
        layoutBodyWidgets();
        setInitialFocus(searchBox);
        updateControls();
    }

    private Button action(String key, int x, int y, int w, int h, Runnable callback) {
        Button button = Button.builder(Component.translatable(key), ignored -> callback.run()).bounds(x, y, w, h).build();
        editorActions.add(button);
        return addBodyWidget(button);
    }

    private Button pinnedAction(String key, int x, int y, int w, int h, Runnable callback) {
        Button button = Button.builder(Component.translatable(key), ignored -> callback.run()).bounds(x, y, w, h).build();
        return addRenderableWidget(button);
    }

    private <T extends AbstractWidget> T addBodyWidget(T widget) {
        bodyWidgets.add(widget);
        int offset = widget.getY() - controlsY;
        bodyOffsets.put(widget, offset);
        bodyLogicalActive.put(widget, true);
        bodyMaxOffset = Math.max(bodyMaxOffset, offset);
        return addRenderableWidget(widget);
    }

    private void layoutBodyWidgets() {
        int controlsBottom = footerY - 4;
        int viewportHeight = Math.max(0, controlsBottom - controlsY);
        int maxScroll = Math.max(0, (int) Math.ceil(Math.max(0, bodyMaxOffset + 18 - viewportHeight)
                / (double) AreaEditorLayout.CONTROL_ROW_HEIGHT));
        controlScroll = Math.max(0, Math.min(controlScroll, maxScroll));
        boolean focusedHidden = false;
        for (AbstractWidget widget : bodyWidgets) {
            int y = controlsY + bodyOffsets.get(widget) - controlScroll * AreaEditorLayout.CONTROL_ROW_HEIGHT;
            widget.setY(y);
            widget.visible = y >= controlsY && y + widget.getHeight() <= controlsBottom;
            widget.active = widget.visible && bodyLogicalActive.getOrDefault(widget, true);
            if (!widget.visible && getFocused() == widget) focusedHidden = true;
        }
        if (focusedHidden) setFocused(null);
    }

    private void toggleScope() {
        if (hasPendingSave()) return;
        if (drawing || newFeature || splitArmed || moveLabelArmed || insertVertexArmed || removeVertexArmed) {
            status = Component.translatable("gui.openmapdelta.area.finish_or_cancel_before_scope");
            return;
        }
        scope = scope == MapArea.Scope.PERSONAL ? MapArea.Scope.SHARED : MapArea.Scope.PERSONAL;
        Draft next = draft();
        selectedId = next.selectedId;
        mergeTargetId = next.mergeTargetId;
        synchronizeFormFromDocument(true);
        if (nameBox != null) nameBox.setValue(next.name);
        if (searchBox != null) searchBox.setValue(next.areaFilter);
        if (parentSearchBox != null) parentSearchBox.setValue(next.parentFilter);
        if (childABox != null) childABox.setValue(next.childA);
        if (childBBox != null) childBBox.setValue(next.childB);
        setPointFieldValues(next);
        selectedPoint = null; movingFrom = movingTo = null;
        if (areaList != null) areaList.resetScroll();
        updateControls();
    }

    private void selectArea(MapArea area) {
        if (area == null) return;
        drawing = false; newFeature = false; pendingPoints.clear(); pendingPaths.clear();
        moveLabelArmed = splitArmed = insertVertexArmed = removeVertexArmed = false;
        selectedId = area.id();
        mergeTargetId = draft().mergeTargetId;
        draft().selectedId = selectedId;
        MapArea editable = currentArea();
        if (editable != null) {
            synchronizeFormFromDocument(false);
            int foundColor = 0;
            for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == style.color()) foundColor = i;
            colorIndex = foundColor;
            selectedPoint = null;
            updatePointFields(null);
        }
        updateControls();
    }

    private void selectMergeTarget(MapArea area) {
        if (area == null) return;
        if (area.id().equals(selectedId)) {
            status = Component.translatable("gui.openmapdelta.area.merge_same");
            return;
        }
        mergeTargetId = area.id();
        draft().mergeTargetId = mergeTargetId;
        status = Component.translatable("gui.openmapdelta.area.merge_target", area.name());
        updateControls();
    }

    private List<MapArea> visibleAreas() {
        String dimension = AreaManager.dimension();
        String filter = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        if (!AreaManager.available(scope) || dimension == null) return List.of();
        return draft().document().areas().stream().filter(area -> area.dimension().equals(dimension))
                .filter(area -> filter.isEmpty() || area.name().toLowerCase(java.util.Locale.ROOT).contains(filter))
                .toList();
    }

    private MapArea currentArea() {
        return selectedId == null ? null : draft().document().find(selectedId).orElse(null);
    }

    /** Keeps persisted/history metadata and all form fields in one canonical state. */
    private void synchronizeFormFromDocument(boolean preservePendingForm) {
        Draft current = draft();
        current.selectedId = selectedId;
        boolean selectedMissing = selectedId != null && current.document().find(selectedId).isEmpty();
        boolean preserve = preservePendingForm && (selectedMissing || current.selectedId == null
                || hasMetadataFormChanges(current));
        if (!preserve) {
            AreaEditorState.Form form = AreaEditorState.synchronize(current.document(), selectedId);
            current.name = form.name();
            current.kind = form.kind();
            current.shape = form.shape();
            current.style = form.style();
            current.parentId = form.parentId();
        }
        kind = current.kind;
        shape = current.shape;
        style = current.style;
        colorIndex = 0;
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index] == style.color()) { colorIndex = index; break; }
        }
        if (nameBox != null && !nameBox.getValue().equals(current.name)) nameBox.setValue(current.name);
    }

    private boolean canEdit(MapArea area) { return AreaManager.canManage(area); }

    private void createFeature() {
        if (!canCreate()) return;
        if (shape != MapArea.Shape.LABEL) {
            newFeature = true;
            selectedId = UUID.randomUUID();
            draft().selectedId = selectedId;
            replaceGeometry = true;
            beginDrawing();
            status = Component.translatable("gui.openmapdelta.area.draw_hint");
            return;
        }
        MapArea.Point center = mapCenter();
        if (center == null) return;
        try {
            AreaEditorState.Creation creation = AreaEditorState.createLabel(UUID.randomUUID(), AreaManager.dimension(),
                    draft().name, kind, scope, AreaManager.actorId(), AreaManager.actorName(), parentIdForCreate(),
                    style, center);
            if (!creation.success()) {
                status = Component.translatable(creation.messageKey());
                updateControls();
                return;
            }
            MapArea area = creation.feature();
            applyDocument(draft().document().upsert(area));
            selectedId = area.id(); draft().selectedId = selectedId;
            synchronizeFormFromDocument(false);
            status = Component.translatable("gui.openmapdelta.area.created_draft");
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        updateControls();
    }

    private boolean canCreate() {
        boolean available = AreaManager.available(scope) && AreaManager.dimension() != null
                && AreaManager.actorId() != null && AreaManager.actorName() != null;
        if (!available) status = Component.translatable("gui.openmapdelta.area.unavailable");
        return available && !hasPendingSave();
    }

    private MapArea newArea(UUID id, MapArea.Geometry geometry, MapArea.Point label) {
        MapArea parent = draft().parentId == null ? null : draft().document().find(draft().parentId).orElse(null);
        if (parent != null && !AreaManager.canManage(parent)) {
            throw new IllegalArgumentException("gui.openmapdelta.area.parent_not_manageable");
        }
        return new MapArea(id, AreaManager.dimension(), draft().name, kind, scope, AreaManager.actorId(),
                AreaManager.actorName(), parentIdForCreate(), 0, geometry, label, style);
    }

    private UUID parentIdForCreate() {
        MapArea parent = draft().parentId == null ? null : draft().document().find(draft().parentId).orElse(null);
        return parent == null ? null : parent.id();
    }

    private void applyMetadata() {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || nameBox == null) return;
        try {
            MapArea changed = new MapArea(area.id(), area.dimension(), nameBox.getValue(), kind, area.scope(),
                    area.ownerId(), area.ownerName(), draft().parentId, area.revision(), area.geometry(), area.label(), style);
            applyDocument(draft().document().upsert(changed));
            synchronizeFormFromDocument(false);
            status = Component.translatable("gui.openmapdelta.area.draft_updated");
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        updateControls();
    }

    private void cycleKind() {
        kind = MapArea.Kind.values()[(kind.ordinal() + 1) % MapArea.Kind.values().length];
        draft().kind = kind;
        updateControls();
    }

    private void cycleShape() {
        shape = MapArea.Shape.values()[(shape.ordinal() + 1) % MapArea.Shape.values().length];
        draft().shape = shape;
        updateControls();
    }

    private void cycleParent() {
        List<MapArea> candidates = parentCandidates();
        ArrayList<UUID> ids = new ArrayList<>();
        ids.add(null);
        candidates.forEach(area -> ids.add(area.id()));
        int index = ids.indexOf(draft().parentId);
        draft().parentId = ids.get((index + 1) % ids.size());
        if (candidates.isEmpty()) status = Component.translatable("gui.openmapdelta.area.parent_no_matches");
        updateControls();
    }

    private List<MapArea> parentCandidates() {
        String filter = parentSearchBox == null ? "" : parentSearchBox.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        return draft().document().areas().stream().filter(area -> !area.id().equals(selectedId)
                && area.dimension().equals(AreaManager.dimension()) && area.scope() == scope
                && AreaDocument.isAdministrative(area.kind()) && AreaManager.canManage(area)
                && (filter.isEmpty() || area.name().toLowerCase(java.util.Locale.ROOT).contains(filter))).toList();
    }

    private void cycleColor() {
        colorIndex = (colorIndex + 1) % COLORS.length;
        style = new MapArea.Style(COLORS[colorIndex], style.visible(), style.labels(), style.border(), style.fill(),
                style.minBpp(), style.maxBpp(), style.priority());
        draft().style = style;
        updateStyleOnSelectedArea();
        updateControls();
    }

    private void toggleStyle(int which) {
        style = switch (which) {
            case 0 -> new MapArea.Style(style.color(), !style.visible(), style.labels(), style.border(), style.fill(), style.minBpp(), style.maxBpp(), style.priority());
            case 1 -> new MapArea.Style(style.color(), style.visible(), !style.labels(), style.border(), style.fill(), style.minBpp(), style.maxBpp(), style.priority());
            case 2 -> new MapArea.Style(style.color(), style.visible(), style.labels(), !style.border(), style.fill(), style.minBpp(), style.maxBpp(), style.priority());
            default -> new MapArea.Style(style.color(), style.visible(), style.labels(), style.border(), !style.fill(), style.minBpp(), style.maxBpp(), style.priority());
        };
        draft().style = style;
        updateStyleOnSelectedArea();
        updateControls();
    }

    private void updateStyleOnSelectedArea() {
        MapArea area = currentArea();
        if (area != null && canEdit(area)) applyDocument(draft().document().upsert(area.withStyle(style)));
    }

    private void beginDrawing() {
        MapArea area = currentArea();
        if (area != null && !canEdit(area)) return;
        if (area == null && !newFeature && !canCreate()) return;
        if (!newFeature) shape = area == null ? shape : area.geometry().shape();
        replaceGeometry = true;
        drawing = true;
        ringMode = RingMode.OUTER;
        pendingPoints.clear();
        pendingPaths.clear();
        insertVertexArmed = removeVertexArmed = false;
        moveLabelArmed = splitArmed = false;
        status = Component.translatable("gui.openmapdelta.area.draw_hint");
        updateControls();
    }

    private void beginBranch() {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || area.geometry().shape() != MapArea.Shape.LINE) return;
        shape = MapArea.Shape.LINE;
        replaceGeometry = false;
        drawing = true;
        ringMode = RingMode.OUTER;
        pendingPoints.clear();
        pendingPaths.clear();
        pendingPaths.addAll(area.geometry().paths());
        status = Component.translatable("gui.openmapdelta.area.branch_hint");
        updateControls();
    }

    private void beginHole() { beginRingAddition(RingMode.HOLE); }
    private void beginIsland() { beginRingAddition(RingMode.ISLAND); }

    private void beginIslandOrSeparatePath() {
        MapArea area = currentArea();
        if (area != null && area.geometry().shape() == MapArea.Shape.LINE) separateSelectedPath();
        else beginIsland();
    }

    private void separateSelectedPath() {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || selectedPoint == null || area.geometry().shape() != MapArea.Shape.LINE) return;
        if (area.geometry().paths().size() < 2) {
            status = Component.translatable("gui.openmapdelta.area.separate_requires_other_path");
            updateControls();
            return;
        }
        try {
            AreaDocument separated = AreaEditorOperations.separateLinePath(draft().document(), area.id(), selectedPoint,
                    nameBox.getValue().strip());
            UUID detachedId = separated.areas().stream().filter(candidate -> !candidate.id().equals(area.id())
                    && draft().document().find(candidate.id()).isEmpty()).map(MapArea::id).findFirst().orElseThrow();
            applyDocument(separated);
            selectedId = detachedId; draft().selectedId = detachedId;
            MapArea detached = currentArea();
            kind = detached.kind(); shape = detached.geometry().shape(); style = detached.style();
            draft().name = detached.name(); nameBox.setValue(detached.name());
            status = Component.translatable("gui.openmapdelta.area.path_separated", detached.name());
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        updateControls();
    }

    private void armMoveLabel() {
        if (!editableSelected()) return;
        drawing = false; insertVertexArmed = removeVertexArmed = splitArmed = false;
        moveLabelArmed = true;
        status = Component.translatable("gui.openmapdelta.area.move_label_hint");
        updateControls();
    }

    private void beginRingAddition(RingMode mode) {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || area.geometry().shape() != MapArea.Shape.POLYGON) return;
        shape = MapArea.Shape.POLYGON;
        replaceGeometry = false;
        drawing = true;
        ringMode = mode;
        pendingPoints.clear();
        pendingPaths.clear();
        status = Component.translatable(mode == RingMode.HOLE
                ? "gui.openmapdelta.area.hole_hint" : "gui.openmapdelta.area.island_hint");
        updateControls();
    }

    private void addDrawingPoint(MapArea.Point point) {
        if (!AreaEditorState.canMutateMap(hasPendingSave())) return;
        if (pendingPoints.size() >= AreaDocument.MAX_POINTS_PER_AREA) {
            status = Component.translatable("gui.openmapdelta.area.point_limit");
            return;
        }
        pendingPoints.add(point);
        FullscreenMapRenderer.invalidate();
    }

    private void finishDrawing() {
        if (!drawing) return;
        try {
            MapArea.Geometry geometry;
            MapArea area = currentArea();
            if (shape == MapArea.Shape.LINE) {
                ArrayList<List<MapArea.Point>> paths = new ArrayList<>();
                if (!replaceGeometry && area != null && pendingPaths.isEmpty()) paths.addAll(area.geometry().paths());
                paths.addAll(pendingPaths);
                if (pendingPoints.size() >= 2) paths.add(List.copyOf(pendingPoints));
                geometry = MapArea.Geometry.line(paths);
            } else if (shape == MapArea.Shape.POLYGON) {
                if (pendingPoints.size() < 3) throw new IllegalArgumentException("gui.openmapdelta.area.need_three_points");
                List<MapArea.Point> ring = List.copyOf(pendingPoints);
                if (replaceGeometry || area == null || area.geometry().shape() != MapArea.Shape.POLYGON) {
                    geometry = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring, List.of())));
                } else if (ringMode == RingMode.ISLAND) {
                    ArrayList<MapArea.Polygon> polygons = new ArrayList<>(area.geometry().polygons());
                    polygons.add(new MapArea.Polygon(ring, List.of()));
                    geometry = MapArea.Geometry.polygon(polygons);
                } else if (ringMode == RingMode.HOLE) {
                    geometry = AreaEditorState.addHole(area.geometry(), ring);
                } else {
                    geometry = MapArea.Geometry.polygon(List.of(new MapArea.Polygon(ring, List.of())));
                }
            } else {
                throw new IllegalArgumentException("gui.openmapdelta.area.select_label_on_map");
            }
            MapArea.Point label = area == null ? AreaGeometryOps.anchor(geometry) : area.label();
            if (newFeature || area == null) {
                area = newArea(selectedId == null ? UUID.randomUUID() : selectedId, geometry, label);
                applyDocument(draft().document().upsert(area));
                selectedId = area.id(); draft().selectedId = selectedId;
                newFeature = false;
                synchronizeFormFromDocument(false);
            } else {
                MapArea changed = area.withGeometry(geometry).withLabel(label);
                applyDocument(draft().document().upsert(changed));
            }
            drawing = false;
            pendingPoints.clear(); pendingPaths.clear();
            status = Component.translatable("gui.openmapdelta.area.geometry_added");
        } catch (IllegalArgumentException invalid) {
            String message = invalid.getMessage();
            status = message != null && message.startsWith("gui.") ? Component.translatable(message)
                    : Component.translatable("gui.openmapdelta.area.geometry_invalid");
        }
        updateControls();
        FullscreenMapRenderer.invalidate();
    }

    private void armInsertVertex() {
        if (!editableSelected()) return;
        drawing = false;
        insertVertexArmed = true; removeVertexArmed = false;
        moveLabelArmed = splitArmed = false;
        status = Component.translatable("gui.openmapdelta.area.insert_hint");
        updateControls();
    }

    private void armRemoveVertex() {
        if (!editableSelected()) return;
        drawing = false;
        removeVertexArmed = true; insertVertexArmed = false;
        moveLabelArmed = splitArmed = false;
        status = Component.translatable("gui.openmapdelta.area.remove_hint");
        updateControls();
    }

    private boolean editableSelected() {
        MapArea area = currentArea();
        return area != null && canEdit(area) && !hasPendingSave();
    }

    private void insertVertexAt(MapArea.Point location) {
        MapArea area = currentArea();
        if (area == null || !canEdit(area)) return;
        ScreenEdge edge = nearestEdge(area.geometry(), location, 10.0);
        if (edge == null) { status = Component.translatable("gui.openmapdelta.area.no_nearby_edge"); return; }
        try {
            MapArea.Point inserted = projectOnEdge(edge.first(), edge.second(), location);
            applyDocument(AreaGeometryOps.insertPoint(draft().document(), area.id(), edge.first(), edge.second(), inserted));
            selectedPoint = inserted; updatePointFields(inserted);
            status = Component.translatable("gui.openmapdelta.area.draft_updated");
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        insertVertexArmed = false; updateControls();
    }

    private void removeVertexAt(MapArea.Point location) {
        MapArea area = currentArea();
        if (area == null || !canEdit(area)) return;
        MapArea.Point point = nearestVertex(area.geometry(), location, 10.0);
        if (point == null) { status = Component.translatable("gui.openmapdelta.area.no_nearby_vertex"); return; }
        try {
            ArrayList<MapArea> changed = new ArrayList<>();
            for (MapArea candidate : draft().document().areas()) {
                boolean sibling = area.id().equals(candidate.id()) || (area.kind() == MapArea.Kind.REGION
                        && candidate.kind() == MapArea.Kind.REGION && area.scope() == candidate.scope()
                        && area.dimension().equals(candidate.dimension()) && Objects.equals(area.parentId(), candidate.parentId()));
                if (sibling && hasVertex(candidate.geometry(), point)) changed.add(candidate.withGeometry(removePoint(candidate.geometry(), point)));
            }
            AreaDocument result = draft().document();
            for (MapArea candidate : changed) result = result.upsert(candidate);
            applyDocument(result);
            selectedPoint = null; updatePointFields(null);
            status = Component.translatable("gui.openmapdelta.area.draft_updated");
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        removeVertexArmed = false; updateControls();
    }

    private void movePointFromFields() {
        if (!editableSelected() || pointXBox == null || pointZBox == null) return;
        try {
            MapArea.Point moved = new MapArea.Point(Double.parseDouble(pointXBox.getValue().strip()),
                    Double.parseDouble(pointZBox.getValue().strip()));
            MapArea area = currentArea();
            if (selectedPoint == null) {
                applyDocument(draft().document().upsert(area.withLabel(moved)));
            } else {
                if (selectedPoint == null) throw new IllegalArgumentException("gui.openmapdelta.area.select_vertex_first");
                applyDocument(AreaGeometryOps.movePoint(draft().document(), area.id(), selectedPoint, moved));
                selectedPoint = moved;
            }
            updatePointFields(moved);
            status = Component.translatable("gui.openmapdelta.area.draft_updated");
        } catch (IllegalArgumentException invalid) {
            String message = invalid.getMessage();
            status = message != null && message.startsWith("gui.") ? Component.translatable(message)
                    : Component.translatable("gui.openmapdelta.area.invalid_coordinate");
        }
        updateControls();
    }

    private void splitChildren() {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || area.geometry().shape() != MapArea.Shape.POLYGON) return;
        if (area.kind() != MapArea.Kind.COUNTRY && area.kind() != MapArea.Kind.REGION) return;
        splitArmed = true;
        moveLabelArmed = insertVertexArmed = removeVertexArmed = false;
        pendingPoints.clear();
        status = Component.translatable("gui.openmapdelta.area.split_hint");
        updateControls();
    }

    private void splitChildren(MapArea.Point first, MapArea.Point second) {
        MapArea area = currentArea();
        if (area == null || !canEdit(area) || area.geometry().shape() != MapArea.Shape.POLYGON) return;
        if (area.kind() != MapArea.Kind.COUNTRY && area.kind() != MapArea.Kind.REGION) return;
        try {
            String firstName = childABox.getValue().strip(), secondName = childBBox.getValue().strip();
            applyDocument(AreaGeometryOps.splitIntoChildren(draft().document(), area.id(), first, second, firstName, secondName));
            status = Component.translatable("gui.openmapdelta.area.draft_updated");
        } catch (IllegalArgumentException invalid) { invalid(invalid); }
        splitArmed = false;
        pendingPoints.clear();
        updateControls();
    }

    private void mergeAreas() {
        MapArea retained = currentArea();
        MapArea merged = mergeTargetId == null ? null : draft().document().find(mergeTargetId).orElse(null);
        if (retained == null || merged == null || !canEdit(retained) || !canEdit(merged)) return;
        if (retained.geometry().shape() != MapArea.Shape.POLYGON || merged.geometry().shape() != MapArea.Shape.POLYGON
                || retained.scope() != merged.scope() || !retained.dimension().equals(merged.dimension())
                || retained.kind() != merged.kind() || !Objects.equals(retained.parentId(), merged.parentId())) {
            status = Component.translatable("gui.openmapdelta.area.merge_incompatible");
            updateControls(); return;
        }
        String retainedName = nameBox.getValue().strip();
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (minecraft == null) return;
            if (confirmed) {
                try {
                    MapArea target = retained.withName(retainedName).withGeometry(
                            AreaGeometryOps.union(List.of(retained.geometry(), merged.geometry())));
                    AreaDocument changed = draft().document().remove(merged.id()).upsert(target);
                    applyDocument(changed);
                    status = Component.translatable("gui.openmapdelta.area.draft_updated");
                } catch (IllegalArgumentException invalid) { invalid(invalid); }
            }
            minecraft.gui.setScreen(this);
            updateControls();
        }, Component.translatable("gui.openmapdelta.area.merge_title"),
                Component.translatable("gui.openmapdelta.area.merge_message", retainedName, merged.name()),
                Component.translatable("gui.openmapdelta.area.merge"), Component.translatable("gui.openmapdelta.area.cancel")));
    }

    private void deleteSelected() {
        MapArea area = currentArea();
        if (area == null || !canEdit(area)) return;
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (minecraft == null) return;
            if (confirmed) {
                applyDocument(draft().document().remove(area.id()));
                selectedId = null; draft().selectedId = null;
                status = Component.translatable("gui.openmapdelta.area.draft_updated");
            }
            minecraft.gui.setScreen(this);
            updateControls();
        }, Component.translatable("gui.openmapdelta.area.delete_title"),
                Component.translatable("gui.openmapdelta.area.delete_message", area.name()),
                Component.translatable("gui.openmapdelta.area.delete"), Component.translatable("gui.openmapdelta.area.cancel")));
    }

    private void undo() {
        if (hasPendingSave()) return;
        draft().history.undo();
        restoreSelectedFields();
        status = Component.translatable("gui.openmapdelta.area.undo_done");
        updateControls();
    }

    private void redo() {
        if (hasPendingSave()) return;
        draft().history.redo();
        restoreSelectedFields();
        status = Component.translatable("gui.openmapdelta.area.redo_done");
        updateControls();
    }

    private void restoreSelectedFields() {
        if (selectedId != null && draft().document().find(selectedId).isEmpty()) selectedId = null;
        draft().selectedId = selectedId;
        synchronizeFormFromDocument(false);
    }

    private void applyDocument(AreaDocument document) {
        if (!AreaEditorState.canMutateMap(hasPendingSave())) return;
        draft().history.apply(document);
        FullscreenMapRenderer.invalidate();
    }

    private void save() {
        if (!AreaManager.available(scope) || hasPendingSave()) return;
        MapArea selected = currentArea();
        if (selected != null && canEdit(selected) && hasMetadataFormChanges(draft())) applyMetadata();
        if (draft().pointFieldsDirty || hasMetadataFormChanges(draft()) || selected == null && hasFormChanges(draft())) return;
        AreaManager.SaveResult result = AreaManager.save(scope, draft().base, draft().document());
        status = Component.translatable(result.messageKey());
        if (result.pending()) pendingRequestId = result.requestId();
        else if (result.success()) rebaseDraft();
        updateControls();
    }

    private void rebaseDraft() {
        AreaDocument saved = AreaManager.document(scope);
        Draft replacement = new Draft(saved);
        replacement.selectedId = selectedId;
        drafts.put(scope, replacement);
        if (currentArea() == null) selectedId = null;
        synchronizeFormFromDocument(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (OpenMapDeltaClient.getSelectedMapLayer() != OpenMapDeltaClient.SURFACE_LAYER) {
            OpenMapDeltaClient.setSelectedMapLayer(OpenMapDeltaClient.SURFACE_LAYER);
        }
        if (pendingRequestId == null) return;
        AreaManager.SaveResult result = AreaManager.pendingResult(pendingRequestId);
        if (!result.pending()) {
            pendingRequestId = null;
            status = Component.translatable(result.messageKey());
            if (result.success()) rebaseDraft();
            updateControls();
        }
    }

    private boolean hasPendingSave() { return pendingRequestId != null; }

    private boolean hasFormChanges(Draft value) {
        MapArea selected = value.selectedId == null ? null : value.document().find(value.selectedId).orElse(null);
        if (selected == null) return !value.name.isBlank() || value.pointFieldsDirty;
        return hasMetadataFormChanges(value) || value.pointFieldsDirty;
    }

    private boolean hasMetadataFormChanges(Draft value) {
        MapArea selected = value.selectedId == null ? null : value.document().find(value.selectedId).orElse(null);
        if (selected == null) return false;
        return !selected.name().equals(value.name) || selected.kind() != value.kind
                || !selected.style().equals(value.style) || !Objects.equals(selected.parentId(), value.parentId);
    }

    private boolean hasUnsavedChanges() {
        return newFeature || (drawing && !pendingPoints.isEmpty()) || splitArmed || moveLabelArmed
                || drafts.values().stream().anyMatch(value -> value.dirty() || hasFormChanges(value));
    }

    private void updateControls() {
        if (scopeButton == null) return;
        boolean available = AreaManager.available(scope);
        scopeButton.setMessage(Component.translatable(scope == MapArea.Scope.PERSONAL
                ? (draft().dirty() || hasFormChanges(draft()) ? "gui.openmapdelta.area.scope_personal_dirty" : "gui.openmapdelta.area.scope_personal")
                : (draft().dirty() || hasFormChanges(draft()) ? "gui.openmapdelta.area.scope_shared_dirty" : "gui.openmapdelta.area.scope_shared")));
        kindButton.setMessage(Component.translatable("gui.openmapdelta.area.kind_value",
                Component.translatable("gui.openmapdelta.area.kind." + kind.name().toLowerCase(java.util.Locale.ROOT))));
        shapeButton.setMessage(Component.translatable("gui.openmapdelta.area.shape_value",
                Component.translatable("gui.openmapdelta.area.shape." + shape.name().toLowerCase(java.util.Locale.ROOT))));
        MapArea parent = draft().parentId == null ? null : draft().document().find(draft().parentId).orElse(null);
        parentButton.setMessage(Component.translatable("gui.openmapdelta.area.parent_value", parent == null
                ? Component.translatable("gui.openmapdelta.area.none") : Component.literal(parent.name())));
        colorButton.setMessage(Component.translatable("gui.openmapdelta.area.color_value", colorIndex + 1));
        visibleButton.setMessage(Component.translatable(style.visible() ? "gui.openmapdelta.area.visible_on" : "gui.openmapdelta.area.visible_off"));
        labelsButton.setMessage(Component.translatable(style.labels() ? "gui.openmapdelta.area.labels_on" : "gui.openmapdelta.area.labels_off"));
        borderButton.setMessage(Component.translatable(style.border() ? "gui.openmapdelta.area.border_on" : "gui.openmapdelta.area.border_off"));
        fillButton.setMessage(Component.translatable(style.fill() ? "gui.openmapdelta.area.fill_on" : "gui.openmapdelta.area.fill_off"));
        scopeButton.active = !hasPendingSave();
        MapArea area = currentArea();
        boolean editable = area == null ? available : canEdit(area);
        boolean locked = hasPendingSave();
        boolean editableForm = AreaEditorState.canEditForm(locked, area != null && editable);
        boolean childNameForm = AreaEditorState.canEditForm(locked, area != null && editable
                && AreaDocument.isAdministrative(area.kind()) && area.geometry().shape() == MapArea.Shape.POLYGON);
        newButton.active = available && !locked;
        applyButton.active = area != null && editable && !locked;
        kindButton.active = area == null ? available && !locked : editable && !locked;
        shapeButton.active = available && !locked;
        parentButton.active = area == null ? available && !locked : editable && !locked;
        colorButton.active = area == null ? available && !locked : editable && !locked;
        visibleButton.active = area == null ? available && !locked : editable && !locked;
        labelsButton.active = area == null ? available && !locked : editable && !locked;
        borderButton.active = area == null ? available && !locked : editable && !locked;
        fillButton.active = area == null ? available && !locked : editable && !locked;
        drawButton.active = (area == null ? available && shape != MapArea.Shape.LABEL : editable) && !locked;
        finishButton.active = drawing && !locked;
        branchButton.active = area != null && editable && area.geometry().shape() == MapArea.Shape.LINE && !locked;
        holeButton.active = area != null && editable && area.geometry().shape() == MapArea.Shape.POLYGON && !locked;
        islandButton.active = holeButton.active;
        islandButton.active = area != null && editable && !locked
                && (area.geometry().shape() == MapArea.Shape.POLYGON
                || (area.geometry().shape() == MapArea.Shape.LINE && selectedPoint != null
                && area.geometry().paths().size() > 1));
        islandButton.setMessage(Component.translatable(area != null && area.geometry().shape() == MapArea.Shape.LINE
                ? "gui.openmapdelta.area.separate_path" : "gui.openmapdelta.area.island"));
        insertButton.active = editable && !locked;
        removePointButton.active = editable && !locked;
        moveLabelButton.active = area != null && editable && !locked;
        moveLabelButton.setMessage(Component.translatable("gui.openmapdelta.area.move_label"));
        movePointButton.active = editable && !locked;
        splitButton.active = area != null && editable && AreaDocument.isAdministrative(area.kind())
                && area.geometry().shape() == MapArea.Shape.POLYGON && !locked;
        splitButton.setMessage(Component.translatable(splitArmed
                ? "gui.openmapdelta.area.split_cancel" : "gui.openmapdelta.area.split_children"));
        MapArea mergeTarget = mergeTargetId == null ? null : draft().document().find(mergeTargetId).orElse(null);
        mergeButton.active = area != null && mergeTarget != null && editable && canEdit(mergeTarget) && !locked;
        undoButton.active = draft().history.canUndo() && !locked;
        redoButton.active = draft().history.canRedo() && !locked;
        saveButton.active = available && (draft().dirty() || hasMetadataFormChanges(draft())) && !locked;
        if (nameBox != null) nameBox.active = area == null ? available && !locked : editable && !locked;
        if (pointXBox != null) pointXBox.active = editableForm;
        if (pointZBox != null) pointZBox.active = editableForm;
        if (childABox != null) childABox.active = childNameForm;
        if (childBBox != null) childBBox.active = childNameForm;
        // Scrolling only clips the filter; it must not permanently disable its logical input state.
        if (parentSearchBox != null) parentSearchBox.active = true;
        cancelButton.active = !locked;
        for (AbstractWidget widget : bodyWidgets) {
            boolean logicalActive = editorActions.contains(widget) || widget instanceof EditBox
                    ? widget.active : true;
            bodyLogicalActive.put(widget, logicalActive);
        }
        layoutBodyWidgets();
        if (areaList != null) {
            areaList.active = !locked;
            areaList.refreshSelection(selectedId, mergeTargetId);
        }
    }

    private void updatePointFields(MapArea.Point point) {
        Draft current = draft();
        current.pointX = point == null ? "" : Double.toString(point.x());
        current.pointZ = point == null ? "" : Double.toString(point.z());
        current.pointFieldsDirty = false;
        if (pointXBox == null || pointZBox == null) return;
        setPointFieldValues(current);
    }

    private void setPointFieldValues(Draft value) {
        if (pointXBox == null || pointZBox == null) return;
        loadingPointValues = true;
        try {
            pointXBox.setValue(value.pointX);
            pointZBox.setValue(value.pointZ);
        } finally {
            loadingPointValues = false;
        }
    }

    private MapArea.Point mapCenter() {
        MapArea.Point center = AreaEditorState.safePoint(getPanX(), getPanZ());
        if (center == null) status = Component.translatable("gui.openmapdelta.area.invalid_coordinate");
        return center;
    }

    private MapArea.Point mapPoint(double screenX, double screenY) {
        FullscreenMapPanel panel = getMapPanel();
        if (panel == null) return mapCenter();
        var projection = new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                getPanX(), getPanZ(), getBlocksPerPixel());
        MapArea.Point point = AreaEditorState.safePoint(projection.worldX(screenX), projection.worldZ(screenY));
        if (point == null) status = Component.translatable("gui.openmapdelta.area.invalid_coordinate");
        return point;
    }

    private boolean isMapPoint(double x, double y) {
        FullscreenMapPanel panel = getMapPanel();
        return panel != null && x >= panel.getX() && x < panel.getX() + panel.getWidth()
                && y >= panel.getY() && y < panel.getY() + panel.getHeight() && x < panelX;
    }

    private boolean isInPanel(double x, double y) { return x >= panelX && y >= PANEL_TOP; }

    @Override
    public List<MapArea> getAreaPreview() {
        ArrayList<MapArea> preview = new ArrayList<>();
        MapArea area = currentArea();
        if (area == null && !newFeature) return List.of();
        try {
            if (movingFrom != null && movingTo != null && area != null) {
                AreaDocument moved = AreaGeometryOps.movePoint(draft().document(), area.id(), movingFrom, movingTo);
                for (MapArea candidate : moved.areas()) {
                    MapArea original = draft().document().find(candidate.id()).orElse(null);
                    if (!candidate.equals(original)) preview.add(candidate);
                }
            } else if (drawing && !pendingPoints.isEmpty()) {
                MapArea.Geometry geometry = previewGeometry(area);
                if (geometry != null) {
                    MapArea.Point label = area == null ? AreaGeometryOps.anchor(geometry) : area.label();
                    area = area == null ? newArea(selectedId == null ? UUID.randomUUID() : selectedId, geometry, label)
                            : area.withGeometry(geometry).withLabel(label);
                    preview.add(area);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // An invalid in-progress ring is not rasterized; the draft remains editable.
        }
        return List.copyOf(preview);
    }

    @Override
    public List<MapArea> getAreaRenderAreas() {
        if (minecraft == null || minecraft.level == null) return List.of();
        EnumMap<MapArea.Scope, AreaDocument> persisted = new EnumMap<>(MapArea.Scope.class);
        for (MapArea.Scope value : MapArea.Scope.values()) persisted.put(value, AreaManager.document(value));
        EnumMap<MapArea.Scope, AreaDocument> currentDrafts = new EnumMap<>(MapArea.Scope.class);
        drafts.forEach((value, current) -> currentDrafts.put(value, current.document()));
        return AreaEditorState.renderAreas(persisted, currentDrafts,
                minecraft.level.dimension().identifier().toString());
    }

    private MapArea.Geometry previewGeometry(MapArea area) {
        if (shape == MapArea.Shape.LINE) {
            ArrayList<List<MapArea.Point>> paths = new ArrayList<>();
            if (!replaceGeometry && area != null && area.geometry().shape() == MapArea.Shape.LINE && pendingPaths.isEmpty()) {
                paths.addAll(area.geometry().paths());
            }
            paths.addAll(pendingPaths);
            if (pendingPoints.size() >= 2) paths.add(List.copyOf(pendingPoints));
            return paths.isEmpty() ? null : MapArea.Geometry.line(paths);
        }
        if (shape == MapArea.Shape.POLYGON && pendingPoints.size() >= 3) {
            return MapArea.Geometry.polygon(List.of(new MapArea.Polygon(List.copyOf(pendingPoints), List.of())));
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.buttonInfo().button();
        if (hasPendingSave() && isMapPoint(x, y) && (button == 0 || button == 1)) return true;
        if (isInPanel(x, y)) {
            if (button == 1 && areaList != null && x >= areaList.getX() && x < areaList.getX() + areaList.getWidth()
                    && y >= areaList.getY() && y < areaList.getY() + areaList.getHeight()) {
                if (hasPendingSave()) return true;
                return areaList.mouseClicked(event, doubleClick);
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (isMapPoint(x, y)) {
            if (button == 1) {
                if (drawing || splitArmed || moveLabelArmed || insertVertexArmed || removeVertexArmed) {
                    drawing = false; splitArmed = moveLabelArmed = insertVertexArmed = removeVertexArmed = false; pendingPoints.clear(); pendingPaths.clear();
                    status = Component.translatable("gui.openmapdelta.area.draw_cancelled"); updateControls();
                }
                return true;
            }
            if (button == 0) {
                MapArea.Point point = mapPoint(x, y);
                if (point == null) return true;
                if (splitArmed) {
                    if (pendingPoints.isEmpty()) {
                        pendingPoints.add(point);
                        status = Component.translatable("gui.openmapdelta.area.split_second_point");
                    } else {
                        splitChildren(pendingPoints.get(0), point);
                    }
                    return true;
                }
                if (drawing) { addDrawingPoint(point); return true; }
                if (insertVertexArmed) { insertVertexAt(point); return true; }
                if (removeVertexArmed) { removeVertexAt(point); return true; }
                MapArea area = currentArea();
                if (moveLabelArmed && area != null && canEdit(area)) {
                    applyDocument(draft().document().upsert(area.withLabel(point)));
                    moveLabelArmed = false; selectedPoint = null; updatePointFields(point);
                    status = Component.translatable("gui.openmapdelta.area.draft_updated"); updateControls();
                    return true;
                }
                if (area != null && canEdit(area) && area.geometry().shape() == MapArea.Shape.LABEL) {
                    applyDocument(draft().document().upsert(area.withLabel(point)));
                    selectedPoint = null; updatePointFields(point);
                    status = Component.translatable("gui.openmapdelta.area.draft_updated");
                    return true;
                }
                MapArea.Point vertex = area == null ? null : nearestVertex(area.geometry(), point, 8.0);
                if (vertex != null && canEdit(area)) {
                    movingFrom = vertex; movingTo = vertex; selectedPoint = vertex; updatePointFields(vertex);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (hasPendingSave() && movingFrom != null) {
            movingFrom = movingTo = null;
            return true;
        }
        if (movingFrom != null && isMapPoint(event.x(), event.y())) {
            MapArea.Point point = mapPoint(event.x(), event.y());
            if (point == null) {
                MapArea.Point original = movingFrom;
                movingFrom = movingTo = null;
                selectedPoint = original;
                updatePointFields(original);
                updateControls();
                return true;
            }
            movingTo = point;
            updatePointFields(movingTo);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (movingFrom != null) {
            if (!hasPendingSave() && movingTo != null && !movingFrom.equals(movingTo)) {
                MapArea area = currentArea();
                try { if (area != null) applyDocument(AreaGeometryOps.movePoint(draft().document(), area.id(), movingFrom, movingTo)); }
                catch (IllegalArgumentException invalid) { invalid(invalid); movingTo = movingFrom; updatePointFields(movingFrom); }
            }
            movingFrom = movingTo = null;
            updateControls();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInPanel(mouseX, mouseY)) {
            if (areaList != null && mouseX >= areaList.getX() && mouseX < areaList.getX() + areaList.getWidth()
                    && mouseY >= areaList.getY() && mouseY < areaList.getY() + areaList.getHeight()) {
                return areaList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            if (mouseY >= controlsY && mouseY < footerY) {
                controlScroll -= (int) Math.signum(scrollY);
                layoutBodyWidgets();
                updateControls();
                return true;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (hasPendingSave() && (!(getFocused() instanceof EditBox)
                && event.hasControlDown() && (key == 90 || key == 89) || drawing && (key == 257 || key == 335))) {
            return true;
        }
        if (!(getFocused() instanceof EditBox)) {
            if (event.hasControlDown() && key == 90) { undo(); return true; }
            if (event.hasControlDown() && key == 89) { redo(); return true; }
        }
        if (drawing && (key == 257 || key == 335)) { finishDrawing(); return true; }
        if (drawing && key == 256) {
            drawing = false; pendingPoints.clear(); pendingPaths.clear();
            status = Component.translatable("gui.openmapdelta.area.draw_cancelled"); updateControls(); return true;
        }
        if (splitArmed && key == 256) { splitArmed = false; pendingPoints.clear(); updateControls(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean handleControllerAction(String action, double cursorGuiX, double cursorGuiY) {
        if ("context".equals(action) || "toggle_waypoints".equals(action)) return true;
        return super.handleControllerAction(action, cursorGuiX, cursorGuiY);
    }

    private void invalid(IllegalArgumentException invalid) {
        String message = invalid.getMessage();
        status = message != null && message.startsWith("gui.") ? Component.translatable(message)
                : Component.translatable("gui.openmapdelta.area.geometry_invalid");
    }

    private record ScreenEdge(MapArea.Point first, MapArea.Point second, double distance) {}

    private double screenDistance(MapArea.Point first, MapArea.Point second, MapArea.Point target) {
        FullscreenMapPanel panel = getMapPanel();
        if (panel == null) return Double.POSITIVE_INFINITY;
        var projection = new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                getPanX(), getPanZ(), getBlocksPerPixel());
        return Math.hypot(projection.screenX(first.x()) - projection.screenX(target.x()),
                projection.screenY(first.z()) - projection.screenY(target.z()));
    }

    private ScreenEdge nearestEdge(MapArea.Geometry geometry, MapArea.Point target, double maximumPixels) {
        ScreenEdge nearest = null;
        for (List<MapArea.Point> path : geometry.paths()) {
            for (int i = 1; i < path.size(); i++) nearest = nearerEdge(nearest, path.get(i - 1), path.get(i), target, maximumPixels);
        }
        for (MapArea.Polygon polygon : geometry.polygons()) {
            nearest = nearestRingEdge(nearest, polygon.outer(), target, maximumPixels);
            for (List<MapArea.Point> hole : polygon.holes()) nearest = nearestRingEdge(nearest, hole, target, maximumPixels);
        }
        return nearest;
    }

    private ScreenEdge nearestRingEdge(ScreenEdge nearest, List<MapArea.Point> ring, MapArea.Point target, double max) {
        for (int i = 0; i < ring.size(); i++) nearest = nearerEdge(nearest, ring.get(i), ring.get((i + 1) % ring.size()), target, max);
        return nearest;
    }

    private ScreenEdge nearerEdge(ScreenEdge nearest, MapArea.Point first, MapArea.Point second,
                                  MapArea.Point target, double maximumPixels) {
        double distance = distanceToSegment(first, second, target);
        return distance <= maximumPixels && (nearest == null || distance < nearest.distance())
                ? new ScreenEdge(first, second, distance) : nearest;
    }

    private MapArea.Point nearestVertex(MapArea.Geometry geometry, MapArea.Point target, double maximumPixels) {
        MapArea.Point nearest = null;
        double distance = maximumPixels;
        for (MapArea.Point point : allVertices(geometry)) {
            double current = screenDistance(point, point, target);
            if (current <= distance) { nearest = point; distance = current; }
        }
        return nearest;
    }

    private List<MapArea.Point> allVertices(MapArea.Geometry geometry) {
        ArrayList<MapArea.Point> points = new ArrayList<>();
        geometry.paths().forEach(points::addAll);
        for (MapArea.Polygon polygon : geometry.polygons()) {
            points.addAll(polygon.outer()); polygon.holes().forEach(points::addAll);
        }
        return points;
    }

    private double distanceToSegment(MapArea.Point first, MapArea.Point second, MapArea.Point target) {
        FullscreenMapPanel panel = getMapPanel();
        if (panel == null) return Double.POSITIVE_INFINITY;
        var projection = new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                getPanX(), getPanZ(), getBlocksPerPixel());
        double x1 = projection.screenX(first.x()), y1 = projection.screenY(first.z());
        double x2 = projection.screenX(second.x()), y2 = projection.screenY(second.z());
        double tx = projection.screenX(target.x()), ty = projection.screenY(target.z());
        double dx = x2 - x1, dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((tx - x1) * dx + (ty - y1) * dy) / lengthSquared));
        return Math.hypot(tx - (x1 + t * dx), ty - (y1 + t * dy));
    }

    private MapArea.Point projectOnEdge(MapArea.Point first, MapArea.Point second, MapArea.Point target) {
        FullscreenMapPanel panel = getMapPanel();
        var projection = new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                getPanX(), getPanZ(), getBlocksPerPixel());
        double x1 = projection.screenX(first.x()), y1 = projection.screenY(first.z());
        double x2 = projection.screenX(second.x()), y2 = projection.screenY(second.z());
        double tx = projection.screenX(target.x()), ty = projection.screenY(target.z());
        double dx = x2 - x1, dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((tx - x1) * dx + (ty - y1) * dy) / lengthSquared));
        return new MapArea.Point(projection.worldX(x1 + t * dx), projection.worldZ(y1 + t * dy));
    }

    private static boolean hasVertex(MapArea.Geometry geometry, MapArea.Point target) { return allVerticesStatic(geometry).contains(target); }
    private static List<MapArea.Point> allVerticesStatic(MapArea.Geometry geometry) {
        ArrayList<MapArea.Point> points = new ArrayList<>();
        geometry.paths().forEach(points::addAll);
        for (MapArea.Polygon polygon : geometry.polygons()) { points.addAll(polygon.outer()); polygon.holes().forEach(points::addAll); }
        return points;
    }

    private static MapArea.Geometry removePoint(MapArea.Geometry geometry, MapArea.Point point) {
        List<List<MapArea.Point>> paths = geometry.paths().stream().map(path -> path.stream().filter(value -> !value.equals(point)).toList()).toList();
        List<MapArea.Polygon> polygons = geometry.polygons().stream().map(polygon -> new MapArea.Polygon(
                polygon.outer().stream().filter(value -> !value.equals(point)).toList(),
                polygon.holes().stream().map(hole -> hole.stream().filter(value -> !value.equals(point)).toList()).toList())).toList();
        return new MapArea.Geometry(geometry.shape(), paths, polygons);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawEditorDetails(graphics);
    }

    private void drawEditorDetails(GuiGraphicsExtractor graphics) {
        MapArea area = currentArea();
        if (area != null) {
            FullscreenMapPanel panel = getMapPanel();
            if (panel != null) {
                var projection = new AreaRenderGeometry.Projection(panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight(),
                        getPanX(), getPanZ(), getBlocksPerPixel());
                graphics.enableScissor(panel.getX(), panel.getY(), Math.min(panel.getX() + panel.getWidth(), panelX), panel.getY() + panel.getHeight());
                for (MapArea.Point point : allVertices(area.geometry())) {
                    int x = (int) Math.round(projection.screenX(point.x()));
                    int y = (int) Math.round(projection.screenY(point.z()));
                    int color = point.equals(selectedPoint) ? 0xFFFFD45E : 0xFFFFFFFF;
                    graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF101820);
                    graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
                }
                if (drawing && !pendingPoints.isEmpty()) {
                    for (MapArea.Point point : pendingPoints) {
                        int x = (int) Math.round(projection.screenX(point.x()));
                        int y = (int) Math.round(projection.screenY(point.z()));
                        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFFFFE06A);
                    }
                }
                graphics.disableScissor();
            }
        }
        int statusY = Math.max(PANEL_TOP + 1, controlsY - 15);
        if (status != null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(status, Math.max(1, panelWidth - 16));
            int line = 0;
            for (net.minecraft.util.FormattedCharSequence value : lines) {
                if (line++ >= 1) break;
                graphics.text(font, value, panelX + PANEL_MARGIN, statusY, 0xFFDDDDDD, false);
            }
        }
    }

    @Override
    public void onClose() {
        if (hasPendingSave()) {
            status = Component.translatable("gui.openmapdelta.area.waiting_for_server");
            return;
        }
        if (!hasUnsavedChanges()) { returnToMap(); return; }
        minecraft.gui.setScreen(new ConfirmScreen(discard -> {
            if (minecraft == null) return;
            if (discard) returnToMap(); else minecraft.gui.setScreen(this);
        }, Component.translatable("gui.openmapdelta.area.discard_title"),
                Component.translatable("gui.openmapdelta.area.discard_message"),
                Component.translatable("gui.openmapdelta.area.discard"), Component.translatable("gui.openmapdelta.area.keep_editing")));
    }

    private void returnToMap() {
        if (minecraft != null) {
            OpenMapDeltaClient.setSelectedMapLayer(returnLayer);
            minecraft.gui.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private final class PanelBackground extends AbstractWidget {
        PanelBackground(int x, int y, int w, int h) { super(x, y, w, h, Component.empty()); active = false; }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xF0181820);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, 0xFF555566);
            graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), 0xFF555566);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private final class AreaListWidget extends AbstractWidget {
        private final java.util.function.Supplier<List<MapArea>> supplier;
        private final java.util.function.Consumer<MapArea> select;
        private final java.util.function.Consumer<MapArea> mergeTarget;
        private int scroll;
        private String filter = "";
        private UUID selected;
        private UUID target;
        AreaListWidget(int x, int y, int w, int h, java.util.function.Supplier<List<MapArea>> supplier,
                       java.util.function.Consumer<MapArea> select, java.util.function.Consumer<MapArea> mergeTarget) {
            super(x, y, w, h, Component.empty());
            this.supplier = supplier; this.select = select; this.mergeTarget = mergeTarget;
        }
        void setFilter(String value) { filter = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT); scroll = 0; }
        void refreshSelection(UUID value, UUID mergeId) { selected = value; target = mergeId; }
        void resetScroll() { scroll = 0; }
        private List<MapArea> rows() { return supplier.get(); }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xD0101018);
            List<MapArea> values = rows();
            int visibleRows = Math.max(0, (getHeight() - 4) / 18);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, values.size() - visibleRows)));
            for (int i = 0; i < visibleRows && i + scroll < values.size(); i++) {
                MapArea area = values.get(i + scroll);
                int top = getY() + 2 + i * 18;
                boolean hover = mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= top && mouseY < top + 18;
                if (area.id().equals(selected)) graphics.fill(getX() + 2, top, getX() + getWidth() - 2, top + 17, 0xFF344C62);
                else if (hover) graphics.fill(getX() + 2, top, getX() + getWidth() - 2, top + 17, 0xFF273640);
                if (area.id().equals(target)) graphics.fill(getX() + getWidth() - 5, top + 3, getX() + getWidth() - 3, top + 14, 0xFFFFD45E);
                String text = font.plainSubstrByWidth(area.name(), Math.max(1, getWidth() - 32));
                graphics.text(font, text, getX() + 6, top + 4, 0xFFE4E4E4, false);
                graphics.text(font, area.kind().name().substring(0, 1), getX() + getWidth() - 18, top + 4, area.style().color(), false);
            }
            if (values.isEmpty()) graphics.text(font, Component.translatable("gui.openmapdelta.area.empty"), getX() + 6, getY() + 6, 0xFFAAAAAA, false);
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.x() < getX() || event.x() >= getX() + getWidth() || event.y() < getY() || event.y() >= getY() + getHeight()) return false;
            int row = (int) ((event.y() - getY() - 2) / 18);
            int index = row + scroll;
            List<MapArea> values = rows();
            if (row < 0 || index >= values.size()) return true;
            MapArea area = values.get(index);
            if (event.buttonInfo().button() == 1) mergeTarget.accept(area); else select.accept(area);
            return true;
        }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (mouseX < getX() || mouseX >= getX() + getWidth() || mouseY < getY() || mouseY >= getY() + getHeight()) return false;
            scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY), Math.max(0, rows().size() - Math.max(0, (getHeight() - 4) / 18))));
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
