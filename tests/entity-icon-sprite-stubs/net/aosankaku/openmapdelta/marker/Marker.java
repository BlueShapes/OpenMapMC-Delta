package net.aosankaku.openmapdelta.marker;

import java.util.UUID;

public final class Marker {
    private final UUID id;
    private final String iconId;
    public Marker(UUID id, String iconId) { this.id = id; this.iconId = iconId; }
    public UUID getId() { return id; }
    public String getIconId() { return iconId; }
}
