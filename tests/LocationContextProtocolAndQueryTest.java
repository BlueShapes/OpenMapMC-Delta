// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
import net.aosankaku.openmapdelta.network.LocationContextRequest;
import net.aosankaku.openmapdelta.network.LocationContextResponse;
import net.aosankaku.openmapdelta.server.LocationContextStructureQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LocationContextProtocolAndQueryTest {
    private record Start(boolean valid, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean containsPoint() {
            int x = 5, y = 70, z = -9;
            return valid && minX <= x && x <= maxX && minY <= y && y <= maxY && minZ <= z && z <= maxZ;
        }
    }
    private record Chunk(Map<String, Start> starts) {}

    private static int assertions;

    public static void main(String[] args) {
        requestCodecRejectsMalformedAndOutOfBoundsData();
        responseCodecBoundsAndPreservesDistinctStructureIds();
        loadedStructureAggregationIsBoundedAndReportsPartialResults();
        System.out.println("Location context protocol/query checks passed: " + assertions);
    }

    private static void requestCodecRejectsMalformedAndOutOfBoundsData() {
        var request = new LocationContextRequest(java.util.UUID.randomUUID(), "minecraft:overworld", -32, 72, 48);
        var decoded = LocationContextRequest.decode(request.encode());
        check(request.equals(decoded), "request token, dimension and coordinates round-trip");
        rejects(() -> LocationContextRequest.decode(append(request.encode(), (byte) 0)), "request trailing byte rejected");
        rejects(() -> LocationContextRequest.decode(new byte[LocationContextRequest.MAX_BYTES + 1]), "oversized request rejected");
        byte[] malformedUtf8 = request.encode();
        malformedUtf8[19] = (byte) 0xFF;
        rejects(() -> LocationContextRequest.decode(malformedUtf8), "malformed UTF-8 resource ID rejected");
        rejects(() -> new LocationContextRequest(java.util.UUID.randomUUID(), "minecraft:overworld", 0, 4097, 0),
                "out-of-range Y rejected");
        rejects(() -> new LocationContextRequest(new java.util.UUID(0L, 0L), "minecraft:overworld", 0, 0, 0),
                "zero request token rejected");
    }

    private static void responseCodecBoundsAndPreservesDistinctStructureIds() {
        var id = java.util.UUID.randomUUID();
        var response = new LocationContextResponse(id, "minecraft:overworld", 5, 70, -9, true, true,
                "minecraft:plains", List.of("mod:z_structure", "minecraft:village_plains"));
        var decoded = LocationContextResponse.decode(response.encode());
        check(response.equals(decoded), "response with multiple IDs round-trips");
        check(decoded.structureIds().equals(List.of("minecraft:village_plains", "mod:z_structure")),
                "structure IDs are stable and sorted");
        rejects(() -> LocationContextResponse.decode(append(response.encode(), (byte) 0)), "response trailing byte rejected");
        byte[] malformedUtf8 = response.encode();
        malformedUtf8[19] = (byte) 0xFF;
        rejects(() -> LocationContextResponse.decode(malformedUtf8), "response malformed UTF-8 rejected");
        rejects(() -> new LocationContextResponse(id, "minecraft:overworld", 0, 0, 0, false, false,
                "minecraft:plains", List.of()), "unavailable response cannot imply known biome");
        rejects(() -> new LocationContextResponse(id, "minecraft:overworld", 0, 0, 0, true, true,
                "minecraft:plains", java.util.Collections.nCopies(65, "minecraft:village_plains")),
                "response structure count is bounded");
    }

    private static void loadedStructureAggregationIsBoundedAndReportsPartialResults() {
        var direct = new LinkedHashMap<String, Start>();
        direct.put("mod:direct", inside());
        direct.put("mod:outside_y", new Start(true, 0, 100, -20, 10, 110, -1));
        direct.put("mod:outside_z", new Start(true, 0, 60, 30, 10, 80, 50));
        direct.put("mod:invalid", new Start(false, 0, 60, -20, 10, 80, -1));
        var references = new LinkedHashMap<String, List<Long>>();
        references.put("minecraft:village", List.of(11L, 12L));
        var loaded = Map.of(11L, new Chunk(Map.of("minecraft:village", inside())),
                12L, new Chunk(Map.of("minecraft:village", inside())));
        var complete = aggregate(direct, references, loaded, 256, 64);
        check(complete.complete(), "loaded reference chunks produce a complete result");
        check(complete.structureIds().equals(List.of("minecraft:village", "mod:direct")),
                "all starts containing the full point are included");
        check(complete.checkedReferences() == 6, "direct starts and structure references are bounded checks");

        var partial = aggregate(Map.of(), references, Map.of(11L, loaded.get(11L)), 256, 64);
        check(!partial.complete(), "an unloaded referenced chunk marks results incomplete");
        check(partial.structureIds().equals(List.of("minecraft:village")), "known starts survive an incomplete lookup");
        var missingStart = aggregate(Map.of(), Map.of("minecraft:village", List.of(14L)),
                Map.of(14L, new Chunk(Map.of())), 256, 64);
        check(!missingStart.complete() && missingStart.structureIds().isEmpty(),
                "a loaded reference with no readable start does not imply no structures");

        var many = Map.of("mod:group", List.of(1L, 2L, 3L));
        var chunks = Map.of(1L, new Chunk(Map.of("mod:group", inside())),
                2L, new Chunk(Map.of("mod:group", inside())), 3L, new Chunk(Map.of("mod:group", inside())));
        var budgeted = aggregate(Map.of(), many, chunks, 2, 64);
        check(!budgeted.complete() && budgeted.checkedReferences() == 2, "reference traversal obeys its hard budget");

        var ids = new LinkedHashMap<String, Start>();
        ids.put("mod:z", inside()); ids.put("mod:a", inside()); ids.put("mod:m", inside());
        var capped = aggregate(ids, Map.of(), Map.of(), 256, 2);
        check(!capped.complete(), "discarded IDs mark the answer incomplete");
        check(capped.structureIds().equals(List.of("mod:a", "mod:m")), "the bounded result is independent of registry iteration order");
    }

    private static LocationContextStructureQuery.Result aggregate(
            Map<String, Start> direct, Map<String, List<Long>> references, Map<Long, Chunk> loaded,
            int maxReferences, int maxIds) {
        return LocationContextStructureQuery.aggregate(direct.entrySet(), references.entrySet(), loaded::get,
                (chunk, structure) -> chunk.starts().get(structure), start -> start != null && start.containsPoint(),
                structure -> structure, maxReferences, maxIds, true);
    }

    private static Start inside() { return new Start(true, 0, 60, -20, 10, 80, -1); }

    private static byte[] append(byte[] value, byte extra) {
        byte[] result = java.util.Arrays.copyOf(value, value.length + 1);
        result[value.length] = extra;
        return result;
    }

    private static void rejects(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
