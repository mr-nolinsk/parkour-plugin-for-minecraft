package com.parkourmod.mixin;

import com.parkourmod.EmoteAnimator;
import com.parkourmod.PoseHolder;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements PoseHolder {
    @Unique
    private EmoteAnimator.PoseSet parkourmod$poseSet;

    @Override
    public EmoteAnimator.PoseSet parkourmod$getPoseSet() {
        return parkourmod$poseSet;
    }

    @Override
    public void parkourmod$setPoseSet(EmoteAnimator.PoseSet poses) {
        this.parkourmod$poseSet = poses;
    }
}
