// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.aosankaku.openmapdelta.marker.TransitWaypointAppearanceStore;
import net.aosankaku.openmapdelta.world.OpenMapDeltaClient;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-only automatic markers. Optional transit mods supply their own synchronized client data. */
public final class TransitWaypointManager {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final TransitWaypointManager INSTANCE = new TransitWaypointManager();
    private record MarkerLists(List<Marker> all, List<Marker> create, List<Marker> mtr) {}
    private record StationDefaults(int color, String iconId) {}
    private final Set<String> warned = new HashSet<>();
    private Map<UUID, TransitWaypointAppearanceStore.Appearance> appearanceOverrides = new LinkedHashMap<>();
    private final Map<UUID, TransitWaypointAppearanceStore.Patch> pendingAppearanceEdits = new LinkedHashMap<>();
    private Map<UUID, StationDefaults> stationDefaults = Map.of();
    private String appearanceWorldIdentity;
    private Object appearanceConnection;
    private List<TransitStation> stations = List.of();
    private Map<ResourceKey<Level>, MarkerLists> markerCache = Map.of();
    private Object connection;
    private Object level;
    private int ticks;
    private boolean createEnabled;
    private boolean mtrEnabled;
    private int createGroupingRadius;

    private TransitWaypointManager() {}
    public static TransitWaypointManager get() { return INSTANCE; }

    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { reset(); return; }
        boolean appearanceScopeChanged = refreshAppearanceScope(mc);
        boolean create = OpenMapDeltaConfig.CREATE_STATION_WAYPOINTS.get();
        boolean mtr = OpenMapDeltaConfig.MTR_STATION_WAYPOINTS.get();
        int groupingRadius = OpenMapDeltaConfig.CREATE_STATION_GROUPING_RADIUS.get();
        if (connection != mc.getConnection() || level != mc.level || create != createEnabled || mtr != mtrEnabled
                || groupingRadius != createGroupingRadius || appearanceScopeChanged) {
            stations = List.of();
            markerCache = Map.of();
            stationDefaults = Map.of();
            TransitStationReader.resetSnapshot();
            TransitStationCatalog.resetSnapshot();
            connection = mc.getConnection();
            level = mc.level;
            ticks = 0;
            createEnabled = create;
            mtrEnabled = mtr;
            createGroupingRadius = groupingRadius;
        }
        if (ticks++ % 100 != 0) return;
        List<TransitStation> discovered = new ArrayList<>();
        if (create && Services.PLATFORM.isModLoaded("create")) {
            try {
                Class<?> createClient = Class.forName("com.simibubi.create.CreateClient");
                Class<?> edgeType = Class.forName("com.simibubi.create.content.trains.graph.EdgePointType");
                Class<?> stationBlock = Class.forName("com.simibubi.create.content.trains.station.StationBlockEntity");
                discovered.addAll(TransitStationReader.readCreate(TransitStationReader.field(createClient, "RAILWAYS"),
                        TransitStationReader.field(edgeType, "STATION"), TransitWaypointManager::blockPosition,
                        (station, dimension, position) -> validCreateStation(mc, stationBlock, station, dimension, position)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { unsupported("Create", error); }
        }
        if (mtr && Services.PLATFORM.isModLoaded("mtr")) {
            try {
                Class<?> mtr4;
                try { mtr4 = Class.forName("org.mtr.mod.client.MinecraftClientData"); }
                catch (ClassNotFoundException absent) { mtr4 = null; }
                if (mtr4 != null) {
                    discovered.addAll(TransitStationReader.readMtr4(TransitStationReader.call(mtr4, "getInstance"),
                            mc.level.dimension(), TransitWaypointManager::mtrPosition));
                } else {
                    discovered.addAll(TransitStationReader.readMtr3(Class.forName("mtr.client.ClientData"),
                            mc.level.dimension(), TransitWaypointManager::blockPosition));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) { unsupported("MTR", error); }
        }
        stations = TransitStationCatalog.unique(discovered, dimension -> ((ResourceKey<?>) dimension).location().toString(), groupingRadius);
        rebuildMarkerCache();
    }

    private static TransitStation.Position blockPosition(Object position) throws ReflectiveOperationException {
        if (!(position instanceof BlockPos pos)) throw new NoSuchMethodException("Expected station BlockPos");
        return new TransitStation.Position(pos.getX(), pos.getY(), pos.getZ());
    }

    private static TransitStation.Position mtrPosition(Object position) throws ReflectiveOperationException {
        // These are MTR's own Position methods, never Minecraft mapped method names.
        return new TransitStation.Position(((Number) TransitStationReader.call(position, "getX")).intValue(),
                ((Number) TransitStationReader.call(position, "getY")).intValue(),
                ((Number) TransitStationReader.call(position, "getZ")).intValue());
    }

    private static boolean validCreateStation(Minecraft mc, Class<?> stationBlock, Object station,
                                               Object dimension, Object position) throws ReflectiveOperationException {
        if (!(position instanceof BlockPos pos) || !(dimension instanceof ResourceKey<?>)) return false;
        // Unloaded stations are valid synchronized graph entries; never force chunk loads.
        if (!mc.level.dimension().equals(dimension)
                || !mc.level.hasChunkAt(pos.getX(), pos.getZ())) return true;
        Object blockEntity = mc.level.getBlockEntity(pos);
        if (blockEntity == null || !stationBlock.isInstance(blockEntity)) return false;
        Object actualStation = TransitStationReader.call(blockEntity, "getStation");
        if (actualStation == null || !TransitStationReader.call(station, "getId")
                .equals(TransitStationReader.call(actualStation, "getId"))) return false;
        Object targeting = TransitStationReader.field(blockEntity, "edgePoint");
        Object target = TransitStationReader.call(targeting, "getGlobalPosition");
        if (!(target instanceof BlockPos track)) return false;
        return !mc.level.hasChunkAt(track.getX(), track.getZ())
                || Boolean.TRUE.equals(TransitStationReader.call(targeting, "hasValidTrack"));
    }

    public List<Marker> markers(ResourceKey<Level> dimension) {
        MarkerLists cached = markerCache.get(dimension);
        if (cached == null) return List.of();
        boolean create = OpenMapDeltaConfig.CREATE_STATION_WAYPOINTS.get();
        boolean mtr = OpenMapDeltaConfig.MTR_STATION_WAYPOINTS.get();
        if (create && mtr) return cached.all();
        if (create) return cached.create();
        if (mtr) return cached.mtr();
        return List.of();
    }

    private void rebuildMarkerCache() {
        Map<ResourceKey<Level>, List<Marker>> all = new HashMap<>();
        Map<UUID, StationDefaults> defaults = new HashMap<>();
        Map<ResourceKey<Level>, List<Marker>> create = new HashMap<>();
        Map<ResourceKey<Level>, List<Marker>> mtr = new HashMap<>();
        for (TransitStation station : stations) {
            if (!(station.dimension() instanceof ResourceKey<?> rawDimension)) continue;
            @SuppressWarnings("unchecked") ResourceKey<Level> dimension = (ResourceKey<Level>) rawDimension;
            TransitStation.Position pos = station.position();
            int color = station.source() == TransitStation.Source.CREATE ? 0xFFFFB45C : 0xFF55CFFF;
            Marker marker = new Marker(TransitStationCatalog.markerId(station, dimension.location().toString()),
                    MarkerType.TRANSIT_STATION, dimension, pos.x(), pos.y(), pos.z(), station.name(), color);
            String defaultIconId = station.source() == TransitStation.Source.CREATE
                    ? "symbol:odysseymap:create_station" : "symbol:odysseymap:mtr_station";
            marker.setIconId(defaultIconId);
            defaults.put(marker.getId(), new StationDefaults(color, defaultIconId));
            TransitWaypointAppearanceStore.Appearance override = appearanceOverrides.get(marker.getId());
            if (override != null) override.applyTo(marker);
            all.computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(marker);
            (station.source() == TransitStation.Source.CREATE ? create : mtr)
                    .computeIfAbsent(dimension, ignored -> new ArrayList<>()).add(marker);
        }
        Map<ResourceKey<Level>, MarkerLists> cache = new HashMap<>();
        all.forEach((dimension, markers) -> cache.put(dimension, new MarkerLists(List.copyOf(markers),
                List.copyOf(create.getOrDefault(dimension, List.of())), List.copyOf(mtr.getOrDefault(dimension, List.of())))));
        markerCache = Collections.unmodifiableMap(cache);
        stationDefaults = defaults;
    }

    public void reset() {
        pendingAppearanceEdits.clear();
        appearanceOverrides.clear();
        appearanceWorldIdentity = null;
        appearanceConnection = null;
        stationDefaults = Map.of();
        stations = List.of();
        markerCache = Map.of();
        TransitStationReader.resetSnapshot();
        TransitStationCatalog.resetSnapshot();
        connection = null;
        level = null;
        ticks = 0;
    }

    private boolean refreshAppearanceScope(Minecraft mc) {
        String worldIdentity = OpenMapDeltaClient.persistentWorldIdentity(mc);
        Object currentConnection = mc.getConnection();
        boolean changed = false;
        if (appearanceConnection != currentConnection) {
            appearanceConnection = currentConnection;
            appearanceWorldIdentity = null;
            appearanceOverrides = new LinkedHashMap<>();
            pendingAppearanceEdits.clear();
            changed = true;
        }
        if (worldIdentity == null) {
            // A missing identity gets a clean, connection-local memory scope, never another world's preferences.
            if (appearanceWorldIdentity != null) {
                appearanceWorldIdentity = null;
                appearanceOverrides = new LinkedHashMap<>();
                pendingAppearanceEdits.clear();
                changed = true;
            }
            return changed;
        }
        if (worldIdentity.equals(appearanceWorldIdentity)) return changed;

        Map<UUID, TransitWaypointAppearanceStore.Patch> pending = appearanceWorldIdentity == null
                ? new LinkedHashMap<>(pendingAppearanceEdits) : Map.of();
        appearanceOverrides = new LinkedHashMap<>(
                TransitWaypointAppearanceStore.load(TransitWaypointAppearanceStore.getPath(), worldIdentity));
        for (Map.Entry<UUID, TransitWaypointAppearanceStore.Patch> entry : pending.entrySet()) {
            TransitWaypointAppearanceStore.Appearance merged = entry.getValue().merge(appearanceOverrides.get(entry.getKey()));
            if (merged.isEmpty()) appearanceOverrides.remove(entry.getKey());
            else appearanceOverrides.put(entry.getKey(), merged);
        }
        pendingAppearanceEdits.clear();
        if (!pending.isEmpty()) {
            // Identity may become available after join; apply only fields edited in this connection's memory scope.
            TransitWaypointAppearanceStore.save(TransitWaypointAppearanceStore.getPath(), worldIdentity, appearanceOverrides);
        }
        appearanceWorldIdentity = worldIdentity;
        return true;
    }

    public void saveAppearance(Marker marker, TransitWaypointAppearanceStore.Field... fields) {
        if (marker != null) saveAppearances(List.of(marker), fields);
    }

    /** Visibility batches update once while preserving unrelated appearance fields. */
    public void saveAppearances(Collection<Marker> markers) {
        saveAppearances(markers, TransitWaypointAppearanceStore.Field.VISIBLE);
    }

    public void saveAppearances(Collection<Marker> markers, TransitWaypointAppearanceStore.Field... fields) {
        if (markers == null || markers.isEmpty() || fields == null || fields.length == 0) return;
        boolean changed = false;
        for (Marker marker : markers) {
            if (marker == null || marker.getType() != MarkerType.TRANSIT_STATION) continue;
            StationDefaults defaults = stationDefaults.get(marker.getId());
            if (defaults == null) continue;
            TransitWaypointAppearanceStore.Appearance appearance = new TransitWaypointAppearanceStore.Appearance(
                    marker.getColor() == defaults.color() ? null : marker.getColor(),
                    marker.isVisible() ? null : false,
                    marker.isFavorite() ? true : null,
                    marker.getIconId().equals(defaults.iconId()) ? null : marker.getIconId(),
                    marker.getCategory());
            if (appearance.isEmpty()) appearanceOverrides.remove(marker.getId());
            else appearanceOverrides.put(marker.getId(), appearance);
            if (appearanceWorldIdentity == null) {
                pendingAppearanceEdits.computeIfAbsent(marker.getId(), ignored -> new TransitWaypointAppearanceStore.Patch())
                        .update(appearance, fields);
            }
            changed = true;
        }
        if (changed && appearanceWorldIdentity != null) {
            TransitWaypointAppearanceStore.save(TransitWaypointAppearanceStore.getPath(),
                    appearanceWorldIdentity, appearanceOverrides);
        }
    }

    private void unsupported(String mod, Throwable error) {
        if (warned.add(mod)) LOG.warn("{} station waypoints skipped: unsupported or unavailable synchronized API ({})",
                mod, error.toString());
    }
}
