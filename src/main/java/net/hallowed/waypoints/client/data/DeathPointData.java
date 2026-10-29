package net.hallowed.waypoints.client.data;

public record DeathPointData(String id, double x, double y, double z, String dimension, long createdAt) {

    public DeathPointData {
        x = WaypointData.round2(x);
        y = WaypointData.round2(y);
        z = WaypointData.round2(z);
    }
}