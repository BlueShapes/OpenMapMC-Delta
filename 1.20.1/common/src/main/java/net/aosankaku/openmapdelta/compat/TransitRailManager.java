// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import net.aosankaku.openmapdelta.config.OpenMapDeltaConfig;
import net.aosankaku.openmapdelta.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session-only snapshots of Create and MTR rail geometry for the fullscreen map. */
public final class TransitRailManager {
    private static final Logger LOG = LoggerFactory.getLogger("OpenMapDelta");
    private static final TransitRailManager INSTANCE = new TransitRailManager();
    private static final int MTR_REFRESH_TICKS = 20;

    private final TransitRailCreateCache createCache = new TransitRailCreateCache();
    private final Set<String> warned = new HashSet<>();
    private List<TransitRail> mtrRails = List.of();
    private Map<String, TransitRailReader.CachedGeometry> mtrGeometry = Map.of();
    private List<TransitRail> selectedRails = List.of();
    private Object connection;
    private Object level;
    private ResourceKey<Level> dimension;
    private int ticks;
    private int createRetryAt;
    private boolean createEnabled;
    private boolean mtrEnabled;
    private boolean mapWasOpen;
    private boolean forceMtrRefresh;
    private boolean selectedCreateEnabled;
    private boolean selectedMtrEnabled;
    private List<TransitRail> selectedCreateSource = List.of();
    private List<TransitRail> selectedMtrSource = List.of();

    private TransitRailManager() {}

    public static TransitRailManager get() { return INSTANCE; }

    /** Reads only while the fullscreen map is open, so rail APIs never add background work in-world. */
    public void tick(Minecraft mc, boolean fullscreenMapOpen) {
        boolean create = OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get();
        boolean mtr = OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (connection != mc.getConnection() || level != mc.level) {
            clearSessionSnapshot();
            warned.clear();
            connection = mc.getConnection();
            level = mc.level;
            dimension = mc.level.dimension();
            ticks = 0;
            createRetryAt = 0;
            createEnabled = create;
            mtrEnabled = mtr;
            mapWasOpen = false;
            forceMtrRefresh = true;
        } else if (create != createEnabled || mtr != mtrEnabled) {
            if (create != createEnabled) {
                createCache.invalidate();
                createRetryAt = 0;
            }
            if (mtr != mtrEnabled) {
                mtrRails = List.of();
                mtrGeometry = Map.of();
                forceMtrRefresh = mtr;
            }
            createEnabled = create;
            mtrEnabled = mtr;
        }

        if (!OpenMapDeltaConfig.MAP_MOD_ENABLED.get() || !fullscreenMapOpen) {
            if (mapWasOpen) {
                // Keep this session's MTR snapshot in memory, but force both sources to refresh on reopen.
                createCache.invalidate();
                createRetryAt = 0;
                mapWasOpen = false;
                ticks = 0;
                forceMtrRefresh = true;
            }
            rebuildSelectedRails();
            return;
        }

        boolean justOpened = !mapWasOpen;
        mapWasOpen = true;
        if (justOpened) {
            createCache.invalidate();
            createRetryAt = 0;
            ticks = 0;
            forceMtrRefresh = true;
        }
        ticks++;
        if (create) refreshCreate(mc);
        else createCache.invalidate();
        if (mtr && (forceMtrRefresh || justOpened || ticks % MTR_REFRESH_TICKS == 0)) {
            refreshMtr(mc);
            forceMtrRefresh = false;
        }
        else if (!mtr) {
            mtrRails = List.of();
            mtrGeometry = Map.of();
        }
        rebuildSelectedRails();
    }

    /** Returns an immutable path snapshot; toggles are read live so hiding a source takes effect at once. */
    public List<TransitRail> rails(ResourceKey<Level> requestedDimension) {
        if (requestedDimension == null || !requestedDimension.equals(dimension)
                || !OpenMapDeltaConfig.MAP_MOD_ENABLED.get()) return List.of();
        boolean showCreate = OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get();
        boolean showMtr = OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get();
        if (showCreate != selectedCreateEnabled || showMtr != selectedMtrEnabled) rebuildSelectedRails();
        return selectedRails;
    }

    /** Clears all source snapshots and session-scoped warnings on disconnect or level unload. */
    public void reset() {
        clearSessionSnapshot();
        warned.clear();
        connection = null;
        level = null;
        dimension = null;
        ticks = 0;
        createRetryAt = 0;
        createEnabled = false;
        mtrEnabled = false;
        mapWasOpen = false;
        forceMtrRefresh = false;
    }

