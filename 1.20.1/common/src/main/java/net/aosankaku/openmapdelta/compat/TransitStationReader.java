// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reads public, upstream station APIs without making either transit mod a dependency. */
public final class TransitStationReader {
    private TransitStationReader() {}

    private record MethodKey(String name, List<Class<?>> argumentTypes) {}
    private static final class NullArgument {}
    private static final ClassValue<ConcurrentHashMap<MethodKey, Optional<Method>>> METHODS = new ClassValue<>() {
        @Override protected ConcurrentHashMap<MethodKey, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<ConcurrentHashMap<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override protected ConcurrentHashMap<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private record PlatformKey(String id, TransitStation.Position position) {}
    private record MtrStationKey(String id, String name, List<PlatformKey> platforms) {}
    private record MtrCenterSnapshot(Class<?> apiType, Object dimension, List<MtrStationKey> stations,
                                     List<TransitStation> result) {}
    // A single immutable snapshot bounds memory while avoiding repeated center calculations on unchanged refreshes.
    private static volatile MtrCenterSnapshot lastMtrCenterSnapshot;

    public static void resetSnapshot() { lastMtrCenterSnapshot = null; }

    @FunctionalInterface public interface PositionReader {
        TransitStation.Position read(Object position) throws ReflectiveOperationException;
    }
    @FunctionalInterface public interface CreateValidator {
        boolean valid(Object station, Object dimension, Object blockPosition) throws ReflectiveOperationException;
    }

    public static List<TransitStation> readCreate(Object railwayManager, Object stationType,
                                                 PositionReader positions, CreateValidator validator)
            throws ReflectiveOperationException {
        List<TransitStation> result = new ArrayList<>();
        for (Object graph : values(field(railwayManager, "trackNetworks"))) {
            for (Object station : values(call(graph, "getPoints", stationType))) {
                Object blockPosition = call(station, "getBlockEntityPos");
                if (blockPosition == null) continue; // An unattached graph point is not a station.
                Object edge = field(station, "edgeLocation");
                if (edge == null) continue;
                Object node1 = call(edge, "getFirst");
                Object node2 = call(edge, "getSecond");
                if (node1 == null || node2 == null) continue;
                Object dimension = call(station, "getBlockEntityDimension");
                // Create does not synchronize blockEntityDimension to clients. Edge nodes do carry it.
                Object edgeDimension = call(node1, "getDimension");
                if (dimension == null) dimension = edgeDimension;
                if (dimension == null || !dimension.equals(edgeDimension)
                        || !dimension.equals(call(node2, "getDimension"))) continue;
                if (!validator.valid(station, dimension, blockPosition)) continue;
                Object name = field(station, "name");
                if (name instanceof String label && !label.isBlank()) {
                    result.add(new TransitStation(TransitStation.Source.CREATE, dimension,
                            upstreamIdentity(call(station, "getId")), label,
                            positions.read(blockPosition)));
                }
            }
        }
        return result;
    }

    public static List<TransitStation> readMtr3(Class<?> clientData, Object dimension, PositionReader positions)
            throws ReflectiveOperationException {
        Object cache = field(clientData, "DATA_CACHE");
        Object rails = field(clientData, "RAILS");
        Object membership = field(cache, "platformIdToStation");
        if (!(membership instanceof Map<?, ?> stations)) throw new NoSuchFieldException("platformIdToStation map");
        Map<String, StationPlatforms> grouped = new LinkedHashMap<>();
        for (Object platform : values(field(clientData, "PLATFORMS"))) {
            // The station cache uses matching transport mode and the platform node midpoint in its area.
            Object station = stations.get(field(platform, "id"));
            if (station == null || !Boolean.FALSE.equals(call(platform, "isInvalidSavedRail", rails))) continue;
            Object midpoint = call(platform, "getMidPos");
            TransitStation.Position position = positions.read(midpoint);
            if (!Boolean.TRUE.equals(call(station, "inArea", position.x(), position.z()))) continue;
            Object name = field(station, "name");
            if (name instanceof String rawLabel && !rawLabel.isBlank()) {
                String label = formatMtrName(rawLabel);
                if (label.isBlank()) continue;
                addPlatform(grouped, upstreamIdentity(field(station, "id")),
                        upstreamIdentity(field(platform, "id")), label, position);
            }
        }
        return stationCenters(grouped, dimension, clientData);
    }

    public static List<TransitStation> readMtr4(Object clientData, Object dimension, PositionReader positions)
            throws ReflectiveOperationException {
        Map<String, StationPlatforms> grouped = new LinkedHashMap<>();
        for (Object platform : values(field(clientData, "platforms"))) {
            Object station = field(platform, "area");
            if (station == null || !Boolean.FALSE.equals(validateMtr4Rail(platform, clientData))) continue;
            Object midpoint = call(platform, "getMidPosition");
            if (!Boolean.TRUE.equals(call(station, "inArea", midpoint))) continue;
            Object name = call(station, "getName");
            if (name instanceof String rawLabel && !rawLabel.isBlank()) {
                String label = formatMtrName(rawLabel);
                if (label.isBlank()) continue;
                addPlatform(grouped, upstreamIdentity(call(station, "getId")),
                        upstreamIdentity(call(platform, "getId")), label, positions.read(midpoint));
            }
        }
        return stationCenters(grouped, dimension, clientData.getClass());
    }

    private record StationPlatforms(String name, Map<String, TransitStation.Position> platforms) {}

    private static void addPlatform(Map<String, StationPlatforms> grouped, String stationId, String platformId,
                                    String name, TransitStation.Position position) {
        StationPlatforms station = grouped.computeIfAbsent(stationId,
                ignored -> new StationPlatforms(name, new LinkedHashMap<>()));
        // Each upstream platform counts once, even if synchronized collections contain duplicate entries.
        station.platforms().merge(platformId, position, (left, right) -> left.compareTo(right) <= 0 ? left : right);
    }

    private static List<TransitStation> stationCenters(Map<String, StationPlatforms> grouped, Object dimension,
                                                        Class<?> apiType) {
        List<MtrStationKey> key = grouped.entrySet().stream().map(entry -> {
            List<PlatformKey> platforms = entry.getValue().platforms().entrySet().stream()
                    .map(platform -> new PlatformKey(platform.getKey(), platform.getValue()))
                    .sorted(Comparator.comparing(PlatformKey::id).thenComparing(PlatformKey::position))
                    .toList();
            return new MtrStationKey(entry.getKey(), entry.getValue().name(), platforms);
        }).sorted(Comparator.comparing(MtrStationKey::id)).toList();
        MtrCenterSnapshot cached = lastMtrCenterSnapshot;
        if (cached != null && cached.apiType() == apiType && cached.dimension().equals(dimension)
                && cached.stations().equals(key)) return cached.result();

        List<TransitStation> result = new ArrayList<>();
        grouped.forEach((identity, station) -> result.add(new TransitStation(TransitStation.Source.MTR,
                dimension, identity, station.name(), TransitStationCenter.center(station.platforms().values()))));
        List<TransitStation> immutable = List.copyOf(result);
        lastMtrCenterSnapshot = new MtrCenterSnapshot(apiType, dimension, key, immutable);
        return immutable;
    }

    /** Formats MTR's language-separated display name and drops metadata after the first double separator. */
    public static String formatMtrName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        String visible = rawName.split("\\|\\|", 2)[0];
        List<String> names = new ArrayList<>();
        for (String part : visible.split("\\|", -1)) {
            String name = part.trim();
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        if (names.isEmpty()) return "";
        return names.size() == 1 ? names.get(0) : names.get(0) + "（" + String.join("・", names.subList(1, names.size())) + "）";
    }

    private static Object validateMtr4Rail(Object platform, Object clientData) throws ReflectiveOperationException {
        try {
            // MTR 4.0.5 checks the saved platform against this dimension's synchronized Data.
            return call(platform, "isInvalidSavedRail", clientData);
        } catch (NoSuchMethodException noDataArgument) {
            // Newer core releases bind the data internally and expose a no-argument validator.
            return call(platform, "isInvalidSavedRail");
        }
    }

    public static Object field(Object target, String name) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        Field field = FIELDS.get(type).computeIfAbsent(name, key -> {
            try { return Optional.of(type.getField(key)); }
            catch (NoSuchFieldException missing) { return Optional.empty(); }
        }).orElseThrow(() -> new NoSuchFieldException(type.getName() + "." + name));
        return field.get(target instanceof Class<?> ? null : target);
    }

