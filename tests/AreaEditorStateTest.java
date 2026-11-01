package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.area.AreaDocument;
import net.aosankaku.openmapdelta.area.AreaGeometryOps;
import net.aosankaku.openmapdelta.area.MapArea;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure command/state regressions for editor form synchronization and multipart edits. */
public final class AreaEditorStateTest {
    private static int assertions;
    private AreaEditorStateTest() {}

    public static void main(String[] args) {
        invalidLabelCreationReturnsAnErrorInsteadOfThrowing();
        rebaseAndHistoryRestoreAllSelectedMetadata();
        pendingSharedSaveRejectsEveryMapMutation();
        pendingAndReadOnlyFormsRejectMetadataInput();
        invalidWorldCoordinatesAreRejectedWithoutClamping();
        holeIsAddedToItsContainingPolygonAndPreservesIslands();
        renderSelectionUsesWholeDraftAndPersistedDocuments();
        System.out.println("PASS AreaEditorStateTest: " + assertions + " assertions");
    }

    private static void invalidLabelCreationReturnsAnErrorInsteadOfThrowing() {
        var result = AreaEditorState.createLabel(UUID.randomUUID(), "minecraft:overworld", "   ",
                MapArea.Kind.LAND, MapArea.Scope.PERSONAL, UUID.randomUUID(), "Mapper", null,
                MapArea.Style.defaults(0xFF46B8FF), new MapArea.Point(2, 3));
        check(!result.success(), "blank label names return a failed creation result");
        check(result.feature() == null && result.messageKey() != null,
                "failed label creation contains no feature and an error message key");
    }

    private static void rebaseAndHistoryRestoreAllSelectedMetadata() {
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(), parent = UUID.randomUUID();
        var style = new MapArea.Style(0xFFDA79FF, false, false, true, true, 0.5, 128, 7);
        MapArea area = feature(id, "Saved name", MapArea.Kind.REGION, style, parent);
        var saved = AreaDocument.empty().upsert(feature(parent, "Country", MapArea.Kind.COUNTRY,
                MapArea.Style.defaults(0xFF46B8FF), null)).upsert(area);

        var rebased = AreaEditorState.synchronize(saved, id);
        equal("Saved name", rebased.name(), "rebase restores the selected name");
        equal(MapArea.Kind.REGION, rebased.kind(), "rebase restores the selected kind");
        equal(MapArea.Shape.POLYGON, rebased.shape(), "rebase restores the selected geometry shape");
        equal(style, rebased.style(), "rebase restores all display settings");
        equal(parent, rebased.parentId(), "rebase restores the selected parent");

        MapArea changed = area.withName("Undo name").withParent(null)
                .withStyle(MapArea.Style.defaults(0xFF65D58A));
        AreaDocument historical = saved.upsert(changed);
        var undoState = AreaEditorState.synchronize(historical, id);
        equal("Undo name", undoState.name(), "undo selection sync restores the historical name");
        equal(null, undoState.parentId(), "undo selection sync restores the historical parent");
        equal(changed.style(), undoState.style(), "undo selection sync restores historical style");
    }

    private static void pendingSharedSaveRejectsEveryMapMutation() {
        check(AreaEditorState.canMutateMap(false), "map editing remains available with no pending save");
        check(!AreaEditorState.canMutateMap(true), "map editing is locked while shared save is pending");
    }

    private static void pendingAndReadOnlyFormsRejectMetadataInput() {
        check(AreaEditorState.canEditForm(false, true), "an editable selection accepts form changes");
        check(!AreaEditorState.canEditForm(true, true), "a pending shared save locks editable fields");
        check(!AreaEditorState.canEditForm(false, false), "read-only selections keep edit fields disabled");
    }

    private static void invalidWorldCoordinatesAreRejectedWithoutClamping() {
        check(AreaEditorState.safePoint(MapArea.WORLD_LIMIT + 1, 0) == null,
                "map conversion rejects points beyond the supported world without clamping");
        check(AreaEditorState.safePoint(Double.NaN, 0) == null
                        && AreaEditorState.safePoint(0, Double.POSITIVE_INFINITY) == null,
                "map conversion rejects non-finite coordinates");
        equal(new MapArea.Point(-MapArea.WORLD_LIMIT, -4),
                AreaEditorState.safePoint(-MapArea.WORLD_LIMIT, -4),
                "valid negative world coordinates remain available");
    }

    private static void holeIsAddedToItsContainingPolygonAndPreservesIslands() {
        var first = new MapArea.Polygon(rectangle(0, 0, 10, 10), List.of());
        var second = new MapArea.Polygon(rectangle(20, 0, 30, 10), List.of());
        var source = MapArea.Geometry.polygon(List.of(first, second));
        var hole = rectangle(22, 2, 28, 8);

        var changed = AreaEditorState.addHole(source, hole);
        equal(2, changed.polygons().size(), "adding a hole preserves each disconnected island");
        equal(List.of(), changed.polygons().get(0).holes(), "an unrelated first polygon remains unchanged");
        equal(List.of(hole), changed.polygons().get(1).holes(), "the hole is attached to the containing polygon");
    }

