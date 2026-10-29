package net.hallowed.waypoints.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;

@Environment(EnvType.CLIENT)
public final class WaypointBarTooltip {

    private static final int DISTANCE_COLOR = 0xFFAAAAAA;
    private static final int LINE_HEIGHT = 10;

    private WaypointBarTooltip() {} // utility class

    /**
     * Renders a two-line tooltip (name, distance) centered over the icon at {@code iconX}.
     *
     * @param color ARGB color used for the name line
     */
    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics,
                              String name, int iconX, float distance, int color) {
        String distText = formatDistance(distance);

        int nameWidth = minecraft.font.width(name);
        int distWidth = minecraft.font.width(distText);
        int contentWidth = Math.max(nameWidth, distWidth);
        int contentHeight = LINE_HEIGHT * 2;

        int contentX = iconX + 4 - contentWidth / 2;
        int contentY = minecraft.getWindow().getGuiScaledHeight() - 44 - contentHeight;

        TooltipRenderUtil.extractTooltipBackground(graphics, contentX, contentY, contentWidth, contentHeight, null);

        graphics.text(minecraft.font, name, contentX + (contentWidth - nameWidth) / 2, contentY, color, true);
        graphics.text(minecraft.font, distText, contentX + (contentWidth - distWidth) / 2,
                contentY + LINE_HEIGHT, DISTANCE_COLOR, true);
    }

    /** Formats a distance as meters, or kilometers from 1000 m (one decimal below 100 km). */
    public static String formatDistance(float distance) {
        if (distance >= 1000.0F) {
            float km = distance / 1000.0F;
            if (km >= 100.0F) {
                return String.format("%dkm", (int) km);
            }
            return String.format("%.1fkm", km);
        }
        return String.format("%dm", (int) distance);
    }
}
