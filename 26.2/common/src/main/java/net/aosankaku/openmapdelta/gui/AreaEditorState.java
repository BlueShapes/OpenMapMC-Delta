// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.area.AreaDocument;
import net.aosankaku.openmapdelta.area.AreaGeometryOps;
import net.aosankaku.openmapdelta.area.MapArea;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure editor state transformations that can be tested without starting Minecraft. */
public final class AreaEditorState {
    public record Form(String name, MapArea.Kind kind, MapArea.Shape shape, MapArea.Style style, UUID parentId) {}
    public record Creation(boolean success, MapArea feature, String messageKey) {}

    private record FeatureKey(MapArea.Scope scope, UUID id) {}

    private AreaEditorState() {}

    public static Creation createLabel(UUID id, String dimension, String name, MapArea.Kind kind,
                                       MapArea.Scope scope, UUID ownerId, String ownerName, UUID parentId,
                                       MapArea.Style style, MapArea.Point position) {
        try {
            return new Creation(true, new MapArea(id, dimension, name, kind, scope, ownerId, ownerName,
                    parentId, 0, MapArea.Geometry.label(), position, style), null);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return new Creation(false, null, "gui.openmapdelta.area.invalid");
        }
    }

    /** Reconstructs all form metadata from the authoritative selected record. */
    public static Form synchronize(AreaDocument document, UUID selectedId) {
        Objects.requireNonNull(document, "document");
        MapArea area = selectedId == null ? null : document.find(selectedId).orElse(null);
        return area == null ? new Form("", MapArea.Kind.OTHER, MapArea.Shape.POLYGON,
                MapArea.Style.defaults(0xFF46B8FF), null)
                : new Form(area.name(), area.kind(), area.geometry().shape(), area.style(), area.parentId());
    }

    public static boolean canMutateMap(boolean pendingSharedSave) { return !pendingSharedSave; }

    public static boolean canEditForm(boolean pendingSharedSave, boolean editableSelection) {
        return !pendingSharedSave && editableSelection;
    }

    /** Converts world coordinates without clamping; null indicates an invalid or out-of-world point. */
    public static MapArea.Point safePoint(double x, double z) {
        try { return new MapArea.Point(x, z); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    /** Adds a hole to whichever disconnected component contains it, preserving all other components. */
    public static MapArea.Geometry addHole(MapArea.Geometry source, List<MapArea.Point> ring) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ring, "ring");
        if (source.shape() != MapArea.Shape.POLYGON) throw new IllegalArgumentException("Only polygons can contain holes");
        MapArea.Polygon candidate = new MapArea.Polygon(ring, List.of());
        MapArea.Point sample = AreaGeometryOps.anchor(MapArea.Geometry.polygon(List.of(candidate)));
        ArrayList<MapArea.Polygon> changed = new ArrayList<>(source.polygons().size());
        boolean inserted = false;
        for (MapArea.Polygon polygon : source.polygons()) {
            if (!inserted && AreaGeometryOps.contains(MapArea.Geometry.polygon(List.of(polygon)), sample)) {
                ArrayList<List<MapArea.Point>> holes = new ArrayList<>(polygon.holes());
                holes.add(candidate.outer());
                changed.add(new MapArea.Polygon(polygon.outer(), holes));
                inserted = true;
            } else {
                changed.add(polygon);
            }
        }
        if (!inserted) throw new IllegalArgumentException("gui.openmapdelta.area.hole_outside");
        return MapArea.Geometry.polygon(changed);
    }

    /**
     * Uses the complete draft for any scope opened in this editor and persisted data for untouched scopes.
     * Filtering by dimension happens after the scope snapshots are selected, so a draft deletion cannot
     * accidentally fall back to its persisted counterpart.
     */
    public static List<MapArea> renderAreas(Map<MapArea.Scope, AreaDocument> persisted,
                                            Map<MapArea.Scope, AreaDocument> drafts,
                                            String dimension) {
        if (dimension == null) return List.of();
        Objects.requireNonNull(persisted, "persisted");
        Objects.requireNonNull(drafts, "drafts");
        Map<FeatureKey, MapArea> result = new LinkedHashMap<>();
        for (MapArea.Scope scope : MapArea.Scope.values()) {
            AreaDocument selected = drafts.get(scope);
            if (selected == null) selected = persisted.get(scope);
            if (selected == null) continue;
            for (MapArea area : selected.areas()) {
                if (scope == area.scope() && dimension.equals(area.dimension())) {
                    result.put(new FeatureKey(scope, area.id()), area);
                }
            }
        }
        return List.copyOf(result.values());
    }
}
