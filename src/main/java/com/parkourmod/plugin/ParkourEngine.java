package com.parkourmod.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверная реализация части механик исходного клиентского мода.
 *
 * ВАЖНО: это не порт 1:1. Клиентский мод каждый тик напрямую переписывал
 * скорость LocalPlayer на основании сырых состояний клавиш (Options.keyXxx.isDown()).
 * На Paper-сервере таких данных просто не существует — сервер видит только:
 *  - onGround, isSneaking(), isSprinting() — как живые, постоянно актуальные флаги;
 *  - дискретные события (прыжок, начало/конец сика, начало/конец спринта);
 *  - позицию игрока раз в тик (из неё вычисляется скорость/направление движения).
 * Runtime-логика ниже построена вокруг того, что реально доступно на сервере,
 * а не является копией mixin-кода мода.
 */
public final class ParkourEngine {

    private final JavaPlugin plugin;
    private final ParkourConfig cfg;
    private final Map<UUID, ParkourState> states = new ConcurrentHashMap<>();
    private final NamespacedKey burstKey;
    private BukkitTask task;
    private int tickCounter;

    public ParkourEngine(JavaPlugin plugin, ParkourConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.burstKey = new NamespacedKey(plugin, "parkour-burst-speed");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID id : states.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) clearBurstModifier(p, states.get(id));
        }
        states.clear();
    }

    public ParkourState stateOf(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), id -> new ParkourState());
    }

    public void forget(Player p) {
        ParkourState s = states.remove(p.getUniqueId());
        if (s != null) clearBurstModifier(p, s);
    }

    public void disable(Player p, ParkourState s) {
        exitState(s);
        s.burstTicks = 0;
        s.burstCooldown = 0;
        clearBurstModifier(p, s);
    }

    // ------------------------------------------------------------------ тик-луп

    private void tickAll() {
        tickCounter++;
        for (Map.Entry<UUID, ParkourState> e : states.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.isOnline()) continue;
            ParkourState s = e.getValue();
            if (!s.enabled) continue;
            tick(p, s);
        }
    }

    private void tick(Player p, ParkourState s) {
        tickTimers(s);

        boolean ground = p.isOnGround();
        s.airTicks = ground ? 0 : s.airTicks + 1;

        Location now = p.getLocation();
        if (s.lastLocation != null) {
            double dx = now.getX() - s.lastLocation.getX();
            double dz = now.getZ() - s.lastLocation.getZ();
            double disp = Math.hypot(dx, dz);
            s.lastDisp = disp;
            s.recentSpeed = Math.max(disp, s.recentSpeed * 0.9);
            if (disp > 0.05) {
                s.lastMoveDir = new Vector(dx / disp, 0, dz / disp);
            }
        }
        s.lastLocation = now;

        tickBurst(p, s);

        if (!canParkour(p)) {
            exitState(s);
            s.wasOnGround = ground;
            return;
        }

        switch (s.state) {
            case NONE -> tryStart(p, s);
            case CLING -> tickCling(p, s);
            case WALL_RUN -> tickWallRun(p, s);
            case MANTLE -> tickMantle(p, s);
            case DASH_CHARGE -> tickCharge(p, s);
            case SLIDE -> tickSlide(p, s);
        }

        s.wasOnGround = ground;
    }

    private void tickTimers(ParkourState s) {
        if (s.dashCooldown > 0) s.dashCooldown--;
        if (s.slideCooldown > 0) s.slideCooldown--;
        if (s.noGrabTicks > 0) s.noGrabTicks--;
        if (s.wallJumpTicks > 0) s.wallJumpTicks--;
    }

    private boolean canParkour(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR
                && !p.isFlying() && !p.isGliding() && !p.isInsideVehicle()
                && !p.isSwimming() && !p.isInWater() && !p.isClimbing();
    }

    private void exitState(ParkourState s) {
        s.state = ParkourState.State.NONE;
        s.stateTicks = 0;
    }

    // ------------------------------------------------------------------ старт состояний (NONE)

    private void tryStart(Player p, ParkourState s) {
        if (p.isOnGround() || s.noGrabTicks > 0) return;

        Vector look = horizontalLook(p);
        Vector prefer = s.lastMoveDir.lengthSquared() > 0.01
                ? s.lastMoveDir.clone().multiply(0.5).add(look.clone().multiply(0.5))
                : look;
        Vector dir = WallUtils.findWall(p, prefer, cfg.wallContactDistance);
        if (dir == null) return;

        if (s.burstTicks > 0 && s.recentSpeed >= cfg.wallRunMinSpeed) {
            s.state = ParkourState.State.WALL_RUN;
            s.wallDir = dir;
            s.stateTicks = 0;
            Vector t1 = new Vector(-dir.getZ(), 0, dir.getX());
            double score = t1.getX() * prefer.getX() + t1.getZ() * prefer.getZ();
            s.runDir = score >= 0 ? t1 : t1.clone().multiply(-1);
            s.runSpeed = clamp(s.recentSpeed * cfg.wallRunSpeedFactor, 0.18, 0.60);
            return;
        }
        if (p.isSneaking() && s.airTicks >= 3) {
            startCling(s, dir);
        }
    }

    private void startCling(ParkourState s, Vector dir) {
        s.state = ParkourState.State.CLING;
        s.wallDir = dir;
        s.stateTicks = 0;
    }

    // ------------------------------------------------------------------ фиксация на стене / залезание

    private void tickCling(Player p, ParkourState s) {
        s.stateTicks++;
        if (p.isOnGround() || !p.isSneaking() || !WallUtils.touching(p, s.wallDir, cfg.wallContactDistance)) {
            exitState(s);
            return;
        }
        if (WallUtils.hasLedge(p, s.wallDir, cfg.ledgeMaxHeight)) {
            // В оригинале для этого нужно было ещё держать W; на сервере "удержание W"
            // не наблюдаемо (см. класс ParkourEngine выше), поэтому подтягивание
            // на уступ запускается автоматически, как только он найден.
            s.state = ParkourState.State.MANTLE;
            s.stateTicks = 0;
            tickMantle(p, s);
            return;
        }
        Vector v = p.getVelocity();
        double y = v.getY() + (-cfg.clingSlideSpeed - v.getY()) * 0.5;
        p.setVelocity(new Vector(0, y, 0));
        p.setFallDistance(0f);
        p.setSprinting(false);
    }

    private void tickMantle(Player p, ParkourState s) {
        s.stateTicks++;
        if (s.stateTicks > 30 || (p.isOnGround() && s.stateTicks > 3)) {
            exitState(s);
            s.noGrabTicks = 6;
            return;
        }
        Location loc = p.getLocation();
        Vector wallDir = s.wallDir;
        double fx = loc.getX() + wallDir.getX() * 0.5;
        double fz = loc.getZ() + wallDir.getZ() * 0.5;
        boolean forwardFree = !WallUtils.solidAt(p.getWorld(), fx, loc.getY() + 0.2, fz)
                && !WallUtils.solidAt(p.getWorld(), fx, loc.getY() + 1.6, fz);
        if (forwardFree) {
            p.setVelocity(new Vector(wallDir.getX() * 0.20, 0.0, wallDir.getZ() * 0.20));
        } else {
            p.setVelocity(new Vector(wallDir.getX() * 0.02, 0.34, wallDir.getZ() * 0.02));
        }
        p.setFallDistance(0f);
        p.setSprinting(false);
    }

    // ------------------------------------------------------------------ бег по стене / отскок

    private void tickWallRun(Player p, ParkourState s) {
        s.stateTicks++;
        if (p.isOnGround() || s.burstTicks <= 0 || s.stateTicks > cfg.wallRunMaxTicks
                || !WallUtils.touching(p, s.wallDir, cfg.wallContactDistance)) {
            exitState(s);
            return;
        }
        if (p.isSneaking()) {
            startCling(s, s.wallDir);
            return;
        }
        float pitch = p.getLocation().getPitch();
        double look = clamp(-pitch / 45.0, -1.0, 1.0);
        if (Math.abs(look) < 0.1) look = 0.0;
        double progress = s.stateTicks / (double) cfg.wallRunMaxTicks;
        double targetY = look * cfg.wallRunVerticalSpeed - 0.004 - 0.02 * progress;
        Vector v = p.getVelocity();
        double blend = s.stateTicks <= 3 ? 0.7 : 0.35;
        double y = v.getY() + (targetY - v.getY()) * blend;
        p.setVelocity(new Vector(
                s.runDir.getX() * s.runSpeed + s.wallDir.getX() * 0.02,
                y,
                s.runDir.getZ() * s.runSpeed + s.wallDir.getZ() * 0.02));
        p.setFallDistance(0f);
    }

    /** Вызывается из слушателя PlayerJumpEvent, когда игрок фиксируется/бежит по стене. */
    void wallJump(Player p, ParkourState s, boolean fromRun) {
        Vector away = new Vector(-s.wallDir.getX(), 0, -s.wallDir.getZ());
        Vector dir;
        double speed;
        if (fromRun) {
            dir = normalizeH(away.clone().multiply(0.85).add(s.runDir.clone().multiply(0.5)));
            speed = Math.max(s.runSpeed, 0.45);
        } else {
            Vector look = horizontalLook(p);
            double lookAway = look.getX() * away.getX() + look.getZ() * away.getZ();
            dir = lookAway > 0.15
                    ? normalizeH(away.clone().multiply(0.6).add(look.clone().multiply(0.4)))
                    : away;
            speed = cfg.wallJumpSpeed;
        }
        p.setVelocity(new Vector(dir.getX() * speed, cfg.wallJumpVertical, dir.getZ() * speed));
        p.setFallDistance(0f);
        exitState(s);
        s.noGrabTicks = 10;
        s.wallJumpTicks = 18;
    }

    // ------------------------------------------------------------------ рывок (dash)

    /** Вызывается из слушателя PlayerJumpEvent, когда игрок на земле и уже сидит (sneak). */
    void startDashCharge(Player p, ParkourState s) {
        s.state = ParkourState.State.DASH_CHARGE;
        s.stateTicks = 0;
        s.dashPhase = 0f;
    }

    private void tickCharge(Player p, ParkourState s) {
        s.stateTicks++;
        if (!p.isOnGround() || !p.isSneaking()) {
            // Потеряли землю, либо снятие сика почему-то не дошло до releaseDash() —
            // подстраховка: просто гасим заряд без рывка.
            exitState(s);
            return;
        }
        s.dashPhase += 1f;
        int section = sectionOf(dashMarker(s));
        Component msg = switch (section) {
            case 0 -> Component.text("Рывок: КОРОТКИЙ — отпустите Шифт для прыжка").color(net.kyori.adventure.text.format.NamedTextColor.GREEN);
            case 1 -> Component.text("Рывок: СРЕДНИЙ").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW);
            default -> Component.text("Рывок: ДАЛЬНИЙ").color(net.kyori.adventure.text.format.NamedTextColor.RED);
        };
        p.sendActionBar(msg);
    }

    /** Вызывается из слушателя PlayerToggleSneakEvent при отпускании sneak во время заряда. */
    void releaseDash(Player p, ParkourState s) {
        if (s.state != ParkourState.State.DASH_CHARGE) return;
        int section = sectionOf(dashMarker(s));
        double dist = section == 0 ? cfg.dashDistance1 : section == 1 ? cfg.dashDistance2 : cfg.dashDistance3;
        double vy = ParkourConfig.DASH_VY[section];
        double speed = dashSpeedFor(dist, vy);
        Vector look = horizontalLook(p);
        p.setVelocity(new Vector(look.getX() * speed, vy, look.getZ() * speed));
        p.setFallDistance(0f);
        exitState(s);
        s.dashCooldown = cfg.dashCooldownTicks();
        s.wallJumpTicks = 30;
    }

    private float dashMarker(ParkourState s) {
        float sweepTicks = Math.max(4.0f, (float) (cfg.dashBarSeconds * 20.0f));
        float c = (s.dashPhase / sweepTicks) % 2.0f;
        return c <= 1.0f ? c : 2.0f - c;
    }

    private int sectionOf(float t) {
        return t < (1.0f / 3.0f) ? 0 : (t < (2.0f / 3.0f) ? 1 : 2);
    }

    /**
     * Какая горизонтальная скорость нужна, чтобы при вертикальном импульсе vy0
     * приземлиться примерно на distance блоков дальше (ровная поверхность).
     * Формула перенесена без изменений из исходного мода — она моделирует
     * ванильное трение/гравитацию и не зависит от того, клиент это считает или сервер.
     */
    private static double dashSpeedFor(double distance, double vy0) {
        double y = 0.0;
        double vy = vy0;
        int ticks = 0;
        do {
            y += vy;
            vy = (vy - 0.08) * 0.98;
            ticks++;
        } while (y > 0.0 && ticks < 100);

        double factor = 0.0;
        double v = 1.0;
        for (int k = 0; k < ticks; k++) {
            factor += v;
            v *= (k == 0) ? 0.546 : 0.91;
        }
        return distance / factor;
    }

    // ------------------------------------------------------------------ скольжение (slide)

    /** Вызывается из слушателей PlayerToggleSneakEvent / PlayerToggleSprintEvent. */
    void startSlide(Player p, ParkourState s) {
        Vector look = horizontalLook(p);
        Vector dir = (s.lastDisp > 0.05 && s.lastMoveDir.lengthSquared() > 0.5) ? s.lastMoveDir : look;
        double s0 = 0.55;
        double stop = 0.05;
        double dInf = cfg.slideDistance / (1.0 - stop / s0);
        s.slideDir = dir;
        s.slideSpeed = s0;
        s.slideDecay = 1.0 - s0 / dInf;
        s.slideTraveled = 0.0;
        s.slideAirTicks = 0;
        s.state = ParkourState.State.SLIDE;
        s.stateTicks = 0;
    }

    private void tickSlide(Player p, ParkourState s) {
        s.stateTicks++;
        if (s.stateTicks > 1) s.slideTraveled += s.lastDisp;
        s.slideAirTicks = p.isOnGround() ? 0 : s.slideAirTicks + 1;

        if (s.slideAirTicks > 3 || s.slideSpeed < 0.05 || s.slideTraveled >= cfg.slideDistance) {
            endSlide(s);
            return;
        }
        Vector v = p.getVelocity();
        p.setVelocity(new Vector(s.slideDir.getX() * s.slideSpeed, v.getY(), s.slideDir.getZ() * s.slideSpeed));
        s.slideSpeed *= s.slideDecay;
        p.setSprinting(false);
    }

    private void endSlide(ParkourState s) {
        exitState(s);
        s.slideCooldown = cfg.slideCooldownTicks();
    }

    /** Вызывается из слушателя PlayerJumpEvent, когда игрок прыгает во время скольжения. */
    void exitSlideOnJump(Player p, ParkourState s) {
        // Прыжок не отменяется (в отличие от wallJump/dash) — горизонтальная скорость
        // скольжения уже "живёт" в реальной скорости игрока, поэтому она естественным
        // образом сохранится и после ванильного прыжка.
        endSlide(s);
    }

    // ------------------------------------------------------------------ разбег (burst)

    /** Вызывается из слушателя PlayerSwapHandItemsEvent. */
    void requestBurst(Player p, ParkourState s) {
        if (!s.enabled || s.burstTicks > 0) return;
        if (s.burstCooldown > 0) {
            p.sendActionBar(Component.text("Перезарядка разбега: " + fmt(s.burstCooldown / 20.0) + " с"));
            return;
        }
        if (!p.isSprinting()) {
            p.sendActionBar(Component.text("Нужен спринт для разбега"));
            return;
        }
        s.burstTicks = cfg.burstTicks();
        s.burstElapsed = 0;
    }

    private void tickBurst(Player p, ParkourState s) {
        if (s.burstTicks > 0) {
            s.burstTicks--;
            s.burstElapsed++;
            if (s.burstTicks == 0) s.burstCooldown = cfg.burstCooldownTicks();
        } else if (s.burstCooldown > 0) {
            s.burstCooldown--;
        }

        double factor = burstFactor(s);
        if (factor <= 0.001 && s.burstModifier == null) return;

        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        if (s.burstModifier != null) {
            attr.removeModifier(s.burstModifier);
            s.burstModifier = null;
        }
        if (factor > 0.001) {
            AttributeModifier mod = new AttributeModifier(burstKey,
                    (cfg.burstMultiplier - 1.0) * factor, Operation.ADD_SCALAR, EquipmentSlotGroup.ANY);
            attr.addModifier(mod);
            s.burstModifier = mod;
        }
        if (s.burstTicks > 0 && tickCounter % 4 == 0) {
            p.sendActionBar(Component.text("Разбег: " + fmt(s.burstTicks / 20.0) + " с"));
        }
    }

    private double burstFactor(ParkourState s) {
        if (s.burstTicks <= 0) return 0.0;
        double ramp = ease(clamp(s.burstElapsed / (double) ParkourConfig.BURST_RAMP_TICKS, 0, 1));
        double fade = ease(clamp(s.burstTicks / (double) ParkourConfig.BURST_FADE_TICKS, 0, 1));
        return ramp * fade;
    }

    private void clearBurstModifier(Player p, ParkourState s) {
        if (s.burstModifier == null) return;
        AttributeInstance attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(s.burstModifier);
        s.burstModifier = null;
    }

    // ------------------------------------------------------------------ геометрия/утилиты

    private static Vector horizontalLook(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static Vector normalizeH(Vector v) {
        double len = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        return len < 1.0E-6 ? new Vector() : new Vector(v.getX() / len, 0.0, v.getZ() / len);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double ease(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static String fmt(double seconds) {
        return String.format(java.util.Locale.US, "%.1f", seconds);
    }
}