    private void refreshCreate(Minecraft mc) {
        if (ticks < createRetryAt) return;
        if (!Services.PLATFORM.isModLoaded("create")) {
            createCache.invalidate();
            return;
        }
        try {
            Class<?> createClient = Class.forName("com.simibubi.create.CreateClient");
            Object manager = TransitStationReader.field(createClient, "RAILWAYS");
            if (manager == null) {
                createCache.invalidate();
                return;
            }
            Object networks = TransitStationReader.field(manager, "trackNetworks");
            Object rawVersion = TransitStationReader.field(manager, "version");
            if (!(rawVersion instanceof Number version)) throw new NoSuchFieldException("Create railway version");
            int currentVersion = version.intValue();
            ResourceKey<Level> currentDimension = mc.level.dimension();
            if (createCache.matches(manager, networks, currentVersion, currentDimension)) return;
            List<TransitRail> found = TransitRailReader.readCreate(networks, currentDimension,
                    TransitRailManager::minecraftPosition);
            createCache.update(manager, networks, currentVersion, currentDimension, found);
            createRetryAt = 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            createCache.invalidate();
            createRetryAt = ticks + 100;
            unsupported("Create", error);
        }
    }

    private void refreshMtr(Minecraft mc) {
        if (!Services.PLATFORM.isModLoaded("mtr")) {
            mtrRails = List.of();
            mtrGeometry = Map.of();
            return;
        }
        try {
            Class<?> mtr4;
            try { mtr4 = Class.forName("org.mtr.mod.client.MinecraftClientData"); }
            catch (ClassNotFoundException absent) { mtr4 = null; }
            TransitRailReader.MtrSnapshot snapshot;
            if (mtr4 != null) {
                Object data = TransitStationReader.call(mtr4, "getInstance");
                snapshot = TransitRailReader.readMtr4(data, mc.level.dimension(), TransitRailManager::mtrPosition,
                        mtrGeometry);
            } else {
                snapshot = TransitRailReader.readMtr3(Class.forName("mtr.client.ClientData"), mc.level.dimension(),
                        TransitRailManager::minecraftPosition, TransitRailManager::blockPosition, mtrGeometry);
            }
            mtrRails = snapshot.rails();
            mtrGeometry = snapshot.geometryCache();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            mtrRails = List.of();
            mtrGeometry = Map.of();
            unsupported("MTR", error);
        }
    }

    private void rebuildSelectedRails() {
        boolean showCreate = OpenMapDeltaConfig.FULLSCREEN_SHOW_CREATE_RAILS.get();
        boolean showMtr = OpenMapDeltaConfig.FULLSCREEN_SHOW_MTR_RAILS.get();
        List<TransitRail> createSnapshot = createCache.rails();
        if (showCreate == selectedCreateEnabled && showMtr == selectedMtrEnabled
                && createSnapshot == selectedCreateSource && mtrRails == selectedMtrSource) return;
        selectedCreateEnabled = showCreate;
        selectedMtrEnabled = showMtr;
        selectedCreateSource = createSnapshot;
        selectedMtrSource = mtrRails;
        if (showCreate && showMtr) {
            List<TransitRail> combined = new ArrayList<>(createSnapshot.size() + mtrRails.size());
            combined.addAll(createSnapshot);
            combined.addAll(mtrRails);
            selectedRails = List.copyOf(combined);
        } else if (showCreate) selectedRails = createSnapshot;
        else if (showMtr) selectedRails = mtrRails;
        else selectedRails = List.of();
    }

    private void clearSessionSnapshot() {
        createCache.invalidate();
        mtrRails = List.of();
        mtrGeometry = Map.of();
        selectedRails = List.of();
        selectedCreateEnabled = false;
        selectedMtrEnabled = false;
        selectedCreateSource = List.of();
        selectedMtrSource = List.of();
    }

    private void unsupported(String source, Throwable error) {
        if (warned.add(source)) LOG.warn("{} rail overlay skipped: unsupported or unavailable synchronized API ({})",
                source, error.toString());
    }

    private static TransitRail.Point minecraftPosition(Object position) throws ReflectiveOperationException {
        if (!(position instanceof Vec3 vector)) throw new NoSuchFieldException("Expected synchronized Minecraft Vec3");
        return new TransitRail.Point(vector.x, vector.y, vector.z);
    }

    private static TransitRail.Point blockPosition(Object position) throws ReflectiveOperationException {
        if (!(position instanceof BlockPos block)) throw new NoSuchFieldException("Expected MTR endpoint BlockPos");
        return new TransitRail.Point(block.getX(), block.getY(), block.getZ());
    }

    private static TransitRail.Point mtrPosition(Object position) throws ReflectiveOperationException {
        return new TransitRail.Point(mtrCoordinate(position, "x"), mtrCoordinate(position, "y"),
                mtrCoordinate(position, "z"));
    }

    private static double mtrCoordinate(Object position, String axis) throws ReflectiveOperationException {
        Object coordinate;
        try { coordinate = TransitStationReader.call(position, axis); }
        catch (NoSuchMethodException noAccessor) { coordinate = TransitStationReader.field(position, axis); }
        if (!(coordinate instanceof Number number)) throw new NoSuchFieldException("MTR vector " + axis + " coordinate");
        return number.doubleValue();
    }
}
