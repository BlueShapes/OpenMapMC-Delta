package net.aosankaku.openmapdelta.marker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class MarkerManager {
    private static final MarkerManager INSTANCE = new MarkerManager();
    private final List<Marker> markers = new ArrayList<>();
    private long now;
    private MarkerManager() { }
    public static MarkerManager get() { return INSTANCE; }
    public static long monotonicMillis() { return INSTANCE.now; }
    public void setNow(long now) { this.now = now; }
    public void advance(long amount) { now += amount; }
    public void setMarkers(Collection<Marker> values) { markers.clear(); markers.addAll(values); }
    public List<Marker> getWaypoints() { return List.copyOf(markers); }
    public List<Marker> getListedWaypointsInDimension(Object dimension) { return getWaypoints(); }
    public boolean canManageWaypoint(Marker marker) { return true; }
    public boolean canEditAppearance(Marker marker) { return true; }
    public void toggleFavorite(UUID id) { find(id).setFavorite(!find(id).isFavorite()); }
    public void toggleVisibility(UUID id) { find(id).setVisible(!find(id).isVisible()); }
    public void setWaypointsVisible(Collection<Marker> values, boolean visible) { values.forEach(marker -> marker.setVisible(visible)); }
    private Marker find(UUID id) { return markers.stream().filter(marker -> marker.getId().equals(id)).findFirst().orElseThrow(); }
}
