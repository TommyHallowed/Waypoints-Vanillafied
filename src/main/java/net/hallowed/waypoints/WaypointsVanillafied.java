package net.hallowed.waypoints;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.data.DeathPointStorage;
import net.hallowed.waypoints.client.data.WaypointStorage;
import net.hallowed.waypoints.client.data.XaeroWaypointImporter;
import net.hallowed.waypoints.client.init.ModKeybinds;

public class WaypointsVanillafied implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModKeybinds.register();
        XaeroWaypointImporter.register();
        WaypointsConfig.HANDLER.load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WaypointStorage.onDisconnect();
            DeathPointStorage.onDisconnect();
        });
    }
}
