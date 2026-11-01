// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import net.aosankaku.openmapdelta.area.AreaDocument;
import net.aosankaku.openmapdelta.area.AreaGeometryOps;
import net.aosankaku.openmapdelta.area.MapArea;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable draft edits that do not belong to the persisted area model. */
public final class AreaEditorOperations {
    private AreaEditorOperations() {}

    /** Moves the path containing {@code selectedVertex} to a new named feature in one document edit. */
    public static AreaDocument separateLinePath(AreaDocument document, UUID areaId,
                                                MapArea.Point selectedVertex, String newName) {
        return separateLinePath(document, areaId, selectedVertex, newName, UUID.randomUUID());
    }

    static AreaDocument separateLinePath(AreaDocument document, UUID areaId, MapArea.Point selectedVertex,
                                         String newName, UUID newId) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(selectedVertex, "selectedVertex");
        Objects.requireNonNull(newId, "newId");
        MapArea source = document.find(areaId).orElseThrow(() -> new IllegalArgumentException("Unknown area"));
        if (source.geometry().shape() != MapArea.Shape.LINE) throw new IllegalArgumentException("Only a path feature can be separated");
        if (source.id().equals(newId) || document.find(newId).isPresent()) throw new IllegalArgumentException("New area id already exists");
        if (source.geometry().paths().size() < 2) throw new IllegalArgumentException("The original feature must keep at least one path");
        int selectedPath = -1;
        for (int i = 0; i < source.geometry().paths().size(); i++) {
            if (source.geometry().paths().get(i).contains(selectedVertex)) { selectedPath = i; break; }
        }
        if (selectedPath < 0) throw new IllegalArgumentException("Selected vertex is not on a source path");

        List<MapArea.Point> detachedPath = source.geometry().paths().get(selectedPath);
        ArrayList<List<MapArea.Point>> remaining = new ArrayList<>(source.geometry().paths());
        remaining.remove(selectedPath);
        MapArea retained = source.withGeometry(MapArea.Geometry.line(remaining));
        MapArea detached = new MapArea(newId, source.dimension(), newName, source.kind(), source.scope(),
                source.ownerId(), source.ownerName(), source.parentId(), 0,
                MapArea.Geometry.line(List.of(detachedPath)), AreaGeometryOps.anchor(MapArea.Geometry.line(List.of(detachedPath))),
                source.style());
        return document.upsert(retained).upsert(detached);
    }
}