    private static void renderSelectionUsesWholeDraftAndPersistedDocuments() {
        MapArea savedA = feature(UUID.randomUUID(), "A", MapArea.Kind.LAND,
                MapArea.Style.defaults(0xFF46B8FF), null);
        AreaDocument personalBase = AreaDocument.empty().upsert(savedA);
        MapArea draftA = savedA.withName("A edited");
        MapArea newB = feature(UUID.randomUUID(), "B", MapArea.Kind.RIVER,
                MapArea.Style.defaults(0xFF65D58A), null).withScope(MapArea.Scope.PERSONAL);
        AreaDocument personalDraft = personalBase.upsert(draftA).upsert(newB);

        MapArea shared = feature(UUID.randomUUID(), "Shared", MapArea.Kind.COUNTRY,
                MapArea.Style.defaults(0xFFFFB347), null).withScope(MapArea.Scope.SHARED);
        AreaDocument sharedPersisted = AreaDocument.empty().upsert(shared);
        Map< MapArea.Scope, AreaDocument> persisted = Map.of(
                MapArea.Scope.PERSONAL, personalBase,
                MapArea.Scope.SHARED, sharedPersisted);
        Map<MapArea.Scope, AreaDocument> drafts = Map.of(MapArea.Scope.PERSONAL, personalDraft);

        List<MapArea> rendered = AreaEditorState.renderAreas(persisted, drafts, "minecraft:overworld");
        check(rendered.stream().anyMatch(area -> area.id().equals(savedA.id()) && area.name().equals("A edited")),
                "render uses the edited geometry and metadata from the complete draft document");
        check(rendered.stream().anyMatch(area -> area.id().equals(newB.id())),
                "a newly created draft area stays visible while another area is selected");
        check(rendered.stream().anyMatch(area -> area.id().equals(shared.id())),
                "an unvisited scope keeps rendering from its persisted document");

        List<MapArea> afterDelete = AreaEditorState.renderAreas(persisted,
                Map.of(MapArea.Scope.PERSONAL, personalDraft.remove(savedA.id())), "minecraft:overworld");
        check(afterDelete.stream().noneMatch(area -> area.id().equals(savedA.id())),
                "deleted draft features are omitted from the map instead of falling back to storage");

        UUID parentId = UUID.randomUUID(), leftId = UUID.randomUUID(), rightId = UUID.randomUUID();
        MapArea country = polygonFeature(parentId, "Country", MapArea.Kind.COUNTRY, null,
                rectangle(0, 0, 10, 10));
        MapArea left = polygonFeature(leftId, "West", MapArea.Kind.REGION, parentId,
                List.of(new MapArea.Point(0, 0), new MapArea.Point(5, 0), new MapArea.Point(5, 5),
                        new MapArea.Point(5, 10), new MapArea.Point(0, 10)));
        MapArea right = polygonFeature(rightId, "East", MapArea.Kind.REGION, parentId,
                List.of(new MapArea.Point(5, 0), new MapArea.Point(10, 0), new MapArea.Point(10, 10),
                        new MapArea.Point(5, 10), new MapArea.Point(5, 5)));
        AreaDocument neighborBase = AreaDocument.empty().upsert(country).upsert(left).upsert(right);
        AreaDocument movedNeighbors = AreaGeometryOps.movePoint(neighborBase, leftId,
                new MapArea.Point(5, 5), new MapArea.Point(6, 5));
        List<MapArea> movedRender = AreaEditorState.renderAreas(
                Map.of(MapArea.Scope.PERSONAL, neighborBase, MapArea.Scope.SHARED, AreaDocument.empty()),
                Map.of(MapArea.Scope.PERSONAL, movedNeighbors), "minecraft:overworld");
        equal(movedNeighbors.find(leftId).orElseThrow().geometry(), movedRender.stream()
                        .filter(area -> area.id().equals(leftId)).findFirst().orElseThrow().geometry(),
                "edited region geometry is read from the draft for rendering");
        equal(movedNeighbors.find(rightId).orElseThrow().geometry(), movedRender.stream()
                        .filter(area -> area.id().equals(rightId)).findFirst().orElseThrow().geometry(),
                "a shared-border neighbor's new geometry is rendered from the same draft");
    }

    private static MapArea polygonFeature(UUID id, String name, MapArea.Kind kind, UUID parent,
                                          List<MapArea.Point> outer) {
        return new MapArea(id, "minecraft:overworld", name, kind, MapArea.Scope.PERSONAL,
                UUID.randomUUID(), "Mapper", parent, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(outer, List.of()))),
                AreaGeometryOps.anchor(MapArea.Geometry.polygon(List.of(new MapArea.Polygon(outer, List.of())))),
                MapArea.Style.defaults(0xFF46B8FF));
    }

    private static MapArea feature(UUID id, String name, MapArea.Kind kind, MapArea.Style style, UUID parent) {
        return new MapArea(id, "minecraft:overworld", name, kind, MapArea.Scope.PERSONAL,
                UUID.randomUUID(), "Mapper", parent, 0,
                MapArea.Geometry.polygon(List.of(new MapArea.Polygon(rectangle(0, 0, 8, 8), List.of()))),
                new MapArea.Point(4, 4), style);
    }

    private static List<MapArea.Point> rectangle(double left, double top, double right, double bottom) {
        return List.of(new MapArea.Point(left, top), new MapArea.Point(right, top),
                new MapArea.Point(right, bottom), new MapArea.Point(left, bottom));
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void equal(Object expected, Object actual, String description) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(description + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
