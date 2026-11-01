// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient;

import net.aosankaku.openmapdelta.platform.DataPaths;
import com.google.gson.Gson;
import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.render.WaypointOwnerFace;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.world.Tile;
import net.aosankaku.openmapdelta.world.TileCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-owned hosting: only explored map data and known player positions are exported. */
public final class WebMapClient {
    private static final Gson GSON = new Gson();
    private static final Path DIRECTORY = DataPaths.configDirectory();
    private static WebMapServer server;
    private static long nextPublish;
    private static long configModified = Long.MIN_VALUE;
    private static boolean active;
    private static Object joinConnection;
    private static boolean urlAnnounced;
    private static long imageRevision;
    private static TileCache lastCache;
    private static final Map<String, CachedTile> tiles = new LinkedHashMap<>();
    private record CachedTile(Tile source, long contentRevision, WebMapSnapshot.Image image) {}
    private WebMapClient() {}

    public static void tick(Minecraft mc) {
        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) { stop(); return; }
        // Logout explicitly clears the join state; temporary player/level absence does not.
        if (mc.level == null || mc.player == null) {
            suspendHosting();
            return;
        }
        if (mc.getConnection() == null) { stop(); return; }
        if (joinConnection != mc.getConnection()) {
            joinConnection = mc.getConnection();
            urlAnnounced = false;
        }
        long now = System.nanoTime();
        if (now < nextPublish) return;
        nextPublish = now + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
        try {
            Path config = DIRECTORY.resolve("webmap.json");
            long modified = Files.exists(config) ? Files.getLastModifiedTime(config).toMillis() : -1;
            if (!active || modified != configModified) {
                closeServer();
                active = true;
                configModified = modified;
                WebMapSettings settings = WebMapSettings.load(DIRECTORY);
                configModified = Files.getLastModifiedTime(config).toMillis();
                if (settings.enabled) {
                    server = new WebMapServer(new InetSocketAddress(settings.bindAddress, settings.port),
                            settings.requirePassword, settings.password, WebMapSettings.sessionSecret(DIRECTORY));
                    server.start();
                    LoggerFactory.getLogger("OpenMapDelta").info("Web map listening on {}:{}", settings.bindAddress, server.port());
                }
            }
            if (server != null) {
                publish(mc);
                if (!urlAnnounced) {
                    mc.player.displayClientMessage(WebMapChat.local(server.url()), false);
                    urlAnnounced = true;
                }
            }
        } catch (Exception e) {
            closeServer();
            // Never log the config contents, password, session cookie, or key.
            LoggerFactory.getLogger("OpenMapDelta").warn("Web map is unavailable: {}", e.getClass().getSimpleName());
        }
    }

    private static void publish(Minecraft mc) {
        WebMapTextureCapture.beginPublish(mc);
        TileCache cache = OpenMapDeltaClient.getActiveTileCache();
        if (cache == null || (OpenMapDeltaClient.isCaveMapSelected() && !ClientServerState.isCaveAllowed())) {
            server.publish(WebMapSnapshot.empty()); return;
        }
        boolean cacheChanged = cache != lastCache;
        if (cacheChanged) tiles.clear();
        lastCache = cache;
        int centerX = mc.player.blockPosition().getX();
        int centerZ = mc.player.blockPosition().getZ();
        List<WebMapServer.ViewCenter> views = server.viewCenters();
        if (views.isEmpty()) views = List.of(new WebMapServer.ViewCenter(centerX, centerZ));
        Map<Long, Tile> selected = new LinkedHashMap<>();
        int quota = 512 / views.size();
        for (var view : views) {
            cache.getTiles().entrySet().stream().filter(entry -> entry.getValue().hasObservedPixels())
                    .sorted(Comparator.comparingDouble(entry -> {
                        double dx = (int) (entry.getKey() >> 32) - (double) view.x();
                        double dz = (int) (long) entry.getKey() - (double) view.z();
                        return dx * dx + dz * dz;
                    })).limit(quota).forEach(entry -> selected.put(entry.getKey(), entry.getValue()));
        }
        Map<String, WebMapSnapshot.Image> images = new LinkedHashMap<>();
        List<Map<String, Object>> tileIndex = new ArrayList<>();
        for (var entry : selected.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            String key = x + "," + z;
            Tile tile = entry.getValue();
            CachedTile previous = tiles.get(key);
            // Loaded data can change without a sampled-content revision, so include the actual pixel copy.
            // One snapshot per second bounds this copy and avoids touching game data on HTTP threads.
            int[] pixels = new int[Tile.PIXEL_COUNT];
            for (int pz = 0; pz < Tile.SIZE; pz++) {
                for (int px = 0; px < Tile.SIZE; px++) pixels[pz * Tile.SIZE + px] = tile.getPixel(px, pz);
            }
            WebMapSnapshot.Image image = previous != null && previous.image().matches(pixels)
                    ? previous.image() : new WebMapSnapshot.Image(pixels, ++imageRevision);
            tiles.put(key, new CachedTile(tile, tile.getContentRevision(), image));
            images.put(key, image);
            tileIndex.add(Map.of("key", key, "x", x - 64, "z", z - 64,
                    "revision", image.revision()));
        }
        tiles.keySet().retainAll(images.keySet());
        Map<String, WebMapSnapshot.Image> iconImages = new LinkedHashMap<>();
        Map<String, Object> players = new LinkedHashMap<>();
        for (var player : ExternalPlayerClient.players()) {
            Map<String, Object> item = player(player.id().toString(), player.name(), player.dimension(),
                    player.x(), player.y(), player.z(), false);
            attachPlayerIcon(mc, player.id(), item, iconImages);
            players.put(player.id().toString(), item);
        }
        for (var player : mc.level.players()) {
            var pos = player.blockPosition();
            Map<String, Object> item = player(player.getUUID().toString(), player.getName().getString(),
                    mc.level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ(), player == mc.player);
            attachPlayerIcon(mc, player.getUUID(), item, iconImages);
            players.put(player.getUUID().toString(), item);
        }
        List<Map<String, Object>> markers = new ArrayList<>();
        for (var marker : MarkerManager.get().getVisibleMarkers(mc.level.dimension())) {
            if (marker.getType() == net.aosankaku.openmapdelta.marker.MarkerType.PLAYER) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", marker.getId().toString()); item.put("name", marker.getDisplayLabel());
            item.put("x", marker.getX()); item.put("y", marker.hasKnownY() ? marker.getY() : null);
            item.put("z", marker.getZ()); item.put("color", marker.getColor());
            item.put("type", marker.getType().name()); item.put("iconId", marker.getIconId());
            if (marker.getOwnerId() != null) {
                item.put("ownerId", marker.getOwnerId().toString());
                item.put("ownerName", marker.getOwnerName());
                var ownerTexture = WaypointOwnerFace.texture(marker.getOwnerId(), marker.getOwnerName());
                WebMapTextureCapture.Published ownerFace = WebMapTextureCapture.playerFace(mc,
                        marker.getOwnerId(), ownerTexture);
                if (ownerFace != null) {
                    item.put("ownerIconKey", ownerFace.key());
                    item.put("ownerIconRevision", ownerFace.image().revision());
                    iconImages.put(ownerFace.key(), ownerFace.image());
                }
            }
            MarkerIconCatalog.Entry entry = MarkerIconCatalog.find(marker.getIconId());
            WebMapTextureCapture.Published icon = WebMapTextureCapture.markerIcon(mc, entry);
            if (icon != null) {
                item.put("iconKey", icon.key()); item.put("iconRevision", icon.image().revision());
                iconImages.put(icon.key(), icon.image());
            }
            markers.add(item);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("active", true); state.put("dimension", mc.level.dimension().location().toString());
        state.put("layer", OpenMapDeltaClient.isCaveMapSelected() ? OpenMapDeltaClient.getSelectedMapLayer() : null);
        state.put("x", centerX); state.put("y", mc.player.blockPosition().getY()); state.put("z", centerZ);
        state.put("revision", cache.getRevision()); state.put("tiles", tileIndex);
        state.put("players", players.values()); state.put("markers", markers);
        state.put("playerIconMaxSize", OpenMapDeltaConfig.PLAYER_ICON_MAX_SIZE.get());
        Map<String, String> locale = new LinkedHashMap<>();
        for (String key : List.of("players", "search", "show_players", "hide_players", "recenter", "zoom_in", "zoom_out",
                "password", "login", "login_failed", "waiting", "surface", "cave", "unknown", "connection_lost")) {
            locale.put(key, Component.translatable("web.odysseymap." + key).getString());
        }
        locale.put("show_grid", Component.translatable("gui.odysseymap.show_grid").getString());
        locale.put("hide_grid", Component.translatable("gui.odysseymap.hide_grid").getString());
        state.put("locale", locale);
        server.publish(new WebMapSnapshot(json(state), json(locale), images, iconImages));
    }

    private static Map<String, Object> player(String id, String name, String dimension, int x, int y, int z, boolean self) {
        Map<String, Object> player = new LinkedHashMap<>();
        player.put("id", id); player.put("name", name); player.put("dimension", dimension);
        player.put("x", x); player.put("y", y); player.put("z", z); player.put("self", self);
        return player;
    }
    private static void attachPlayerIcon(Minecraft mc, java.util.UUID id, Map<String, Object> player,
                                         Map<String, WebMapSnapshot.Image> iconImages) {
        if (mc.getConnection() == null) return;
        var info = mc.getConnection().getPlayerInfo(id);
        var skin = info == null ? null : info.getSkin();
        ResourceLocation location = skin == null ? null : skin.texture();
        WebMapTextureCapture.Published icon = WebMapTextureCapture.playerFace(mc, id, location);
        if (icon == null) return;
        player.put("iconKey", icon.key()); player.put("iconRevision", icon.image().revision());
        iconImages.put(icon.key(), icon.image());
    }
    private static byte[] json(Object value) { return GSON.toJson(value).getBytes(StandardCharsets.UTF_8); }
    private static void closeServer() {
        if (server != null) { server.close(); server = null; }
        tiles.clear(); lastCache = null;
    }
    private static void suspendHosting() {
        closeServer(); active = false; nextPublish = 0; configModified = Long.MIN_VALUE;
    }
    public static void stop() {
        suspendHosting();
        WebMapTextureCapture.clear();
        joinConnection = null; urlAnnounced = false;
    }
}
