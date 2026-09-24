package com.parkourmod.mixin;

import com.parkourmod.CameraEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private static final Vector3f PARKOUR_FORWARDS = new Vector3f(0.0F, 0.0F, -1.0F);
    @Unique
    private static final Vector3f PARKOUR_UP = new Vector3f(0.0F, 1.0F, 0.0F);
    @Unique
    private static final Vector3f PARKOUR_LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);

    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;

    @Inject(method = "setup", at = @At("RETURN"))
    private void parkourmod$applyEffects(Level level, Entity entity, boolean detached, boolean mirrored,
                                         float partialTicks, CallbackInfo ci) {
        if (detached || !(entity instanceof LocalPlayer) || !CameraEffects.hasEffect()) return;

        float pitch = CameraEffects.pitch(partialTicks);
        float yaw = CameraEffects.yaw(partialTicks);
        float roll = CameraEffects.roll(partialTicks);

        rotation.rotationYXZ(
                (float) (Math.PI - Math.toRadians(this.yRot + yaw)),
                (float) -Math.toRadians(this.xRot + pitch),
                (float) -Math.toRadians(roll));
        PARKOUR_FORWARDS.rotate(rotation, forwards);
        PARKOUR_UP.rotate(rotation, up);
        PARKOUR_LEFT.rotate(rotation, left);
    }
}