    private static String upstreamIdentity(Object id) throws NoSuchFieldException {
        if (id instanceof UUID uuid) return uuid.toString();
        if (id instanceof Long value) return Long.toUnsignedString(value);
        throw new NoSuchFieldException("Expected upstream station UUID or long identity");
    }

    public static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> clazz ? clazz : target.getClass();
        List<Class<?>> argumentTypes = new ArrayList<>(arguments.length);
        for (Object argument : arguments) argumentTypes.add(argument == null ? NullArgument.class : argument.getClass());
        Method method = METHODS.get(type).computeIfAbsent(new MethodKey(name, List.copyOf(argumentTypes)), key -> {
            for (Method candidate : type.getMethods()) {
                if (!candidate.getName().equals(key.name()) || candidate.getParameterCount() != key.argumentTypes().size()) continue;
                Class<?>[] parameters = candidate.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    Class<?> argumentType = key.argumentTypes().get(i);
                    if (argumentType == NullArgument.class) {
                        if (parameters[i].isPrimitive()) { matches = false; break; }
                    } else if (!boxed(parameters[i]).isAssignableFrom(argumentType)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) return Optional.of(candidate);
            }
            return Optional.empty();
        }).orElseThrow(() -> new NoSuchMethodException(type.getName() + "." + name));
        return method.invoke(target instanceof Class<?> ? null : target, arguments);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Collection<?> values(Object value) throws NoSuchFieldException {
        if (value instanceof Map<?, ?> map) return new ArrayList<>(map.values());
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        throw new NoSuchFieldException("Expected synchronized station collection");
    }
}
