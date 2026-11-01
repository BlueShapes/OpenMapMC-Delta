// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Client-session destinations. No Minecraft types, clocks, persistence, or implicit eviction. */
public final class NavigationState {
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;
    private final Map<UUID, Entry> targets = new LinkedHashMap<>();

    public record Position(String dimension, int x, int y, int z) {
        public Position {
            Objects.requireNonNull(dimension);
            if (dimension.isBlank()) throw new IllegalArgumentException("Missing dimension");
        }
    }

    /** Actual feet coordinates, rather than the camera position or rounded player block coordinates. */
    public record Observer(String dimension, double x, double y, double z) {
        public boolean valid() {
            return dimension != null && !dimension.isBlank()
                    && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public record Target(UUID id, UUID trackedPlayerId, Position position) {}
    private static final class Entry {
        private final UUID id;
        private final UUID trackedPlayerId;
        private Position position;
        private boolean armed;
        private Entry(UUID id, UUID trackedPlayerId, Position position, boolean armed) {
            this.id = id;
            this.trackedPlayerId = trackedPlayerId;
            this.position = position;
            this.armed = armed;
        }
        private Target snapshot() { return new Target(id, trackedPlayerId, position); }
    }

    public boolean start(UUID id, UUID trackedPlayerId, Position position, Observer observer,
                         int maximumTargets, double arrivalDistance) {
        if (id == null || position == null || targets.containsKey(id)) return false;
        int maximum = Math.max(1, Math.min(16, maximumTargets));
        if (targets.size() >= maximum) return false;
        double radius = validArrivalDistance(arrivalDistance);
        boolean inside = radius > 0.0 && distanceSquared(observer, position) <= radius * radius;
        targets.put(id, new Entry(id, trackedPlayerId, position, !inside));
        return true;
    }

    /** Unknown or departed players keep their last position; only supplied live players update it. */
    public List<UUID> update(Observer observer, double arrivalDistance, Map<UUID, Position> livePlayers) {
        List<UUID> arrived = new ArrayList<>();
        double radius = validArrivalDistance(arrivalDistance);
        var iterator = targets.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.trackedPlayerId != null) {
                Position latest = livePlayers.get(entry.trackedPlayerId);
                if (latest != null) entry.position = latest;
            }
            if (radius == 0.0 || observer == null || !observer.valid()) continue;
            if (!entry.position.dimension().equals(observer.dimension())) {
                entry.armed = true;
                continue;
            }
            double distanceSquared = distanceSquared(observer, entry.position);
            if (!Double.isFinite(distanceSquared)) continue;
            if (distanceSquared > radius * radius) entry.armed = true;
            else if (entry.armed) {
                arrived.add(entry.id);
                iterator.remove();
            }
        }
        return List.copyOf(arrived);
    }

    public boolean contains(UUID id) { return targets.containsKey(id); }
    public void stop(UUID id) { targets.remove(id); }
    public void clear() { targets.clear(); }
    public int size() { return targets.size(); }
    public List<Target> targets() { return targets.values().stream().map(Entry::snapshot).toList(); }
    public double distance(Observer observer, UUID id) {
        Entry entry = targets.get(id);
        if (entry == null || observer == null || !observer.valid()
                || !entry.position.dimension().equals(observer.dimension())) return Double.NaN;
        if (entry.position.y() == UNKNOWN_Y) {
            // Display a useful horizontal remainder, while arrival still requires a known Y.
            return Math.hypot(entry.position.x() + 0.5 - observer.x(), entry.position.z() + 0.5 - observer.z());
        }
        return Math.sqrt(distanceSquared(observer, entry.position));
    }

    /** Match the temporary-waypoint convention: X/Z block center and the destination's standing Y. */
    public static double distanceSquared(Observer observer, Position position) {
        if (observer == null || !observer.valid() || position == null || position.y() == UNKNOWN_Y
                || !Objects.equals(observer.dimension(), position.dimension())) return Double.NaN;
        double dx = position.x() + 0.5 - observer.x();
        double dy = position.y() - observer.y();
        double dz = position.z() + 0.5 - observer.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double validArrivalDistance(double distance) {
        return Double.isFinite(distance) && distance >= 0.0 && distance <= 128.0 ? distance : 5.0;
    }
}
