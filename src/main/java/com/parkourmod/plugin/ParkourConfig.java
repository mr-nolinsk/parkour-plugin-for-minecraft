package com.parkourmod.plugin;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Настраиваемые параметры плагина, загружаются из config.yml.
 * Единицы скорости/дистанции — блоки/тик, как во внутренней физике Minecraft
 * (Bukkit-вектор скорости использует ровно те же единицы, поэтому числа
 * из исходного ParkourConfig.java мода перенесены без пересчёта).
 */
public final class ParkourConfig {

    // Фиксированные значения (в оригинале тоже были константами, не вынесены в конфиг).
    public static final int BURST_RAMP_TICKS = 20;
    public static final int BURST_FADE_TICKS = 10;
    public static final double[] DASH_VY = {0.42, 0.46, 0.50};

    public double wallContactDistance;
    public double wallJumpVertical;
    public double wallJumpSpeed;
    public double clingSlideSpeed;

    public double wallRunMinSpeed;
    public int wallRunMaxTicks;
    public double wallRunSpeedFactor;
    public double wallRunVerticalSpeed;

    public double ledgeMaxHeight;

    public double burstSeconds;
    public double burstCooldownSeconds;
    public double burstMultiplier;

    public double dashBarSeconds;
    public double dashCooldownSeconds;
    public double dashDistance1;
    public double dashDistance2;
    public double dashDistance3;

    public double slideDistance;
    public double slideCooldownSeconds;

    public ParkourConfig(FileConfiguration c) {
        reload(c);
    }

    public void reload(FileConfiguration c) {
        wallContactDistance = c.getDouble("wallContactDistance", 0.06);
        wallJumpVertical = c.getDouble("wallJump.vertical", 0.46);
        wallJumpSpeed = c.getDouble("wallJump.speed", 0.44);
        clingSlideSpeed = c.getDouble("cling.slideSpeed", 0.04);

        wallRunMinSpeed = c.getDouble("wallRun.minSpeed", 0.30);
        wallRunMaxTicks = c.getInt("wallRun.maxTicks", 100);
        wallRunSpeedFactor = c.getDouble("wallRun.speedFactor", 0.75);
        wallRunVerticalSpeed = c.getDouble("wallRun.verticalSpeed", 0.10);

        ledgeMaxHeight = c.getDouble("ledge.maxHeight", 1.25);

        burstSeconds = c.getDouble("burst.seconds", 4.0);
        burstCooldownSeconds = c.getDouble("burst.cooldownSeconds", 3.0);
        burstMultiplier = c.getDouble("burst.multiplier", 2.0);

        dashBarSeconds = c.getDouble("dash.barSeconds", 1.2);
        dashCooldownSeconds = c.getDouble("dash.cooldownSeconds", 1.0);
        dashDistance1 = c.getDouble("dash.distance1", 3.0);
        dashDistance2 = c.getDouble("dash.distance2", 5.0);
        dashDistance3 = c.getDouble("dash.distance3", 7.0);

        slideDistance = c.getDouble("slide.distance", 5.0);
        slideCooldownSeconds = c.getDouble("slide.cooldownSeconds", 1.0);
    }

    public int burstTicks() { return Math.max(1, (int) Math.round(burstSeconds * 20.0)); }
    public int burstCooldownTicks() { return Math.max(0, (int) Math.round(burstCooldownSeconds * 20.0)); }
    public int dashCooldownTicks() { return Math.max(0, (int) Math.round(dashCooldownSeconds * 20.0)); }
    public int slideCooldownTicks() { return Math.max(0, (int) Math.round(slideCooldownSeconds * 20.0)); }
}
