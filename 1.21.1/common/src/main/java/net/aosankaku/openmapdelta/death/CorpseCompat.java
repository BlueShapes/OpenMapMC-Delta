// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Optional Corpse integration without a loader or relocated Corelib dependency. */
public final class CorpseCompat {
    private static final String ENTITY = "de.maxhenkel.corpse.entities.CorpseEntity";
    private static final String[] CONFIG_HOLDERS = {"de.maxhenkel.corpse.Main", "de.maxhenkel.corpse.CorpseMod"};
    private CorpseCompat() {}

    public record Snapshot(int age, boolean empty, int forceDespawnTicks, List<?> items) {
        public Snapshot { items = List.copyOf(items); }
        public long remainingForceTicks() {
            return forceDespawnTicks <= 0 ? -1 : Math.max(1, (long) forceDespawnTicks - age + 1);
        }
    }

    public static boolean isCorpse(Object entity) {
        if (entity == null) return false;
        for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals(ENTITY)) return true;
        return false;
    }

    public static UUID owner(Object entity) {
        if (entity == null) return null;
        try {
            for (String name : List.of("getPlayerUuid", "getCorpseUUID")) {
                try {
                    Object value = call(entity, name);
                    if (value instanceof Optional<?> optional) value = optional.orElse(null);
                    if (value instanceof UUID id) return id;
                } catch (ReflectiveOperationException unavailable) { /* Try the older API. */ }
            }
            Object value = call(call(entity, "getDeath"), "getPlayerUUID");
            return value instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    public static Snapshot snapshot(Object entity) {
        for (String name : CONFIG_HOLDERS) {
            try {
                Class<?> holder = Class.forName(name, false, CorpseCompat.class.getClassLoader());
                Object config = holder.getField("SERVER_CONFIG").get(null);
                Snapshot snapshot = read(entity, config);
                if (snapshot != null) return snapshot;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { /* Mod absent or API changed. */ }
        }
        return null;
    }

    static Snapshot read(Object entity, Object config) {
        try {
            // SERVER config is synchronized by Forge/NeoForge. Never use unloaded/default values as server truth.
            if (!Boolean.TRUE.equals(call(call(config, "getConfigSpec"), "isLoaded"))) return null;
            Object forceValue = field(config.getClass(), "corpseForceDespawnTime").get(config);
            int force = ((Number) call(forceValue, "get")).intValue();
            int age = field(entity.getClass(), "age").getInt(entity);
            boolean empty = Boolean.TRUE.equals(call(entity, "isEmpty"));
            Object items = call(call(entity, "getDeath"), "getAllItems");
            if (!(items instanceof List<?> list)) return null;
            return new Snapshot(age, empty, force, list);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        for (; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) { /* Look on the superclass. */ }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
