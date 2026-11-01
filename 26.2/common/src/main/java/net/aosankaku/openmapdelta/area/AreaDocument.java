// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable validated collection of local or server-owned surface places. */
public record AreaDocument(long revision, List<MapArea> areas) {
    public static final int MAX_AREAS = 256;
    public static final int MAX_PARTS = 32;
    public static final int MAX_POINTS_PER_AREA = 512;
    /** Aggregate snapshot cap shared with the area network protocol. */
    public static final int MAX_TOTAL_POINTS = 16_384;

    public AreaDocument {
        if (revision < 0) throw new IllegalArgumentException("Area document revision cannot be negative");
        Objects.requireNonNull(areas, "areas");
        if (areas.size() > MAX_AREAS) throw new IllegalArgumentException("Area document exceeds its feature limit");
        ArrayList<MapArea> copied = new ArrayList<>(areas.size());
        Set<UUID> ids = new HashSet<>();
        int pointCount = 0;
        for (MapArea area : areas) {
            Objects.requireNonNull(area, "area");
            if (!ids.add(area.id())) throw new IllegalArgumentException("Duplicate area id");
            copied.add(area);
            pointCount = Math.addExact(pointCount, area.geometry().pointCount());
            if (pointCount > MAX_TOTAL_POINTS) throw new IllegalArgumentException("Area document exceeds its total point limit");
        }
        areas = List.copyOf(copied);
        validateParents(areas);
    }

    public static AreaDocument empty() { return new AreaDocument(0, List.of()); }

    public Optional<MapArea> find(UUID id) {
        if (id == null) return Optional.empty();
        return areas.stream().filter(area -> area.id().equals(id)).findFirst();
    }

    /** Returns a document with the area inserted or replaced and every hierarchy invariant checked. */
    public AreaDocument upsert(MapArea area) {
        Objects.requireNonNull(area, "area");
        ArrayList<MapArea> changed = new ArrayList<>(areas.size() + (find(area.id()).isEmpty() ? 1 : 0));
        boolean replaced = false;
        for (MapArea current : areas) {
            if (current.id().equals(area.id())) {
                changed.add(area);
                replaced = true;
            } else changed.add(current);
        }
        if (!replaced) changed.add(area);
        return new AreaDocument(revision, changed);
    }

    /** Removes a feature and detaches its direct children in the same immutable update. */
    public AreaDocument remove(UUID id) {
        if (id == null || find(id).isEmpty()) return this;
        ArrayList<MapArea> changed = new ArrayList<>(areas.size() - 1);
        for (MapArea area : areas) {
            if (!area.id().equals(id)) changed.add(id.equals(area.parentId()) ? area.withParent(null) : area);
        }
        return new AreaDocument(revision, changed);
    }

    public AreaDocument withRevision(long value) { return new AreaDocument(value, areas); }

    public static boolean isAdministrative(MapArea.Kind kind) {
        return kind == MapArea.Kind.COUNTRY || kind == MapArea.Kind.REGION;
    }

    private static void validateParents(List<MapArea> areas) {
        Map<UUID, MapArea> byId = new HashMap<>(areas.size());
        for (MapArea area : areas) byId.put(area.id(), area);
        Map<UUID, Byte> visited = new HashMap<>(areas.size());
        for (MapArea area : areas) visit(area, byId, visited);
    }

    private static void visit(MapArea area, Map<UUID, MapArea> byId, Map<UUID, Byte> visited) {
        byte state = visited.getOrDefault(area.id(), (byte) 0);
        if (state == 1) throw new IllegalArgumentException("Area hierarchy contains a cycle");
        if (state == 2) return;
        visited.put(area.id(), (byte) 1);
        if (area.parentId() != null) {
            MapArea parent = byId.get(area.parentId());
            if (parent == null) throw new IllegalArgumentException("Area parent does not exist");
            if (parent.scope() != area.scope() || !parent.dimension().equals(area.dimension())) {
                throw new IllegalArgumentException("Area parent must share its dimension and storage scope");
            }
            if (!isAdministrative(parent.kind()) || area.kind() != MapArea.Kind.REGION) {
                throw new IllegalArgumentException("Only a country or region can contain a child region");
            }
            visit(parent, byId, visited);
        }
        visited.put(area.id(), (byte) 2);
    }
}
