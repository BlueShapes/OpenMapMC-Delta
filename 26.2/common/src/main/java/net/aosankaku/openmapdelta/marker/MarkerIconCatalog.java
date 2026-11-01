// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.aosankaku.openmapdelta.OpenMapDeltaCommon;
import net.aosankaku.openmapdelta.platform.Services;
import net.aosankaku.openmapdelta.compat.EmojifulIconRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Cached item, mob, and built-in transit icon catalog. */
public final class MarkerIconCatalog implements ResourceManagerReloadListener {
    public record Texture(Identifier location, int u, int v, int width, int height,
                          int textureWidth, int textureHeight) {}

    /** Items use their baked model; texture is only populated for cropped sprites and symbols. */
    public record Entry(String id, Component label, ItemStack item, Texture texture,
                        MarkerIconSearchQuery.Metadata metadata) {}

    private static final String MTR_SYMBOL = "symbol:odysseymap:mtr_station";
    private static final String CREATE_SYMBOL = "symbol:odysseymap:create_station";
    private static final String DEATH_SYMBOL = "symbol:openmapdelta:death";
    private static final String DEATH_BLACK_SYMBOL = "symbol:openmapdelta:death_black";
    private static final EmojifulIconRegistry EMOJIFUL = EmojifulIconRegistry.forEmojiful(
            () -> Services.PLATFORM.isModLoaded("emojiful"), System::currentTimeMillis);
    private static final Identifier MTR_TEXTURE = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "textures/marker/mtr_station.png");
    private static final Identifier CREATE_TEXTURE = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "textures/marker/create_station.png");
    private static final Identifier DEATH_TEXTURE = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "textures/marker/death.png");
    private static final Identifier DEATH_BLACK_TEXTURE = Identifier.fromNamespaceAndPath(OpenMapDeltaCommon.MOD_ID, "textures/marker/death_black.png");
    private static final Map<String, Entry> ITEMS = new LinkedHashMap<>();
    private static final Map<String, Entry> SPECIALS = new LinkedHashMap<>();
    private static final Map<String, Entry> EMOJIS = new LinkedHashMap<>();
    private static final MarkerIconCatalog RESOURCE_LISTENER = new MarkerIconCatalog();
    private static final Set<String> UNRESOLVED_ITEMS = new HashSet<>();
    private static List<Entry> sortedEntries = List.of();
    private static ResourceManager loadedItemManager;
    private static ResourceManager loadedSpecialManager;
    private static ResourceManager reloadListenerManager;
    private static volatile long resourceGeneration;
    private static long loadedEmojiRevision = Long.MIN_VALUE;

    private MarkerIconCatalog() {}

    /** Builds the complete selectable list on demand when the player opens the picker. */
    public static List<Entry> entries() {
        ensureSpecialsLoaded();
        syncEmojis();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (loadedItemManager != resources) {
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) itemEntry(item, id);
            }
            loadedItemManager = resources;
        }
        if (!sortedEntries.isEmpty()) return sortedEntries;
        List<Entry> sorted = new ArrayList<>(ITEMS.values());
        sorted.addAll(SPECIALS.values());
        sorted.addAll(EMOJIS.values());
        sorted.sort(Comparator.comparing((Entry entry) -> entry.label().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id));
        sorted.add(0, new Entry("", Component.translatable("gui.odysseymap.icon_none"), ItemStack.EMPTY, null,
                new MarkerIconSearchQuery.Metadata("", "", "", "", List.of(), List.of())));
        sortedEntries = List.copyOf(sorted);
        return sortedEntries;
    }

    /** Looks up markers without enumerating the item registry. */
    public static Entry find(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("emoji:")) {
            if (!id.equals(MarkerIconId.normalize(id))) return null;
            ensureReloadListenerRegistered();
            syncEmojis();
            return EMOJIS.get(id);
        }
        if (id.startsWith("symbol:") || id.startsWith("mob:")) {
            ensureSpecialsLoaded();
            return SPECIALS.get(id);
        }
        if (!id.startsWith("item:")) return null;
        ensureReloadListenerRegistered();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (loadedSpecialManager != resources && loadedSpecialManager != null) invalidate(resources);
        if (loadedItemManager != null && loadedItemManager != resources) invalidate(resources);
        Entry cached = ITEMS.get(id);
        if (cached != null || UNRESOLVED_ITEMS.contains(id)) return cached;
        try {
            Identifier itemId = Identifier.tryParse(id.substring("item:".length()));
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                UNRESOLVED_ITEMS.add(id);
                return null;
            }
            return itemEntry(BuiltInRegistries.ITEM.getValue(itemId), itemId);
        } catch (RuntimeException malformedOrMissing) {
            UNRESOLVED_ITEMS.add(id);
            return null;
        }
    }

    /** True while the optional integration is installed, including while its list is loading. */
    public static boolean emojifulAvailable() {
        try { return Services.PLATFORM.isModLoaded("emojiful"); }
        catch (RuntimeException | LinkageError unavailablePlatform) { return false; }
    }

    /** Snapshot revision for lightweight picker refresh checks. */
    public static long emojifulRevision() { return EMOJIFUL.revision(); }

    /** Forces the next lookup to refresh after a world or resource lifecycle change. */
    public static void invalidateEmojiful() {
        EMOJIFUL.invalidate();
        loadedEmojiRevision = Long.MIN_VALUE;
        sortedEntries = List.of();
    }

    /** Resolves animated frames on demand and returns null until Emojiful's texture is uploaded. */
    public static Texture resolveTexture(Entry entry) {
        if (entry == null) return null;
        if (!entry.id().startsWith("emoji:")) return entry.texture();
        try {
            EmojifulIconRegistry.EmojiIcon emoji = EMOJIFUL.find(entry.id());
            if (emoji == null) return null;
            Object frame = EMOJIFUL.resolveFrame(emoji);
            if (EMOJIFUL.isPlaceholderFrame(frame)) return null;
            Identifier location = Identifier.tryParse(String.valueOf(frame));
            if (location == null) return null;
            Object registered = Minecraft.getInstance().getTextureManager().getTexture(location);
            if (!EMOJIFUL.isUploaded(registered)) return null;
            return new Texture(location, 0, 0, 16, 16, 16, 16);
        } catch (RuntimeException | LinkageError unavailableTexture) { return null; }
    }

    private static void syncEmojis() {
        long revision = EMOJIFUL.revision();
        if (loadedEmojiRevision == revision) return;
        EMOJIS.clear();
        for (EmojifulIconRegistry.EmojiIcon emoji : EMOJIFUL.snapshot()) {
            String readable = readableEmojiName(emoji);
            String searchableAliases = String.join(" ", emoji.aliases());
            List<String> tooltip = new ArrayList<>(emoji.texts());
            if (!emoji.aliases().isEmpty()) tooltip.add("Aliases: " + searchableAliases);
            MarkerIconSearchQuery.Metadata metadata = new MarkerIconSearchQuery.Metadata(
                    readable + " " + searchableAliases, emoji.id() + " " + searchableAliases,
                    "emojiful", "Emojiful", List.of(), tooltip);
            EMOJIS.put(emoji.id(), new Entry(emoji.id(), Component.literal(readable), ItemStack.EMPTY, null, metadata));
        }
        loadedEmojiRevision = revision;
        sortedEntries = List.of();
    }

    private static String readableEmojiName(EmojifulIconRegistry.EmojiIcon emoji) {
        String alias = emoji.alias();
        while (alias.startsWith(":")) alias = alias.substring(1);
        while (alias.endsWith(":")) alias = alias.substring(0, alias.length() - 1);
        alias = alias.replace('_', ' ').trim();
        if (!alias.isEmpty()) return alias;
        String name = emoji.name().replaceFirst("(?i)^(twemojis?|custom)[_-]", "").replace('_', ' ').trim();
        return name.isEmpty() ? "emoji" : name;
    }

    /** Monotonically changes whenever client resources are reloaded. */
    public static long resourceGeneration() { return resourceGeneration; }

    private static void ensureSpecialsLoaded() {
        ensureReloadListenerRegistered();
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (loadedSpecialManager == resources) return;
        if (loadedSpecialManager != null) invalidate(resources);
        SPECIALS.clear();
        EMOJIS.clear();
        loadedEmojiRevision = Long.MIN_VALUE;
        addMobs();
        SPECIALS.entrySet().removeIf(entry -> resources.getResource(entry.getValue().texture().location()).isEmpty());
        addSymbol(DEATH_BLACK_SYMBOL, "gui.odysseymap.icons_death", DEATH_BLACK_TEXTURE, resources);
        addSymbol(DEATH_SYMBOL, "gui.odysseymap.icons_death", DEATH_TEXTURE, resources);
        addSymbol(MTR_SYMBOL, "gui.odysseymap.icons_mtr_station", MTR_TEXTURE, resources);
        addSymbol(CREATE_SYMBOL, "gui.odysseymap.icons_create_station", CREATE_TEXTURE, resources);
        loadedSpecialManager = resources;
        sortedEntries = List.of();
    }

    private static void ensureReloadListenerRegistered() {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        if (reloadListenerManager == resources) return;
        if (resources instanceof ReloadableResourceManager reloadable) {
            reloadable.registerReloadListener(RESOURCE_LISTENER);
            reloadListenerManager = resources;
        }
    }

    private static Entry itemEntry(Item item, Identifier itemId) {
        String id = "item:" + itemId;
        Entry existing = ITEMS.get(id);
        if (existing != null) return existing;
        try {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty()) {
                UNRESOLVED_ITEMS.add(id);
                return null;
            }
            String name = safeName(stack, itemId);
            String modId = itemId.getNamespace();
            String modName;
            try { modName = Services.PLATFORM.getModName(modId); }
            catch (RuntimeException unavailableMetadata) { modName = modId; }
            List<String> tags = safeTags(stack);
            List<String> tooltip = safeTooltip(stack, Minecraft.getInstance(), name);
            MarkerIconSearchQuery.Metadata metadata = new MarkerIconSearchQuery.Metadata(
                    name, itemId.toString(), modId, modName, tags, tooltip);
            Entry entry = new Entry(id, Component.literal(name), stack, null, metadata);
            ITEMS.put(id, entry);
            UNRESOLVED_ITEMS.remove(id);
            sortedEntries = List.of();
            return entry;
        } catch (RuntimeException brokenModItem) {
            UNRESOLVED_ITEMS.add(id);
            return null;
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) { invalidate(manager); }

    private static void invalidate(ResourceManager ignored) {
        EMOJIFUL.invalidate();
        ITEMS.clear();
        SPECIALS.clear();
        EMOJIS.clear();
        loadedEmojiRevision = Long.MIN_VALUE;
        UNRESOLVED_ITEMS.clear();
        sortedEntries = List.of();
        loadedItemManager = null;
        loadedSpecialManager = null;
        resourceGeneration++;
    }

    private static String safeName(ItemStack stack, Identifier id) {
        try { return stack.getHoverName().getString(); }
        catch (RuntimeException brokenName) { return id.toString(); }
    }

    private static List<String> safeTags(ItemStack stack) {
        try { return stack.typeHolder().tags().map(tag -> tag.location().toString()).toList(); }
        catch (RuntimeException brokenTags) { return List.of(); }
    }

    private static List<String> safeTooltip(ItemStack stack, Minecraft mc, String name) {
        try {
            return stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
                    .stream().map(Component::getString).toList();
        } catch (RuntimeException brokenTooltip) {
            return List.of(name);
        }
    }

    private static void addMobs() {
        mob("creeper", "creeper/creeper", 8, 8, 8, 8, 64, 32);
        mob("zombie", "zombie/zombie", 8, 8, 8, 8, 64, 64);
        mob("skeleton", "skeleton/skeleton", 8, 8, 8, 8, 64, 32);
        mob("wither_skeleton", "skeleton/wither_skeleton", 8, 8, 8, 8, 64, 32);
        mob("stray", "skeleton/stray", 8, 8, 8, 8, 64, 32);
        mob("husk", "zombie/husk", 8, 8, 8, 8, 64, 64);
        mob("drowned", "zombie/drowned", 8, 8, 8, 8, 64, 64);
        mob("enderman", "enderman/enderman", 8, 8, 8, 8, 64, 32);
        mob("pig", "pig/pig_temperate", 8, 8, 8, 8, 64, 64);
        mob("cow", "cow/cow_temperate", 6, 6, 8, 8, 64, 64);
        mob("mooshroom", "cow/mooshroom_red", 6, 6, 8, 8, 64, 64);
        mob("sheep", "sheep/sheep", 8, 8, 6, 6, 64, 32);
        mob("chicken", "chicken/chicken_temperate", 3, 3, 4, 6, 64, 32);
        mob("wolf", "wolf/wolf", 4, 4, 6, 6, 64, 32);
        mob("cat", "cat/cat_tabby", 5, 5, 6, 4, 64, 32);
        mob("fox", "fox/fox", 7, 11, 8, 6, 48, 32);
        mob("bee", "bee/bee", 10, 10, 7, 7, 64, 64);
        mob("ghast", "ghast/ghast", 32, 32, 32, 32, 128, 64);
        mob("blaze", "blaze/blaze", 8, 8, 8, 8, 64, 32);
        mob("spider", "spider/spider", 40, 12, 8, 8, 64, 32);
        mob("cave_spider", "spider/cave_spider", 40, 12, 8, 8, 64, 32);
        mob("villager", "villager/villager", 8, 8, 8, 10, 64, 64);
        mob("iron_golem", "iron_golem/iron_golem", 8, 8, 8, 10, 128, 128);
        // Readable fallbacks while the variant-aware live model image is being captured.
        mobSprite("cod", "minecraft:textures/item/cod.png");
        mobSprite("salmon", "minecraft:textures/item/salmon.png");
        mobSprite("pufferfish", "minecraft:textures/item/pufferfish.png");
        mobSprite("tropical_fish", "minecraft:textures/item/tropical_fish.png");
    }

    private static void mob(String name, String texture, int u, int v, int width, int height, int tw, int th) {
        Identifier location = Identifier.parse("minecraft:textures/entity/" + texture + ".png");
        mobEntry(name, new Texture(location, u, v, width, height, tw, th));
    }

    private static void mobSprite(String name, String location) {
        Texture texture = pngTexture(Minecraft.getInstance().getResourceManager(), Identifier.parse(location));
        if (texture != null) mobEntry(name, texture);
    }

    private static void mobEntry(String name, Texture texture) {
        String id = "mob:minecraft:" + name;
        Component label = Component.translatable("entity.minecraft." + name);
        String modName;
        try { modName = Services.PLATFORM.getModName("minecraft"); }
        catch (RuntimeException unavailableMetadata) { modName = "minecraft"; }
        MarkerIconSearchQuery.Metadata metadata = new MarkerIconSearchQuery.Metadata(
                label.getString(), "minecraft:" + name, "minecraft", modName, List.of(), List.of());
        SPECIALS.put(id, new Entry(id, label, ItemStack.EMPTY, texture, metadata));
    }

    private static void addSymbol(String id, String translationKey, Identifier location,
                                  ResourceManager resources) {
        Texture texture = pngTexture(resources, location);
        if (texture == null) return;
        Component label = Component.translatable(translationKey);
        String symbolResource = id.substring("symbol:".length());
        int namespaceSeparator = symbolResource.indexOf(':');
        String modId = namespaceSeparator > 0 ? symbolResource.substring(0, namespaceSeparator) : "";
        MarkerIconSearchQuery.Metadata metadata = new MarkerIconSearchQuery.Metadata(
                label.getString(), symbolResource, modId, "OpenMap Delta", List.of(), List.of());
        SPECIALS.put(id, new Entry(id, label, ItemStack.EMPTY, texture, metadata));
    }

    private static Texture pngTexture(ResourceManager resources, Identifier location) {
        try {
            Resource resource = resources.getResource(location).orElse(null);
            if (resource == null) return null;
            try (InputStream raw = resource.open(); DataInputStream png = new DataInputStream(raw)) {
                if (png.readInt() != 0x89504E47 || png.readInt() != 0x0D0A1A0A) return null;
                png.readInt();
                if (png.readInt() != 0x49484452) return null;
                int width = png.readInt();
                int height = png.readInt();
                if (width <= 0 || height <= 0) return null;
                return new Texture(location, 0, 0, width, height, width, height);
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
