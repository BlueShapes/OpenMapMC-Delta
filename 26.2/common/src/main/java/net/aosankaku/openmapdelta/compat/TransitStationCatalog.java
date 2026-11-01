// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Deduplicates upstream identities and combines nearby same-name Create station blocks. */
public final class TransitStationCatalog {
    private static final int DEFAULT_CREATE_GROUP_RADIUS = 50;

    private TransitStationCatalog() {}
    private record Key(TransitStation.Source source, String dimension, String waypointIdentity) {}
    private record CreateGroup(String dimension, String name) {}
    private record StationKey(TransitStation.Source source, String dimension, String identity, String name,
                              TransitStation.Position position) {}
    private record Snapshot(int radius, List<StationKey> input, List<TransitStation> result) {}
    private record Cell(long x, long y, long z) {}
    private static volatile Snapshot lastSnapshot;

    public static void resetSnapshot() { lastSnapshot = null; }

    private static final class Bucket {
        private final List<TransitStation> stations = new ArrayList<>();
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        private void add(TransitStation station) {
            stations.add(station);
            TransitStation.Position p = station.position();
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
        }
    }

    public static List<TransitStation> unique(Collection<TransitStation> stations, Function<Object, String> dimensions) {
        return unique(stations, dimensions, DEFAULT_CREATE_GROUP_RADIUS);
    }

    public static List<TransitStation> unique(Collection<TransitStation> stations, Function<Object, String> dimensions,
                                              int createGroupingRadius) {
        if (createGroupingRadius < 0) throw new IllegalArgumentException("Create grouping radius must be nonnegative");
        double radiusSquared = (double) createGroupingRadius * createGroupingRadius;
        Map<Key, TransitStation> unique = new LinkedHashMap<>();
        for (TransitStation station : stations) {
            Key key = new Key(station.source(), dimensions.apply(station.dimension()), station.waypointIdentity());
            unique.merge(key, station, (previous, incoming) ->
                    previous.position().compareTo(incoming.position()) <= 0 ? previous : incoming);
        }

        List<StationKey> inputKey = unique.entrySet().stream()
                .map(entry -> new StationKey(entry.getKey().source(), entry.getKey().dimension(),
                        entry.getKey().waypointIdentity(), entry.getValue().name(), entry.getValue().position()))
                .toList();
        Snapshot cached = lastSnapshot;
        if (cached != null && cached.radius() == createGroupingRadius && cached.input().equals(inputKey)) {
            return cached.result();
        }

        if (createGroupingRadius == 0) {
            List<TransitStation> immutable = List.copyOf(unique.values());
            lastSnapshot = new Snapshot(createGroupingRadius, inputKey, immutable);
            return immutable;
        }
        Map<CreateGroup, List<TransitStation>> createGroups = new LinkedHashMap<>();
        unique.forEach((key, station) -> {
            if (station.source() == TransitStation.Source.CREATE) {
                createGroups.computeIfAbsent(new CreateGroup(key.dimension(), station.name()), ignored -> new ArrayList<>())
                        .add(station);
            }
        });
        List<TransitStation> result = new ArrayList<>();
        unique.forEach((key, station) -> {
            if (station.source() != TransitStation.Source.CREATE) {
                result.add(station);
            } else {
                // Emit each name/dimension group at its first occurrence, retaining other source ordering.
                List<TransitStation> group = createGroups.remove(new CreateGroup(key.dimension(), station.name()));
                if (group != null) result.addAll(createCenters(group, radiusSquared, createGroupingRadius));
            }
        });
        List<TransitStation> immutable = List.copyOf(result);
        lastSnapshot = new Snapshot(createGroupingRadius, inputKey, immutable);
        return immutable;
    }

