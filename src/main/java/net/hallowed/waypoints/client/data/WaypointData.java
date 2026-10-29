package net.hallowed.waypoints.client.data;

import java.util.Locale;
import java.util.UUID;

public record WaypointData(
        String id,
        String name,
        double x,
        double y,
        double z,
        String dimension,
        String style,
        int color
) {

    /** Coordinates are rounded to 2 decimals so saved files stay tidy. */
    public WaypointData {
        x = round2(x);
        y = round2(y);
        z = round2(z);
    }

    /** Creates a waypoint with a freshly generated id. */
    public static WaypointData create(String name, double x, double y, double z,
                                      String dimension, String style, int color) {
        return new WaypointData(UUID.randomUUID().toString(), name, x, y, z, dimension, style, color);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Formats a coordinate with 2 decimals, locale-independent. */
    public static String formatCoord(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
