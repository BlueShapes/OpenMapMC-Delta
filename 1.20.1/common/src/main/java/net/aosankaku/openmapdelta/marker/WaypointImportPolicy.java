// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Import and notification decisions that can be verified without a Minecraft client. */
public final class WaypointImportPolicy {
    public record Key(String name, String dimension, int x, int y, int z) {}
    public record Plan(List<WaypointImportReader.Waypoint> additions, int duplicates, int skipped) {
        public Plan { additions = List.copyOf(additions); }
    }

    private WaypointImportPolicy() {}

    public static Key key(WaypointImportReader.Waypoint point) {
        return new Key(point.name(), point.dimension(), point.x(), point.y(), point.z());
    }

    public static boolean valid(WaypointImportReader.Waypoint point) {
        return point.name() != null && point.dimension() != null
                && point.dimension().matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    public static Plan plan(WaypointImportReader.Source source, Set<Key> existing) {
        Set<Key> seen = new HashSet<>(existing);
        List<WaypointImportReader.Waypoint> additions = new ArrayList<>();
        int duplicates = 0;
        int skipped = source.skipped();
        for (WaypointImportReader.Waypoint point : source.waypoints()) {
            if (!valid(point)) skipped++;
            else if (!seen.add(key(point))) duplicates++;
            else additions.add(point);
        }
        return new Plan(additions, duplicates, skipped);
    }

    public static List<WaypointImportReader.Source> unannounced(
            List<WaypointImportReader.Source> sources, Set<Key> existing, Set<String> notified) {
        return sources.stream()
                .filter(source -> !plan(source, existing).additions().isEmpty())
                .filter(source -> !notified.contains(fingerprint(source))).toList();
    }

    /** Uses full source data, independent of which of its points have already been imported. */
    public static String fingerprint(WaypointImportReader.Source source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, source.modName());
            field(digest, source.directory().toAbsolutePath().normalize().toString());
            List<WaypointImportReader.Waypoint> points = source.waypoints().stream().filter(WaypointImportPolicy::valid)
                    .sorted(Comparator.comparing(WaypointImportReader.Waypoint::dimension)
                            .thenComparing(WaypointImportReader.Waypoint::name)
                            .thenComparingInt(WaypointImportReader.Waypoint::x)
                            .thenComparingInt(WaypointImportReader.Waypoint::y)
                            .thenComparingInt(WaypointImportReader.Waypoint::z)
                            .thenComparingInt(WaypointImportReader.Waypoint::color)
                            .thenComparing(WaypointImportReader.Waypoint::visible)).toList();
            for (WaypointImportReader.Waypoint point : points) {
                field(digest, point.dimension());
                field(digest, point.name());
                field(digest, Integer.toString(point.x()));
                field(digest, Integer.toString(point.y()));
                field(digest, Integer.toString(point.z()));
                field(digest, Integer.toString(point.color()));
                field(digest, Boolean.toString(point.visible()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
