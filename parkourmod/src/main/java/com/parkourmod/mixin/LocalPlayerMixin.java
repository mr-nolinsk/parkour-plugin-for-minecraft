package com.parkourmod.mixin;

import com.parkourmod.ParkourController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void parkourmod$before(CallbackInfo ci) {
        ParkourController.before((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void parkourmod$after(CallbackInfo ci) {
        ParkourController.after((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void parkourmod$afterTick(CallbackInfo ci) {
        ParkourController.afterTick((LocalPlayer) (Object) this);
    }
}
