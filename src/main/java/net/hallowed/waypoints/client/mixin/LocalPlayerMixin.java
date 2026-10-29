package net.hallowed.waypoints.client.mixin;

import net.hallowed.waypoints.client.config.WaypointsConfig;
import net.hallowed.waypoints.client.data.DeathPointStorage;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    /** Prevents recording more than one death point per death (hurtTo can fire repeatedly at 0 HP). */
    @Unique
    private boolean wv$deathRecorded = false;

    @Inject(method = "hurtTo", at = @At("TAIL"))
    private void wv$onHurtTo(float newHealth, CallbackInfo ci) {
        if (newHealth > 0 || wv$deathRecorded) return;

        wv$deathRecorded = true;
        if (WaypointsConfig.HANDLER.instance().createDeathPoints) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            BlockPos pos = self.blockPosition();
            String dimension = self.level().dimension().identifier().toString();
            DeathPointStorage.getInstance().addDeathPoint(pos.getX(), pos.getY(), pos.getZ(), dimension);
        }
    }

    /** Re-arms death recording once the player respawns. */
    @Inject(method = "respawn", at = @At("HEAD"))
    private void wv$onRespawn(CallbackInfo ci) {
        wv$deathRecorded = false;
    }
}
