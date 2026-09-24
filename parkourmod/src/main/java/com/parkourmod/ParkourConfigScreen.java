package com.parkourmod;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран настроек на Cloth Config. Открывается из Mod Menu. */
public final class ParkourConfigScreen {
    private ParkourConfigScreen() {}

    private static Component t(String key) {
        return Component.translatable("config.parkourmod." + key);
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(t("title"));
        builder.setSavingRunnable(ParkourConfig::save);
        ConfigEntryBuilder eb = builder.entryBuilder();

        // ---------------- Разбег ----------------
        ConfigCategory burst = builder.getOrCreateCategory(t("category.burst"));
        burst.addEntry(eb.startFloatField(t("burst_seconds"), ParkourConfig.burstSeconds)
                .setDefaultValue(ParkourConfig.DEF_BURST_SECONDS).setMin(1.0f).setMax(10.0f)
                .setTooltip(t("burst_seconds.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.burstSeconds = v).build());
        burst.addEntry(eb.startFloatField(t("burst_cooldown"), ParkourConfig.burstCooldownSeconds)
                .setDefaultValue(ParkourConfig.DEF_BURST_COOLDOWN_SECONDS).setMin(0.0f).setMax(15.0f)
                .setTooltip(t("burst_cooldown.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.burstCooldownSeconds = v).build());
        burst.addEntry(eb.startFloatField(t("burst_multiplier"), ParkourConfig.burstMultiplier)
                .setDefaultValue(ParkourConfig.DEF_BURST_MULTIPLIER).setMin(1.0f).setMax(3.0f)
                .setTooltip(t("burst_multiplier.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.burstMultiplier = v).build());

        // ---------------- Стены ----------------
        ConfigCategory walls = builder.getOrCreateCategory(t("category.walls"));
        walls.addEntry(eb.startFloatField(t("wall_jump_speed"), ParkourConfig.wallJumpSpeed)
                .setDefaultValue(ParkourConfig.DEF_WALL_JUMP_SPEED).setMin(0.2f).setMax(0.8f)
                .setTooltip(t("wall_jump_speed.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.wallJumpSpeed = v).build());
        walls.addEntry(eb.startFloatField(t("cling_slide_speed"), ParkourConfig.clingSlideSpeed)
                .setDefaultValue(ParkourConfig.DEF_CLING_SLIDE_SPEED).setMin(0.0f).setMax(0.2f)
                .setTooltip(t("cling_slide_speed.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.clingSlideSpeed = v).build());
        walls.addEntry(eb.startFloatField(t("wall_run_speed"), ParkourConfig.wallRunSpeedFactor)
                .setDefaultValue(ParkourConfig.DEF_WALL_RUN_SPEED_FACTOR).setMin(0.4f).setMax(1.2f)
                .setTooltip(t("wall_run_speed.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.wallRunSpeedFactor = v).build());
        walls.addEntry(eb.startFloatField(t("wall_run_vertical"), ParkourConfig.wallRunVerticalSpeed)
                .setDefaultValue(ParkourConfig.DEF_WALL_RUN_VERTICAL_SPEED).setMin(0.0f).setMax(0.25f)
                .setTooltip(t("wall_run_vertical.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.wallRunVerticalSpeed = v).build());

        // ---------------- Камера ----------------
        ConfigCategory camera = builder.getOrCreateCategory(t("category.camera"));
        camera.addEntry(eb.startBooleanToggle(t("camera_effects"), ParkourConfig.cameraEffects)
                .setDefaultValue(ParkourConfig.DEF_CAMERA_EFFECTS)
                .setTooltip(t("camera_effects.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.cameraEffects = v).build());
        camera.addEntry(eb.startFloatField(t("shake"), ParkourConfig.shakeIntensity)
                .setDefaultValue(ParkourConfig.DEF_SHAKE_INTENSITY).setMin(0.0f).setMax(3.0f)
                .setTooltip(t("shake.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.shakeIntensity = v).build());
        camera.addEntry(eb.startFloatField(t("strafe_lean"), ParkourConfig.strafeLean)
                .setDefaultValue(ParkourConfig.DEF_STRAFE_LEAN).setMin(0.0f).setMax(8.0f)
                .setTooltip(t("strafe_lean.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.strafeLean = v).build());
        camera.addEntry(eb.startFloatField(t("wall_run_tilt"), ParkourConfig.wallRunTilt)
                .setDefaultValue(ParkourConfig.DEF_WALL_RUN_TILT).setMin(0.0f).setMax(30.0f)
                .setTooltip(t("wall_run_tilt.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.wallRunTilt = v).build());
        camera.addEntry(eb.startBooleanToggle(t("invert_tilt"), ParkourConfig.invertTilt)
                .setDefaultValue(ParkourConfig.DEF_INVERT_TILT)
                .setTooltip(t("invert_tilt.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.invertTilt = v).build());
        camera.addEntry(eb.startFloatField(t("fov_bonus"), ParkourConfig.fovBonus)
                .setDefaultValue(ParkourConfig.DEF_FOV_BONUS).setMin(0.0f).setMax(25.0f)
                .setTooltip(t("fov_bonus.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.fovBonus = v).build());

        // ---------------- Рывок и скольжение ----------------
        ConfigCategory moves = builder.getOrCreateCategory(t("category.moves"));
        moves.addEntry(eb.startBooleanToggle(t("dash_jump_shift"), ParkourConfig.dashJumpShift)
                .setDefaultValue(ParkourConfig.DEF_DASH_JUMP_SHIFT)
                .setTooltip(t("dash_jump_shift.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashJumpShift = v).build());
        moves.addEntry(eb.startFloatField(t("dash_bar_seconds"), ParkourConfig.dashBarSeconds)
                .setDefaultValue(ParkourConfig.DEF_DASH_BAR_SECONDS).setMin(0.4f).setMax(4.0f)
                .setTooltip(t("dash_bar_seconds.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashBarSeconds = v).build());
        moves.addEntry(eb.startFloatField(t("dash_distance_1"), ParkourConfig.dashDistance1)
                .setDefaultValue(ParkourConfig.DEF_DASH_DISTANCE_1).setMin(1.0f).setMax(15.0f)
                .setTooltip(t("dash_distance.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashDistance1 = v).build());
        moves.addEntry(eb.startFloatField(t("dash_distance_2"), ParkourConfig.dashDistance2)
                .setDefaultValue(ParkourConfig.DEF_DASH_DISTANCE_2).setMin(1.0f).setMax(15.0f)
                .setTooltip(t("dash_distance.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashDistance2 = v).build());
        moves.addEntry(eb.startFloatField(t("dash_distance_3"), ParkourConfig.dashDistance3)
                .setDefaultValue(ParkourConfig.DEF_DASH_DISTANCE_3).setMin(1.0f).setMax(15.0f)
                .setTooltip(t("dash_distance.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashDistance3 = v).build());
        moves.addEntry(eb.startFloatField(t("dash_cooldown"), ParkourConfig.dashCooldownSeconds)
                .setDefaultValue(ParkourConfig.DEF_DASH_COOLDOWN_SECONDS).setMin(0.0f).setMax(10.0f)
                .setTooltip(t("dash_cooldown.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.dashCooldownSeconds = v).build());
        moves.addEntry(eb.startFloatField(t("slide_distance"), ParkourConfig.slideDistance)
                .setDefaultValue(ParkourConfig.DEF_SLIDE_DISTANCE).setMin(2.0f).setMax(12.0f)
                .setTooltip(t("slide_distance.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.slideDistance = v).build());
        moves.addEntry(eb.startFloatField(t("slide_cooldown"), ParkourConfig.slideCooldownSeconds)
                .setDefaultValue(ParkourConfig.DEF_SLIDE_COOLDOWN_SECONDS).setMin(0.0f).setMax(10.0f)
                .setTooltip(t("slide_cooldown.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.slideCooldownSeconds = v).build());

        // ---------------- Анимация ----------------
        ConfigCategory anim = builder.getOrCreateCategory(t("category.animation"));
        anim.addEntry(eb.startBooleanToggle(t("run_animation"), ParkourConfig.runAnimation)
                .setDefaultValue(ParkourConfig.DEF_RUN_ANIMATION)
                .setTooltip(t("run_animation.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.runAnimation = v).build());
        anim.addEntry(eb.startFloatField(t("run_animation_speed"), ParkourConfig.runAnimationSpeed)
                .setDefaultValue(ParkourConfig.DEF_RUN_ANIMATION_SPEED).setMin(0.5f).setMax(2.0f)
                .setTooltip(t("run_animation_speed.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.runAnimationSpeed = v).build());

        anim.addEntry(eb.startBooleanToggle(t("swap_wall_run"), ParkourConfig.swapWallRunAnimations)
                .setDefaultValue(ParkourConfig.DEF_SWAP_WALL_RUN_ANIMATIONS)
                .setTooltip(t("swap_wall_run.tooltip"))
                .setSaveConsumer(v -> ParkourConfig.swapWallRunAnimations = v).build());

        return builder.build();
    }
}
