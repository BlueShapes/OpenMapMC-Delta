// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class WaypointImportLogicTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        policy();
        session();
        storage();
        System.out.println("Waypoint import logic tests passed (" + assertions + " assertions).");
    }

    private static void policy() {
        var home = point("Home", 1, 0xFFFF0000, true);
        var same = point("Home", 1, 0xFF00FF00, false);
        var other = point("Home", 2, 0xFFFF0000, true);
        var bad = new WaypointImportReader.Waypoint("bad dimension", 0, 1, 2, "Bad", 0, true);
        var sourceA = new WaypointImportReader.Source("JourneyMap", Path.of("world-A"), List.of(home, same, other, bad), 2);
        Set<WaypointImportPolicy.Key> existing = new HashSet<>(Set.of(WaypointImportPolicy.key(home)));
        var plan = WaypointImportPolicy.plan(sourceA, existing);
        check(plan.additions().equals(List.of(other)), "same name at different coordinates remains distinct");
        check(plan.duplicates() == 2, "existing and internal duplicates ignore color/visibility");
        check(plan.skipped() == 3, "reader errors plus invalid dimensions reported");
        check(existing.size() == 1, "planning doesn't mutate existing markers");
        var emptyPlan = WaypointImportPolicy.plan(
                new WaypointImportReader.Source("JourneyMap", Path.of("world-A"), List.of(home, same), 0), Set.of());
        check(emptyPlan.additions().size() == 1 && emptyPlan.duplicates() == 1, "duplicates within one source");
        var sourceB = new WaypointImportReader.Source("Xaero's Minimap", Path.of("world-B"), List.of(other), 0);
        Set<String> notified = Set.of(WaypointImportPolicy.fingerprint(sourceA), WaypointImportPolicy.fingerprint(sourceB));
        check(WaypointImportPolicy.unannounced(List.of(sourceA, sourceB), existing, notified).isEmpty(),
                "partial import of source A doesn't re-announce unchanged source B");
        var changed = new WaypointImportReader.Source("Xaero's Minimap", Path.of("world-B"), List.of(other, home), 0);
        check(WaypointImportPolicy.unannounced(List.of(sourceA, changed), existing, notified).equals(List.of(changed)),
                "changed source data is announced independently");
        var reordered = new WaypointImportReader.Source("JourneyMap", Path.of("world-A"), List.of(bad, other, same, home), 2);
        check(WaypointImportPolicy.fingerprint(sourceA).equals(WaypointImportPolicy.fingerprint(reordered)),
                "notification fingerprint is independent of file ordering");
        check(WaypointImportPolicy.unannounced(List.of(sourceB),
                Set.of(WaypointImportPolicy.key(other)), Set.of()).isEmpty(), "fully imported data isn't announced");
        check(!WaypointImportPolicy.fingerprint(sourceA).equals(WaypointImportPolicy.fingerprint(
                new WaypointImportReader.Source("JourneyMap", Path.of("other-world"), sourceA.waypoints(), 2))),
                "different source worlds have separate notification history");
    }

    private static WaypointImportReader.Waypoint point(String name, int x, int color, boolean visible) {
        return new WaypointImportReader.Waypoint("minecraft:overworld", x, 64, 2, name, color, visible);
    }

    private static void session() {
        var session = new WaypointImportSession();
        Object firstConnection = new Object();
        Object secondConnection = new Object();
        check(session.enter(firstConnection, "world-A"), "first world establishes session");
        long first = session.token();
        check(session.matches(firstConnection, "world-A", first), "current session accepted");
        check(!session.enter(firstConnection, "world-A") && session.token() == first,
                "dimension changes within same world don't invalidate session");
        session.clear();
        check(!session.matches(firstConnection, "world-A", first), "logout invalidates old screen and scan");
        session.enter(secondConnection, "world-A");
        check(!session.matches(secondConnection, "world-A", first), "same-world reconnect rejects old token");
        long second = session.token();
        session.enter(secondConnection, "world-B");
        check(!session.matches(secondConnection, "world-B", second), "world switch rejects old import target");
        check(!WaypointImportSession.visibleInWorld("world-A", "world-B"), "imports hidden in other world");
        check(!WaypointImportSession.visibleInWorld("world-A", null), "imports hidden outside a world");
        check(WaypointImportSession.visibleInWorld("world-A", "world-A"), "imports visible after returning");
        check(WaypointImportSession.visibleInWorld(null, "world-B"), "legacy unscoped markers preserved");
    }

    private static void storage() throws Exception {
        Path directory = Files.createTempDirectory("odysseymap-save-test-");
        try {
            Path destination = directory.resolve("waypoints.json");
            Files.writeString(destination, "original");
            boolean failed = false;
            try {
                WaypointFileStore.write(destination, writer -> {
                    writer.write("incomplete");
                    throw new IOException("simulated disk write failure");
                });
            } catch (IOException expected) { failed = true; }
            check(failed, "write error reaches import rollback");
            check(Files.readString(destination).equals("original"), "failed serialization preserves old saved waypoints");
            try (Stream<Path> files = Files.list(directory)) {
                check(files.count() == 1, "failed write cleans temporary files");
            }
            WaypointFileStore.write(destination, writer -> writer.write("complete"));
            check(Files.readString(destination).equals("complete"), "successful replacement persists new data");
            Path blocked = directory.resolve("blocked");
            Files.createDirectories(blocked);
            Files.writeString(blocked.resolve("keep"), "untouched");
            failed = false;
            try { WaypointFileStore.write(blocked, writer -> writer.write("replacement")); }
            catch (IOException expected) { failed = true; }
            check(failed && Files.readString(blocked.resolve("keep")).equals("untouched"),
                    "replacement failure preserves existing destination");
            Files.writeString(destination, "original");
            try {
                WaypointFileStore.write(destination, writer -> writer.write("new"), new FallbackOperations() {
                    @Override void copy(Path source, Path target) throws IOException {
                        Files.writeString(target, "partial backup");
                        throw new IOException("simulated full disk during backup");
                    }
                });
                throw new AssertionError("Expected backup copy failure");
            } catch (IOException expected) {
                check(Files.readString(destination).equals("original"), "failed backup copy never restores partial data");
            }
            try {
                WaypointFileStore.write(destination, writer -> writer.write("new"), new FallbackOperations() {
                    @Override void move(Path source, Path target, boolean atomic) throws IOException {
                        if (atomic) super.move(source, target, true);
                        Files.delete(target);
                        throw new IOException("simulated failed non-atomic replacement");
                    }
                });
                throw new AssertionError("Expected fallback replacement failure");
            } catch (IOException expected) {
                check(Files.readString(destination).equals("original"), "non-atomic failure restores complete backup");
            }
            WaypointFileStore.write(destination, writer -> writer.write("fallback"), new FallbackOperations());
            check(Files.readString(destination).equals("fallback"), "non-atomic fallback succeeds");
            try (Stream<Path> files = Files.list(directory)) {
                check(files.filter(path -> path.getFileName().toString().endsWith(".tmp")).findAny().isEmpty(),
                        "fallback cleans temporary and backup files");
            }
            try {
                WaypointFileStore.write(destination, writer -> writer.write("new"), new FallbackOperations() {
                    private int copies;
                    @Override void copy(Path source, Path target) throws IOException {
                        if (++copies == 2) throw new IOException("simulated failed backup restoration");
                        super.copy(source, target);
                    }
                    @Override void move(Path source, Path target, boolean atomic) throws IOException {
                        if (atomic) super.move(source, target, true);
                        Files.delete(target);
                        throw new IOException("simulated replacement failure");
                    }
                });
                throw new AssertionError("Expected failed restoration");
            } catch (IOException expected) {
                check(expected.getSuppressed().length == 1, "failed restoration is reported");
                try (Stream<Path> files = Files.list(directory)) {
                    Path backup = files.filter(path -> path.getFileName().toString().startsWith("odysseymap-backup-"))
                            .findFirst().orElseThrow();
                    check(Files.readString(backup).equals("fallback"), "failed restoration retains recoverable backup");
                }
            }
        } finally {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static class FallbackOperations extends WaypointFileStore.Operations {
        @Override void move(Path source, Path destination, boolean atomic) throws IOException {
            if (atomic) throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test");
            super.move(source, destination, false);
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
