package com.parkourmod.mixin;

import com.parkourmod.CameraEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void parkourmod$fov(Camera camera, float partialTick, boolean changingFov,
                                CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return; // руку (changingFov=false) не трогаем, как и ваниль
        float boost = CameraEffects.fovBoost(partialTick);
        if (Math.abs(boost) > 1.0E-3f) {
            cir.setReturnValue(cir.getReturnValue() + boost);
        }
    }
}
