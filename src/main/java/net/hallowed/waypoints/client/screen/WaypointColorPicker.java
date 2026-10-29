package net.hallowed.waypoints.client.screen;

import dev.isxander.yacl3.gui.render.ColorGradientRenderState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import org.lwjgl.sdl.SDLMouse;

import java.awt.Color;

@Environment(EnvType.CLIENT)
public class WaypointColorPicker {

    private static final int HUE_SLIDER_HEIGHT = 8;
    private static final int GAP = 4;
    private static final int OUTLINE = 1;
    private static final int HUE_SEGMENTS = 6;

    private final int previewX, previewY, previewW, previewH;
    private final int gradientX, gradientY, gradientW, gradientH;
    private final int hueX, hueY, hueW, hueH;

    private float hue = 0f;
    private float saturation = 0f;
    private float brightness = 1f;

    private boolean draggingGradient = false;
    private boolean draggingHue = false;

    public WaypointColorPicker(int x, int y, int width, int height) {
        int mainH = height - HUE_SLIDER_HEIGHT - GAP;

        this.previewW = Math.max(mainH / 2, 24);
        this.previewH = mainH;
        this.previewX = x;
        this.previewY = y;

        this.gradientX = x + previewW + GAP;
        this.gradientY = y;
        this.gradientW = width - previewW - GAP;
        this.gradientH = mainH;

        this.hueX = x;
        this.hueY = y + mainH + GAP;
        this.hueW = width;
        this.hueH = HUE_SLIDER_HEIGHT;
    }

    /** Sets the picker to the given 0xRRGGBB color. */
    public void setColor(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    /** Returns the selected color as 0xRRGGBB (no alpha). */
    public int getColor() {
        return Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Poll the mouse directly so dragging keeps working outside the pressed widget.
        int mouseState = SDLMouse.SDL_GetMouseState(null, null);
        boolean leftDown = (mouseState & 1) != 0;
        if (!leftDown) {
            draggingGradient = false;
            draggingHue = false;
        } else {
            if (draggingGradient) updateFromGradient(mouseX, mouseY);
            if (draggingHue) updateFromHue(mouseX);
        }

        // Preview swatch
        graphics.fill(previewX - OUTLINE, previewY - OUTLINE,
                previewX + previewW + OUTLINE, previewY + previewH + OUTLINE, 0xFF000000);
        graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF000000 | getColor());

        // Saturation (left -> right) / brightness (top -> bottom) field
        graphics.fill(gradientX - OUTLINE, gradientY - OUTLINE,
                gradientX + gradientW + OUTLINE, gradientY + gradientH + OUTLINE, 0xFF000000);

        int pureHue = 0xFF000000 | (Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF);
        ColorGradientRenderState.createHorizontal(
                graphics,
                gradientX, gradientY,
                gradientX + gradientW, gradientY + gradientH,
                0xFFFFFFFF, pureHue
        ).submit(graphics);

        graphics.fillGradient(gradientX, gradientY,
                gradientX + gradientW, gradientY + gradientH,
                0x00000000, 0xFF000000);

        int gThumbX = Mth.clamp((int) (gradientX + gradientW * saturation), gradientX, gradientX + gradientW);
        int gThumbY = Mth.clamp((int) (gradientY + gradientH * (1f - brightness)), gradientY, gradientY + gradientH);
        graphics.fill(gThumbX - 3, gThumbY - 3, gThumbX + 2, gThumbY + 2, 0xFF404040);
        graphics.fill(gThumbX - 2, gThumbY - 2, gThumbX + 1, gThumbY + 1, 0xFFFFFFFF);

        // Hue slider
        graphics.fill(hueX - OUTLINE, hueY - OUTLINE,
                hueX + hueW + OUTLINE, hueY + hueH + OUTLINE, 0xFF000000);
        drawRainbowGradient(graphics, hueX, hueY, hueX + hueW, hueY + hueH);

        int hThumbX = Mth.clamp((int) (hueX + hueW * hue), hueX, hueX + hueW);
        graphics.fill(hThumbX - 2, hueY - 1, hThumbX + 2, hueY + hueH + 1, 0xFF404040);
        graphics.fill(hThumbX - 1, hueY, hThumbX + 1, hueY + hueH, 0xFFFFFFFF);
    }

    /** Draws the hue rainbow as {@link #HUE_SEGMENTS} horizontal gradient segments. */
    private void drawRainbowGradient(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        float segW = (x2 - x1) / (float) HUE_SEGMENTS;
        for (int i = 0; i < HUE_SEGMENTS; i++) {
            int left = (int) (x1 + i * segW);
            int right = (i == HUE_SEGMENTS - 1) ? x2 : (int) (x1 + (i + 1) * segW);
            int cL = 0xFF000000 | (Color.HSBtoRGB(i / (float) HUE_SEGMENTS, 1f, 1f) & 0x00FFFFFF);
            int cR = 0xFF000000 | (Color.HSBtoRGB((i + 1) / (float) HUE_SEGMENTS, 1f, 1f) & 0x00FFFFFF);
            ColorGradientRenderState.createHorizontal(graphics, left, y1, right, y2, cL, cR).submit(graphics);
        }
    }

    /** Starts a drag when the left button is pressed on the field or slider. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        if (isInGradient(mx, my)) {
            draggingGradient = true;
            updateFromGradient(mx, my);
            return true;
        }
        if (isInHue(mx, my)) {
            draggingHue = true;
            updateFromHue(mx);
            return true;
        }
        return false;
    }

    private boolean isInGradient(double mx, double my) {
        return mx >= gradientX && mx <= gradientX + gradientW
                && my >= gradientY && my <= gradientY + gradientH;
    }

    private boolean isInHue(double mx, double my) {
        return mx >= hueX && mx <= hueX + hueW
                && my >= hueY && my <= hueY + hueH;
    }

    private void updateFromGradient(double mx, double my) {
        saturation = Mth.clamp((float) (mx - gradientX) / gradientW, 0f, 1f);
        brightness = Mth.clamp(1f - (float) (my - gradientY) / gradientH, 0f, 1f);
    }

    private void updateFromHue(double mx) {
        hue = Mth.clamp((float) (mx - hueX) / hueW, 0f, 1f);
    }
}
