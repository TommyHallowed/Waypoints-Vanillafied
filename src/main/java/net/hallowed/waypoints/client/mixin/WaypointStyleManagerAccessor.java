package net.hallowed.waypoints.client.mixin;

import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.client.resources.WaypointStyleManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Waypoints Vanillafied - exposes the loaded waypoint styles so the create screen
 * can list every available icon style.
 */
@Mixin(WaypointStyleManager.class)
public interface WaypointStyleManagerAccessor {

    @Accessor("waypointStyles")
    Map<ResourceKey<WaypointStyleAsset>, WaypointStyle> wv$getWaypointStyles();
}
