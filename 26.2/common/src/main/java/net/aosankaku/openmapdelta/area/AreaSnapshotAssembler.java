// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Holds each server revision privately until every bounded page has arrived. */
public final class AreaSnapshotAssembler {
    private UUID worldId;
    private long revision = -1;
    private int pageCount;
    private int nextPage;
    private boolean administrator;
    private boolean available;
    private final List<List<MapArea>> pages = new ArrayList<>();
    private final Set<UUID> ids = new HashSet<>();

    public synchronized Optional<List<MapArea>> accept(AreaWire.Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Missing shared-area snapshot");
        if (worldId == null || !worldId.equals(snapshot.worldId()) || revision != snapshot.revision()
                || pageCount != snapshot.pages() || administrator != snapshot.administrator()
                || available != snapshot.available()) {
            begin(snapshot);
        }
        if (snapshot.page() < nextPage) {
            List<MapArea> previous = pages.get(snapshot.page());
            if (previous.equals(snapshot.areas())) return Optional.empty();
            reset();
            return Optional.empty();
        }
        if (snapshot.page() != nextPage || snapshot.page() >= pageCount) {
            reset();
            return Optional.empty();
        }
        int total = snapshot.areas().size();
        int totalPoints = snapshot.areas().stream().mapToInt(area -> area.geometry().pointCount()).sum();
        for (List<MapArea> page : pages) total += page.size();
        for (List<MapArea> page : pages) {
            for (MapArea area : page) totalPoints += area.geometry().pointCount();
        }
        if (total > AreaDocument.MAX_AREAS || totalPoints > AreaDocument.MAX_TOTAL_POINTS) {
            reset();
            return Optional.empty();
        }
        for (MapArea area : snapshot.areas()) {
            if (area.scope() != MapArea.Scope.SHARED || !ids.add(area.id())) {
                reset();
                return Optional.empty();
            }
        }
        pages.add(List.copyOf(snapshot.areas()));
        nextPage++;
        if (nextPage != pageCount) return Optional.empty();
        List<MapArea> complete = new ArrayList<>(total);
        for (List<MapArea> part : pages) complete.addAll(part);
        reset();
        return Optional.of(List.copyOf(complete));
    }

    public synchronized boolean isOlderThanPending(AreaWire.Snapshot snapshot) {
        return snapshot != null && worldId != null && worldId.equals(snapshot.worldId())
                && snapshot.revision() < revision;
    }

    public synchronized void reset() {
        worldId = null;
        revision = -1;
        pageCount = 0;
        nextPage = 0;
        administrator = false;
        available = false;
        pages.clear();
        ids.clear();
    }

    private void begin(AreaWire.Snapshot snapshot) {
        reset();
        worldId = Objects.requireNonNull(snapshot.worldId());
        revision = snapshot.revision();
        pageCount = snapshot.pages();
        administrator = snapshot.administrator();
        available = snapshot.available();
    }
}
