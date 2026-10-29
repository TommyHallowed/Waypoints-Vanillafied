package net.hallowed.waypoints.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.data.DeathPointData;
import net.hallowed.waypoints.client.data.DeathPointStorage;
import net.hallowed.waypoints.client.data.WaypointData;
import net.hallowed.waypoints.client.data.WaypointStorage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class WaypointBarRenderer {

    private static final Identifier ARROW_UP = Identifier.withDefaultNamespace("hud/locator_bar_arrow_up");
    private static final Identifier ARROW_DOWN = Identifier.withDefaultNamespace("hud/locator_bar_arrow_down");

    /** Style id of the death point icon (shipped in the mod's assets). */
    private static final Identifier DEATH_STYLE_ID = Identifier.parse("waypoints_vanillafied:death");
    public static final ResourceKey<WaypointStyleAsset> DEATH_STYLE_KEY =
            ResourceKey.create(WaypointStyleAssets.ROOT_ID, DEATH_STYLE_ID);

    /** Icons further than this many pixels from the bar center are off the bar and not drawn. */
    private static final int MAX_BAR_OFFSET = 88;
    /** Width of the locator bar in pixels and the horizontal angle (degrees) it covers each side. */
    private static final double BAR_WIDTH_PX = 173.0;
    private static final double BAR_HALF_ANGLE = 60.0;

    /** Death points are picked up (removed) when the player walks within this many blocks. */
    private static final double DEATH_POINT_PICKUP_RADIUS = 3.0;

    private static boolean iconsVisible = true;

    private WaypointBarRenderer() {} // utility class

    public static boolean isIconsVisible() {
        return iconsVisible;
    }

    public static void toggleIconsVisible() {
        iconsVisible = !iconsVisible;
    }

    /** Tracks the icon closest to the bar center, which is the one the tooltip is shown for. */
    private static final class Closest {
        String name;
        int xOffset;
        int absOffset = Integer.MAX_VALUE;
        float distance;
        int color;

        void consider(String name, int xOffset, float distance, int color) {
            int abs = Math.abs(xOffset);
            if (abs < absOffset) {
                this.absOffset = abs;
                this.name = name;
                this.xOffset = xOffset;
                this.distance = distance;
                this.color = color;
            }
        }
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!iconsVisible) return;

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null || minecraft.player == null) return;
        if (minecraft.player.isSpectator()) return;

        Level level = cameraEntity.level();
        String currentDimension = level.dimension().identifier().toString();

        int barTop = minecraft.getWindow().getGuiScaledHeight() - 24 - 5;
        int centerX = Mth.ceil((guiGraphics.guiWidth() - 9) / 2.0F);

        TrackedWaypoint.Camera camera = minecraft.gameRenderer.mainCamera();
        TrackedWaypoint.Projector projector = minecraft.gameRenderer;

        WaypointsConfig config = WaypointsConfig.HANDLER.instance();
        boolean tooltipShown = config.tooltipMode == WaypointsConfig.TooltipMode.ALWAYS || (
                config.tooltipMode == WaypointsConfig.TooltipMode.TAB
                        && minecraft.gui.screen() == null
                        && InputConstants.isKeyDown(InputConstants.KEY_TAB));
        boolean showPitchArrows = config.showPitchArrows;

        Closest closest = new Closest();

        // ---- Custom waypoints ----
        for (WaypointData wp : WaypointStorage.getInstance().getWaypoints()) {
            if (!wp.dimension().equals(currentDimension)) continue;

            Vec3 pos = new Vec3(wp.x(), wp.y(), wp.z());
            int xOffset = computeXOffset(camera, pos);
            if (Math.abs(xOffset) > MAX_BAR_OFFSET) continue;

            ResourceKey<WaypointStyleAsset> styleKey =
                    ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.parse(wp.style()));
            WaypointStyle style = minecraft.gui.hud.getWaypointStyles().get(styleKey);

            float distance = Mth.sqrt((float) cameraEntity.distanceToSqr(pos));
            int color = ARGB.color(255, wp.color());
            int iconX = centerX + xOffset;

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.sprite(distance), iconX, barTop - 2, 9, 9, color);
            if (showPitchArrows) {
                renderPitchArrow(guiGraphics, projector, pos, iconX, barTop);
            }
            if (tooltipShown) {
                closest.consider(wp.name(), xOffset, distance, color);
            }
        }

        // ---- Death points ----
        List<DeathPointData> deathPoints = DeathPointStorage.getInstance().getDeathPoints();

        // Remove death points the player has walked back to (item pickup range).
        if (!minecraft.player.isDeadOrDying()) {
            Vec3 playerPos = minecraft.player.position();
            List<String> toRemove = new ArrayList<>();
            for (DeathPointData dp : deathPoints) {
                if (!dp.dimension().equals(currentDimension)) continue;
                double dx = dp.x() - playerPos.x();
                double dy = dp.y() - playerPos.y();
                double dz = dp.z() - playerPos.z();
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= DEATH_POINT_PICKUP_RADIUS) {
                    toRemove.add(dp.id());
                }
            }
            if (!toRemove.isEmpty()) {
                toRemove.forEach(id -> DeathPointStorage.getInstance().removeDeathPoint(id));
                deathPoints = DeathPointStorage.getInstance().getDeathPoints();
            }
        }

        List<DeathPointData> dimDeathPoints = new ArrayList<>();
        for (DeathPointData dp : deathPoints) {
            if (dp.dimension().equals(currentDimension)) {
                dimDeathPoints.add(dp);
            }
        }

        WaypointStyle deathStyle = minecraft.gui.hud.getWaypointStyles().get(DEATH_STYLE_KEY);
        int total = dimDeathPoints.size();

        for (int i = 0; i < total; i++) {
            DeathPointData dp = dimDeathPoints.get(i);
            Vec3 pos = new Vec3(dp.x(), dp.y(), dp.z());

            int xOffset = computeXOffset(camera, pos);
            if (Math.abs(xOffset) > MAX_BAR_OFFSET) continue;

            float distance = Mth.sqrt((float) cameraEntity.distanceToSqr(pos));
            int color = getDeathPointColor(i, total);
            int iconX = centerX + xOffset;

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, deathStyle.sprite(distance), iconX, barTop - 2, 9, 9, color);
            if (showPitchArrows) {
                renderPitchArrow(guiGraphics, projector, pos, iconX, barTop);
            }
            if (tooltipShown) {
                closest.consider("Death #" + (i + 1), xOffset, distance, color);
            }
        }

        // ---- Vanilla (player) waypoints: only considered for the tooltip ----
        if (tooltipShown) {
            considerVanillaWaypoints(minecraft, deltaTracker, cameraEntity, level, camera, closest);
        }

        if (closest.name != null) {
            WaypointBarTooltip.render(minecraft, guiGraphics, closest.name,
                    centerX + closest.xOffset, closest.distance, closest.color);
        }
    }

    /** Adds vanilla tracked waypoints (other players etc.) as tooltip candidates. */
    private static void considerVanillaWaypoints(Minecraft minecraft, DeltaTracker deltaTracker, Entity cameraEntity,
                                                 Level level, TrackedWaypoint.Camera camera, Closest closest) {
        assert minecraft.player != null;
        ClientWaypointManager waypointManager = minecraft.player.connection.getWaypointManager();
        TickRateManager tickRateManager = level.tickRateManager();
        PartialTickSupplier partialTickSupplier = e ->
                deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(e));

        List<TrackedWaypoint> vanillaWaypoints = new ArrayList<>();
        waypointManager.forEachWaypoint(cameraEntity, vanillaWaypoints::add);

        for (TrackedWaypoint tw : vanillaWaypoints) {
            boolean isSelf = tw.id().left().map(uuid -> uuid.equals(cameraEntity.getUUID())).orElse(false);
            if (isSelf) continue;

            double distSq = tw.distanceSquared(cameraEntity);
            if (Double.isInfinite(distSq) || Double.isNaN(distSq)) continue;

            double yawAngle = tw.yawAngleToCamera(level, camera, partialTickSupplier);
            if (Double.isNaN(yawAngle) || yawAngle <= -BAR_HALF_ANGLE || yawAngle > BAR_HALF_ANGLE) continue;

            int xOffset = Mth.floor(yawAngle * BAR_WIDTH_PX / 2.0 / BAR_HALF_ANGLE);
            if (Math.abs(xOffset) > MAX_BAR_OFFSET) continue;

            String name = tw.id().map(
                    uuid -> {
                        assert minecraft.player != null;
                        PlayerInfo info = minecraft.player.connection.getPlayerInfo(uuid);
                        return info != null ? info.getProfile().name() : null;
                    },
                    stringId -> stringId
            );
            if (name == null) continue;

            int color = tw.icon().color.orElseGet(
                    () -> tw.id().map(
                            uuid -> ARGB.setBrightness(ARGB.color(255, uuid.hashCode()), 0.9F),
                            str -> ARGB.setBrightness(ARGB.color(255, str.hashCode()), 0.9F)
                    )
            );

            closest.consider(name, xOffset, Mth.sqrt((float) distSq), color);
        }
    }

    /** Converts a world position into a horizontal pixel offset from the center of the locator bar. */
    private static int computeXOffset(TrackedWaypoint.Camera camera, Vec3 pos) {
        Vec3 diff = camera.position().subtract(pos).rotateClockwise90();
        float angle = (float) Mth.atan2(diff.z(), diff.x()) * (180.0F / (float) Math.PI);
        double yawAngle = Mth.degreesDifference(camera.yaw(), angle);
        return Mth.floor(yawAngle * BAR_WIDTH_PX / 2.0 / BAR_HALF_ANGLE);
    }

    /** Death point color: bright red (newest/only) fading to dark red (oldest). */
    public static int getDeathPointColor(int index, int total) {
        if (total <= 1) return 0xFFFF0000;
        float t = (float) index / (total - 1);
        int r = (int) (255 - t * 175); // 255 -> 80
        return 0xFF000000 | (r << 16);
    }

    /** Draws an up/down arrow next to an icon when the target is above or below the player's view. */
    private static void renderPitchArrow(GuiGraphicsExtractor guiGraphics,
                                         TrackedWaypoint.Projector projector,
                                         Vec3 waypointPos, int iconX, int barTop) {
        Vec3 projected = projector.projectPointToScreen(waypointPos);
        boolean behind = projected.z > 1.0;
        double screenY = behind ? -projected.y : projected.y;

        Identifier arrowSprite = null;
        int arrowYOffset = 0;

        if (screenY < -1.0) {
            arrowSprite = ARROW_DOWN;
            arrowYOffset = 6;
        } else if (screenY > 1.0) {
            arrowSprite = ARROW_UP;
            arrowYOffset = -6;
        } else if (behind) {
            if (projected.y > 0.0) {
                arrowSprite = ARROW_UP;
                arrowYOffset = -6;
            } else if (projected.y < 0.0) {
                arrowSprite = ARROW_DOWN;
                arrowYOffset = 6;
            }
        }

        if (arrowSprite != null) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, arrowSprite, iconX + 1, barTop + arrowYOffset, 7, 5);
        }
    }
}
