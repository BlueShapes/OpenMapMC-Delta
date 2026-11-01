// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Optional JourneyMap/Xaero player readers. All methods must be called from the client thread. */
public final class InstalledMapPlayers {
    private static final Class<?> JOURNEY_MAP_MANAGER = optionalClass("journeymap.common.util.PlayerRadarManager");
    private static final Class<?> XAERO_SESSION = optionalClass("xaero.common.XaeroMinimapSession");
    private static final int MAX_PLAYERS = 4096;
    private static final int MAX_SCANNED_PLAYERS = 8192;
    private static final int MAX_TRACKER_SYSTEMS = 128;
    private static final Set<String> NO_ARGUMENT_METHODS = Set.of(
            "getInstance", "getPlayers", "getCurrentSession", "getModMain", "getMinimapProcessor",
            "getRenderedPlayerTrackerManager", "getPlayerTracker", "getAllSystems", "getReader",
            "getTrackedPlayerIterator", "getSyncedTrackedPlayerManager", "getId", "getX", "getY",
            "getZ", "getDimensionKey", "getDimension");
    private static final Set<String> OBJECT_ARGUMENT_METHODS = Set.of(
            "getId", "getX", "getY", "getZ", "getDimension", "getDimensionKey");
    private static final ClassValue<MethodTable> METHOD_TABLES = new ClassValue<>() {
        @Override protected MethodTable computeValue(Class<?> type) { return new MethodTable(type); }
    };

    private static final class MethodTable {
        private final Map<String, Method> noArguments;
        private final Map<String, Method> objectArgument;

        private MethodTable(Class<?> type) {
            Map<String, Method> noArguments = new HashMap<>();
            Map<String, Method> objectArgument = new HashMap<>();
            try {
                for (Method method : type.getMethods()) {
                    String name = method.getName();
                    if (method.getParameterCount() == 0 && NO_ARGUMENT_METHODS.contains(name)) {
                        noArguments.putIfAbsent(name, accessible(method));
                    } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class
                            && OBJECT_ARGUMENT_METHODS.contains(name)) {
                        objectArgument.putIfAbsent(name, accessible(method));
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A changed optional API has no usable methods for this class.
            }
            this.noArguments = Collections.unmodifiableMap(noArguments);
            this.objectArgument = Collections.unmodifiableMap(objectArgument);
        }

        private static Method accessible(Method method) {
            try { method.trySetAccessible(); } catch (RuntimeException | LinkageError ignored) { }
            return method;
        }
    }

    /** Client-derived identity and validation hooks; no optional-mod or Minecraft classes are linked here. */
    public record Context(String dimension, UUID selfId, Map<UUID, String> onlineNames,
                          Function<Object, PlayerPosition> journeyMapPlayer,
                          Function<Object, String> dimensionKeyId) {
        public Context {
            onlineNames = safeOnlineNames(onlineNames);
            journeyMapPlayer = journeyMapPlayer == null ? ignored -> null : journeyMapPlayer;
            dimensionKeyId = dimensionKeyId == null ? ignored -> null : dimensionKeyId;
        }
    }

    private InstalledMapPlayers() { }

