package com.parkourmod;

/**
 * Готовый клип анимации: значения каналов, "запечённые" с шагом 1/rate тика.
 * Раскладка: 5 частей (head, rightArm, leftArm, rightLeg, leftLeg) x 6 каналов (dx, dy, dz, rx, ry, rz),
 * затем тело целиком (tx, ty, tz, rx, ry, rz).
 */
public final class AnimClip {
    public final String name;
    /** Длина клипа в тиках. */
    public final int length;
    /** Зацикленный участок [loopFrom, loopTo) в тиках; loopFrom < 0 - без цикла. */
    public final int loopFrom;
    public final int loopTo;
    public final int rate;
    /** [часть][канал][отсчёт] */
    final float[][][] parts = new float[5][6][];
    /** [канал][отсчёт] */
    final float[][] body = new float[6][];

    private AnimClip(String name, int length, int loopFrom, int loopTo, int rate) {
        this.name = name;
        this.length = length;
        this.loopFrom = loopFrom;
        this.loopTo = loopTo;
        this.rate = rate;
    }

    public boolean loops() {
        return loopFrom >= 0 && loopTo > loopFrom;
    }

    public static AnimClip parse(String name, int length, int loopFrom, int loopTo, int rate, String data) {
        AnimClip clip = new AnimClip(name, length, loopFrom, loopTo, rate);
        String[] tokens = data.trim().split(" ");
        int samples = length * rate + 1;
        int pos = 0;
        for (int part = 0; part < 5; part++) {
            for (int ch = 0; ch < 6; ch++) {
                float[] arr = new float[samples];
                for (int i = 0; i < samples; i++) arr[i] = Float.parseFloat(tokens[pos++]);
                clip.parts[part][ch] = arr;
            }
        }
        for (int ch = 0; ch < 6; ch++) {
            float[] arr = new float[samples];
            for (int i = 0; i < samples; i++) arr[i] = Float.parseFloat(tokens[pos++]);
            clip.body[ch] = arr;
        }
        if (pos != tokens.length) {
            throw new IllegalStateException("Clip " + name + ": unexpected data size " + tokens.length + " vs " + pos);
        }
        return clip;
    }

    /** Значение канала в момент t (тики): линейная интерполяция между отсчётами, цикл и удержание последнего кадра. */
    float value(float[] a, float t) {
        if (loops() && t >= loopTo) {
            t = loopFrom + ((t - loopFrom) % (loopTo - loopFrom));
        }
        float idx = t * rate;
        int i = (int) idx;
        if (i >= a.length - 1) return a[a.length - 1];
        float f = idx - i;
        return a[i] + (a[i + 1] - a[i]) * f;
    }
}
