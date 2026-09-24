package com.parkourmod.mixin;

import com.parkourmod.EmoteAnimator;
import com.parkourmod.PoseHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("HEAD"))
    private void parkourmod$extractPoses(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        // Анимации получает только наш собственный игрок.
        EmoteAnimator.PoseSet poses = avatar == Minecraft.getInstance().player ? EmoteAnimator.sample(partialTick) : null;
        ((PoseHolder) state).parkourmod$setPoseSet(poses);
    }
}