    private static List<TransitStation> createCenters(List<TransitStation> stations, double radiusSquared, int radius) {
        double cellSize = Math.max(0.5, radius / 2.0);
        Map<Cell, Bucket> spatial = new LinkedHashMap<>();
        for (TransitStation station : stations) {
            spatial.computeIfAbsent(cell(station.position(), cellSize), ignored -> new Bucket()).add(station);
        }
        List<Map.Entry<Cell, Bucket>> buckets = new ArrayList<>(spatial.entrySet());
        Map<Cell, Integer> indices = new HashMap<>();
        for (int i = 0; i < buckets.size(); i++) indices.put(buckets.get(i).getKey(), i);

        List<TransitStation> result = new ArrayList<>();
        boolean[] visited = new boolean[buckets.size()];
        Set<Long> testedBucketPairs = new HashSet<>();
        for (int seed = 0; seed < buckets.size(); seed++) {
            if (visited[seed]) continue;
            List<TransitStation> component = new ArrayList<>();
            List<Integer> queue = new ArrayList<>();
            queue.add(seed);
            visited[seed] = true;
            // A bucket's diagonal is shorter than the radius, so each bucket forms one connected unit.
            for (int current = 0; current < queue.size(); current++) {
                int bucketIndex = queue.get(current);
                Map.Entry<Cell, Bucket> entry = buckets.get(bucketIndex);
                component.addAll(entry.getValue().stations);
                Cell origin = entry.getKey();
                // Radius/2 cells require at most two cells of reach per axis. Cache each bucket pair
                // so input order does not affect reachability and dense buckets are not repeatedly scanned.
                for (long dx = -2; dx <= 2; dx++) for (long dy = -2; dy <= 2; dy++) for (long dz = -2; dz <= 2; dz++) {
                    Integer candidate = indices.get(new Cell(origin.x() + dx, origin.y() + dy, origin.z() + dz));
                    if (candidate == null || visited[candidate]) continue;
                    int lowerIndex = Math.min(bucketIndex, candidate);
                    int upperIndex = Math.max(bucketIndex, candidate);
                    long pairKey = ((long) lowerIndex << 32) | (upperIndex & 0xffff_ffffL);
                    if (!testedBucketPairs.add(pairKey)) continue;
                    Bucket other = buckets.get(candidate).getValue();
                    if (aabbsWithinRadius(entry.getValue(), other, radiusSquared)
                            && bucketsConnected(entry.getValue(), other, radiusSquared)) {
                        visited[candidate] = true;
                        queue.add(candidate);
                    }
                }
            }
            TransitStation representative = component.get(0);
            List<TransitStation.Position> positions = new ArrayList<>(component.size());
            for (TransitStation member : component) {
                positions.add(member.position());
                if (member.waypointIdentity().compareTo(representative.waypointIdentity()) < 0) representative = member;
            }
            // An upstream UUID gives a deterministic identity independent of graph enumeration and the center.
            result.add(component.size() == 1 ? representative : new TransitStation(TransitStation.Source.CREATE,
                    representative.dimension(), representative.waypointIdentity(), representative.name(),
                    TransitStationCenter.center(positions)));
        }
        return result;
    }

    private static Cell cell(TransitStation.Position p, double size) {
        return new Cell((long) Math.floor(p.x() / size), (long) Math.floor(p.y() / size),
                (long) Math.floor(p.z() / size));
    }

    private static boolean aabbsWithinRadius(Bucket a, Bucket b, double radiusSquared) {
        double dx = intervalGap(a.minX, a.maxX, b.minX, b.maxX);
        double dy = intervalGap(a.minY, a.maxY, b.minY, b.maxY);
        double dz = intervalGap(a.minZ, a.maxZ, b.minZ, b.maxZ);
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static double intervalGap(int aMin, int aMax, int bMin, int bMax) {
        if (aMax < bMin) return (double) bMin - aMax;
        if (bMax < aMin) return (double) aMin - bMax;
        return 0;
    }

    private static boolean bucketsConnected(Bucket a, Bucket b, double radiusSquared) {
        for (TransitStation left : a.stations) {
            for (TransitStation right : b.stations) {
                if (nearby(left.position(), right.position(), radiusSquared)) return true;
            }
        }
        return false;
    }

    private static boolean nearby(TransitStation.Position left, TransitStation.Position right, double radiusSquared) {
        double dx = (double) left.x() - right.x(), dy = (double) left.y() - right.y(),
                dz = (double) left.z() - right.z();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    public static UUID markerId(TransitStation station, String dimension) {
        return UUID.nameUUIDFromBytes(("odysseymap:transit:" + station.source() + "\0" + dimension + "\0" + station.waypointIdentity())
                .getBytes(StandardCharsets.UTF_8));
    }
}
