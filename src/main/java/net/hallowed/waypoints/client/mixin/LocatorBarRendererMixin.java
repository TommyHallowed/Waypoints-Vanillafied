package net.hallowed.waypoints.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.render.WaypointBarRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocatorBar.class)
public abstract class LocatorBarRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    @Unique
    private boolean wv$isSpectator() {
        return this.minecraft.player != null && this.minecraft.player.isSpectator();
    }

    @Unique
    private boolean wv$isCreative() {
        return this.minecraft.player != null && this.minecraft.player.isCreative();
    }

    /** Hides the locator background (always for spectators; optionally via config, except in creative). */
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void wv$hideBackground(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (wv$isSpectator()) {
            ci.cancel();
            return;
        }
        if (wv$isCreative()) return;
        if (WaypointsConfig.HANDLER.instance().hideLocatorBackground) {
            ci.cancel();
        }
    }

    /** Skips icon rendering for spectators or when the user toggled icons off. */
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void wv$hideIconsWhenToggled(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (wv$isSpectator() || !WaypointBarRenderer.isIconsVisible()) {
            ci.cancel();
        }
    }

    /** Draws custom waypoints and death points after the vanilla icons. */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wv$renderCustomWaypoints(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (wv$isSpectator()) return;
        WaypointBarRenderer.render(this.minecraft, graphics, deltaTracker);
    }
}
