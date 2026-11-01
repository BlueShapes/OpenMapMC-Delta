// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class Marker {
    public static final int UNKNOWN_Y = Integer.MIN_VALUE;

    private final UUID id;
    private final MarkerType type;
    private ResourceKey<Level> dimension;
    private int x;
    private int y;
    private int z;
    private String label;
    private int color;
    private boolean visible = true;
    private boolean favorite = false;
    private String importedWorld;
    private String iconId = "";
    private String category = "";
    private UUID trackedPlayerId;
    private UUID ownerId;
    private String ownerName = "";
    private net.aosankaku.openmapdelta.death.DeathPointData deathPoint;

    public Marker(UUID id, MarkerType type, ResourceKey<Level> dimension, int x, int z, String label) {
        this(id, type, dimension, x, UNKNOWN_Y, z, label, type.defaultColor);
    }

    public Marker(UUID id, MarkerType type, ResourceKey<Level> dimension, int x, int y, int z,
                  String label, int color) {
        this.id = id;
        this.type = type;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
        this.color = color;
        if (type == MarkerType.BED) setIconId("item:minecraft:red_bed");
    }

    public String getIconId() { return deathPoint != null && deathPoint.color() == net.aosankaku.openmapdelta.death.DeathPointData.BLACK
            ? net.aosankaku.openmapdelta.death.DeathPointData.ICON_BLACK : iconId; }
    public void setIconId(String id) { iconId = MarkerIconId.normalize(id); }
    public String getCategory() { return category; }
    public static String normalizeCategory(String category) { return category == null ? "" : category.trim(); }
    public void setCategory(String category) { this.category = normalizeCategory(category); }

    public UUID getId() { return id; }
    public MarkerType getType() { return type; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public void setDimension(ResourceKey<Level> dimension) { this.dimension = java.util.Objects.requireNonNull(dimension); }
    /** The notified player to follow, or null for a fixed coordinate notice. */
    public UUID getTrackedPlayerId() { return trackedPlayerId; }
    public void setTrackedPlayerId(UUID id) { trackedPlayerId = id; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwner(UUID id, String name) {
        ownerId = id;
        ownerName = name == null ? "" : name;
    }
    public boolean isShared() { return type == MarkerType.SHARED_WAYPOINT || ownerId != null; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public net.aosankaku.openmapdelta.death.DeathPointData getDeathPoint() { return deathPoint; }
    public void setDeathPoint(net.aosankaku.openmapdelta.death.DeathPointData data) {
        deathPoint = data;
        if (data != null) data.motion(x, y, z);
    }
    public String getDisplayLabel() { return deathPoint == null ? label : deathPoint.displayLabel(label); }
    public int getColor() { return deathPoint == null ? color : deathPoint.color(); }
    public void setColor(int color) { this.color = color; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public boolean hasKnownY() { return y != UNKNOWN_Y; }
    public String getImportedWorld() { return importedWorld; }
    public void setImportedWorld(String world) { importedWorld = world; }
}