    /** Reads visible JourneyMap players; the supplied mapper rejects players outside the current level. */
    public static List<PlayerPosition> readJourneyMap(Context context) {
        if (!validContext(context)) return List.of();
        Map<?, ?> players = journeyMapPlayers();
        if (players == null || players.isEmpty()) return List.of();
        LinkedHashMap<UUID, PlayerPosition> result = new LinkedHashMap<>();
        int scanned = 0;
        try {
            for (Map.Entry<?, ?> entry : players.entrySet()) {
                if (scanned++ >= MAX_SCANNED_PLAYERS || result.size() >= MAX_PLAYERS) break;
                if (!(entry.getKey() instanceof UUID id) || entry.getValue() == null || !isOnline(context, id)) continue;
                PlayerPosition mapped;
                try {
                    mapped = context.journeyMapPlayer().apply(entry.getValue());
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (mapped == null || !id.equals(mapped.id()) || !context.dimension().equals(mapped.dimension())) continue;
                addProfilePosition(context, result, id, mapped.dimension(), mapped.x(), mapped.y(), mapped.z());
            }
        } catch (RuntimeException | LinkageError ignored) {
            // An optional integration failing mid-read must not escape into the game loop.
        }
        return List.copyOf(result.values());
    }

    /** Reads Xaero's tracked-player systems and synced store, including compatible tracker add-ons. */
    public static List<PlayerPosition> readXaero(Context context) {
        if (!validContext(context)) return List.of();
        Object session = xaeroCurrentSession();
        if (session == null) return List.of();

        LinkedHashMap<UUID, PlayerPosition> result = new LinkedHashMap<>();
        readXaeroTrackerSystems(session, context, result);
        readXaeroSyncedPlayers(session, context, result);
        return List.copyOf(result.values());
    }

    private static void readXaeroTrackerSystems(Object session, Context context,
                                                LinkedHashMap<UUID, PlayerPosition> result) {
        Object main = invokeNoArgs(session, "getModMain");
        if (main == null) return;
        Object trackerManager = firstNonNull(invokeNoArgs(main, "getRenderedPlayerTrackerManager"),
                invokeNoArgs(main, "getPlayerTracker"));
        Object systemsValue = invokeNoArgs(trackerManager, "getAllSystems");
        if (!(systemsValue instanceof Iterable<?> systems)) return;
        int systemCount = 0;
        try {
            Iterator<?> systemIterator = systems.iterator();
            while (systemCount < MAX_TRACKER_SYSTEMS && result.size() < MAX_PLAYERS) {
                Object system;
                try {
                    if (!systemIterator.hasNext()) break;
                    system = systemIterator.next();
                } catch (RuntimeException | LinkageError ignored) {
                    break;
                }
                systemCount++;
                Object reader = invokeNoArgs(system, "getReader");
                Object playerIterator = invokeNoArgs(system, "getTrackedPlayerIterator");
                if (!(playerIterator instanceof Iterator<?> iterator) || reader == null) continue;
                int scanned = 0;
                try {
                    while (scanned < MAX_SCANNED_PLAYERS && result.size() < MAX_PLAYERS) {
                        Object tracked;
                        try {
                            if (!iterator.hasNext()) break;
                            tracked = iterator.next();
                        } catch (RuntimeException | LinkageError ignored) {
                            break;
                        }
                        scanned++;
                        if (tracked == null) continue;
                        Object idValue = invokeOneArg(reader, "getId", tracked);
                        if (!(idValue instanceof UUID id) || !isOnline(context, id)) continue;
                        Object dimensionKey = invokeOneArg(reader, "getDimension", tracked);
                        String dimension = safeDimension(context, dimensionKey);
                        if (dimension == null) continue;
                        Object x = invokeOneArg(reader, "getX", tracked);
                        Object y = invokeOneArg(reader, "getY", tracked);
                        Object z = invokeOneArg(reader, "getZ", tracked);
                        addXaeroPosition(context, result, id, dimension, x, y, z);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // Isolate a faulty tracker system and continue with other systems.
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A broken registry iterator must not escape the client tick.
        }
    }

    private static void readXaeroSyncedPlayers(Object session, Context context,
                                                LinkedHashMap<UUID, PlayerPosition> result) {
        Object processor = invokeNoArgs(session, "getMinimapProcessor");
        Object manager = invokeNoArgs(processor, "getSyncedTrackedPlayerManager");
        Object playersValue = invokeNoArgs(manager, "getPlayers");
        if (!(playersValue instanceof Iterable<?> players)) return;
        int scanned = 0;
        try {
            Iterator<?> iterator = players.iterator();
            while (scanned < MAX_SCANNED_PLAYERS && result.size() < MAX_PLAYERS) {
                Object player;
                try {
                    if (!iterator.hasNext()) break;
                    player = iterator.next();
                } catch (RuntimeException | LinkageError ignored) {
                    break;
                }
                scanned++;
                if (player == null) continue;
                Object idValue = invokeNoArgs(player, "getId");
                if (!(idValue instanceof UUID id) || !isOnline(context, id)) continue;
                Object dimensionKey = firstNonNull(invokeNoArgs(player, "getDimensionKey"),
                        invokeNoArgs(player, "getDimension"));
                String dimension = safeDimension(context, dimensionKey);
                if (dimension == null) continue;
                addXaeroPosition(context, result, id, dimension,
                        invokeNoArgs(player, "getX"), invokeNoArgs(player, "getY"), invokeNoArgs(player, "getZ"));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep the last state replacement bounded even when an optional store changes during iteration.
        }
    }

    private static void addXaeroPosition(Context context, LinkedHashMap<UUID, PlayerPosition> result,
                                         UUID id, String dimension, Object rawX, Object rawY, Object rawZ) {
        if (!isOnline(context, id) || result.size() >= MAX_PLAYERS) return;
        Integer x = floorCoordinate(rawX);
        Integer y = floorCoordinate(rawY);
        Integer z = floorCoordinate(rawZ);
        if (x == null || y == null || z == null) return;
        addProfilePosition(context, result, id, dimension, x, y, z);
    }

    private static void addProfilePosition(Context context, LinkedHashMap<UUID, PlayerPosition> result,
                                           UUID id, String dimension, int x, int y, int z) {
        String name = context.onlineNames().get(id);
        if (name == null || result.size() >= MAX_PLAYERS) return;
        try {
            PlayerPosition position = new PlayerPosition(id, name, dimension, x, y, z);
            result.putIfAbsent(id, position);
        } catch (IllegalArgumentException ignored) {
            // Reject malformed names, dimensions, and world bounds supplied by optional integrations.
        }
    }

    private static String safeDimension(Context context, Object rawDimension) {
        if (rawDimension == null) return null;
        try {
            String dimension = context.dimensionKeyId().apply(rawDimension);
            return dimension != null && dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                    ? dimension : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Integer floorCoordinate(Object value) {
        if (!(value instanceof Number number)) return null;
        double coordinate = number.doubleValue();
        if (!Double.isFinite(coordinate) || coordinate <= -30_000_000.0 || coordinate >= 30_000_000.0) return null;
        return (int) Math.floor(coordinate);
    }

    private static boolean isOnline(Context context, UUID id) {
        return id != null && !id.equals(context.selfId()) && validName(context.onlineNames().get(id));
    }

    private static boolean validContext(Context context) {
        return context != null && context.dimension() != null
                && context.dimension().matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}")
                && context.selfId() != null && !context.onlineNames().isEmpty();
    }

    private static Map<UUID, String> safeOnlineNames(Map<UUID, String> onlineNames) {
        if (onlineNames == null || onlineNames.isEmpty()) return Map.of();
        LinkedHashMap<UUID, String> valid = new LinkedHashMap<>();
        int scanned = 0;
        for (Map.Entry<UUID, String> entry : onlineNames.entrySet()) {
            if (scanned++ >= MAX_SCANNED_PLAYERS) break;
            if (entry.getKey() != null && validName(entry.getValue())) {
                valid.putIfAbsent(entry.getKey(), entry.getValue());
                if (valid.size() >= MAX_PLAYERS) break;
            }
        }
        return Collections.unmodifiableMap(valid);
    }

    private static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static Map<?, ?> journeyMapPlayers() {
        Object manager = invokeStaticNoArgs(JOURNEY_MAP_MANAGER, "getInstance");
        Object players = invokeNoArgs(manager, "getPlayers");
        return players instanceof Map<?, ?> map ? map : null;
    }

    private static Object xaeroCurrentSession() {
        return invokeStaticNoArgs(XAERO_SESSION, "getCurrentSession");
    }

    private static Object invokeStaticNoArgs(Class<?> target, String name) {
        if (target == null) return null;
        Method method = METHOD_TABLES.get(target).noArguments.get(name);
        return invoke(method, null);
    }

    private static Object invokeNoArgs(Object target, String name) {
        if (target == null) return null;
        Method method = METHOD_TABLES.get(target.getClass()).noArguments.get(name);
        return invoke(method, target);
    }

    private static Object invokeOneArg(Object target, String name, Object argument) {
        if (target == null || argument == null) return null;
        Method method = METHOD_TABLES.get(target.getClass()).objectArgument.get(name);
        return invoke(method, target, argument);
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        if (method == null) return null;
        try {
            if (!Modifier.isStatic(method.getModifiers()) && target == null) return null;
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, InstalledMapPlayers.class.getClassLoader());
        } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Object firstNonNull(Object first, Object second) {
        return first == null ? second : first;
    }
}
