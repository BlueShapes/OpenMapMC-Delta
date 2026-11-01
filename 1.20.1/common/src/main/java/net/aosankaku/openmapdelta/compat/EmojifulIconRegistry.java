// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Optional, reflection-only bridge to Emojiful's live emoji catalog. */
public final class EmojifulIconRegistry {
    private static final String EMOJI_CLASS = "com.hrznstudio.emojiful.api.Emoji";
    private static final String CONSTANTS_CLASS = "com.hrznstudio.emojiful.Constants";
    private static final long REFRESH_INTERVAL_MILLIS = 1_000L;

    private static final ClassValue<EmojiAccess> EMOJI_ACCESS = new ClassValue<>() {
        @Override protected EmojiAccess computeValue(Class<?> type) { return EmojiAccess.create(type); }
    };
    private static final ClassValue<UploadAccess> UPLOAD_ACCESS = new ClassValue<>() {
        @Override protected UploadAccess computeValue(Class<?> type) { return UploadAccess.create(type); }
    };
    private static final ClassValue<PlaceholderAccess> PLACEHOLDER_ACCESS = new ClassValue<>() {
        @Override protected PlaceholderAccess computeValue(Class<?> type) { return PlaceholderAccess.create(type); }
    };
    private static final ClassValue<FieldAccess> CONSTANTS_ACCESS = new ClassValue<>() {
        @Override protected FieldAccess computeValue(Class<?> type) { return field(type, "EMOJI_LIST", true); }
    };

    /** Immutable metadata snapshot. The handle is intentionally opaque and never persisted. */
    public record EmojiIcon(String id, String alias, String name, List<String> aliases,
                            List<String> texts, boolean worldBased, Object handle) {
        public EmojiIcon {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            texts = texts == null ? List.of() : List.copyOf(texts);
        }
    }

    private final BooleanSupplier modPresent;
    private final Supplier<?> liveEmojiList;
    private final LongSupplier clockMillis;
    private final Map<String, EmojiIcon> byId = new HashMap<>();
    private List<EmojiIcon> snapshot = List.of();
    private Object previousSource;
    private long lastRefreshMillis;
    private long revision;
    private boolean refreshed;
    private Class<?> emojiType;

    /** Injectable constructor used by the pure-Java compatibility tests. */
    public EmojifulIconRegistry(BooleanSupplier modPresent, Supplier<?> liveEmojiList, LongSupplier clockMillis) {
        this.modPresent = Objects.requireNonNull(modPresent, "modPresent");
        this.liveEmojiList = Objects.requireNonNull(liveEmojiList, "liveEmojiList");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
    }

