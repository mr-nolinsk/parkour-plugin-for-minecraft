package com.parkourmod;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Настройки мода. Хранятся в config/parkourmod.properties, редактируются через Mod Menu
 * (нужен Cloth Config) или вручную в файле. Единицы скорости: блоки/тик (1 тик = 0.05 c).
 */
public final class ParkourConfig {
    private ParkourConfig() {}

    // ---- Фиксированные значения (не настраиваются) ----
    public static final int BURST_RAMP_TICKS = 20;
    public static final int BURST_FADE_TICKS = 10;
    public static final double WALL_CONTACT_DISTANCE = 0.06;
    public static final double WALL_JUMP_VERTICAL = 0.46;
    /** Минимальная скорость подлёта к стене, чтобы началась пробежка по ней. */
    public static final double WALL_RUN_MIN_SPEED = 0.30;
    public static final int WALL_RUN_MAX_TICKS = 100;
    public static final double LEDGE_MAX_HEIGHT = 1.25;
    public static final float CLING_TILT = 3.0f;

    // ---- Значения по умолчанию ----
    public static final float DEF_BURST_SECONDS = 4.0f;
    public static final float DEF_BURST_COOLDOWN_SECONDS = 3.0f;
    public static final float DEF_BURST_MULTIPLIER = 2.0f;
    public static final float DEF_WALL_JUMP_SPEED = 0.44f;
    public static final float DEF_CLING_SLIDE_SPEED = 0.04f;
    public static final float DEF_WALL_RUN_SPEED_FACTOR = 0.75f;
    public static final float DEF_WALL_RUN_VERTICAL_SPEED = 0.10f;
    public static final boolean DEF_CAMERA_EFFECTS = true;
    public static final float DEF_SHAKE_INTENSITY = 1.0f;
    public static final float DEF_STRAFE_LEAN = 2.2f;
    public static final float DEF_WALL_RUN_TILT = 14.0f;
    public static final boolean DEF_INVERT_TILT = false;
    public static final float DEF_FOV_BONUS = 10.0f;
    public static final boolean DEF_DASH_JUMP_SHIFT = true;
    public static final float DEF_DASH_BAR_SECONDS = 1.2f;
    public static final float DEF_DASH_COOLDOWN_SECONDS = 1.0f;
    public static final float DEF_DASH_DISTANCE_1 = 3.0f;
    public static final float DEF_DASH_DISTANCE_2 = 5.0f;
    public static final float DEF_DASH_DISTANCE_3 = 7.0f;
    public static final float DEF_SLIDE_DISTANCE = 5.0f;
    public static final float DEF_SLIDE_COOLDOWN_SECONDS = 1.0f;
    public static final boolean DEF_RUN_ANIMATION = true;
    public static final float DEF_RUN_ANIMATION_SPEED = 1.0f;
    public static final boolean DEF_SWAP_WALL_RUN_ANIMATIONS = false;

    // ---- Настраиваемые значения ----
    /** Длительность разбега, секунды. */
    public static float burstSeconds = DEF_BURST_SECONDS;
    /** Перезарядка разбега, секунды. */
    public static float burstCooldownSeconds = DEF_BURST_COOLDOWN_SECONDS;
    /** Во сколько раз ускоряемся на пике разбега. */
    public static float burstMultiplier = DEF_BURST_MULTIPLIER;
    /** Горизонтальная скорость отскока из фиксации (~3-4 блока при 0.44). */
    public static float wallJumpSpeed = DEF_WALL_JUMP_SPEED;
    /** Скорость сползания при фиксации на стене, блоков/тик. */
    public static float clingSlideSpeed = DEF_CLING_SLIDE_SPEED;
    /** Множитель скорости бега по стене относительно скорости подлёта (меньше = медленнее). */
    public static float wallRunSpeedFactor = DEF_WALL_RUN_SPEED_FACTOR;
    /** Насколько быстро можно подниматься/опускаться по стене взглядом вверх/вниз, блоков/тик. */
    public static float wallRunVerticalSpeed = DEF_WALL_RUN_VERTICAL_SPEED;
    public static boolean cameraEffects = DEF_CAMERA_EFFECTS;
    public static float shakeIntensity = DEF_SHAKE_INTENSITY;
    public static float strafeLean = DEF_STRAFE_LEAN;
    public static float wallRunTilt = DEF_WALL_RUN_TILT;
    /** Если наклон при беге по стене смотрит "не в ту сторону" - включите. */
    public static boolean invertTilt = DEF_INVERT_TILT;
    public static float fovBonus = DEF_FOV_BONUS;

