// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure bounded traversal for already-loaded structure references. */
public final class LocationContextStructureQuery {
    private LocationContextStructureQuery() {}

    public record Result(boolean complete, List<String> structureIds, int checkedReferences) {
        public Result { structureIds = List.copyOf(structureIds); }
    }

    public static <S, C, ST> Result aggregate(
            Iterable<? extends Map.Entry<S, ST>> directStarts,
            Iterable<? extends Map.Entry<S, ? extends Iterable<Long>>> references,
            Function<Long, C> loadedChunk,
            BiFunction<C, S, ST> startAt,
            Predicate<ST> contains,
            Function<S, String> resourceId,
            int maxReferences,
            int maxDistinctIds,
            boolean initiallyComplete) {
        if (maxReferences < 0 || maxDistinctIds < 0) throw new IllegalArgumentException("Negative query budget");
        var ids = new TreeSet<String>();
        boolean complete = initiallyComplete;
        int checked = 0;

        outerStarts:
        for (var entry : directStarts) {
            if (checked >= maxReferences) { complete = false; break; }
            checked++;
            complete = collect(entry.getKey(), entry.getValue(), contains, resourceId, ids, maxDistinctIds, complete);
        }

        int referenceGroups = 0;
        outerReferences:
        for (var entry : references) {
            if (++referenceGroups > maxReferences) { complete = false; break; }
            for (long packedPosition : entry.getValue()) {
                if (checked >= maxReferences) { complete = false; break outerReferences; }
                checked++;
                C chunk = loadedChunk.apply(packedPosition);
                if (chunk == null) {
                    complete = false;
                    continue;
                }
                ST start = startAt.apply(chunk, entry.getKey());
                complete = collect(entry.getKey(), start, contains, resourceId, ids, maxDistinctIds, complete);
            }
        }
        return new Result(complete, new ArrayList<>(ids), checked);
    }

    private static <S, ST> boolean collect(S structure, ST start, Predicate<ST> contains,
                                            Function<S, String> resourceId, TreeSet<String> ids,
                                            int maxDistinctIds, boolean complete) {
        if (start == null) return false;
        if (!contains.test(start)) return complete;
        String id = resourceId.apply(structure);
        if (id == null || id.isBlank()) return false;
        if (ids.contains(id)) return complete;
        if (ids.size() < maxDistinctIds) {
            ids.add(id);
            return complete;
        }
        complete = false;
        if (!ids.isEmpty() && id.compareTo(ids.last()) < 0) {
            ids.pollLast();
            ids.add(id);
        }
        return complete;
    }
}
