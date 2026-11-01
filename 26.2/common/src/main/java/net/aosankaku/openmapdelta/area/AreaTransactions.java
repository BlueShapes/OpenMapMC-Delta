// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure authorization and candidate-building logic for server-owned area edits. */
public final class AreaTransactions {
    private static final String PREFIX = "openmapdelta.area.feedback.";

    public record Result(boolean success, boolean changed, String messageKey, AreaDocument document) {
        public Result {
            Objects.requireNonNull(messageKey, "messageKey");
            Objects.requireNonNull(document, "document");
        }
    }

    private AreaTransactions() {}

    public static Result apply(AreaDocument current, UUID currentWorldId, AreaWire.Request request,
                               UUID actorId, String actorName, boolean administrator,
                               Predicate<String> knownDimension) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(knownDimension, "knownDimension");
        if (request == null || currentWorldId == null || actorId == null || actorName == null) {
            return failure("invalid", current);
        }
        if (!currentWorldId.equals(request.worldId())) return failure("conflict", current);
        if (request.operation() == AreaWire.Operation.SYNC) {
            return new Result(true, false, PREFIX + "synced", current);
        }
        if (request.expectedRevision() != current.revision()) return failure("conflict", current);
        if (current.areas().size() > AreaDocument.MAX_AREAS) return failure("limit", current);
        try {
            return mutate(current, request, actorId, actorName, administrator, knownDimension);
        } catch (IllegalArgumentException malformed) {
            return failure("invalid", current);
        } catch (ArithmeticException overflow) {
            return failure("storage_error", current);
        }
    }

    private static Result mutate(AreaDocument current, AreaWire.Request request, UUID actorId, String actorName,
                                 boolean administrator, Predicate<String> knownDimension) {
        Map<UUID, MapArea> next = new LinkedHashMap<>();
        for (MapArea area : current.areas()) {
            if (area.scope() != MapArea.Scope.SHARED) return failure("invalid", current);
            next.put(area.id(), area);
        }
        for (UUID id : request.deletes()) {
            MapArea existing = next.get(id);
            if (existing == null) return failure("conflict", current);
            if (!canManage(existing, actorId, administrator)) return failure("denied", current);
            next.remove(id);
        }

        long nextRevision = Math.addExact(current.revision(), 1L);
        for (MapArea proposed : request.upserts()) {
            if (proposed.scope() != MapArea.Scope.SHARED) return failure("invalid", current);
            MapArea existing = next.get(proposed.id());
            final MapArea candidate;
            if (existing == null) {
                candidate = proposed.withScope(MapArea.Scope.SHARED)
                        .withOwner(actorId, actorName)
                        .withRevision(nextRevision);
            } else {
                if (!canManage(existing, actorId, administrator)) return failure("denied", current);
                if (!knownDimension.test(proposed.dimension())) return failure("invalid", current);
                candidate = proposed.withScope(MapArea.Scope.SHARED)
                        .withOwner(existing.ownerId(), existing.ownerName())
                        .withRevision(nextRevision);
            }
            if (!knownDimension.test(candidate.dimension())) return failure("invalid", current);
            next.put(candidate.id(), candidate);
        }

        // Assigning a new parent requires authority over that parent. Existing links do not.
        for (MapArea proposed : request.upserts()) {
            MapArea previous = current.find(proposed.id()).orElse(null);
            MapArea candidate = next.get(proposed.id());
            if (candidate.parentId() == null
                    || (previous != null && Objects.equals(previous.parentId(), candidate.parentId()))) continue;
            MapArea parent = next.get(candidate.parentId());
            if (parent != null && !canManage(parent, actorId, administrator)) return failure("denied", current);
        }

        // Parent deletion detaches direct children as one transaction. Every changed child still needs consent.
        List<MapArea> detached = new ArrayList<>();
        for (MapArea area : next.values()) {
            if (area.parentId() != null && !next.containsKey(area.parentId())) {
                MapArea old = current.find(area.id()).orElse(null);
                if (old != null && !canManage(old, actorId, administrator)) return failure("denied", current);
                detached.add(area.withParent(null).withRevision(nextRevision));
            }
        }
        for (MapArea area : detached) next.put(area.id(), area);
        if (next.size() > AreaDocument.MAX_AREAS) return failure("limit", current);
        AreaDocument committed = new AreaDocument(nextRevision, List.copyOf(next.values()));
        return new Result(true, true, PREFIX + "saved", committed);
    }

    public static boolean canManage(MapArea area, UUID actorId, boolean administrator) {
        return area != null && actorId != null
                && (administrator || actorId.equals(area.ownerId()));
    }

    private static Result failure(String reason, AreaDocument current) {
        return new Result(false, false, PREFIX + reason, current);
    }
}