    // ---- Рывок ----
    /** Заряжать рывок сочетанием Прыжок + Шифт (иначе только отдельной клавишей из Управления). */
    public static boolean dashJumpShift = DEF_DASH_JUMP_SHIFT;
    /** За сколько секунд ползунок проходит путь от 1-й секции до 3-й (в одну сторону). */
    public static float dashBarSeconds = DEF_DASH_BAR_SECONDS;
    public static float dashCooldownSeconds = DEF_DASH_COOLDOWN_SECONDS;
    /** Дистанции рывка для зелёной / жёлтой / красной секций, блоков. */
    public static float dashDistance1 = DEF_DASH_DISTANCE_1;
    public static float dashDistance2 = DEF_DASH_DISTANCE_2;
    public static float dashDistance3 = DEF_DASH_DISTANCE_3;

    // ---- Скольжение ----
    public static float slideDistance = DEF_SLIDE_DISTANCE;
    public static float slideCooldownSeconds = DEF_SLIDE_COOLDOWN_SECONDS;

    // ---- Анимация бега ----
    /** Проигрывать анимацию бега (из Emotecraft-файла) при спринте в паркур-режиме. */
    public static boolean runAnimation = DEF_RUN_ANIMATION;
    /** Множитель скорости анимации бега. */
    public static float runAnimationSpeed = DEF_RUN_ANIMATION_SPEED;
    /** Поменять местами анимации бега по левой и правой стене (если выглядят зеркально). */
    public static boolean swapWallRunAnimations = DEF_SWAP_WALL_RUN_ANIMATIONS;

    public static int burstTicks() {
        return Math.max(1, Math.round(burstSeconds * 20.0f));
    }

    public static int dashCooldownTicks() {
        return Math.max(0, Math.round(dashCooldownSeconds * 20.0f));
    }

    public static int slideCooldownTicks() {
        return Math.max(0, Math.round(slideCooldownSeconds * 20.0f));
    }

    public static int burstCooldownTicks() {
        return Math.max(0, Math.round(burstCooldownSeconds * 20.0f));
    }

