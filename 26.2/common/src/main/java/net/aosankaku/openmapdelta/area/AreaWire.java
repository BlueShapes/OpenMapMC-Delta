// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Versioned, bounded wire models for the independent shared-area channel. */
public final class AreaWire {
    public static final int MAX_REQUEST_BYTES = 30 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 30 * 1024;
    public static final int MAX_FEEDBACK_BYTES = 512;
    public static final int MAX_PAGES = AreaDocument.MAX_AREAS;
    public static final int MAX_CHANGES = 32;
    public static final int MAX_TOTAL_POINTS = AreaDocument.MAX_TOTAL_POINTS;
    private static final int MAGIC = 0x4F4D4131; // OMA1
    private static final int VERSION = 1;
    private static final int MAX_STRING_BYTES = 512;
    private static final int MAX_DIMENSION_BYTES = 256;
    private static final int MAX_POINTS_PER_AREA = 512;
    private static final int MAX_PARTS_PER_AREA = 32;
    private static final int MAX_AREAS_PER_PAGE = 32;
    private static final Pattern FEEDBACK_KEY = Pattern.compile(
            "openmapdelta\\.area\\.(feedback|error)\\.[a-z0-9_.]{1,96}");

    public enum Operation { SYNC, MUTATE }

    /** Fixed request fields needed to correlate bounded responses without inspecting mutation bodies. */
    record RequestHeader(UUID requestId, UUID worldId, long expectedRevision, Operation operation) {}

    public record Request(UUID requestId, UUID worldId, long expectedRevision, Operation operation,
                          List<MapArea> upserts, List<UUID> deletes) {
        public Request {
            requireUuid(requestId, "request id");
            requireUuid(worldId, "world id");
            if (expectedRevision < 0) throw new IllegalArgumentException("Invalid expected area revision");
            Objects.requireNonNull(operation, "operation");
            upserts = List.copyOf(Objects.requireNonNull(upserts, "upserts"));
            deletes = List.copyOf(Objects.requireNonNull(deletes, "deletes"));
            if (operation == Operation.SYNC && (!upserts.isEmpty() || !deletes.isEmpty())) {
                throw new IllegalArgumentException("Sync requests cannot contain mutations");
            }
            if (operation == Operation.MUTATE && upserts.isEmpty() && deletes.isEmpty()) {
                throw new IllegalArgumentException("Area mutation is empty");
            }
            if (upserts.size() + deletes.size() > MAX_CHANGES) throw new IllegalArgumentException("Too many area changes");
            Set<UUID> ids = new HashSet<>();
            for (MapArea area : upserts) {
                validateSharedArea(area);
                if (!ids.add(area.id())) throw new IllegalArgumentException("Duplicate area mutation id");
            }
            for (UUID id : deletes) {
                requireUuid(id, "deleted area id");
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate area mutation id");
            }
        }
    }

    public record Snapshot(UUID worldId, long revision, int page, int pages, boolean administrator,
                          boolean available, List<MapArea> areas) {
        public Snapshot {
            requireUuid(worldId, "world id");
            if (revision < 0 || pages < 1 || pages > MAX_PAGES || page < 0 || page >= pages) {
                throw new IllegalArgumentException("Invalid area snapshot header");
            }
            areas = List.copyOf(Objects.requireNonNull(areas, "areas"));
            if (areas.size() > MAX_AREAS_PER_PAGE) throw new IllegalArgumentException("Area snapshot page is too large");
            if (!available && !areas.isEmpty()) throw new IllegalArgumentException("Unavailable snapshots cannot contain areas");
            Set<UUID> ids = new HashSet<>();
            for (MapArea area : areas) {
                validateSharedArea(area);
                if (!ids.add(area.id())) throw new IllegalArgumentException("Duplicate area in page");
            }
        }
    }

    public record Feedback(UUID requestId, UUID worldId, boolean success, String messageKey, long revision) {
        public Feedback {
            requireUuid(requestId, "request id");
            requireUuid(worldId, "world id");
            if (revision < 0) throw new IllegalArgumentException("Invalid area revision");
            if (messageKey == null || !FEEDBACK_KEY.matcher(messageKey).matches()) {
                throw new IllegalArgumentException("Unknown area feedback key");
            }
        }
    }

