package com.parkourmod.mixin;

import com.parkourmod.EmoteAnimator;
import com.parkourmod.PoseHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    // Конструктор нужен только для компиляции (наследуемся, чтобы видеть поля head/body/руки/ноги).
    public PlayerModelMixin(ModelPart root, Function<Identifier, RenderType> renderType) {
        super(root, renderType);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void parkourmod$applyPoses(AvatarRenderState state, CallbackInfo ci) {
        if (!(state instanceof PoseHolder holder)) return;
        EmoteAnimator.PoseSet poses = holder.parkourmod$getPoseSet();
        if (poses == null) return;

        for (EmoteAnimator.ClipPose pose : poses.layers) {
            float w = pose.weight;
            // Голова: поворот прибавляем к ванильному, чтобы она следила за камерой.
            EmoteAnimator.applyToPart(this.head, pose.parts[EmoteAnimator.HEAD], w, true);
            EmoteAnimator.applyToPart(this.rightArm, pose.parts[EmoteAnimator.RIGHT_ARM], w, false);
            EmoteAnimator.applyToPart(this.leftArm, pose.parts[EmoteAnimator.LEFT_ARM], w, false);
            EmoteAnimator.applyToPart(this.rightLeg, pose.parts[EmoteAnimator.RIGHT_LEG], w, false);
            EmoteAnimator.applyToPart(this.leftLeg, pose.parts[EmoteAnimator.LEFT_LEG], w, false);
        }
    }
}