    /** Creates the production bridge without linking any Emojiful class into OpenMapMC Delta. */
    public static EmojifulIconRegistry forEmojiful(BooleanSupplier modPresent, LongSupplier clockMillis) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = EmojifulIconRegistry.class.getClassLoader();
        ClassLoader selected = loader;
        return new EmojifulIconRegistry(modPresent, () -> readEmojiList(selected), clockMillis);
    }

    /** Returns the latest immutable metadata snapshot, refreshed at most once per second. */
    public synchronized List<EmojiIcon> snapshot() {
        boolean present;
        try { present = modPresent.getAsBoolean(); }
        catch (RuntimeException | LinkageError unavailablePlatform) { present = false; }
        if (!present) {
            if (!snapshot.isEmpty() || previousSource != null) publish(List.of(), null);
            return snapshot;
        }

        long now = clockMillis.getAsLong();
        if (refreshed && now >= lastRefreshMillis && now - lastRefreshMillis < REFRESH_INTERVAL_MILLIS) return snapshot;
        refreshed = true;
        lastRefreshMillis = now;

        final Object source;
        try { source = liveEmojiList.get(); }
        catch (RuntimeException | LinkageError temporarilyUnavailable) { return snapshot; }
        if (source == null) {
            publish(List.of(), null);
            return snapshot;
        }

        List<Object> handles = stableCopy(source);
        if (handles == null) return snapshot;
        List<EmojiIcon> next = createSnapshot(handles);
        publish(next, source);
        return snapshot;
    }

    public synchronized long revision() {
        snapshot();
        return revision;
    }

    public synchronized EmojiIcon find(String persistedId) {
        snapshot();
        return byId.get(persistedId);
    }

    /** Allows a resource or world lifecycle event to bypass the normal refresh throttle. */
    public synchronized void invalidate() {
        refreshed = false;
        previousSource = null;
    }

    /** Resolves the current static or animated frame; this deliberately is not cached in the entry. */
    public Object resolveFrame(EmojiIcon icon) {
        if (icon == null || icon.handle() == null) return null;
        try { return EMOJI_ACCESS.get(icon.handle().getClass()).getFrame(icon.handle()); }
        catch (RuntimeException | LinkageError unavailableFrame) { return null; }
    }

    /** Returns true for Emojiful's loading/error placeholders, and for a missing frame. */
    public synchronized boolean isPlaceholderFrame(Object frame) {
        if (frame == null) return true;
        snapshot();
        Class<?> type = emojiType;
        if (type == null) type = loadEmojiType();
        if (type == null) return false;
        try { return PLACEHOLDER_ACCESS.get(type).contains(frame); }
        catch (RuntimeException | LinkageError unavailableConstants) { return false; }
    }

    /** Accepts only Emojiful DownloadImageData instances whose upload flag is true. */
    public boolean isUploaded(Object registeredTexture) {
        if (registeredTexture == null) return false;
        try { return UPLOAD_ACCESS.get(registeredTexture.getClass()).isUploaded(registeredTexture); }
        catch (RuntimeException | LinkageError unavailableTextureState) { return false; }
    }

    private void publish(List<EmojiIcon> next, Object source) {
        boolean changed = source != previousSource || !sameIcons(snapshot, next);
        previousSource = source;
        if (!changed) return;
        snapshot = List.copyOf(next);
        byId.clear();
        for (EmojiIcon icon : snapshot) byId.put(icon.id(), icon);
        if (!snapshot.isEmpty()) emojiType = emojiApiClass(snapshot.get(0).handle().getClass());
        else if (source == null) emojiType = null;
        revision++;
    }

    private static boolean sameIcons(List<EmojiIcon> left, List<EmojiIcon> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            EmojiIcon a = left.get(i), b = right.get(i);
            if (!a.id().equals(b.id()) || a.handle() != b.handle() || !a.name().equals(b.name())
                    || !a.aliases().equals(b.aliases()) || !a.texts().equals(b.texts())
                    || a.worldBased() != b.worldBased()) return false;
        }
        return true;
    }

    private static List<Object> stableCopy(Object source) {
        if (!(source instanceof Iterable<?> iterable)) return null;
        try {
            List<Object> copy = new ArrayList<>();
            for (Object value : iterable) if (value != null) copy.add(value);
            return copy;
        } catch (RuntimeException concurrentlyMutated) {
            try {
                if (!(source instanceof Collection<?> collection)) return null;
                Object[] values = collection.toArray();
                List<Object> copy = new ArrayList<>(values.length);
                for (Object value : values) if (value != null) copy.add(value);
                return copy;
            } catch (RuntimeException stillMutating) { return null; }
        }
    }

    private static List<EmojiIcon> createSnapshot(List<Object> handles) {
        List<Candidate> candidates = new ArrayList<>();
        for (Object handle : handles) {
            try {
                EmojiAccess access = EMOJI_ACCESS.get(handle.getClass());
                List<String> aliases = strings(access.aliases(handle));
                String canonical = aliases.stream().filter(value -> !value.isBlank()).findFirst().orElse("");
                if (canonical.isEmpty()) continue;
                String name = access.name(handle);
                List<String> texts = strings(access.texts(handle));
                boolean worldBased = access.worldBased(handle);
                String id = "emoji:emojiful:" + sha256(canonical);
                candidates.add(new Candidate(new EmojiIcon(id, canonical, name, aliases, texts, worldBased, handle), sourceRank(name)));
            } catch (RuntimeException | LinkageError ignoredBrokenEmoji) {
                // One malformed optional-mod entry must not hide the rest of its catalog.
            }
        }
        candidates.sort(Comparator
                .comparing((Candidate candidate) -> !candidate.icon().worldBased())
                .thenComparingInt(Candidate::sourceRank)
                .thenComparing(candidate -> candidate.icon().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(candidate -> candidate.icon().name())
                .thenComparing(candidate -> candidate.icon().alias()));
        Map<String, EmojiIcon> unique = new LinkedHashMap<>();
        for (Candidate candidate : candidates) unique.putIfAbsent(candidate.icon().id(), candidate.icon());
        return List.copyOf(unique.values());
    }

    private static int sourceRank(String name) {
        String value = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("custom_") || value.startsWith("custom-")) return 0;
        if (value.startsWith("twemojis_") || value.startsWith("twemoji_") || value.startsWith("twemojis-")) return 1;
        return 2;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        try {
            for (Object entry : iterable) {
                if (entry instanceof String text && !text.isBlank()) result.add(text.trim());
            }
        } catch (RuntimeException concurrentlyMutated) { return List.of(); }
        return List.copyOf(result);
    }

    private static String sha256(String alias) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(alias.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private synchronized Class<?> loadEmojiType() {
        if (emojiType != null) return emojiType;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = EmojifulIconRegistry.class.getClassLoader();
            emojiType = Class.forName(EMOJI_CLASS, false, loader);
            return emojiType;
        } catch (ClassNotFoundException | LinkageError unavailable) { return null; }
    }

    private static Class<?> emojiApiClass(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (EMOJI_CLASS.equals(current.getName())) return current;
        }
        return type;
    }

    private static Object readEmojiList(ClassLoader loader) {
        try {
            Class<?> constants = Class.forName(CONSTANTS_CLASS, true, loader);
            return CONSTANTS_ACCESS.get(constants).get(null);
        } catch (ClassNotFoundException | IllegalAccessException | LinkageError unavailable) { return null; }
    }

    private record Candidate(EmojiIcon icon, int sourceRank) {}

    private record FieldAccess(Field field) {
        static FieldAccess missing() { return new FieldAccess(null); }
        Object get(Object owner) throws IllegalAccessException { return field == null ? null : field.get(owner); }
    }

    private record EmojiAccess(FieldAccess name, FieldAccess aliases, FieldAccess texts,
                               FieldAccess worldBased, Method frame, Method worldBasedMethod) {
        static EmojiAccess create(Class<?> type) {
            return new EmojiAccess(field(type, "name", false), field(type, "strings", false),
                    field(type, "texts", false), field(type, "worldBased", false),
                    method(type, "getResourceLocationForBinding"), method(type, "worldBased"));
        }
        String name(Object owner) {
            try { Object value = name.get(owner); return value instanceof String text ? text : ""; }
            catch (IllegalAccessException unavailable) { return ""; }
        }
        Object aliases(Object owner) {
            try { return aliases.get(owner); }
            catch (IllegalAccessException unavailable) { return null; }
        }
        Object texts(Object owner) {
            try { return texts.get(owner); }
            catch (IllegalAccessException unavailable) { return null; }
        }
        boolean worldBased(Object owner) {
            try {
                if (worldBasedMethod != null) return Boolean.TRUE.equals(worldBasedMethod.invoke(owner));
                return Boolean.TRUE.equals(worldBased.get(owner));
            } catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }
        Object getFrame(Object owner) {
            if (frame == null) return null;
            try { return frame.invoke(owner); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
        }
    }

    private record UploadAccess(Field field, boolean acceptedOwner) {
        static UploadAccess create(Class<?> type) {
            Field found = null;
            boolean validOwner = false;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                String name = current.getName();
                if (name.equals("com.hrznstudio.emojiful.api.Emoji$DownloadImageData")
                        || name.equals("com.hrznstudio.emojiful.api.EmojiFromTwitmoji$DownloadImageData")) {
                    validOwner = true;
                    try { found = current.getDeclaredField("textureUploaded"); }
                    catch (NoSuchFieldException absent) { /* try the next supported base class */ }
                    if (found != null) break;
                }
            }
            if (found != null) {
                try { found.setAccessible(true); }
                catch (RuntimeException denied) { return new UploadAccess(null, validOwner); }
            }
            return new UploadAccess(found, validOwner);
        }
        boolean isUploaded(Object texture) {
            if (!acceptedOwner || field == null) return false;
            try { return field.getBoolean(texture); }
            catch (IllegalAccessException | IllegalArgumentException unavailable) { return false; }
        }
    }

    private record PlaceholderAccess(List<Object> values) {
        static PlaceholderAccess create(Class<?> type) {
            List<Object> values = new ArrayList<>(3);
            for (String name : List.of("loading_texture", "noSignal_texture", "error_texture")) {
                try {
                    Field field = type.getField(name);
                    Object value = field.get(null);
                    if (value != null) values.add(value);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            return new PlaceholderAccess(List.copyOf(values));
        }
        boolean contains(Object value) { return values.contains(value); }
    }

    private static FieldAccess field(Class<?> type, String name, boolean staticField) {
        try {
            Field field = type.getField(name);
            if (staticField && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) return FieldAccess.missing();
            return new FieldAccess(field);
        } catch (NoSuchFieldException absent) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (staticField && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) return FieldAccess.missing();
                    field.setAccessible(true);
                    return new FieldAccess(field);
                } catch (NoSuchFieldException ignored) { }
                catch (RuntimeException denied) { return FieldAccess.missing(); }
            }
            return FieldAccess.missing();
        }
    }

    private static Method method(Class<?> type, String name) {
        try { return type.getMethod(name); }
        catch (NoSuchMethodException absent) { return null; }
    }
}