    private AreaWire() {}

    public static byte[] encode(Request value) { return encode(value, MAX_REQUEST_BYTES, out -> writeRequest(out, value)); }
    public static byte[] encode(Snapshot value) { return encode(value, MAX_SNAPSHOT_BYTES, out -> writeSnapshot(out, value)); }
    public static byte[] encode(Feedback value) { return encode(value, MAX_FEEDBACK_BYTES, out -> writeFeedback(out, value)); }

    public static Request decodeRequest(byte[] bytes) {
        return decode(bytes, MAX_REQUEST_BYTES, 1, AreaWire::readRequest);
    }
    public static Snapshot decodeSnapshot(byte[] bytes) {
        return decode(bytes, MAX_SNAPSHOT_BYTES, 2, AreaWire::readSnapshot);
    }
    public static Feedback decodeFeedback(byte[] bytes) {
        return decode(bytes, MAX_FEEDBACK_BYTES, 3, AreaWire::readFeedback);
    }

    /** Cheap request-header hint for charging the correct rate bucket before full bounded decoding. */
    static Operation operationHint(byte[] bytes) {
        if (bytes == null || bytes.length < 47 || bytes.length > MAX_REQUEST_BYTES) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION || in.readUnsignedByte() != 1) return null;
            in.skipNBytes(16 + 16 + 8);
            int ordinal = in.readUnsignedByte();
            return ordinal < Operation.values().length ? Operation.values()[ordinal] : null;
        } catch (IOException malformedHeader) {
            return null;
        }
    }

    /** Reads only the fixed request header; geometry counts and payload bytes are deliberately untouched. */
    static RequestHeader decodeRequestHeader(byte[] bytes) {
        if (bytes == null || bytes.length < 47 || bytes.length > MAX_REQUEST_BYTES) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION || in.readUnsignedByte() != 1) return null;
            UUID requestId = readUuid(in);
            UUID worldId = readUuid(in);
            long expectedRevision = in.readLong();
            Operation operation = enumValue(Operation.values(), in.readUnsignedByte(), "area operation");
            if (expectedRevision < 0) return null;
            return new RequestHeader(requestId, worldId, expectedRevision, operation);
        } catch (IOException | IllegalArgumentException malformedHeader) {
            return null;
        }
    }

    /** Splits an ordered collection by encoded byte budget; no single geometry may exceed a page. */
    public static List<Snapshot> snapshotPages(UUID worldId, long revision, boolean administrator,
                                               boolean available, List<MapArea> areas) {
        List<MapArea> all = List.copyOf(Objects.requireNonNull(areas, "areas"));
        if (all.size() > AreaDocument.MAX_AREAS) throw new IllegalArgumentException("Too many shared areas");
        long pointCount = all.stream().mapToLong(area -> area.geometry().pointCount()).sum();
        if (pointCount > MAX_TOTAL_POINTS) throw new IllegalArgumentException("Shared areas exceed the total point limit");
        List<List<MapArea>> groups = new ArrayList<>();
        List<MapArea> current = new ArrayList<>();
        for (MapArea area : all) {
            validateSharedArea(area);
            if (current.size() == MAX_AREAS_PER_PAGE) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(area);
            try {
                new Snapshot(worldId, revision, 0, 1, administrator, available, current);
                encode(new Snapshot(worldId, revision, 0, 1, administrator, available, current));
            } catch (IllegalArgumentException tooLarge) {
                MapArea last = current.remove(current.size() - 1);
                if (current.isEmpty()) throw new IllegalArgumentException("A shared area exceeds the packet limit", tooLarge);
                groups.add(List.copyOf(current));
                current.clear();
                current.add(last);
                encode(new Snapshot(worldId, revision, 0, 1, administrator, available, current));
            }
        }
        if (!current.isEmpty() || groups.isEmpty()) groups.add(List.copyOf(current));
        if (groups.size() > MAX_PAGES) throw new IllegalArgumentException("Too many shared area pages");
        List<Snapshot> result = new ArrayList<>(groups.size());
        for (int page = 0; page < groups.size(); page++) {
            Snapshot snapshot = new Snapshot(worldId, revision, page, groups.size(), administrator, available, groups.get(page));
            encode(snapshot);
            result.add(snapshot);
        }
        return List.copyOf(result);
    }

    private static byte[] encode(Object model, int limit, Writer action) {
        Objects.requireNonNull(model, "model");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeByte(VERSION);
                out.writeByte(model instanceof Request ? 1 : model instanceof Snapshot ? 2 : 3);
                action.write(out);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > limit) throw new IllegalArgumentException("Area packet exceeds its byte limit");
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode bounded area packet", impossible);
        }
    }

    private static <T> T decode(byte[] bytes, int limit, int kind, Reader<T> action) {
        if (bytes == null || bytes.length < 6 || bytes.length > limit) throw new IllegalArgumentException("Invalid area packet size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION || in.readUnsignedByte() != kind) {
                throw new IllegalArgumentException("Invalid area packet header");
            }
            T value = action.read(in);
            if (in.available() != 0) throw new IllegalArgumentException("Trailing area packet bytes");
            return value;
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Malformed area packet", malformed);
        } catch (IllegalStateException | IndexOutOfBoundsException malformed) {
            throw new IllegalArgumentException("Malformed area packet", malformed);
        }
    }

    private static void writeRequest(DataOutputStream out, Request value) throws IOException {
        writeUuid(out, value.requestId());
        writeUuid(out, value.worldId());
        out.writeLong(value.expectedRevision());
        out.writeByte(value.operation().ordinal());
        out.writeByte(value.upserts().size());
        for (MapArea area : value.upserts()) writeArea(out, area);
        out.writeByte(value.deletes().size());
        for (UUID id : value.deletes()) writeUuid(out, id);
    }

    private static Request readRequest(DataInputStream in) throws IOException {
        UUID requestId = readUuid(in), worldId = readUuid(in);
        long revision = in.readLong();
        Operation operation = enumValue(Operation.values(), in.readUnsignedByte(), "area operation");
        int count = in.readUnsignedByte();
        if (count > MAX_CHANGES) throw new IllegalArgumentException("Too many area upserts");
        List<MapArea> upserts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) upserts.add(readArea(in));
        int deletes = in.readUnsignedByte();
        if (deletes > MAX_CHANGES || count + deletes > MAX_CHANGES) throw new IllegalArgumentException("Too many area deletes");
        List<UUID> deleteIds = new ArrayList<>(deletes);
        for (int i = 0; i < deletes; i++) deleteIds.add(readUuid(in));
        return new Request(requestId, worldId, revision, operation, upserts, deleteIds);
    }

    private static void writeSnapshot(DataOutputStream out, Snapshot value) throws IOException {
        writeUuid(out, value.worldId());
        out.writeLong(value.revision());
        out.writeShort(value.page());
        out.writeShort(value.pages());
        out.writeBoolean(value.administrator());
        out.writeBoolean(value.available());
        out.writeByte(value.areas().size());
        for (MapArea area : value.areas()) writeArea(out, area);
    }

    private static Snapshot readSnapshot(DataInputStream in) throws IOException {
        UUID world = readUuid(in);
        long revision = in.readLong();
        int page = in.readUnsignedShort(), pages = in.readUnsignedShort();
        boolean administrator = readBoolean(in), available = readBoolean(in);
        int count = in.readUnsignedByte();
        if (count > MAX_AREAS_PER_PAGE) throw new IllegalArgumentException("Too many areas on snapshot page");
        List<MapArea> areas = new ArrayList<>(count);
        for (int i = 0; i < count; i++) areas.add(readArea(in));
        return new Snapshot(world, revision, page, pages, administrator, available, areas);
    }

    private static void writeFeedback(DataOutputStream out, Feedback value) throws IOException {
        writeUuid(out, value.requestId());
        writeUuid(out, value.worldId());
        out.writeBoolean(value.success());
        writeString(out, value.messageKey(), 128);
        out.writeLong(value.revision());
    }

    private static Feedback readFeedback(DataInputStream in) throws IOException {
        return new Feedback(readUuid(in), readUuid(in), readBoolean(in), readString(in, 128), in.readLong());
    }

    private static void writeArea(DataOutputStream out, MapArea area) throws IOException {
        validateSharedArea(area);
        writeUuid(out, area.id());
        writeString(out, area.dimension(), MAX_DIMENSION_BYTES);
        writeString(out, area.name(), MAX_STRING_BYTES);
        out.writeByte(area.kind().ordinal());
        out.writeByte(area.scope().ordinal());
        writeUuid(out, area.ownerId());
        writeString(out, area.ownerName(), MAX_STRING_BYTES);
        out.writeBoolean(area.parentId() != null);
        if (area.parentId() != null) writeUuid(out, area.parentId());
        out.writeLong(area.revision());
        out.writeByte(area.geometry().shape().ordinal());
        writePoint(out, area.label());
        MapArea.Style style = area.style();
        out.writeInt(style.color());
        out.writeBoolean(style.visible());
        out.writeBoolean(style.labels());
        out.writeBoolean(style.border());
        out.writeBoolean(style.fill());
        out.writeDouble(style.minBpp());
        out.writeDouble(style.maxBpp());
        out.writeInt(style.priority());
        out.writeByte(area.geometry().paths().size());
        int pointCount = 0;
        for (List<MapArea.Point> path : area.geometry().paths()) {
            if (path.size() > MAX_POINTS_PER_AREA || (pointCount += path.size()) > MAX_POINTS_PER_AREA) {
                throw new IllegalArgumentException("Too many line points");
            }
            out.writeShort(path.size());
            for (MapArea.Point point : path) writePoint(out, point);
        }
        out.writeByte(area.geometry().polygons().size());
        for (MapArea.Polygon polygon : area.geometry().polygons()) {
            if ((pointCount += polygon.outer().size()) > MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Too many area points");
            writeRing(out, polygon.outer());
            if (polygon.holes().size() > MAX_PARTS_PER_AREA) throw new IllegalArgumentException("Too many polygon holes");
            out.writeByte(polygon.holes().size());
            for (List<MapArea.Point> hole : polygon.holes()) {
                if ((pointCount += hole.size()) > MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Too many area points");
                writeRing(out, hole);
            }
        }
    }

    private static MapArea readArea(DataInputStream in) throws IOException {
        UUID id = readUuid(in);
        String dimension = readString(in, MAX_DIMENSION_BYTES), name = readString(in, MAX_STRING_BYTES);
        MapArea.Kind kind = enumValue(MapArea.Kind.values(), in.readUnsignedByte(), "area kind");
        MapArea.Scope scope = enumValue(MapArea.Scope.values(), in.readUnsignedByte(), "area scope");
        if (scope != MapArea.Scope.SHARED) throw new IllegalArgumentException("Private area found on shared wire");
        UUID owner = readUuid(in);
        String ownerName = readString(in, MAX_STRING_BYTES);
        UUID parent = readBoolean(in) ? readUuid(in) : null;
        long revision = in.readLong();
        MapArea.Shape shape = enumValue(MapArea.Shape.values(), in.readUnsignedByte(), "area shape");
        MapArea.Point label = readPoint(in);
        MapArea.Style style = new MapArea.Style(in.readInt(), readBoolean(in), readBoolean(in),
                readBoolean(in), readBoolean(in), in.readDouble(), in.readDouble(), in.readInt());
        int pathCount = in.readUnsignedByte();
        if (pathCount > MAX_PARTS_PER_AREA) throw new IllegalArgumentException("Too many line branches");
        int pointCount = 0;
        List<List<MapArea.Point>> paths = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) {
            int count = in.readUnsignedShort();
            if (count > MAX_POINTS_PER_AREA || (pointCount += count) > MAX_POINTS_PER_AREA) {
                throw new IllegalArgumentException("Too many line points");
            }
            List<MapArea.Point> points = new ArrayList<>(count);
            for (int j = 0; j < count; j++) points.add(readPoint(in));
            paths.add(List.copyOf(points));
        }
        int polygonCount = in.readUnsignedByte();
        if (polygonCount > MAX_PARTS_PER_AREA) throw new IllegalArgumentException("Too many polygon components");
        List<MapArea.Polygon> polygons = new ArrayList<>(polygonCount);
        for (int i = 0; i < polygonCount; i++) {
            List<MapArea.Point> outer = readRing(in, pointCount);
            pointCount += outer.size();
            int holes = in.readUnsignedByte();
            if (holes > MAX_PARTS_PER_AREA) throw new IllegalArgumentException("Too many polygon holes");
            List<List<MapArea.Point>> holeRings = new ArrayList<>(holes);
            for (int j = 0; j < holes; j++) {
                List<MapArea.Point> hole = readRing(in, pointCount);
                pointCount += hole.size();
                holeRings.add(hole);
            }
            polygons.add(new MapArea.Polygon(outer, holeRings));
        }
        return new MapArea(id, dimension, name, kind, scope, owner, ownerName, parent, revision,
                new MapArea.Geometry(shape, paths, polygons), label, style);
    }

    private static void writeRing(DataOutputStream out, List<MapArea.Point> points) throws IOException {
        if (points.size() < 3 || points.size() > MAX_POINTS_PER_AREA) throw new IllegalArgumentException("Invalid area ring size");
        out.writeShort(points.size());
        for (MapArea.Point point : points) writePoint(out, point);
    }

    private static List<MapArea.Point> readRing(DataInputStream in, int existingPoints) throws IOException {
        int count = in.readUnsignedShort();
        if (count < 3 || count > MAX_POINTS_PER_AREA || existingPoints + count > MAX_POINTS_PER_AREA) {
            throw new IllegalArgumentException("Invalid area ring size");
        }
        List<MapArea.Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) points.add(readPoint(in));
        return List.copyOf(points);
    }

    private static void writePoint(DataOutputStream out, MapArea.Point point) throws IOException {
        out.writeDouble(point.x());
        out.writeDouble(point.z());
    }

    private static MapArea.Point readPoint(DataInputStream in) throws IOException {
        return new MapArea.Point(in.readDouble(), in.readDouble());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        requireUuid(id, "area packet identifier");
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        UUID id = new UUID(in.readLong(), in.readLong());
        requireUuid(id, "area packet identifier");
        return id;
    }

    private static void writeString(DataOutputStream out, String value, int limit) throws IOException {
        Objects.requireNonNull(value, "string");
        final ByteBuffer encoded;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Malformed Unicode in area string", malformed);
        }
        int length = encoded.remaining();
        if (length > limit || length > 0xFFFF) throw new IllegalArgumentException("Area string exceeds its byte limit");
        byte[] bytes = new byte[length];
        encoded.get(bytes);
        out.writeShort(length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in, int limit) throws IOException {
        int length = in.readUnsignedShort();
        if (length > limit) throw new IllegalArgumentException("Area string exceeds its byte limit");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) {
            throw new IllegalArgumentException("Malformed UTF-8 in area string", malformed);
        }
    }

    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IllegalArgumentException("Invalid area boolean");
        return value == 1;
    }

    private static <T> T enumValue(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Unknown " + name);
        return values[ordinal];
    }

    private static void requireUuid(UUID value, String name) {
        if (value == null || (value.getMostSignificantBits() == 0L && value.getLeastSignificantBits() == 0L)) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static void validateSharedArea(MapArea area) {
        Objects.requireNonNull(area, "area");
        if (area.scope() != MapArea.Scope.SHARED) throw new IllegalArgumentException("Private areas cannot use the shared channel");
        if (area.ownerId() == null) throw new IllegalArgumentException("Shared areas must have an owner");
        if (area.geometry() == null || area.label() == null || area.style() == null) {
            throw new IllegalArgumentException("Incomplete shared area");
        }
    }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface
    private interface Reader<T> { T read(DataInputStream in) throws IOException; }
}
