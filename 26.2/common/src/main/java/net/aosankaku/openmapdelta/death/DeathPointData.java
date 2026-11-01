// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.death;

import java.util.UUID;

/** Persistent death state. All times are world game ticks, so pausing does not consume the timer. */
public final class DeathPointData {
    public static final long LIFETIME_TICKS = 5 * 60 * 20;
    public static final int RED = 0xFFFF0000;
    public static final int BLACK = 0xFF000000;
    public static final String ICON = "symbol:openmapdelta:death";
    public static final String ICON_BLACK = "symbol:openmapdelta:death_black";
    private boolean smart;
    private long deathTick;
    private long timerStartTick = -1;
    private boolean expired;
    private boolean collectedByOthers;
    private long blackAtTick = -1;
    private boolean cached;
    private boolean restored;
    private boolean keepInventoryKnown;
    private boolean keepInventory;
    private boolean observedItems;
    private boolean observedGrave;
    private boolean recoveryConfirmed;
    private UUID serverId;
    private UUID playerId;
    private boolean localDeathObserved;
    private DeathPointMotion motion;
    private UUID followedItem;
    private UUID followedGrave;
    private int[] graveBlock;
    private java.util.Set<UUID> existingGraves = new java.util.HashSet<>();
    private java.util.Set<UUID> existingItems = new java.util.HashSet<>();
    private java.util.Map<UUID, Integer> existingItemCounts = new java.util.HashMap<>();
    private java.util.Set<UUID> observedDeathItems = new java.util.HashSet<>();
    // The presence flag keeps older persisted data with absent primitive fields in fallback mode.
    private boolean itemTimerKnown;
    private long itemTimerRemainingTicks = -1;
    private long itemTimerSampleGameTick;
    private boolean itemTimerTicking;
    private boolean itemTimerExpired;
    private transient long displayRemainingTicks;

