package net.aosankaku.openmapdelta.marker;

import java.util.UUID;

public final class Marker {
    private final UUID id;
    private final MarkerType type;
    private final String label;
    private String category = "";
    private boolean favorite, visible = true;
    public Marker(String id, String label) { this(UUID.fromString(id), MarkerType.WAYPOINT, label); }
    public Marker(UUID id, MarkerType type, String label) { this.id = id; this.type = type; this.label = label; }
    public UUID getId() { return id; }
    public MarkerType getType() { return type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLabel() { return label; }
    public String getDisplayLabel() { return label; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public Object getOwnerId() { return null; }
    public String getOwnerName() { return ""; }
    public boolean isShared() { return false; }
}
