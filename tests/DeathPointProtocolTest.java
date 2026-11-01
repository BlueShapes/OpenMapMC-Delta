// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.aosankaku.openmapdelta.death.DeathDropLedger;
import net.aosankaku.openmapdelta.death.DeathPointInbox;
import net.aosankaku.openmapdelta.death.DeathPointUpdate;
import net.aosankaku.openmapdelta.death.DeathLootTimer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class DeathPointProtocolTest {
    public static void main(String[] args) {
        var id = UUID.randomUUID();
        var inventory = List.of(new DeathPointUpdate.InventoryItem("minecraft:oak_log", 63, false, List.of("minecraft:logs")));
        var report = new DeathPointUpdate(id, "minecraft:overworld", 2, 65, -3, 1234, false, true, false,
                DeathPointUpdate.Outcome.COLLECTED_BY_OTHERS, List.of("Alice", "Bob"), inventory, 10);
        // Valid game state can lie outside the bounded optional recovery protocol.
        // Server capture must contain these rejections and let the game's death processing continue.
        for (int y : new int[]{-2048, 4096}) {
            var boundary = new DeathPointUpdate(id, "minecraft:overworld", 2, y, -3, 1234, false, true, false,
                    DeathPointUpdate.Outcome.ACTIVE, List.of(), inventory, 10);
            check(boundary.equals(DeathPointUpdate.decode(boundary.encode())), "inclusive death-height boundary roundtrips");
        }
        for (int y : new int[]{-2049, 4097, 5000, Integer.MIN_VALUE}) {
            rejects(() -> new DeathPointUpdate(id, "minecraft:overworld", 2, y, -3, 1234, false, true, false,
                    DeathPointUpdate.Outcome.ACTIVE, List.of(), inventory, 10), "unsupported death height remains bounded");
        }
        rejects(() -> new DeathPointUpdate(id, "minecraft:overworld", 2, 65, -3, 1234, false, true, false,
                DeathPointUpdate.Outcome.ACTIVE, List.of(), inventory, 1_000_001), "unsupported experience remains bounded");
        rejects(() -> new DeathPointUpdate.InventoryItem("example:" + "a".repeat(193), 1, false, List.of()),
                "valid but oversized registry IDs remain outside the protocol");
        check(report.equals(DeathPointUpdate.decode(report.encode())), "inventory tags and multi-collector attribution survive protocol/store reload");
        var partial = new DeathPointUpdate(id, report.dimension(), report.x(), report.y(), report.z(), report.gameTime(),
                false, true, false, DeathPointUpdate.Outcome.ACTIVE, List.of("Alice"), inventory, 10);
        check(DeathPointUpdate.decode(partial.encode()).collectorNames().equals(List.of("Alice")),
                "partial collector snapshot persists before another player completes recovery");
        var moved = report.withPosition(new DeathPointUpdate.Position(2.75, 32.5, -3.25, -0.5));
        check(moved.equals(DeathPointUpdate.decode(moved.encode())), "falling location and velocity survive transport/store reload");
        check(moved.x() == 2 && moved.y() == 65 && moved.z() == -3 && moved.recoveryY() == 32 && moved.recoveryZ() == -4,
                "moving recovery location does not change original death identity");
        check(DeathPointUpdate.decode(report.encode()).position() == null, "older report without motion remains compatible");
        check(DeathPointUpdate.decode(report.encode()).itemTimer() == null, "older reports retain fallback item timer semantics");
        var timed = moved.withItemTimer(new DeathPointUpdate.ItemTimer(12000, true, false));
        check(timed.equals(DeathPointUpdate.decode(timed.encode())), "authoritative remaining item lifetime survives transport/store reload");
        check(timed.withPosition(null).itemTimer().equals(timed.itemTimer()), "position updates preserve resource deadline");
        for (var timer : List.of(new DeathPointUpdate.ItemTimer(-1, false, false), new DeathPointUpdate.ItemTimer(0, false, true),
                new DeathPointUpdate.ItemTimer(2L * Integer.MAX_VALUE + 1, false, false))) {
            check(report.withItemTimer(timer).equals(DeathPointUpdate.decode(report.withItemTimer(timer).encode())), "infinite, expired and maximum timers roundtrip");
        }
        rejects(() -> new DeathPointUpdate.ItemTimer(-2, false, false), "invalid negative timer rejected");
        rejects(() -> new DeathPointUpdate.ItemTimer(-1, true, false), "infinite timer cannot tick");
        rejects(() -> new DeathPointUpdate.ItemTimer(1, false, true), "live timer cannot confirm expiration");
        rejects(() -> new DeathPointUpdate.ItemTimer(0, true, true), "expired timer cannot tick");
        var badTimer = JsonParser.parseString(new String(timed.encode(), StandardCharsets.UTF_8)).getAsJsonObject();
        var timerJson = badTimer.getAsJsonObject("update").getAsJsonObject("itemTimer");
        timerJson.addProperty("remainingTicks", 1.5);
        rejects(() -> DeathPointUpdate.decode(badTimer.toString().getBytes(StandardCharsets.UTF_8)), "fractional timer rejected before narrowing");
        timerJson.addProperty("remainingTicks", "6000");
        rejects(() -> DeathPointUpdate.decode(badTimer.toString().getBytes(StandardCharsets.UTF_8)), "timer numeric string rejected");
        timerJson.addProperty("remainingTicks", Long.MAX_VALUE);
        rejects(() -> DeathPointUpdate.decode(badTimer.toString().getBytes(StandardCharsets.UTF_8)), "oversized timer rejected");
        rejects(() -> new DeathPointUpdate.Position(0, Double.NaN, 0, 0), "nonfinite position rejected");
        rejects(() -> new DeathPointUpdate.Position(0, 64, 0, Double.POSITIVE_INFINITY), "nonfinite speed rejected");
        var badPosition = JsonParser.parseString(new String(moved.encode(), StandardCharsets.UTF_8)).getAsJsonObject();
        badPosition.getAsJsonObject("update").getAsJsonObject("position").addProperty("y", "64");
        rejects(() -> DeathPointUpdate.decode(badPosition.toString().getBytes(StandardCharsets.UTF_8)), "motion field types validated");
        badPosition.getAsJsonObject("update").getAsJsonObject("position").addProperty("y", 1.0e300);
        rejects(() -> DeathPointUpdate.decode(badPosition.toString().getBytes(StandardCharsets.UTF_8)), "out-of-world motion rejected");
        var json = JsonParser.parseString(new String(report.encode(), StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("update").addProperty("x", 4294967298L);
        rejects(() -> DeathPointUpdate.decode(json.toString().getBytes(StandardCharsets.UTF_8)), "overflow coordinate rejected before narrowing");
        json.getAsJsonObject("update").addProperty("x", 2);
        json.getAsJsonObject("update").remove("keepInventory");
        rejects(() -> DeathPointUpdate.decode(json.toString().getBytes(StandardCharsets.UTF_8)), "missing keepInventory rejected");
        rejects(() -> DeathPointUpdate.decode(new byte[]{(byte) 0xC3, (byte) 0x28}), "malformed UTF-8 rejected");
        rejects(() -> DeathPointUpdate.decode(new byte[DeathPointUpdate.MAX_BYTES + 1]), "packet bound enforced");

        var finiteLoot = List.of(new DeathLootTimer.Resource(6000, true), new DeathLootTimer.Resource(12000, true));
        check(DeathLootTimer.sample(finiteLoot, false, false, true, null).remainingTicks() == 12000,
                "all-items availability uses the longest live drop lifetime");
        check(DeathLootTimer.sample(List.of(new DeathLootTimer.Resource(-1, false)), false, true, true, timed.itemTimer()).remainingTicks() == -1,
                "known indefinitely protected corpse remains untimed even when accompanying drops unload");
        check(DeathLootTimer.sample(finiteLoot, true, false, true, null).remainingTicks() == -1,
                "another unsupported grave still protects loot beside an empty recognized corpse");
        var beforeUnload = new DeathPointUpdate.ItemTimer(3000, true, false);
        var unavailableGrave = DeathLootTimer.sample(List.of(), false, true, true, beforeUnload);
        check(!unavailableGrave.expired() && !unavailableGrave.ticking() && unavailableGrave.remainingTicks() == 3000,
                "missing resources or unsearched expected grave region cannot prove loss even if marker region is loaded");
        check(DeathLootTimer.sample(finiteLoot, false, true, true, beforeUnload).remainingTicks() == 12000,
                "loaded item lifespan extension is retained while another resource remains unavailable");
        check(DeathLootTimer.sample(List.of(new DeathLootTimer.Resource(1, true)), false, false, true, beforeUnload).remainingTicks() == 1,
                "live item at nominal expiration never confirms loot loss");
        check(DeathLootTimer.sample(List.of(), false, false, true, beforeUnload).expired(),
                "fully observed absence of previously tracked resources confirms loss");
        check(DeathLootTimer.sample(List.of(), false, false, false, null) == null,
                "XP-only death with no observed item resources retains fallback instead of immediate expiry");
        rejects(() -> new DeathLootTimer.Resource(0, true), "zero remaining prediction is not a live-resource proof");

        Object oldConnection = new Object(), current = new Object();
        DeathPointInbox.reset();
        DeathPointInbox.markSupported(oldConnection, current);
        check(!DeathPointInbox.supported(current), "stale support handshake rejected");
        DeathPointInbox.markSupported(current, current);
        check(DeathPointInbox.supported(current), "S2C-only channel support known before first death");
        DeathPointInbox.apply(oldConnection, current, report);
        check(DeathPointInbox.drain().isEmpty(), "stale connection cannot modify deathpoints");
        var active = new DeathPointUpdate(id, report.dimension(), report.x(), report.y(), report.z(), report.gameTime(),
                false, true, false, DeathPointUpdate.Outcome.ACTIVE, List.of(), inventory, 10);
        DeathPointInbox.apply(current, current, active);
        DeathPointInbox.apply(current, current, report);
        DeathPointInbox.markSupported(current, current);
        check(DeathPointInbox.supported(current) && DeathPointInbox.drain().equals(List.of(report)),
                "terminal first delivery retains full inventory/XP snapshot after ACTIVE coalesces; support frame preserves queue");
        for (int i = 0; i < 300; i++) DeathPointInbox.apply(current, current,
                new DeathPointUpdate(UUID.randomUUID(), "minecraft:overworld", 0, 64, 0, 0, false, false, false,
                        DeathPointUpdate.Outcome.ACTIVE, List.of(), List.of(), 0));
        check(DeathPointInbox.drain().size() == 256, "inbox bounded independently of sender");
        DeathPointInbox.reset();
        check(!DeathPointInbox.supported(current), "logout resets accepted support");

        check(DeathDropLedger.attributableAmount(64, 1, 1) == 0, "partial pickup of mixed stack does not claim death item");
        check(DeathDropLedger.attributableAmount(64, 1, 63) == 0, "all unrelated items can leave without death completion");
        check(DeathDropLedger.attributableAmount(64, 1, 64) == 1, "full mixed stack pickup accounts for death item");
        check(DeathDropLedger.attributableAmount(64, 40, 32) == 8, "partial mixed merge transfers only conservative tracked mass");
        check(DeathDropLedger.attributableAmount(32, 32, 16) == 16, "tracked-only partial pickup exact");
        check(DeathDropLedger.attributableAmount(64, 40, 64) == 40, "merged ownership across multiple deaths bounded by total tracked mass");
        check(!DeathDropLedger.completelyCollected(0, true), "despawn or destruction never names a collector");
        check(!DeathDropLedger.completelyCollected(1, false), "partial pickup retains death recovery");
        check(DeathDropLedger.completelyCollected(0, false), "all positively attributed pickups complete recovery");
        var drop = new DeathDropLedger.Drop(UUID.randomUUID(), 32, 2, 65, -3);
        var gson = new Gson();
        check(drop.equals(DeathDropLedger.Drop.fromJson(gson.toJsonTree(drop).getAsJsonObject())), "persisted lineage UUID/count/last loaded position survive reload");
        var corruptedDrop = gson.toJsonTree(drop).getAsJsonObject();
        corruptedDrop.addProperty("count", 4294967297L);
        rejects(() -> DeathDropLedger.Drop.fromJson(corruptedDrop), "persisted oversized count cannot narrow to a valid value");
        rejects(() -> new DeathDropLedger.Drop(id, 0, 0, 0, 0), "persisted drop count validation");
        System.out.println("DeathPointProtocolTest passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
