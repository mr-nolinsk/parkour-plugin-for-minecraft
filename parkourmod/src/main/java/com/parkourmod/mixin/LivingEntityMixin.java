package com.parkourmod.mixin;

import com.parkourmod.ParkourController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Пока заряжается рывок (Прыжок + Шифт), обычный прыжок игрока отменяется. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void parkourmod$blockJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && ParkourController.shouldBlockJump(player)) {
            ci.cancel();
        }
    }
}
