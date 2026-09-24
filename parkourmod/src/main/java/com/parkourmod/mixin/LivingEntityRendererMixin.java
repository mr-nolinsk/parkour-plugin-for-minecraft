package com.parkourmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.parkourmod.EmoteAnimator;
import com.parkourmod.PoseHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void parkourmod$bodyTransform(LivingEntityRenderState state, PoseStack poseStack,
                                          SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (state instanceof PoseHolder holder) {
            EmoteAnimator.PoseSet poses = holder.parkourmod$getPoseSet();
            if (poses != null) {
                for (EmoteAnimator.ClipPose pose : poses.layers) {
                    EmoteAnimator.applyBodyTransform(poseStack, pose);
                }
            }
        }
    }
}
