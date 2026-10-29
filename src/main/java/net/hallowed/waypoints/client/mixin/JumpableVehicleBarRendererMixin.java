package net.hallowed.waypoints.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(JumpableVehicleBar.class)
public abstract class JumpableVehicleBarRendererMixin implements ContextualBar {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private PlayerRideableJumping playerJumpableVehicle;

    @Unique private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_background");
    @Unique private static final Identifier COOLDOWN   = Identifier.withDefaultNamespace("hud/jump_bar_cooldown");
    @Unique private static final Identifier PROGRESS   = Identifier.withDefaultNamespace("hud/jump_bar_progress");

    /** Lazily created locator bar used only to draw the icons over the jump bar. */
    @Unique
    private LocatorBar wv$locator;

    /**
     * @author TommyHallowed
     * @reason Re-implements the jump bar render to draw locator icons on top of it without
     *         drawing a second (duplicate) locator background.
     */
    @Overwrite
    public void extractBackground(@NotNull GuiGraphicsExtractor context, @NotNull DeltaTracker deltaTracker) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        int left = this.left(this.minecraft.getWindow());
        int top = this.top(this.minecraft.getWindow());

        // Vanilla jump bar
        context.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 182, 5);

        if (this.playerJumpableVehicle != null && this.playerJumpableVehicle.getJumpCooldown() > 0) {
            context.blitSprite(RenderPipelines.GUI_TEXTURED, COOLDOWN, left, top, 182, 5);
        } else {
            float strength = this.minecraft.player.getJumpRidingScale();
            if (strength > 0.0F) {
                int progress = (int) (strength * 183.0F);
                context.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, 182, 5, 0, 0, left, top, progress, 5);
            }
        }

        // Icons only - the locator background is intentionally not drawn here.
        if (wv$locator == null) {
            wv$locator = new LocatorBar(this.minecraft);
        }
        wv$locator.extractRenderState(context, deltaTracker);
    }
}
