package net.aosankaku.openmapdelta.marker;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Boundary fixture used only to construct the real editor's edit/station modes. */
public final class Marker {
    private final UUID id;
    private final ResourceKey<Level> dimension;
    private final int x, y, z, color;
    private final String label, iconId, category;
    private final MarkerType type;
    private final boolean shared;

    public Marker(UUID id, ResourceKey<Level> dimension, int x, int y, int z, String label, int color,
                  String iconId, String category, MarkerType type, boolean shared) {
        this.id=id; this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.label=label; this.color=color;
        this.iconId=iconId; this.category=category; this.type=type; this.shared=shared;
    }
    public UUID getId() { return id; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean hasKnownY() { return true; }
    public int getZ() { return z; }
    public String getLabel() { return label; }
    public int getColor() { return color; }
    public String getIconId() { return iconId; }
    public String getCategory() { return category; }
    public MarkerType getType() { return type; }
    public boolean isShared() { return shared; }
}
