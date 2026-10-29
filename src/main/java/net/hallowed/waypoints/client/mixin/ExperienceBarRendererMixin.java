package net.hallowed.waypoints.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public abstract class ExperienceBarRendererMixin implements ContextualBar {

    @Shadow @Final private Minecraft minecraft;

    @Unique
    private LocatorBar wv$locator;

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void wv$appendLocatorBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (wv$locator == null) {
            wv$locator = new LocatorBar(this.minecraft);
        }
        wv$locator.extractBackground(graphics, deltaTracker);
        wv$locator.extractRenderState(graphics, deltaTracker);
    }
}
