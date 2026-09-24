package com.parkourmod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

/**
 * Проигрывает клипы анимации (бег, бег по стене, прыжок от стены) на модели нашего игрока.
 * Клипы приходят из Emotecraft-файлов (см. AnimClips). Работает по образу PlayerAnimator:
 *  - "body" из файла - поворот/сдвиг ВСЕГО тела вокруг пояса (наклон при беге, крен на стене);
 *  - head / руки / ноги - позы частей модели, заменяют ванильную анимацию ходьбы;
 *  - для головы поворот прибавляется к ванильному, чтобы она продолжала следить за камерой.
 * Между клипами идёт плавный переход (кроссфейд). Изгибы (bend) колен и локтей не поддерживаются.
 */
public final class EmoteAnimator {
    private EmoteAnimator() {}

    public static final int HEAD = 0, RIGHT_ARM = 1, LEFT_ARM = 2, RIGHT_LEG = 3, LEFT_LEG = 4;
    private static final float FADE_STEP = 0.25f;

    private static final class Layer {
        AnimClip clip;
        float time, prevTime, weight, prevWeight;

        void copyFrom(Layer o) {
            clip = o.clip;
            time = o.time;
            prevTime = o.prevTime;
            weight = o.weight;
            prevWeight = o.prevWeight;
        }
    }

    private static final Layer current = new Layer();
    private static final Layer fading = new Layer();
    private static AnimClip oneShot;
    private static float oneShotLeft;
    private static boolean restart;

    /** Поза одного клипа на один кадр отрисовки. */
    public static final class ClipPose {
        public final float weight;
        /** [часть][dx, dy, dz, rx, ry, rz] */
        public final float[][] parts = new float[5][6];
        /** tx, ty, tz, rx, ry, rz */
        public final float[] body = new float[6];

        ClipPose(float weight) {
            this.weight = weight;
        }
    }

    /** Все активные слои (уходящий и текущий) на один кадр. */
    public static final class PoseSet {
        public final ClipPose[] layers;

        PoseSet(ClipPose[] layers) {
            this.layers = layers;
        }
    }

    /** Запустить клип один раз поверх всего остального (например, прыжок от стены). */
    public static void playOnce(AnimClip clip) {
        oneShot = clip;
        oneShotLeft = clip.length + 1;
        restart = true;
    }

    /**
     * Раз в тик.
     * @param desired       какой клип должен играть сейчас (null - никакой)
     * @param speedPerTick  горизонтальная скорость игрока (блоков/тик) - ускоряет шаг бега
     */
    public static void tick(AnimClip desired, double speedPerTick) {
        current.prevTime = current.time;
        current.prevWeight = current.weight;
        fading.prevTime = fading.time;
        fading.prevWeight = fading.weight;

        if (oneShotLeft > 0) {
            desired = oneShot;
            oneShotLeft -= 1.0f;
        }

        if (desired != current.clip || restart) {
            if (current.clip != null && current.weight > 0.01f) {
                fading.copyFrom(current);
            }
            current.clip = desired;
            current.time = 0.0f;
            current.prevTime = 0.0f;
            current.weight = 0.0f;
            current.prevWeight = 0.0f;
            restart = false;
        }

        if (fading.clip != null) {
            fading.weight = Math.max(0.0f, fading.weight - FADE_STEP);
            if (fading.weight <= 0.0f) fading.clip = null;
        }
        if (current.clip != null) {
            current.weight = Math.min(1.0f, current.weight + FADE_STEP);
        }

        float userSpeed = ParkourConfig.runAnimationSpeed;
        if (current.clip != null) {
            current.time += clipSpeed(current.clip, speedPerTick) * userSpeed;
        }
        if (fading.clip != null) {
            fading.time += clipSpeed(fading.clip, speedPerTick) * userSpeed;
        }
    }

    private static float clipSpeed(AnimClip clip, double speedPerTick) {
        if (clip == AnimClips.RUN) {
            // Чем быстрее бежим (например, на разбеге), тем чаще шаг.
            return (float) Mth.clamp(speedPerTick / 0.28, 0.6, 2.0);
        }
        return 1.0f;
    }

    /** Позы для отрисовки с интерполяцией между тиками; null, если ничего не играет. */
    public static PoseSet sample(float partialTick) {
        ClipPose a = sampleLayer(fading, partialTick);
        ClipPose b = sampleLayer(current, partialTick);
        if (a == null && b == null) return null;
        if (a == null) return new PoseSet(new ClipPose[] {b});
        if (b == null) return new PoseSet(new ClipPose[] {a});
        return new PoseSet(new ClipPose[] {a, b});
    }

    private static ClipPose sampleLayer(Layer layer, float partialTick) {
        if (layer.clip == null) return null;
        float w = Mth.lerp(partialTick, layer.prevWeight, layer.weight);
        if (w < 0.005f) return null;
        w = w * w * (3.0f - 2.0f * w); // плавное включение и выключение
        float t = Mth.lerp(partialTick, layer.prevTime, layer.time);

        AnimClip clip = layer.clip;
        ClipPose pose = new ClipPose(w);
        for (int part = 0; part < 5; part++) {
            for (int ch = 0; ch < 6; ch++) {
                pose.parts[part][ch] = clip.value(clip.parts[part][ch], t);
            }
        }
        for (int ch = 0; ch < 6; ch++) {
            pose.body[ch] = clip.value(clip.body[ch], t);
        }
        return pose;
    }

    /** Применяет позу к части модели поверх того, что уже выставила ваниль. */
    public static void applyToPart(ModelPart part, float[] v, float w, boolean additiveRotation) {
        PartPose initial = part.getInitialPose();
        part.x = Mth.lerp(w, part.x, initial.x() + v[0]);
        part.y = Mth.lerp(w, part.y, initial.y() + v[1]);
        part.z = Mth.lerp(w, part.z, initial.z() + v[2]);
        if (additiveRotation) {
            part.xRot += w * v[3];
            part.yRot += w * v[4];
            part.zRot += w * v[5];
        } else {
            part.xRot = Mth.lerp(w, part.xRot, v[3]);
            part.yRot = Mth.lerp(w, part.yRot, initial.yRot() + v[4]);
            part.zRot = Mth.lerp(w, part.zRot, v[5]);
        }
    }

    /**
     * Наклон/сдвиг всего тела вокруг пояса. Вызывается в LivingEntityRenderer.submit перед scale():
     * на этот момент матрица уже "перевёрнута" ванилью, поэтому сначала возвращаем обычную систему координат.
     */
    public static void applyBodyTransform(PoseStack poseStack, ClipPose pose) {
        float w = pose.weight;
        float[] b = pose.body;
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(b[0] * w, b[1] * w + 0.75F, b[2] * w);
        PoseStack.Pose last = poseStack.last();
        float rx = b[3] * w;
        float ry = b[4] * w;
        float rz = b[5] * w;
        last.pose().rotateZYX(rz, ry, rx);
        last.normal().rotateZYX(rz, ry, rx);
        poseStack.translate(0.0F, -0.75F, 0.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
    }
}
