package net.hallowed.waypoints.client.init;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.hallowed.waypoints.client.render.WaypointBarRenderer;
import net.hallowed.waypoints.client.screen.WaypointCreateScreen;
import net.hallowed.waypoints.client.screen.WaypointListScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class ModKeybinds {

    private static final KeyMapping.Category WAYPOINTS_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("waypoints_vanillafied", "waypoints_vanillafied")
    );

    private static final KeyMapping OPEN_WAYPOINT_LIST = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.waypoints_vanillafied.open_list",
                    InputConstants.Type.KEYBOARD,
                    InputConstants.KEY_U,
                    WAYPOINTS_CATEGORY
            )
    );

    private static final KeyMapping QUICK_CREATE_WAYPOINT = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.waypoints_vanillafied.quick_create",
                    InputConstants.Type.KEYBOARD,
                    InputConstants.KEY_N,
                    WAYPOINTS_CATEGORY
            )
    );

    private static final KeyMapping TOGGLE_WAYPOINT_ICONS = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.waypoints_vanillafied.toggle_icons",
                    InputConstants.Type.KEYBOARD,
                    InputConstants.KEY_H,
                    WAYPOINTS_CATEGORY
            )
    );

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_WAYPOINT_LIST.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new WaypointListScreen());
                }
            }
            while (QUICK_CREATE_WAYPOINT.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new WaypointCreateScreen());
                }
            }
            while (TOGGLE_WAYPOINT_ICONS.consumeClick()) {
                WaypointBarRenderer.toggleIconsVisible();
            }
        });
    }
}