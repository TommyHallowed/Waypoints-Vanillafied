package net.hallowed.waypoints.client.screen;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hallowed.waypoints.client.data.WaypointData;
import net.hallowed.waypoints.client.data.WaypointStorage;
import net.hallowed.waypoints.client.mixin.WaypointStyleManagerAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.client.resources.WaypointStyleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class WaypointCreateScreen extends Screen {

    private static final int PANEL_WIDTH = 220;
    private static final int ICON_SIZE = 18;
    private static final int ICON_SPACING = 20;
    private static final int ICONS_PER_ROW = 10;
    private static final int COLOR_PICKER_HEIGHT = 60;

    /** Scancode of the Enter / Return key (matches the key values reported by KeyEvent#key). */
    private static final int KEY_ENTER = 40;

    @Nullable
    private final Screen parentScreen;
    @Nullable
    private final WaypointData editingWaypoint;
    private final boolean isEditMode;

    private EditBox nameField;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;

    private int selectedStyleIndex = 0;
    private int selectedColor;

    private WaypointColorPicker colorPicker;

    /** All available waypoint styles, sorted by id. */
    private List<Map.Entry<ResourceKey<WaypointStyleAsset>, WaypointStyle>> styles = new ArrayList<>();

    private int panelLeft;
    private int panelTop;
    private int iconGridX;
    private int iconGridY;

    /** Opens the screen in "create" mode. */
    public WaypointCreateScreen() {
        this(null, null);
    }

    /**
     * @param parentScreen    screen to return to on close (may be null)
     * @param editingWaypoint waypoint to edit, or null to create a new one
     */
    public WaypointCreateScreen(@Nullable Screen parentScreen, @Nullable WaypointData editingWaypoint) {
        super(editingWaypoint != null
                ? Component.translatable("screen.waypoints_vanillafied.edit")
                : Component.translatable("screen.waypoints_vanillafied.create"));
        this.parentScreen = parentScreen;
        this.editingWaypoint = editingWaypoint;
        this.isEditMode = editingWaypoint != null;
        this.selectedColor = isEditMode ? editingWaypoint.color() : 0xF9FFFE;
    }

    @Override
    protected void init() {
        // init() also runs on window resize - keep the color the user already picked.
        if (this.colorPicker != null) {
            this.selectedColor = this.colorPicker.getColor();
        }

        WaypointStyleManager manager = this.minecraft.gui.hud.getWaypointStyles();
        Map<ResourceKey<WaypointStyleAsset>, WaypointStyle> styleMap =
                ((WaypointStyleManagerAccessor) manager).wv$getWaypointStyles();
        this.styles = new ArrayList<>(styleMap.entrySet());
        this.styles.sort(Comparator.comparing(e -> e.getKey().identifier().toString()));

        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = 20;

        // --- Name ---
        int nameY = panelTop + 24;
        this.nameField = new EditBox(this.font, panelLeft, nameY, PANEL_WIDTH, 20,
                Component.translatable("gui.waypoints_vanillafied.name"));
        this.nameField.setMaxLength(32);
        this.nameField.setHint(Component.literal("Waypoint name..."));
        this.addRenderableWidget(this.nameField);

        // --- Coordinates ---
        int posLabelY = nameY + 28;
        int posY = posLabelY + 12;
        int fieldWidth = (PANEL_WIDTH - 10) / 3;

        this.xField = new EditBox(this.font, panelLeft, posY, fieldWidth, 20, Component.literal("X"));
        this.yField = new EditBox(this.font, panelLeft + fieldWidth + 5, posY, fieldWidth, 20, Component.literal("Y"));
        this.zField = new EditBox(this.font, panelLeft + (fieldWidth + 5) * 2, posY, fieldWidth, 20, Component.literal("Z"));

        this.xField.setHint(Component.literal("X"));
        this.yField.setHint(Component.literal("Y"));
        this.zField.setHint(Component.literal("Z"));

        if (this.isEditMode) {
            assert this.editingWaypoint != null;
            this.nameField.setValue(this.editingWaypoint.name());
            this.xField.setValue(WaypointData.formatCoord(this.editingWaypoint.x()));
            this.yField.setValue(WaypointData.formatCoord(this.editingWaypoint.y()));
            this.zField.setValue(WaypointData.formatCoord(this.editingWaypoint.z()));

            for (int i = 0; i < styles.size(); i++) {
                if (styles.get(i).getKey().identifier().toString().equals(this.editingWaypoint.style())) {
                    this.selectedStyleIndex = i;
                    break;
                }
            }
        } else if (this.minecraft.player != null) {
            // New waypoints default to the player's current position.
            this.xField.setValue(WaypointData.formatCoord(this.minecraft.player.getX()));
            this.yField.setValue(WaypointData.formatCoord(this.minecraft.player.getY()));
            this.zField.setValue(WaypointData.formatCoord(this.minecraft.player.getZ()));
        }

        this.addRenderableWidget(this.xField);
        this.addRenderableWidget(this.yField);
        this.addRenderableWidget(this.zField);

        // --- Icon grid + color picker ---
        int styleLabelY = posY + 28;
        this.iconGridX = panelLeft;
        this.iconGridY = styleLabelY + 12;

        int colorLabelY = getColorLabelY();
        int colorPickerY = colorLabelY + 12;

        this.colorPicker = new WaypointColorPicker(panelLeft, colorPickerY, PANEL_WIDTH, COLOR_PICKER_HEIGHT);
        this.colorPicker.setColor(this.selectedColor);

        // --- Buttons ---
        int buttonY = colorPickerY + COLOR_PICKER_HEIGHT + 12;
        int centerX = this.width / 2;

        Component saveLabel = this.isEditMode
                ? Component.translatable("gui.waypoints_vanillafied.save")
                : Component.translatable("gui.waypoints_vanillafied.create");
        this.addRenderableWidget(
                Button.builder(saveLabel, this::onSave)
                        .bounds(centerX - 104, buttonY, 100, 20)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(Component.translatable("gui.cancel"), this::onCancel)
                        .bounds(centerX + 4, buttonY, 100, 20)
                        .build()
        );

        this.setInitialFocus(this.nameField);
    }

    /** Y position of the "Color" label, which sits below the (variable-height) icon grid. */
    private int getColorLabelY() {
        int iconRows = Math.max(1, (styles.size() + ICONS_PER_ROW - 1) / ICONS_PER_ROW);
        return iconGridY + (iconRows * ICON_SPACING) + 8;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.nextStratum();
        graphics.centeredText(this.font, this.title, this.width / 2, panelTop + 6, 0xFFFFFF);

        int posLabelY = panelTop + 24 + 28;
        graphics.text(this.font, Component.translatable("gui.waypoints_vanillafied.position"),
                panelLeft, posLabelY, 0xA0A0A0);

        int styleLabelY = posLabelY + 12 + 28;
        graphics.text(this.font, Component.translatable("gui.waypoints_vanillafied.icon_style"),
                panelLeft, styleLabelY, 0xA0A0A0);

        renderIconGrid(graphics, mouseX, mouseY);

        graphics.text(this.font, Component.translatable("gui.waypoints_vanillafied.color"),
                panelLeft, getColorLabelY(), 0xA0A0A0);

        colorPicker.render(graphics, mouseX, mouseY, delta);
    }

    /** Draws every style icon tinted with the currently picked color, highlighting hover/selection. */
    private void renderIconGrid(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int tintColor = ARGB.color(255, colorPicker.getColor());

        for (int i = 0; i < styles.size(); i++) {
            int x = iconGridX + (i % ICONS_PER_ROW) * ICON_SPACING;
            int y = iconGridY + (i / ICONS_PER_ROW) * ICON_SPACING;

            graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x40000000);

            Identifier sprite = styles.get(i).getValue().spriteLocations().getFirst();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x + 1, y + 1, 16, 16, tintColor);

            boolean hovered = mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE;
            if (hovered) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }

            if (i == selectedStyleIndex) {
                graphics.outline(x - 1, y - 1, ICON_SIZE + 2, ICON_SIZE + 2, 0xFFFFFFFF);
            } else if (hovered) {
                graphics.outline(x - 1, y - 1, ICON_SIZE + 2, ICON_SIZE + 2, 0xFF808080);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubled) {
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();

        // Icon grid selection
        for (int i = 0; i < styles.size(); i++) {
            int x = iconGridX + (i % ICONS_PER_ROW) * ICON_SPACING;
            int y = iconGridY + (i / ICONS_PER_ROW) * ICON_SPACING;
            if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
                selectedStyleIndex = i;
                return true;
            }
        }

        if (colorPicker.mouseClicked(mouseX, mouseY, 0)) {
            return true;
        }

        return super.mouseClicked(mouseButtonEvent, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == KEY_ENTER) {
            this.onSave(null);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parentScreen);
    }

    /** Validates the input and creates/updates the waypoint. Invalid input keeps the screen open. */
    private void onSave(@Nullable Button button) {
        String name = nameField.getValue().trim();
        if (name.isEmpty() || styles.isEmpty()) return;

        try {
            double x = parseCoord(xField.getValue());
            double y = parseCoord(yField.getValue());
            double z = parseCoord(zField.getValue());

            String style = styles.get(selectedStyleIndex).getKey().identifier().toString();
            int color = colorPicker.getColor();

            if (this.isEditMode) {
                assert this.editingWaypoint != null;
                // The dimension of an existing waypoint never changes when editing.
                WaypointStorage.getInstance().updateWaypoint(new WaypointData(
                        this.editingWaypoint.id(), name, x, y, z,
                        this.editingWaypoint.dimension(), style, color));
            } else {
                String dimension = this.minecraft.player != null
                        ? this.minecraft.player.level().dimension().identifier().toString()
                        : "minecraft:overworld";
                WaypointStorage.getInstance().addWaypoint(
                        WaypointData.create(name, x, y, z, dimension, style, color));
            }

            this.onClose();
        } catch (NumberFormatException ignored) {
            // Invalid coordinates: stay on the screen so the user can fix them.
        }
    }

    /** Parses a coordinate, accepting both '.' and ',' as the decimal separator. */
    private static double parseCoord(String text) {
        double value = Double.parseDouble(text.trim().replace(',', '.'));
        if (!Double.isFinite(value)) throw new NumberFormatException("Not a finite number");
        return value;
    }

    private void onCancel(Button button) {
        this.onClose();
    }
}
