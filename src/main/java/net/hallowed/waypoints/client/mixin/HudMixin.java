package net.hallowed.waypoints.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.data.DeathPointData;
import net.hallowed.waypoints.client.data.DeathPointStorage;
import net.hallowed.waypoints.client.data.WaypointData;
import net.hallowed.waypoints.client.data.WaypointStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * Forces the locator bar to be shown when the player has a custom waypoint or death point
     * in the current dimension, even if vanilla would not show it. Never shown for spectators.
     */
    @ModifyVariable(method = "nextContextualInfoState", at = @At("STORE"), name = "canShowLocatorInfo")
    private boolean wv$includeCustomWaypoints(boolean canShowLocatorInfo) {
        if (this.minecraft.player == null) return false;
        if (this.minecraft.player.isSpectator()) return false;
        if (canShowLocatorInfo) return true;

        String currentDimension = this.minecraft.player.level().dimension().identifier().toString();

        for (WaypointData wp : WaypointStorage.getInstance().getWaypoints()) {
            if (wp.dimension().equals(currentDimension)) return true;
        }
        for (DeathPointData dp : DeathPointStorage.getInstance().getDeathPoints()) {
            if (dp.dimension().equals(currentDimension)) return true;
        }
        return false;
    }

    /** Keeps the XP bar prioritized (so icons render over it) when the locator background is hidden. */
    @Inject(method = "willPrioritizeExperienceInfo", at = @At("RETURN"), cancellable = true)
    private void wv$alwaysShowXp(CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.player != null && this.minecraft.player.isCreative()) return;
        if (WaypointsConfig.HANDLER.instance().hideLocatorBackground) {
            cir.setReturnValue(true);
        }
    }

    /** Always prioritizes the jump bar while riding so waypoint icons render over it. */
    @Inject(method = "willPrioritizeJumpInfo", at = @At("RETURN"), cancellable = true)
    private void wv$alwaysShowJump(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
