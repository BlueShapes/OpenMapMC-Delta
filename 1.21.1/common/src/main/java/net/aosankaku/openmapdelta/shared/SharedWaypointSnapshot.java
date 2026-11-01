// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** One bounded page of a complete server snapshot. */
public record SharedWaypointSnapshot(UUID worldId, long revision, boolean multiplayer, boolean administrator,
                                     int pageIndex, int pageCount, List<SharedWaypoint> waypoints) {
    public static final int MAX_WAYPOINTS = 4096;
    public static final int PAGE_SIZE = 64;
    public static final int MAX_PAGES = MAX_WAYPOINTS / PAGE_SIZE;
    public static final int MAX_BYTES = 131_072;
    private static final int VERSION = 1;

    public SharedWaypointSnapshot {
        if (worldId == null || isZero(worldId) || revision < 0 || pageCount < 1 || pageCount > MAX_PAGES || pageIndex < 0 || pageIndex >= pageCount) {
            throw new IllegalArgumentException("Invalid snapshot metadata");
        }
        waypoints = List.copyOf(waypoints);
        if (waypoints.size() > PAGE_SIZE || (pageCount > 1 && waypoints.isEmpty())) throw new IllegalArgumentException("Invalid snapshot page size");
        var ids = new HashSet<UUID>();
        for (SharedWaypoint waypoint : waypoints) if (!ids.add(waypoint.id())) throw new IllegalArgumentException("Duplicate waypoint in page");
    }

    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            SharedWaypointData.writeUuid(out, worldId);
            out.writeLong(revision);
            out.writeBoolean(multiplayer);
            out.writeBoolean(administrator);
            out.writeShort(pageIndex);
            out.writeShort(pageCount);
            out.writeShort(waypoints.size());
            for (SharedWaypoint waypoint : waypoints) SharedWaypointData.writeWaypoint(out, waypoint);
            out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Snapshot page too large");
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static SharedWaypointSnapshot decode(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Snapshot page too large");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported snapshot version");
            UUID worldId = SharedWaypointData.readUuid(in);
            long revision = in.readLong();
            int multiplayer = in.readUnsignedByte(), admin = in.readUnsignedByte();
            if (multiplayer > 1 || admin > 1) throw new IllegalArgumentException("Invalid snapshot flags");
            int pageIndex = in.readUnsignedShort(), pageCount = in.readUnsignedShort(), count = in.readUnsignedShort();
            if (count > PAGE_SIZE) throw new IllegalArgumentException("Invalid snapshot page count");
            var waypoints = new java.util.ArrayList<SharedWaypoint>(count);
            for (int i = 0; i < count; i++) waypoints.add(SharedWaypointData.readWaypoint(in));
            if (in.available() != 0) throw new IllegalArgumentException("Trailing snapshot data");
            return new SharedWaypointSnapshot(worldId, revision, multiplayer == 1, admin == 1, pageIndex, pageCount, waypoints);
        } catch (IOException malformed) { throw new IllegalArgumentException("Truncated snapshot", malformed); }
    }

    private static boolean isZero(UUID id) { return id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L; }
}
