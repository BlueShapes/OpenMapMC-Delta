// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

/** Text policy for minimap waypoints; fullscreen and world labels remain separate. */
public final class MinimapWaypointLabel {
    private MinimapWaypointLabel() {}

    /** A zero cutoff keeps every name. Unknown distances also keep the name useful. */
    public static String format(String name, String distanceText, double distance,
                                double nameDistance, boolean showDistance) {
        return label(name, distanceText, distance, nameDistance, showDistance).text();
    }

    public static Label label(String name, String distanceText, double distance,
                              double nameDistance, boolean showDistance) {
        if (name == null) name = "";
        boolean showName = nameVisible(distance, nameDistance);
        if (!showName) return new Label(showDistance ? distanceText : "", showDistance);
        if (!showDistance) return new Label(name, false);
        return new Label(name.isEmpty() ? distanceText : name + " " + distanceText, name.isEmpty());
    }

    /** Names at or beyond the cutoff are distant; zero keeps all markers individual. */
    public static boolean nameVisible(double distance, double nameDistance) {
        return nameDistance <= 0.0 || !Double.isFinite(distance)
                || distance < 0.0 || distance < nameDistance;
    }

    /** Equal parts stored waypoint RGB and white, rounded to the nearest opaque channel. */
    public static int distanceColor(int markerColor) {
        int red = (((markerColor >>> 16) & 0xFF) + 256) / 2;
        int green = (((markerColor >>> 8) & 0xFF) + 256) / 2;
        int blue = ((markerColor & 0xFF) + 256) / 2;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    public record Label(String text, boolean distanceOnly) {}
}