    // ------------------------------------------------------------------ сохранение

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("parkourmod.properties");
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("[parkourmod] Не удалось прочитать конфиг: " + e);
            return;
        }
        burstSeconds = readFloat(props, "burstSeconds", DEF_BURST_SECONDS, 1.0f, 10.0f);
        burstCooldownSeconds = readFloat(props, "burstCooldownSeconds", DEF_BURST_COOLDOWN_SECONDS, 0.0f, 15.0f);
        burstMultiplier = readFloat(props, "burstMultiplier", DEF_BURST_MULTIPLIER, 1.0f, 3.0f);
        wallJumpSpeed = readFloat(props, "wallJumpSpeed", DEF_WALL_JUMP_SPEED, 0.2f, 0.8f);
        clingSlideSpeed = readFloat(props, "clingSlideSpeed", DEF_CLING_SLIDE_SPEED, 0.0f, 0.2f);
        wallRunSpeedFactor = readFloat(props, "wallRunSpeedFactor", DEF_WALL_RUN_SPEED_FACTOR, 0.4f, 1.2f);
        wallRunVerticalSpeed = readFloat(props, "wallRunVerticalSpeed", DEF_WALL_RUN_VERTICAL_SPEED, 0.0f, 0.25f);
        cameraEffects = Boolean.parseBoolean(props.getProperty("cameraEffects", String.valueOf(DEF_CAMERA_EFFECTS)));
        shakeIntensity = readFloat(props, "shakeIntensity", DEF_SHAKE_INTENSITY, 0.0f, 3.0f);
        strafeLean = readFloat(props, "strafeLean", DEF_STRAFE_LEAN, 0.0f, 8.0f);
        wallRunTilt = readFloat(props, "wallRunTilt", DEF_WALL_RUN_TILT, 0.0f, 30.0f);
        invertTilt = Boolean.parseBoolean(props.getProperty("invertTilt", String.valueOf(DEF_INVERT_TILT)));
        fovBonus = readFloat(props, "fovBonus", DEF_FOV_BONUS, 0.0f, 25.0f);
        dashJumpShift = Boolean.parseBoolean(props.getProperty("dashJumpShift", String.valueOf(DEF_DASH_JUMP_SHIFT)));
        dashBarSeconds = readFloat(props, "dashBarSeconds", DEF_DASH_BAR_SECONDS, 0.4f, 4.0f);
        dashCooldownSeconds = readFloat(props, "dashCooldownSeconds", DEF_DASH_COOLDOWN_SECONDS, 0.0f, 10.0f);
        dashDistance1 = readFloat(props, "dashDistance1", DEF_DASH_DISTANCE_1, 1.0f, 15.0f);
        dashDistance2 = readFloat(props, "dashDistance2", DEF_DASH_DISTANCE_2, 1.0f, 15.0f);
        dashDistance3 = readFloat(props, "dashDistance3", DEF_DASH_DISTANCE_3, 1.0f, 15.0f);
        slideDistance = readFloat(props, "slideDistance", DEF_SLIDE_DISTANCE, 2.0f, 12.0f);
        slideCooldownSeconds = readFloat(props, "slideCooldownSeconds", DEF_SLIDE_COOLDOWN_SECONDS, 0.0f, 10.0f);
        runAnimation = Boolean.parseBoolean(props.getProperty("runAnimation", String.valueOf(DEF_RUN_ANIMATION)));
        runAnimationSpeed = readFloat(props, "runAnimationSpeed", DEF_RUN_ANIMATION_SPEED, 0.5f, 2.0f);
        swapWallRunAnimations = Boolean.parseBoolean(props.getProperty("swapWallRunAnimations", String.valueOf(DEF_SWAP_WALL_RUN_ANIMATIONS)));
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("burstSeconds", String.valueOf(burstSeconds));
        props.setProperty("burstCooldownSeconds", String.valueOf(burstCooldownSeconds));
        props.setProperty("burstMultiplier", String.valueOf(burstMultiplier));
        props.setProperty("wallJumpSpeed", String.valueOf(wallJumpSpeed));
        props.setProperty("clingSlideSpeed", String.valueOf(clingSlideSpeed));
        props.setProperty("wallRunSpeedFactor", String.valueOf(wallRunSpeedFactor));
        props.setProperty("wallRunVerticalSpeed", String.valueOf(wallRunVerticalSpeed));
        props.setProperty("cameraEffects", String.valueOf(cameraEffects));
        props.setProperty("shakeIntensity", String.valueOf(shakeIntensity));
        props.setProperty("strafeLean", String.valueOf(strafeLean));
        props.setProperty("wallRunTilt", String.valueOf(wallRunTilt));
        props.setProperty("invertTilt", String.valueOf(invertTilt));
        props.setProperty("fovBonus", String.valueOf(fovBonus));
        props.setProperty("dashJumpShift", String.valueOf(dashJumpShift));
        props.setProperty("dashBarSeconds", String.valueOf(dashBarSeconds));
        props.setProperty("dashCooldownSeconds", String.valueOf(dashCooldownSeconds));
        props.setProperty("dashDistance1", String.valueOf(dashDistance1));
        props.setProperty("dashDistance2", String.valueOf(dashDistance2));
        props.setProperty("dashDistance3", String.valueOf(dashDistance3));
        props.setProperty("slideDistance", String.valueOf(slideDistance));
        props.setProperty("slideCooldownSeconds", String.valueOf(slideCooldownSeconds));
        props.setProperty("runAnimation", String.valueOf(runAnimation));
        props.setProperty("runAnimationSpeed", String.valueOf(runAnimationSpeed));
        props.setProperty("swapWallRunAnimations", String.valueOf(swapWallRunAnimations));
        try (Writer writer = Files.newBufferedWriter(file())) {
            props.store(writer, "Parkour Mod settings");
        } catch (IOException e) {
            System.err.println("[parkourmod] Не удалось сохранить конфиг: " + e);
        }
    }

    private static float readFloat(Properties props, String key, float def, float min, float max) {
        try {
            float v = Float.parseFloat(props.getProperty(key, String.valueOf(def)));
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
