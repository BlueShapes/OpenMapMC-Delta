// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.compat.players.ExternalPlayerClient;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.compat.TransitWaypointManager;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.network.ClientServerState;
import net.aosankaku.openmapdelta.navigation.NavigationState;
import net.aosankaku.openmapdelta.navigation.NavigationMapSelection;
import net.aosankaku.openmapdelta.navigation.NavigationWorldSelection;
import net.aosankaku.openmapdelta.navigation.NavigationArrivalSnapshot;
import net.aosankaku.openmapdelta.navigation.NavigationRequestPolicy;
import net.aosankaku.openmapdelta.shared.SharedWaypoint;
import net.aosankaku.openmapdelta.shared.SharedWaypointClient;
import net.aosankaku.openmapdelta.shared.SharedWaypointPreferences;
import net.aosankaku.openmapdelta.shared.SharedWaypointRequest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();

    private final List<Marker> waypoints = new CopyOnWriteArrayList<>();
    private final Map<UUID, Marker> sessionMarkers = new HashMap<>();
    private final Map<UUID, TransientEntry> transientMarkers = new LinkedHashMap<>();
    private record TransientEntry(Marker marker, TransientMarkerLifetime lifetime) {}
    private Object noticeRetentionOwner;
    private final NavigationState navigation = new NavigationState();
    private final Map<UUID, Marker> navigationMarkers = new LinkedHashMap<>();
    private final Map<UUID, Marker> sharedMarkers = new LinkedHashMap<>();
    private long sharedClientRevision = Long.MIN_VALUE;
    private UUID sharedPreferencePlayer;
    private UUID sharedPreferenceWorld;
    private String currentWorld;

    public static MarkerManager get() { return INSTANCE; }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        updatePlayerMarkers(mc);
        updateTrackedNotices(mc);
        updateNavigation(mc);
        long now = monotonicMillis();
        transientMarkers.values().removeIf(entry -> !(noticeRetentionOwner != null
                && entry.marker().getType() == MarkerType.LOCATION_NOTICE) && entry.lifetime().expired(
                entry.marker().getDimension().equals(mc.level.dimension()),
                distanceSquared(mc, entry.marker()), now));
        if (OpenMapDeltaConfig.SHOW_ENTITIES.get()) {
            updateEntityMarkers(mc);
        }
    }

    private void updatePlayerMarkers(Minecraft mc) {
        sessionMarkers.entrySet().removeIf(e -> e.getValue().getType() == MarkerType.PLAYER);
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        for (var position : ExternalPlayerClient.players()) {
            if (position.id().equals(self.getUUID())) continue;
            var dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(position.dimension()));
            Marker marker = new Marker(position.id(), MarkerType.PLAYER, dimension,
                    position.x(), position.y(), position.z(), position.name(), MarkerType.PLAYER.defaultColor);
            sessionMarkers.put(position.id(), marker);
        }
        // Tracked entities update every tick and take priority over the one-second server snapshot.
        for (AbstractClientPlayer player : level.players()) {
            if (player == self) continue;
            Marker m = new Marker(player.getUUID(), MarkerType.PLAYER, level.dimension(),
                    player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ(),
                    player.getName().getString(), MarkerType.PLAYER.defaultColor);
            sessionMarkers.put(player.getUUID(), m);
        }
    }

    private void updateEntityMarkers(Minecraft mc) {
        sessionMarkers.entrySet().removeIf(e -> e.getValue().getType() == MarkerType.ENTITY);
        ClientLevel level = mc.level;
        LocalPlayer self = mc.player;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == self || entity instanceof AbstractClientPlayer) continue;
            if (self.distanceTo(entity) > 64) continue;
            Marker m = new Marker(entity.getUUID(), MarkerType.ENTITY, level.dimension(),
                    entity.blockPosition().getX(), entity.blockPosition().getY(), entity.blockPosition().getZ(),
                    entity.getType().getDescription().getString(), MarkerType.ENTITY.defaultColor);
            m.setIconId("mob:" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            sessionMarkers.put(entity.getUUID(), m);
        }
    }

    public Marker addWaypoint(ResourceKey<Level> dimension, int x, int z, String label) {
        return addWaypoint(dimension, x, Marker.UNKNOWN_Y, z, label, MarkerType.WAYPOINT.defaultColor);
    }

    public Marker addWaypoint(ResourceKey<Level> dimension, int x, int y, int z, String label, int color) {
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.WAYPOINT, dimension, x, y, z, label, color);
        waypoints.add(marker);
        MarkerStorage.save();
        return marker;
    }

    public static long monotonicMillis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static double distanceSquared(Minecraft mc, Marker marker) {
        return mc.player.distanceToSqr(marker.getX() + 0.5, marker.getY(), marker.getZ() + 0.5);
    }

    public Marker addTemporaryWaypoint(Minecraft mc, ResourceKey<Level> dimension, int x, int y, int z) {
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.TEMPORARY_WAYPOINT,
                dimension, x, y, z, Component.translatable("odysseymap.marker.temporary").getString(),
                MarkerType.TEMPORARY_WAYPOINT.defaultColor);
        boolean initiallyInside = mc.player != null && mc.level != null
                && dimension.equals(mc.level.dimension())
                && distanceSquared(mc, marker) <= TransientMarkerLifetime.ARRIVAL_RADIUS_SQUARED;
        transientMarkers.put(marker.getId(), new TransientEntry(marker,
                TransientMarkerLifetime.arrival(initiallyInside)));
        return marker;
    }

    public void addLocationNotice(ResourceKey<Level> dimension, int x, int y, int z, String sender) {
        addLocationNotice(dimension, x, y, z, sender,
                TransientMarkerLifetime.DEFAULT_NOTICE_DURATION_SECONDS);
    }

    public void addLocationNotice(ResourceKey<Level> dimension, int x, int y, int z, String sender,
                                  int durationSeconds) {
        addLocationNotice(dimension, x, y, z, sender, durationSeconds, null);
    }

    public void addLocationNotice(ResourceKey<Level> dimension, int x, int y, int z, String sender,
                                  int durationSeconds, UUID trackedPlayerId) {
        TransientMarkerLifetime lifetime = TransientMarkerLifetime.notice(monotonicMillis(), durationSeconds);
        // Bound the number of remotely supplied beams and labels.
        List<UUID> notices = transientMarkers.values().stream()
                .filter(entry -> entry.marker().getType() == MarkerType.LOCATION_NOTICE)
                .map(entry -> entry.marker().getId()).toList();
        if (notices.size() >= 64) {
            // Do not evict a point while a map session keeps it selectable.
            if (noticeRetentionOwner != null) return;
            transientMarkers.remove(notices.get(0));
        }
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.LOCATION_NOTICE,
                dimension, x, y, z, noticeLabel(sender, trackedPlayerId),
                MarkerType.LOCATION_NOTICE.defaultColor);
        marker.setTrackedPlayerId(trackedPlayerId);
        transientMarkers.put(marker.getId(), new TransientEntry(marker, lifetime));
    }

    private String noticeLabel(String sender, UUID trackedPlayerId) {
        if (trackedPlayerId == null) return Component.translatable("odysseymap.marker.notice", sender).getString();
        Marker tracked = currentPlayerMarker(Minecraft.getInstance(), trackedPlayerId);
        String target = tracked != null ? tracked.getLabel() : trackedPlayerId.toString();
        return Component.translatable("odysseymap.marker.tracked_notice", sender, target).getString();
    }

    /** Resolve current server or locally tracked player data, independently of marker visibility. */
    private Marker currentPlayerMarker(Minecraft mc, UUID id) {
        if (id == null) return null;
        if (mc.level != null) {
            for (AbstractClientPlayer player : mc.level.players()) {
                if (id.equals(player.getUUID())) return playerMarker(player);
            }
        }
        if (mc.player != null && id.equals(mc.player.getUUID())) return playerMarker(mc.player);
        Marker snapshot = sessionMarkers.get(id);
        if (snapshot != null && snapshot.getType() == MarkerType.PLAYER) return snapshot;
        // A menu can open between snapshots and the next MarkerManager tick.
        for (var player : ExternalPlayerClient.players()) {
            if (id.equals(player.id())) return new Marker(player.id(), MarkerType.PLAYER,
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(player.dimension())),
                    player.x(), player.y(), player.z(), player.name(), MarkerType.PLAYER.defaultColor);
        }
        return null;
    }

    private Marker playerMarker(AbstractClientPlayer player) {
        var position = player.blockPosition();
        return new Marker(player.getUUID(), MarkerType.PLAYER, player.level().dimension(),
                position.getX(), position.getY(), position.getZ(), player.getName().getString(), MarkerType.PLAYER.defaultColor);
    }

    private void updateTrackedNotices(Minecraft mc) {
        for (TransientEntry entry : transientMarkers.values()) {
            Marker notice = entry.marker();
            if (notice.getType() != MarkerType.LOCATION_NOTICE || notice.getTrackedPlayerId() == null) continue;
            Marker tracked = currentPlayerMarker(mc, notice.getTrackedPlayerId());
            if (tracked == null) continue;
            notice.setDimension(tracked.getDimension());
            notice.setX(tracked.getX());
            notice.setY(tracked.getY());
            notice.setZ(tracked.getZ());
        }
    }

    /** Static markers are copied; only PLAYER destinations follow an online UUID. */
    public boolean startNavigation(Minecraft mc, Marker source) {
        if (mc.player == null || mc.level == null || source == null || source.getId() == null
                || !NavigationRequestPolicy.canStart(source.getId(), mc.player.getUUID())
                || !source.getType().isMapSelectable()) return false;
        if (navigation.contains(source.getId())) return false;
        int maximum = OpenMapDeltaConfig.NAVIGATION_MAX_TARGETS.get();
        if (navigation.size() >= maximum) {
            mc.gui.hud.setOverlayMessage(Component.translatable("odysseymap.navigation.full", maximum), false);
            return false;
        }
        int y = source.getY();
        if (y == Marker.UNKNOWN_Y && source.getDimension().equals(mc.level.dimension())
                && mc.level.hasChunkAt(source.getX(), source.getZ())) {
            y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, source.getX(), source.getZ());
        }
        MarkerType navigationType = NavigationWorldSelection.snapshotType(source.getType());
        Marker snapshot = new Marker(source.getId(), navigationType, source.getDimension(),
                source.getX(), y, source.getZ(), source.getLabel(), source.getColor());
        snapshot.setIconId(source.getIconId());
        snapshot.setTrackedPlayerId(source.getType() == MarkerType.PLAYER ? source.getId() : null);
        if (source.isShared()) snapshot.setOwner(source.getOwnerId(), source.getOwnerName());
        UUID tracked = source.getType() == MarkerType.PLAYER ? source.getId() : null;
        if (!navigation.start(source.getId(), tracked, markerPosition(snapshot), observer(mc),
                maximum, OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get())) return false;
        navigationMarkers.put(snapshot.getId(), snapshot);
        return true;
    }

    public boolean isNavigating(UUID id) { return navigation.contains(id); }
    public boolean hasActiveNavigationPoints() {
        return navigationMarkers.values().stream().anyMatch(marker -> marker.getType() == MarkerType.NAVIGATION_POINT
                && navigation.contains(marker.getId()));
    }
    public void stopNavigation(UUID id) { navigation.stop(id); navigationMarkers.remove(id); }
    public void stopAllNavigation() { navigation.clear(); navigationMarkers.clear(); }
    public int getNavigationCount() { return navigation.size(); }
    public double navigationDistance(Minecraft mc, UUID id) { return navigation.distance(observer(mc), id); }

    /** Look up selectable markers, including hidden sources, live players and session fallbacks. */
    public Marker getMapMarker(UUID id) {
        if (id == null) return null;
        for (Marker marker : getWaypoints()) if (id.equals(marker.getId())) return marker;
        Marker shared = getSharedWaypoint(id);
        if (shared != null) return shared;
        TransientEntry transientEntry = transientMarkers.get(id);
        if (transientEntry != null) return transientEntry.marker();
        Minecraft mc = Minecraft.getInstance();
        Marker player = currentPlayerMarker(mc, id);
        if (player != null) return player;
        Marker session = sessionMarkers.get(id);
        if (session != null) return session;
        if (mc.level != null) {
            for (Marker marker : TransitWaypointManager.get().markers(mc.level.dimension())) {
                if (id.equals(marker.getId())) return marker;
            }
        }
        return navigationMarkers.get(id);
    }

    private static NavigationState.Position markerPosition(Marker marker) {
        return new NavigationState.Position(marker.getDimension().identifier().toString(),
                marker.getX(), marker.getY(), marker.getZ());
    }

    private static NavigationState.Observer observer(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        return new NavigationState.Observer(mc.level.dimension().identifier().toString(),
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void updateNavigation(Minecraft mc) {
        Map<UUID, NavigationState.Position> live = new HashMap<>();
        sessionMarkers.values().stream().filter(marker -> marker.getType() == MarkerType.PLAYER)
                .forEach(marker -> live.put(marker.getId(), markerPosition(marker)));
        Marker self = playerMarker(mc.player);
        live.put(self.getId(), markerPosition(self));
        // Older sessions may still have a destination tracking this client itself.
        // Remove it before arrival processing so it cannot remain permanently unarmed.
        navigation.stop(self.getId());
        navigationMarkers.remove(self.getId());
        List<UUID> arrived = navigation.update(observer(mc), OpenMapDeltaConfig.NAVIGATION_ARRIVAL_DISTANCE.get(), live);
        List<String> arrivedLabels = NavigationArrivalSnapshot.capture(arrived, navigationMarkers, Marker::getLabel);
        for (String label : arrivedLabels) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("odysseymap.navigation.arrived", label));
        }
        navigationMarkers.keySet().removeIf(id -> !navigation.contains(id));
        for (NavigationState.Target target : navigation.targets()) {
            Marker marker = navigationMarkers.get(target.id());
            if (marker == null) continue;
            NavigationState.Position position = target.position();
            marker.setDimension(ResourceKey.create(Registries.DIMENSION, Identifier.parse(position.dimension())));
            marker.setX(position.x());
            marker.setY(position.y());
            marker.setZ(position.z());
        }
    }

    /** Navigation targets bypass map visibility; coordinate destinations also render in the world. */
    public List<Marker> getMapMarkers(ResourceKey<Level> dimension) {
        return NavigationMapSelection.merge(getVisibleMarkers(dimension), navigation.targets(),
                dimension.identifier().toString(), Marker::getId, MarkerManager::markerPosition,
                target -> navigationMarkers.get(target.id()));
    }

    public void retainLocationNotices(Object owner) { noticeRetentionOwner = owner; }

    public void releaseLocationNotices(Object owner) {
        if (noticeRetentionOwner == owner) noticeRetentionOwner = null;
    }

    public Marker getLocationNotice(UUID id) {
        TransientEntry entry = transientMarkers.get(id);
        return entry != null && entry.marker().getType() == MarkerType.LOCATION_NOTICE ? entry.marker() : null;
    }

    public boolean hasLocationNotices() {
        return transientMarkers.values().stream()
                .anyMatch(entry -> entry.marker().getType() == MarkerType.LOCATION_NOTICE);
    }

    public void updateWaypoint(UUID id, String label, int color, int x, int y, int z) {
        Marker marker = getWaypoint(id);
        if (marker == null) return;
        if (marker.isShared()) {
            saveSharedWaypoint(id, marker.getDimension(), x, y, z, label, color,
                    marker.getIconId(), marker.getCategory());
            return;
        }
        if (marker.getType() == MarkerType.TRANSIT_STATION) {
            // Station names and coordinates belong to the source mod; only their color is editable here.
            if (marker.getColor() != color) {
                marker.setColor(color);
                TransitWaypointManager.get().saveAppearance(marker, TransitWaypointAppearanceStore.Field.COLOR);
            }
            return;
        }
        if (marker.getType() == MarkerType.TEMPORARY_WAYPOINT) {
            marker.setLabel(label);
            marker.setColor(color);
            marker.setX(x);
            marker.setY(y);
            marker.setZ(z);
            Minecraft mc = Minecraft.getInstance();
            boolean inside = mc.player != null && mc.level != null
                    && marker.getDimension().equals(mc.level.dimension())
                    && distanceSquared(mc, marker) <= TransientMarkerLifetime.ARRIVAL_RADIUS_SQUARED;
            transientMarkers.put(id, new TransientEntry(marker, TransientMarkerLifetime.arrival(inside)));
            return;
        }
        if (marker.getType() == MarkerType.DEATH) {
            marker.setLabel(label);
            MarkerStorage.save();
            return;
        }
        if (marker.getType() == MarkerType.WAYPOINT || marker.getType() == MarkerType.BED) {
            marker.setLabel(label);
            marker.setColor(color);
            marker.setX(x);
            marker.setY(y);
            marker.setZ(z);
            MarkerStorage.save();
        }
    }
    public Marker getWaypoint(UUID id) {
        if (id == null) return null;
        Marker shared = getSharedWaypoint(id);
        if (shared != null) return shared;
        for (Marker marker : waypoints) {
            if (marker.getId().equals(id) && isOwnedDeathPoint(marker)) return marker;
        }
        TransientEntry entry = transientMarkers.get(id);
        if (entry != null && entry.marker().getType().isEditableWaypoint()) return entry.marker();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Marker marker : TransitWaypointManager.get().markers(mc.level.dimension())) {
                if (marker.getId().equals(id) && isOwnedDeathPoint(marker)) return marker;
            }
        }
        return null;
    }

    public void removeWaypoint(UUID id) {
        Marker marker = getWaypoint(id);
        if (marker != null && marker.isShared()) {
            if (canManageWaypoint(marker)) SharedWaypointClient.send(SharedWaypointRequest.delete(id));
            return;
        }
        if (marker == null || !WaypointListPolicy.canRemove(marker.getType())) return;
        if (marker.getType() == MarkerType.TEMPORARY_WAYPOINT) {
            transientMarkers.remove(id);
            return;
        }
        if (marker.getType() == MarkerType.DEATH) {
            net.aosankaku.openmapdelta.death.DeathPointController.get().onManualDelete(marker);
            stopNavigation(id);
            waypoints.removeIf(existing -> existing.getId().equals(id));
            MarkerStorage.save();
        } else if (marker.getType() == MarkerType.WAYPOINT || marker.getType() == MarkerType.BED) {
            waypoints.removeIf(existing -> existing.getId().equals(id));
            MarkerStorage.save();
        }
    }

    public void toggleFavorite(UUID id) {
        Marker marker = getWaypoint(id);
        if (marker == null) return;
        marker.setFavorite(!marker.isFavorite());
        if (marker.isShared()) SharedWaypointPreferences.setFavorite(sharedPreferenceWorld,
                sharedPreferencePlayer, marker.getId(), marker.isFavorite());
        else if (marker.getType() == MarkerType.WAYPOINT || marker.getType() == MarkerType.DEATH
                || marker.getType() == MarkerType.BED) MarkerStorage.save();
        else if (marker.getType() == MarkerType.TRANSIT_STATION) TransitWaypointManager.get().saveAppearance(marker, TransitWaypointAppearanceStore.Field.FAVORITE);
    }

    public void toggleVisibility(UUID id) {
        Marker marker = getWaypoint(id);
        if (marker != null) setWaypointsVisible(List.of(marker), !marker.isVisible());
    }

    /** Saves icon and category edits after re-resolving the marker by ID, so refreshed station caches are safe. */
    public void saveWaypointAppearance(Marker marker, String iconId, String category) {
        if (marker == null) return;
        Marker current = getWaypoint(marker.getId());
        if (current == null || (!current.getType().isEditableWaypoint()
                && !current.isShared()
                && current.getType() != MarkerType.TRANSIT_STATION)) return;
        String normalizedIcon = current.getType() == MarkerType.DEATH
                ? net.aosankaku.openmapdelta.death.DeathPointData.ICON : MarkerIconId.normalize(iconId);
        String normalizedCategory = Marker.normalizeCategory(category);
        boolean iconChanged = !current.getIconId().equals(normalizedIcon);
        boolean categoryChanged = !current.getCategory().equals(normalizedCategory);
        if (!iconChanged && !categoryChanged) return;
        if (current.isShared()) {
            saveSharedWaypoint(current.getId(), current.getDimension(), current.getX(), current.getY(),
                    current.getZ(), current.getLabel(), current.getColor(), normalizedIcon, normalizedCategory);
            return;
        }
        current.setIconId(normalizedIcon);
        current.setCategory(normalizedCategory);
        if (current.getType() == MarkerType.WAYPOINT || current.getType() == MarkerType.DEATH
                || current.getType() == MarkerType.BED) MarkerStorage.save();
        else if (current.getType() == MarkerType.TRANSIT_STATION) {
            if (iconChanged && categoryChanged) TransitWaypointManager.get().saveAppearance(current,
                    TransitWaypointAppearanceStore.Field.ICON, TransitWaypointAppearanceStore.Field.CATEGORY);
            else if (iconChanged) TransitWaypointManager.get().saveAppearance(current, TransitWaypointAppearanceStore.Field.ICON);
            else TransitWaypointManager.get().saveAppearance(current, TransitWaypointAppearanceStore.Field.CATEGORY);
        }
    }
    private Map<UUID, Marker> currentListedWaypointLookup() {
        Map<UUID, Marker> current = new HashMap<>();
        for (Marker marker : getWaypoints()) current.put(marker.getId(), marker);
        for (Marker marker : getSharedWaypoints()) current.put(marker.getId(), marker);
        for (TransientEntry entry : transientMarkers.values()) {
            Marker marker = entry.marker();
            if (marker.getType().isEditableWaypoint()) current.put(marker.getId(), marker);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Marker marker : TransitWaypointManager.get().markers(mc.level.dimension())) {
                current.put(marker.getId(), marker);
            }
        }
        return current;
    }

    /** Changes visibility for a complete selection, persisting each backing store at most once. */
    public void setWaypointsVisible(Collection<Marker> markers, boolean visible) {
        if (markers == null || markers.isEmpty()) return;
        Map<UUID, Marker> current = currentListedWaypointLookup();
        boolean saveManual = false;
        List<Marker> transit = new ArrayList<>();
        List<UUID> shared = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Marker selected : markers) {
            if (selected == null || selected.getId() == null || !seen.add(selected.getId())) continue;
            Marker marker = current.get(selected.getId());
            if (marker == null || (!marker.getType().isEditableWaypoint()
                    && !marker.isShared()
                    && marker.getType() != MarkerType.TRANSIT_STATION)) continue;
            marker.setVisible(visible);
            if (marker.isShared()) shared.add(marker.getId());
            else if (marker.getType() == MarkerType.WAYPOINT || marker.getType() == MarkerType.DEATH
                    || marker.getType() == MarkerType.BED) saveManual = true;
            else if (marker.getType() == MarkerType.TRANSIT_STATION) transit.add(marker);
        }
        if (saveManual) MarkerStorage.save();
        if (!transit.isEmpty()) TransitWaypointManager.get().saveAppearances(transit, TransitWaypointAppearanceStore.Field.VISIBLE);
        SharedWaypointPreferences.setVisible(sharedPreferenceWorld, sharedPreferencePlayer, shared, visible);
    }


    public Marker addDeathPoint(ResourceKey<Level> dimension, int x, int y, int z,
                                net.aosankaku.openmapdelta.death.DeathPointData data) {
        Marker marker = new Marker(UUID.randomUUID(), MarkerType.DEATH, dimension, x, y, z,
                Component.translatable("odysseymap.marker.death").getString(), MarkerType.DEATH.defaultColor);
        marker.setImportedWorld(currentWorld != null ? currentWorld : "death:unknown-session");
        marker.setDeathPoint(data);
        marker.setIconId(net.aosankaku.openmapdelta.death.DeathPointData.ICON);
        if (data.isCached()) for (Marker existing : List.copyOf(waypoints)) {
            if (net.aosankaku.openmapdelta.death.DeathPointPolicy.replaceCached(data, existing.getDeathPoint())
                    && Objects.equals(existing.getImportedWorld(), marker.getImportedWorld())) removeDeathPoint(existing.getId());
        }
        waypoints.add(marker);
        MarkerStorage.save();
        return marker;
    }

    public void removeDeathPoint(UUID id) {
        Marker existing = getWaypoint(id);
        if (existing != null && existing.getType() == MarkerType.DEATH)
            net.aosankaku.openmapdelta.death.DeathPointController.get().onManualDelete(existing);
        waypoints.removeIf(marker -> marker.getType() == MarkerType.DEATH && marker.getId().equals(id));
        stopNavigation(id);
        MarkerStorage.save();
    }

    public boolean hasCachedDeathPoint() {
        return waypoints.stream().anyMatch(marker -> marker.getType() == MarkerType.DEATH
                && marker.getDeathPoint() != null && marker.getDeathPoint().isCached() && isOwnedDeathPoint(marker)
                && Objects.equals(marker.getImportedWorld(), currentWorld));
    }

    public boolean restoreCachedDeathPoint() {
        Marker last = null;
        for (Marker marker : waypoints) if (marker.getType() == MarkerType.DEATH
                && marker.getDeathPoint() != null && marker.getDeathPoint().isCached() && isOwnedDeathPoint(marker)
                && Objects.equals(marker.getImportedWorld(), currentWorld)) last = marker;
        if (last == null) return false;
        last.getDeathPoint().restore();
        last.setVisible(true);
        MarkerStorage.save();
        return true;
    }

    public void setBedPoint(ResourceKey<Level> dimension, int x, int y, int z) {
        if (WaypointImportManager.get().sessionId(Minecraft.getInstance()) < 0
                || currentWorld == null || currentWorld.isBlank()) return;
        String world = currentWorld;
        Marker candidate = new Marker(UUID.randomUUID(), MarkerType.BED, dimension, x, y, z,
                Component.translatable("odysseymap.marker.bed").getString(), MarkerType.BED.defaultColor);
        WaypointListPolicy.upsertBedForWorld(waypoints, world, candidate);
        MarkerStorage.save();
    }

    public List<Marker> getVisibleMarkers(ResourceKey<Level> dimension) {
        List<Marker> all = new ArrayList<>();
        // Notifications stay conspicuous even when ordinary waypoints are hidden.
        transientMarkers.values().stream().map(TransientEntry::marker)
                .filter(m -> m.getDimension().equals(dimension) && m.isVisible()
                        && (m.getType() == MarkerType.LOCATION_NOTICE || OpenMapDeltaConfig.SHOW_WAYPOINTS.get()))
                .forEach(all::add);
        if (OpenMapDeltaConfig.SHOW_WAYPOINTS.get()) {
            getWaypoints().stream().filter(m -> m.getType() != MarkerType.DEATH && m.getType() != MarkerType.BED
                    && m.getDimension().equals(dimension) && m.isVisible()).forEach(all::add);
            TransitWaypointManager.get().markers(dimension).stream().filter(Marker::isVisible).forEach(all::add);
        }
        if (OpenMapDeltaConfig.SHOW_SHARED_WAYPOINTS.get()) {
            getSharedWaypoints().stream().filter(m -> m.getDimension().equals(dimension) && m.isVisible()).forEach(all::add);
        }
        if (OpenMapDeltaConfig.SHOW_DEATH.get()) getWaypoints().stream()
                .filter(m -> m.getType() == MarkerType.DEATH && m.getDimension().equals(dimension) && m.isVisible())
                .forEach(all::add);
        if (OpenMapDeltaConfig.SHOW_BEDS.get()) getWaypoints().stream()
                .filter(m -> m.getType() == MarkerType.BED && m.getDimension().equals(dimension) && m.isVisible())
                .forEach(all::add);
        sessionMarkers.values().stream()
                .filter(m -> m.getDimension().equals(dimension) && isTypeVisible(m.getType()))
                .forEach(all::add);
        return all;
    }

    public List<Marker> getWaypointsInDimension(ResourceKey<Level> dimension) {
        return getWaypoints().stream()
                .filter(m -> m.getDimension().equals(dimension) && m.isVisible())
                .toList();
    }

    /** Lists waypoint sources for editing, including hidden manual markers and generated stations. */
    public List<Marker> getListedWaypointsInDimension(ResourceKey<Level> dimension) {
        List<Marker> temporary = transientMarkers.values().stream().map(TransientEntry::marker)
                .filter(marker -> marker.getType() == MarkerType.TEMPORARY_WAYPOINT)
                .toList();
        List<Marker> listed = new ArrayList<>(WaypointListPolicy.list(getWaypoints(), temporary,
                TransitWaypointManager.get().markers(dimension), dimension));
        getSharedWaypoints().stream().filter(marker -> marker.getDimension().equals(dimension)).forEach(listed::add);
        return List.copyOf(listed);
    }

    public List<Marker> getRenderableWaypointsInDimension(ResourceKey<Level> dimension) {
        return NavigationWorldSelection.merge(getVisibleMarkers(dimension), navigation.targets(),
                dimension.identifier().toString(), Marker::getId, MarkerManager::markerPosition,
                Marker::getType, target -> navigationMarkers.get(target.id()));
    }

    private boolean isTypeVisible(MarkerType type) {
        return switch (type) {
            case PLAYER -> OpenMapDeltaConfig.SHOW_PLAYERS.get();
            case ENTITY -> OpenMapDeltaConfig.SHOW_ENTITIES.get();
            case PORTAL -> OpenMapDeltaConfig.SHOW_PORTALS.get();
            case STRUCTURE -> OpenMapDeltaConfig.SHOW_STRUCTURES.get();
            default -> true;
        };
    }

    private boolean isOwnedDeathPoint(Marker marker) {
        var player = Minecraft.getInstance().player;
        return marker.getDeathPoint() == null || net.aosankaku.openmapdelta.death.DeathPointPolicy.visibleToOwner(
                marker.getDeathPoint().playerId(), player == null ? null : player.getUUID());
    }
    public List<Marker> getWaypoints() {
        return waypoints.stream().filter(marker -> WaypointImportSession.visibleInWorld(
                marker.getImportedWorld(), currentWorld)).filter(this::isOwnedDeathPoint)
                .filter(marker -> marker.getDeathPoint() == null || !marker.getDeathPoint().isCached()).toList();
    }
    public List<Marker> getSharedWaypoints() {
        refreshSharedWaypoints();
        return List.copyOf(sharedMarkers.values());
    }
    public Marker getSharedWaypoint(UUID id) {
        if (id == null) return null;
        refreshSharedWaypoints();
        return sharedMarkers.get(id);
    }
    public boolean canManageWaypoint(Marker marker) {
        if (marker == null) return false;
        if (marker.isShared()) return SharedWaypointClient.canManage(marker.getOwnerId());
        return marker.getType().isEditableWaypoint();
    }
    /** Generated stations keep their existing appearance-only editor. */
    public boolean canEditAppearance(Marker marker) {
        return canManageWaypoint(marker) || (marker != null && marker.getType() == MarkerType.TRANSIT_STATION);
    }
    public boolean saveSharedWaypoint(UUID id, ResourceKey<Level> dimension, int x, int y, int z,
                                     String label, int color, String iconId, String category) {
        Marker marker = getSharedWaypoint(id);
        if (marker == null || !canManageWaypoint(marker)) return false;
        return SharedWaypointClient.send(SharedWaypointRequest.update(id, dimension.identifier().toString(),
                x, y, z, label, color, iconId, category));
    }
    private void refreshSharedWaypoints() {
        long revision = SharedWaypointClient.revision();
        Minecraft mc = Minecraft.getInstance();
        UUID playerId = mc.player == null ? null : mc.player.getUUID();
        UUID worldId = SharedWaypointClient.worldId();
        if (revision == sharedClientRevision && Objects.equals(playerId, sharedPreferencePlayer)
                && Objects.equals(worldId, sharedPreferenceWorld)) return;
        sharedMarkers.clear();
        if (playerId != null && worldId != null) {
            List<SharedWaypoint> currentPoints = SharedWaypointClient.points();
            Set<UUID> currentIds = new HashSet<>();
            for (SharedWaypoint waypoint : currentPoints) currentIds.add(waypoint.id());
            SharedWaypointPreferences.retain(worldId, playerId, currentIds);
            for (SharedWaypoint waypoint : currentPoints) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                        Identifier.parse(waypoint.dimension()));
                Marker marker = new Marker(waypoint.id(), MarkerType.SHARED_WAYPOINT, dimension,
                        waypoint.x(), waypoint.y(), waypoint.z(), waypoint.label(), waypoint.color());
                marker.setIconId(waypoint.iconId());
                marker.setCategory(waypoint.category());
                marker.setOwner(waypoint.ownerId(), waypoint.ownerName());
                marker.setVisible(SharedWaypointPreferences.isVisible(worldId, playerId, waypoint.id()));
                marker.setFavorite(SharedWaypointPreferences.isFavorite(worldId, playerId, waypoint.id()));
                sharedMarkers.put(marker.getId(), marker);
            }
        }
        sharedClientRevision = revision;
        sharedPreferencePlayer = playerId;
        sharedPreferenceWorld = worldId;
    }
    public List<Marker> getAllWaypoints() { return Collections.unmodifiableList(waypoints); }
    public void setCurrentWorld(String world) {
        if (!Objects.equals(currentWorld, world)) { navigation.clear(); navigationMarkers.clear(); }
        currentWorld = world;
    }
    public String currentWorld() { return currentWorld; }
    public void clearWaypoints() { waypoints.clear(); }
    public void addWaypointDirect(Marker marker) { waypoints.add(marker); }
    public void clearSession() {
        navigation.clear();
        navigationMarkers.clear();
        TransitWaypointManager.get().reset();
        sessionMarkers.clear();
        transientMarkers.clear();
        noticeRetentionOwner = null;
        sharedMarkers.clear();
        sharedClientRevision = Long.MIN_VALUE;
        sharedPreferencePlayer = null;
        sharedPreferenceWorld = null;
    }
}
