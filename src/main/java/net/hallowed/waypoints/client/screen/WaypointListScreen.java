package net.hallowed.waypoints.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.data.DeathPointData;
import net.hallowed.waypoints.client.data.DeathPointStorage;
import net.hallowed.waypoints.client.data.WaypointData;
import net.hallowed.waypoints.client.data.WaypointStorage;
import net.hallowed.waypoints.client.render.WaypointBarRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class WaypointListScreen extends Screen {

    private static final Component TITLE = Component.translatable("screen.waypoints_vanillafied.manage");

    // Key scancodes (values reported by KeyEvent#key)
    private static final int KEY_E = 8;
    private static final int KEY_N = 17;
    private static final int KEY_ENTER = 40;
    private static final int KEY_DELETE = 76;

    @Nullable
    private WaypointList waypointList;
    @Nullable
    private Button editButton;
    @Nullable
    private Button deleteButton;

    public WaypointListScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        int listTop = 32;
        int listBottom = this.height - 52;
        this.waypointList = new WaypointList(this.minecraft, this.width, listBottom - listTop, listTop, 36);
        this.addRenderableWidget(this.waypointList);
        refreshList();

        int buttonY = this.height - 28;
        int centerX = this.width / 2;

        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.waypoints_vanillafied.new"), ignored -> openCreateScreen())
                        .bounds(centerX - 186, buttonY, 90, 20).build()
        );

        this.editButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.waypoints_vanillafied.edit"), ignored -> editSelectedEntry())
                        .bounds(centerX - 92, buttonY, 90, 20).build()
        );

        this.deleteButton = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.waypoints_vanillafied.delete"), ignored -> deleteSelectedEntry())
                        .bounds(centerX + 2, buttonY, 90, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, ignored -> this.onClose())
                        .bounds(centerX + 96, buttonY, 90, 20).build()
        );

        updateButtonStates();
    }

    /** Rebuilds the list with the waypoints and death points of the current dimension. */
    private void refreshList() {
        if (this.waypointList == null) return;
        this.waypointList.clear();

        String currentDimension = null;
        if (this.minecraft.player != null) {
            currentDimension = this.minecraft.player.level().dimension().identifier().toString();
        }

        for (WaypointData wp : WaypointStorage.getInstance().getWaypoints()) {
            if (currentDimension == null || wp.dimension().equals(currentDimension)) {
                this.waypointList.add(new WaypointEntry(wp));
            }
        }

        List<DeathPointData> dimDeathPoints = new ArrayList<>();
        for (DeathPointData dp : DeathPointStorage.getInstance().getDeathPoints()) {
            if (currentDimension == null || dp.dimension().equals(currentDimension)) {
                dimDeathPoints.add(dp);
            }
        }
        int total = dimDeathPoints.size();
        for (int i = 0; i < total; i++) {
            this.waypointList.add(new WaypointEntry(
                    dimDeathPoints.get(i), "Death #" + (i + 1), WaypointBarRenderer.getDeathPointColor(i, total)));
        }

        updateButtonStates();
    }

    private void openCreateScreen() {
        this.minecraft.gui.setScreen(new WaypointCreateScreen(this, null));
    }

    /** Opens the edit screen for the selected waypoint (death points cannot be edited). */
    private boolean editSelectedEntry() {
        WaypointEntry entry = this.waypointList != null ? this.waypointList.getSelected() : null;
        if (entry == null || entry.isDeathPoint()) return false;
        this.minecraft.gui.setScreen(new WaypointCreateScreen(this, entry.getWaypoint()));
        return true;
    }

    private void deleteSelectedEntry() {
        WaypointEntry entry = this.waypointList != null ? this.waypointList.getSelected() : null;
        if (entry == null) return;

        if (entry.isDeathPoint()) {
            assert entry.getDeathPoint() != null;
            DeathPointStorage.getInstance().removeDeathPoint(entry.getDeathPoint().id());
        } else {
            assert entry.getWaypoint() != null;
            WaypointStorage.getInstance().removeWaypoint(entry.getWaypoint().id());
        }
        refreshList();
    }

    private void updateButtonStates() {
        WaypointEntry selected = this.waypointList != null ? this.waypointList.getSelected() : null;
        boolean hasSelection = selected != null;
        boolean isWaypoint = hasSelection && !selected.isDeathPoint();

        if (this.editButton != null) this.editButton.active = isWaypoint;
        if (this.deleteButton != null) this.deleteButton.active = hasSelection;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.nextStratum();
        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        if (this.waypointList != null && this.waypointList.children().isEmpty()) {
            graphics.centeredText(this.font,
                    Component.translatable("gui.waypoints_vanillafied.no_waypoints"),
                    this.width / 2, this.height / 2 - 4, 0x808080);
        }

        updateButtonStates();
    }

    /** Shortcuts: Delete = delete, N = new, E = edit, Enter = close. */
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        switch (keyEvent.key()) {
            case KEY_DELETE -> {
                if (this.waypointList != null && this.waypointList.getSelected() != null) {
                    deleteSelectedEntry();
                    return true;
                }
            }
            case KEY_N -> {
                openCreateScreen();
                return true;
            }
            case KEY_E -> {
                if (editSelectedEntry()) return true;
            }
            case KEY_ENTER -> {
                this.onClose();
                return true;
            }
            default -> { }
        }
        return super.keyPressed(keyEvent);
    }

    private static String formatCoords(double x, double y, double z) {
        return "X: " + WaypointData.formatCoord(x)
                + "  Y: " + WaypointData.formatCoord(y)
                + "  Z: " + WaypointData.formatCoord(z);
    }

    @Environment(EnvType.CLIENT)
    class WaypointList extends ObjectSelectionList<WaypointEntry> {

        public WaypointList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 372;
        }

        public void clear() {
            super.clearEntries();
        }

        public void add(WaypointEntry entry) {
            super.addEntry(entry);
        }
    }

    /** One row of the list: either a regular waypoint or a death point. */
    @Environment(EnvType.CLIENT)
    class WaypointEntry extends ObjectSelectionList.Entry<WaypointEntry> {

        private final @Nullable WaypointData waypoint;
        private final @Nullable DeathPointData deathPoint;
        private final @Nullable String deathName;
        private final int deathColor;

        WaypointEntry(@NonNull WaypointData waypoint) {
            this.waypoint = waypoint;
            this.deathPoint = null;
            this.deathName = null;
            this.deathColor = 0;
        }

        WaypointEntry(@NonNull DeathPointData deathPoint, @NonNull String name, int color) {
            this.waypoint = null;
            this.deathPoint = deathPoint;
            this.deathName = name;
            this.deathColor = color;
        }

        public boolean isDeathPoint() {
            return deathPoint != null;
        }

        public @Nullable WaypointData getWaypoint() {
            return waypoint;
        }

        public @Nullable DeathPointData getDeathPoint() {
            return deathPoint;
        }

        @Override
        public @NonNull Component getNarration() {
            if (deathPoint != null) {
                assert deathName != null;
                return Component.literal(deathName);
            }
            assert waypoint != null;
            return Component.literal(waypoint.name());
        }

        @Override
        public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int cx = this.getContentX();
            int cy = this.getContentY();

            if (deathPoint != null) {
                renderDeathPoint(graphics, cx, cy);
            } else {
                renderWaypoint(graphics, cx, cy);
            }
        }

        private void renderWaypoint(@NonNull GuiGraphicsExtractor graphics, int cx, int cy) {
            assert waypoint != null;
            ResourceKey<WaypointStyleAsset> styleKey =
                    ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.parse(waypoint.style()));
            WaypointStyle style = WaypointListScreen.this.minecraft.gui.hud.getWaypointStyles().get(styleKey);
            int color = ARGB.color(255, waypoint.color());

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.spriteLocations().getFirst(), cx, cy + 4, 16, 16, color);

            graphics.text(WaypointListScreen.this.font, waypoint.name(), cx + 22, cy + 2, color);

            String line2 = formatCoords(waypoint.x(), waypoint.y(), waypoint.z())
                    + "  §7[" + formatDimension(waypoint.dimension()) + "]";
            graphics.text(WaypointListScreen.this.font, line2, cx + 22, cy + 14, 0xA0A0A0);
        }

        private void renderDeathPoint(@NonNull GuiGraphicsExtractor graphics, int cx, int cy) {
            assert deathPoint != null;
            WaypointStyle style = WaypointListScreen.this.minecraft.gui.hud.getWaypointStyles()
                    .get(WaypointBarRenderer.DEATH_STYLE_KEY);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, style.spriteLocations().getFirst(), cx, cy + 4, 16, 16, deathColor);

            graphics.text(WaypointListScreen.this.font, deathName, cx + 22, cy + 2, deathColor);

            String line2 = formatCoords(deathPoint.x(), deathPoint.y(), deathPoint.z())
                    + "  §7[" + formatDimension(deathPoint.dimension()) + "]";

            // Only show a countdown if death points actually expire (config value > 0).
            int expiryMinutes = WaypointsConfig.HANDLER.instance().deathPointExpiryMinutes;
            if (expiryMinutes > 0) {
                long elapsed = System.currentTimeMillis() - deathPoint.createdAt();
                long remaining = Math.max(0, expiryMinutes * 60_000L - elapsed);
                line2 += String.format("  §c%dm %ds remaining", remaining / 60000, (remaining % 60000) / 1000);
            }
            graphics.text(WaypointListScreen.this.font, line2, cx + 22, cy + 14, 0xA0A0A0);
        }

        /** Double-click opens the edit screen for regular waypoints. */
        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent mouseButtonEvent, boolean doubled) {
            if (doubled && !isDeathPoint()) {
                WaypointListScreen.this.minecraft.gui.setScreen(
                        new WaypointCreateScreen(WaypointListScreen.this, this.waypoint));
                return true;
            }
            return super.mouseClicked(mouseButtonEvent, doubled);
        }

        private String formatDimension(String dimension) {
            return switch (dimension) {
                case "minecraft:overworld" -> "Overworld";
                case "minecraft:the_nether" -> "Nether";
                case "minecraft:the_end" -> "The End";
                default -> dimension;
            };
        }
    }
}
