package com.parkourmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Считает эффекты камеры (тряска, наклоны, удар при приземлении, FOV).
 * tick() вызывается раз в тик, а геттеры - каждый кадр с partialTick для плавности.
 * Соглашение по крену: положительный roll = голова наклоняется ВПРАВО.
 */
public final class CameraEffects {
    private CameraEffects() {}

    private static float prevAmp, amp;
    private static float prevPhase, phase;
    private static float prevLean, lean;
    private static float prevTilt, tilt;
    private static float prevKick, kick;
    private static float prevFov, fov;
    private static float fovKick;

    public static void tick(LocalPlayer p, boolean landed, double fallSpeedBeforeLanding) {
        prevAmp = amp;
        prevPhase = phase;
        prevLean = lean;
        prevTilt = tilt;
        prevKick = kick;
        prevFov = fov;

        boolean on = ParkourController.isEnabled() && ParkourConfig.cameraEffects;
        ParkourController.State st = ParkourController.state();
        float speedNorm = (float) Mth.clamp(ParkourController.lastDisplacement() / 0.28, 0.0, 2.2);
        boolean ground = p.onGround();

        // --- тряска при беге ---
        float ampTarget = 0.0f;
        if (on) {
            if (st == ParkourController.State.WALL_RUN) {
                ampTarget = 1.5f;
            } else if (st == ParkourController.State.NONE) {
                ampTarget = ground ? speedNorm : speedNorm * 0.25f;
            } else if (st == ParkourController.State.DASH_CHARGE) {
                // Дрожь усиливается с секцией: зелёная - слабая, красная - сильная.
                ampTarget = 0.45f + 0.4f * ParkourController.dashSection();
            } else if (st == ParkourController.State.SLIDE) {
                ampTarget = 0.8f + (float) Math.min(1.0, ParkourController.lastDisplacement() / 0.5);
            }
        }
        amp += (ampTarget - amp) * 0.25f;
        phase += 0.30f + speedNorm * 0.40f + (st == ParkourController.State.DASH_CHARGE ? 0.5f : 0.0f);

        // --- наклон при стрейфе ---
        Options o = Minecraft.getInstance().options;
        float strafe = (o.keyRight.isDown() ? 1.0f : 0.0f) - (o.keyLeft.isDown() ? 1.0f : 0.0f);
        float leanTarget = (on && ground && st == ParkourController.State.NONE)
                ? strafe * ParkourConfig.strafeLean * Math.min(speedNorm, 1.5f)
                : 0.0f;
        lean += (leanTarget - lean) * 0.2f;

        // --- наклон на стене (в зависимости от того, с какой стороны стена) ---
        float tiltTarget = 0.0f;
        if (on && (st == ParkourController.State.WALL_RUN || st == ParkourController.State.CLING)) {
            Vec3 w = ParkourController.wallDir();
            double yaw = Math.toRadians(p.getYRot());
            // >0 - стена слева от взгляда, <0 - справа
            double side = w.x * Math.cos(yaw) + w.z * Math.sin(yaw);
            float mag = st == ParkourController.State.WALL_RUN ? ParkourConfig.wallRunTilt : ParkourConfig.CLING_TILT;
            tiltTarget = (float) (mag * Mth.clamp(side * 1.5, -1.0, 1.0)) * (ParkourConfig.invertTilt ? -1.0f : 1.0f);
        }
        tilt += (tiltTarget - tilt) * 0.18f;

        // --- "удар" камеры при приземлении ---
        if (landed && on && fallSpeedBeforeLanding < -0.35) {
            kick = (float) Math.min(-fallSpeedBeforeLanding * 6.0, 7.0);
        }
        kick *= 0.65f;

        // --- FOV ---
        float fovTarget = on
                ? ParkourController.burstFactor() * ParkourConfig.fovBonus
                  + (st == ParkourController.State.WALL_RUN ? 4.0f : 0.0f)
                  + (st == ParkourController.State.SLIDE ? 5.0f : 0.0f)
                  + (st == ParkourController.State.DASH_CHARGE ? -3.0f : 0.0f)
                  + fovKick
                : 0.0f;
        fov += (fovTarget - fov) * 0.2f;
        fovKick *= 0.85f;
    }

    /** Вызывается в момент рывка: короткий "пинок" камеры и FOV. */
    public static void onDash(int section) {
        fovKick = 5.0f + 3.0f * section;
        kick = -(2.0f + section);
    }

    /** Смещение по тангажу в градусах (+ = вниз). */
    public static float pitch(float pt) {
        float a = Mth.lerp(pt, prevAmp, amp);
        float ph = Mth.lerp(pt, prevPhase, phase);
        return Mth.sin(ph * 2.0f) * 0.7f * a * ParkourConfig.shakeIntensity + Mth.lerp(pt, prevKick, kick);
    }

    /** Смещение по рысканью в градусах. */
    public static float yaw(float pt) {
        float a = Mth.lerp(pt, prevAmp, amp);
        float ph = Mth.lerp(pt, prevPhase, phase);
        return Mth.sin(ph + 0.7f) * 0.45f * a * ParkourConfig.shakeIntensity;
    }

    /** Крен в градусах (+ = голова вправо). */
    public static float roll(float pt) {
        float a = Mth.lerp(pt, prevAmp, amp);
        float ph = Mth.lerp(pt, prevPhase, phase);
        return Mth.sin(ph) * 1.1f * a * ParkourConfig.shakeIntensity
                + Mth.lerp(pt, prevLean, lean)
                + Mth.lerp(pt, prevTilt, tilt);
    }

    public static float fovBoost(float pt) {
        return Mth.lerp(pt, prevFov, fov);
    }

    /** Есть ли что применять (чтобы не трогать камеру, когда всё в нуле). */
    public static boolean hasEffect() {
        return Math.abs(amp) > 1.0E-3f || Math.abs(prevAmp) > 1.0E-3f
                || Math.abs(lean) > 1.0E-3f || Math.abs(tilt) > 1.0E-3f
                || Math.abs(kick) > 1.0E-3f || Math.abs(prevKick) > 1.0E-3f;
    }
}