    public DeathPointData(boolean smart, long deathTick, boolean timer, boolean cached) {
        this.smart = smart;
        this.deathTick = deathTick;
        this.timerStartTick = timer ? deathTick : -1;
        this.cached = cached;
        update(deathTick);
    }
    public boolean isSmart() { return smart; }
    public void makeBasic() {
        smart = false;
        timerStartTick = -1;
        expired = false;
        collectedByOthers = false;
        blackAtTick = -1;
        clearItemTimer();
        displayRemainingTicks = 0;
    }
    public long deathTick() { return deathTick; }
    public DeathPointMotion motion(int x, int y, int z) {
        if (motion == null) motion = new DeathPointMotion(x, y, z);
        return motion;
    }
    public void setKeepInventory(boolean keep) { keepInventoryKnown = true; keepInventory = keep; }
    /** Old cached/restored points predate explicit gamerule metadata. */
    public boolean keepsInventory() {
        return keepInventoryKnown ? keepInventory : cached || restored && timerStartTick < 0 && !observedItems;
    }
    /** Countdown suppression by a grave mod must not suppress falling recovery locations. */
    public boolean canPredictItemMotion() { return smart && (!keepsInventory() || observedItems); }
    public boolean resetUnobservedMotion(long tick) {
        return motion != null && keepsInventory() && !observedGrave && !observedItems
                && motion.resetToOrigin(tick);
    }
    public UUID followedItem() { return followedItem; }
    public void setExistingGraves(java.util.Collection<UUID> ids) { existingGraves = new java.util.HashSet<>(ids); }
    public boolean isNewGrave(UUID id) { return existingGraves == null || !existingGraves.contains(id); }
    public UUID followedGrave() { return followedGrave; }
    public void followGrave(UUID id) { followedGrave = id; }
    public void followItem(UUID id) { followedItem = id; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached && !restored; }
    public void restore() { cached = false; restored = true; }
    public boolean isExpired() { return expired; }
    public boolean isCollectedByOthers() { return collectedByOthers; }
    public boolean observedItems() { return observedItems; }
    public boolean observedGrave() { return observedGrave; }
    public boolean recoveryConfirmed() { return recoveryConfirmed; }
    public void confirmRecovery() { recoveryConfirmed = true; }
    public UUID serverId() { return serverId; }
    public UUID playerId() { return playerId; }
    public void setPlayerId(UUID id) { playerId = id; }
    public boolean localDeathObserved() { return localDeathObserved; }
    public void observeLocalDeath() { localDeathObserved = true; }
    public void setServerId(UUID id) { serverId = id; }
    public void observeGrave() { observedGrave = true; }
    public int[] graveBlock() { return graveBlock != null && graveBlock.length == 3 ? graveBlock.clone() : null; }
    public boolean observeGraveBlock(int x, int y, int z) {
        observedGrave = true;
        int[] position = {x, y, z};
        boolean changed = !java.util.Arrays.equals(graveBlock, position);
        graveBlock = position;
        return changed;
    }
    public void clearGraveBlock() { graveBlock = null; }
    public void setExistingItems(java.util.Collection<UUID> ids) { existingItems = new java.util.HashSet<>(ids); }
    public boolean isExistingItem(UUID id) { return existingItems != null && existingItems.contains(id); }
    public void setExistingItemCounts(java.util.Map<UUID, Integer> counts) {
        existingItemCounts = new java.util.HashMap<>(counts);
        setExistingItems(counts.keySet());
    }
    public boolean isDeathItem(UUID id, int count) {
        if (observedDeathItems != null && observedDeathItems.contains(id)) return count > 0;
        if (!isExistingItem(id)) return true;
        Integer baseline = existingItemCounts == null ? null : existingItemCounts.get(id);
        return baseline != null && count > baseline;
    }
    public boolean observeDeathItem(UUID id) {
        if (observedDeathItems == null) observedDeathItems = new java.util.HashSet<>();
        return observedDeathItems.add(id);
    }
    public void observeItems() {
        observedItems = true;
        if (smart && timerStartTick < 0 && !collectedByOthers) timerStartTick = deathTick;
    }
    public void setTimerExpected(boolean timer) {
        if (!smart || collectedByOthers) return;
        if (timer || observedItems) timerStartTick = deathTick;
        else { timerStartTick = -1; expired = false; blackAtTick = -1; displayRemainingTicks = 0; }
    }
    public void collectByOthers(long worldTick) {
        collectedByOthers = true;
        displayRemainingTicks = 0;
        if (blackAtTick < 0) blackAtTick = worldTick;
    }
    public void expire() {
        expireAt(deathTick + LIFETIME_TICKS);
    }
    private void expireAt(long worldTick) {
        expired = true;
        if (blackAtTick < 0) blackAtTick = worldTick;
        displayRemainingTicks = 0;
    }
    /** A server sample overrides the fallback timer, including a known infinite resource lifetime. */
    public void setItemTimer(long remaining, boolean ticking, boolean resourceExpired, long now) {
        if (remaining < -1 || now < 0) throw new IllegalArgumentException("Invalid authoritative item timer");
        if (smart && expired && !resourceExpired && remaining != 0) {
            // New live loot can correct a fallback expiry or revive an existing point after confirmed loss.
            // A manually deleted point is still protected by its immutable deletion receipt.
            expired = false;
            if (!collectedByOthers) blackAtTick = -1;
        }
        itemTimerKnown = true;
        itemTimerRemainingTicks = remaining;
        itemTimerSampleGameTick = now;
        itemTimerTicking = ticking && remaining >= 0;
        itemTimerExpired = resourceExpired;
        if (smart && resourceExpired) expireAt(now);
        update(now);
    }
    /** Returns to local drop observations when the connected server provides no authoritative timer. */
    public void clearItemTimer() {
        itemTimerKnown = false;
        itemTimerRemainingTicks = -1;
        itemTimerSampleGameTick = 0;
        itemTimerTicking = false;
        itemTimerExpired = false;
    }
    public boolean hasItemTimer() { return itemTimerKnown; }
    public long itemTimerRemainingTicks(long now) {
        if (!itemTimerKnown || itemTimerRemainingTicks < 0) return -1;
        long predictedTicks = itemTimerTicking ? Math.min(20, Math.max(0, now - itemTimerSampleGameTick)) : 0;
        return Math.max(0, itemTimerRemainingTicks - predictedTicks);
    }
    public boolean shouldRemove(long worldTick, int retentionSeconds) {
        return smart && !cached && retentionSeconds > 0 && blackAtTick >= 0
                && worldTick >= blackAtTick && worldTick - blackAtTick >= retentionSeconds * 20L;
    }
    /** Returns true only for a newly expired marker, avoiding per-tick disk writes. */
    public boolean update(long worldTick) {
        boolean before = expired;
        if (smart && itemTimerKnown && itemTimerExpired && !expired) expireAt(itemTimerSampleGameTick);
        displayRemainingTicks = smart && !expired && !collectedByOthers
                ? itemTimerKnown ? Math.max(0, itemTimerRemainingTicks(worldTick))
                : timerStartTick >= 0 ? Math.max(0, LIFETIME_TICKS - Math.max(0, worldTick - timerStartTick)) : 0
                : 0;
        if (smart && !itemTimerKnown && timerStartTick >= 0 && worldTick >= timerStartTick
                && worldTick - timerStartTick >= LIFETIME_TICKS) expire();
        return before != expired;
    }
    public int color() { return smart && (expired || collectedByOthers) ? BLACK : RED; }
    public String displayLabel(String base) {
        if (!smart || (itemTimerKnown ? itemTimerRemainingTicks < 0 : timerStartTick < 0)
                || expired || collectedByOthers) return base;
        long seconds = (displayRemainingTicks + 19) / 20;
        return base + " [" + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60) + "]";
    }
}
