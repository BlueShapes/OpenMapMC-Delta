// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.platform.DataPaths;
import net.aosankaku.openmapdelta.gui.WaypointImportScreen;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** State changes run on the client thread; discovery only reads files off-thread. */
public final class WaypointImportManager {
    public static final String IMPORT_COMMAND = "odysseymap import";
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final WaypointImportManager INSTANCE = new WaypointImportManager();
    private final WaypointImportSession session = new WaypointImportSession();
    private CompletableFuture<List<WaypointImportReader.Source>> scanTask;
    private List<WaypointImportReader.Source> sources = List.of();
    private boolean scanHandled;
    private String scanError;
    private Long requestedOpen;

    private WaypointImportManager() {}
    public static WaypointImportManager get() { return INSTANCE; }

    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        ensureScan(mc);
        if (requestedOpen != null) {
            long expected = requestedOpen;
            requestedOpen = null;
            if (isCurrentSession(mc, expected)) mc.gui.setScreen(new WaypointImportScreen(null));
        }
        finishScan(mc);
    }

    public long sessionId(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return -1;
        String world = worldIdentity(mc);
        if (world == null || mc.getConnection() == null) return -1;
        if (session.enter(mc.getConnection(), world)) {
            clearScan();
            requestedOpen = null;
        }
        MarkerManager.get().setCurrentWorld(world);
        return session.token();
    }

    public boolean isCurrentSession(Minecraft mc, long expected) {
        return mc != null && mc.player != null && mc.level != null
                && session.matches(mc.getConnection(), worldIdentity(mc), expected);
    }

    private static String worldIdentity(Minecraft mc) {
        if (mc.getSingleplayerServer() != null) {
            return "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize();
        }
        ServerData server = mc.getCurrentServer();
        if (server == null && mc.getConnection() != null) server = mc.getConnection().getServerData();
        if (server != null && server.ip != null && !server.ip.isBlank()) return "server:" + server.ip;
        if (mc.getConnection() != null) return "server:" + mc.getConnection().getConnection().getRemoteAddress();
        return null;
    }

    /** The command hook only queues opening, so the chat click can finish closing its own screen. */
    public boolean requestOpen(String command) {
        if (!IMPORT_COMMAND.equals(command)) return false;
        long token = sessionId(Minecraft.getInstance());
        if (token >= 0) requestedOpen = token;
        return true;
    }

    public void ensureScan(Minecraft mc) {
        if (sessionId(mc) < 0 || scanTask != null || scanHandled) return;
        Path gameDirectory = Path.of(mc.gameDirectory.toString());
        scanTask = CompletableFuture.supplyAsync(() -> {
            try { return WaypointImportReader.scan(gameDirectory); }
            catch (IOException e) { throw new CompletionException(e); }
        });
    }

    public void rescan(Minecraft mc) {
        if (sessionId(mc) < 0) return;
        clearScan();
        ensureScan(mc);
    }

    private void finishScan(Minecraft mc) {
        CompletableFuture<List<WaypointImportReader.Source>> task = scanTask;
        if (task == null || scanHandled || !task.isDone()) return;
        scanHandled = true;
        try {
            sources = List.copyOf(task.join());
            notifyIfNeeded(mc);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            scanError = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            LOG.warn("Failed to scan waypoint import sources", cause);
        }
    }

    private void notifyIfNeeded(Minecraft mc) {
        Set<String> notified = readNotifiedFingerprints();
        List<WaypointImportReader.Source> unseen = WaypointImportPolicy.unannounced(sources, existingPoints(), notified);
        if (unseen.isEmpty()) return;
        String mods = String.join(", ", unseen.stream().map(WaypointImportReader.Source::modName).distinct().toList());
        Component link = Component.translatable("gui.odysseymap.import_waypoints")
                .withStyle(style -> style.withUnderlined(true).withColor(0x55FFFF)
                        .withClickEvent(new ClickEvent.RunCommand("/" + IMPORT_COMMAND)));
        mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("odysseymap.import.prompt",
                Component.literal(mods), Component.keybind("key.odysseymap.settings"))
                .append(" ").append(link));
        for (WaypointImportReader.Source source : unseen) notified.add(WaypointImportPolicy.fingerprint(source));
        writeNotifiedFingerprints(notified);
    }

    public boolean isScanning() { return scanTask != null && !scanHandled; }
    public boolean isReady() { return scanHandled && scanError == null; }
    public String getScanError() { return scanError; }
    public List<WaypointImportReader.Source> getSources() { return sources; }

    public ImportResult importSource(WaypointImportReader.Source source, long expectedSession) {
        Minecraft mc = Minecraft.getInstance();
        if (!isCurrentSession(mc, expectedSession) || !isReady() || !sources.contains(source)) {
            return new ImportResult(0, 0, 0, false, false);
        }
        List<Marker> oldWaypoints = new ArrayList<>(MarkerManager.get().getAllWaypoints());
        WaypointImportPolicy.Plan plan = WaypointImportPolicy.plan(source, existingPoints());
        List<Marker> imported = new ArrayList<>();
        int skipped = plan.skipped();
        for (WaypointImportReader.Waypoint point : plan.additions()) {
            Identifier location = Identifier.tryParse(point.dimension());
            if (location == null) { skipped++; continue; }
            ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location);
            Marker marker = new Marker(UUID.randomUUID(), MarkerType.WAYPOINT, dimension,
                    point.x(), point.y(), point.z(), point.name(), point.color());
            marker.setVisible(point.visible());
            marker.setImportedWorld(session.world());
            imported.add(marker);
        }
        if (imported.isEmpty()) return new ImportResult(0, plan.duplicates(), skipped, true, true);
        imported.forEach(MarkerManager.get()::addWaypointDirect);
        if (!MarkerStorage.save()) {
            MarkerManager.get().clearWaypoints();
            oldWaypoints.forEach(MarkerManager.get()::addWaypointDirect);
            return new ImportResult(0, plan.duplicates(), skipped, false, true);
        }
        return new ImportResult(imported.size(), plan.duplicates(), skipped, true, true);
    }

    public int countValid(WaypointImportReader.Source source) {
        return (int) source.waypoints().stream().filter(WaypointImportPolicy::valid).count();
    }

    public int countSkipped(WaypointImportReader.Source source) {
        return source.skipped() + source.waypoints().size() - countValid(source);
    }

    private static Set<WaypointImportPolicy.Key> existingPoints() {
        Set<WaypointImportPolicy.Key> existing = new HashSet<>();
        for (Marker marker : MarkerManager.get().getWaypoints()) {
            existing.add(new WaypointImportPolicy.Key(marker.getLabel(), marker.getDimension().identifier().toString(),
                    marker.getX(), marker.getY(), marker.getZ()));
        }
        return existing;
    }

    private static Path fingerprintsPath() {
        return DataPaths.configDirectory().resolve("notified-imports.txt");
    }

    private static Set<String> readNotifiedFingerprints() {
        Path path = fingerprintsPath();
        if (!Files.exists(path)) return new HashSet<>();
        try { return new HashSet<>(Files.readAllLines(path, StandardCharsets.UTF_8)); }
        catch (IOException e) {
            LOG.warn("Failed to read waypoint import notification history", e);
            return new HashSet<>();
        }
    }

    private static void writeNotifiedFingerprints(Set<String> fingerprints) {
        try {
            List<String> lines = fingerprints.stream().sorted().toList();
            WaypointFileStore.write(fingerprintsPath(), writer -> {
                for (String line : lines) { writer.write(line); writer.write('\n'); }
            });
        } catch (IOException e) {
            LOG.warn("Failed to persist waypoint import notification history", e);
        }
    }

    private void clearScan() {
        if (scanTask != null && !scanTask.isDone()) scanTask.cancel(true);
        scanTask = null;
        sources = List.of();
        scanHandled = false;
        scanError = null;
    }

    public void onPlayerLogout() {
        clearScan();
        session.clear();
        requestedOpen = null;
        MarkerManager.get().setCurrentWorld(null);
    }

    public record ImportResult(int added, int duplicates, int skipped, boolean saved, boolean currentSession) {}
}
