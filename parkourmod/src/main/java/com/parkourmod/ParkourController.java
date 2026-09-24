package com.parkourmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Вся паркур-механика. Работает только на клиенте: методы before/after вызываются
 * из LocalPlayerMixin в начале и в конце LocalPlayer.aiStep() (то есть раз в тик).
 */
public final class ParkourController {
    private ParkourController() {}

    public enum State { NONE, CLING, WALL_RUN, MANTLE, DASH_CHARGE, SLIDE }

    /** Четыре горизонтальных направления (единичные векторы). */
    private static final Vec3[] DIRS = {
            new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 0, -1)
    };

    private static boolean enabled;
    private static State state = State.NONE;
    /** Направление ВНУТРЬ стены (единичный горизонтальный вектор). */
    private static Vec3 wallDir = Vec3.ZERO;
    /** Направление бега вдоль стены. */
    private static Vec3 runDir = Vec3.ZERO;
    private static double runSpeed;
    private static int stateTicks;
    private static int mantleForwardTicks;

    private static int airTicks;
    private static int noGrabTicks;
    /** Пока > 0, разбег не "догоняет" смещение (после отскока и рывка, чтобы не удваивать дальность). */
    private static int wallJumpTicks;

    // --- Рывок (dash) ---
    private static final double[] DASH_VY = {0.42, 0.46, 0.50};
    private static float dashPhase;
    private static float dashPrevPhase;
    private static int dashCooldown;
    /** Пока > 0, обычный прыжок отменяется (чтобы он не смешивался с рывком). */
    private static int suppressJumpTicks;

    // --- Скольжение (slide) ---
    private static Vec3 slideDir = Vec3.ZERO;
    private static double slideSpeed;
    private static double slideDecay;
    private static double slideTraveled;
    private static int slideAirTicks;
    private static int slideCooldown;

    private static boolean prevShift;
    private static boolean prevSprintKey;
    private static boolean prevSlideKey;
    private static boolean prevWallJump;
    /** Рывок заряжается сочетанием Прыжок+Шифт (а не своей клавишей): тогда обычный прыжок отменяем. */
    private static boolean dashByCombo = true;

    private static int burstTicks;
    private static int burstElapsed;
    private static int burstCooldown;

    private static boolean prevJump;
    private static boolean wasOnGround = true;
    private static double recentSpeed;
    private static double lastDisp;
    private static double headVy;
    private static Vec3 lastMoveDir = Vec3.ZERO;
    private static Vec3 headPos = Vec3.ZERO;
    private static LocalPlayer tracked;

    // ------------------------------------------------------------------ публичное API

    public static boolean isEnabled() { return enabled; }
    public static State state() { return state; }
    public static Vec3 wallDir() { return wallDir; }
    public static double lastDisplacement() { return lastDisp; }
    public static boolean isSliding() { return state == State.SLIDE; }

    /** Положение ползунка рывка 0..1 (треугольная волна: туда-обратно), с интерполяцией по кадрам. */
    public static float dashMarker(float partial) {
        float sweepTicks = Math.max(4.0f, ParkourConfig.dashBarSeconds * 20.0f);
        float ph = Mth.lerp(partial, dashPrevPhase, dashPhase);
        float c = (ph / sweepTicks) % 2.0f;
        return c <= 1.0f ? c : 2.0f - c;
    }

    /** 0 = зелёная, 1 = жёлтая, 2 = красная секция. */
    public static int sectionOf(float t) {
        return t < (1.0f / 3.0f) ? 0 : (t < (2.0f / 3.0f) ? 1 : 2);
    }

    /** Текущая секция (по последнему тику). */
    public static int dashSection() {
        return sectionOf(dashMarker(1.0f));
    }

    /** Нужно ли отменить ванильный прыжок в этот тик (вызывается из LivingEntityMixin). */
    public static boolean shouldBlockJump(LocalPlayer p) {
        return enabled && p == tracked && ((state == State.DASH_CHARGE && dashByCombo) || suppressJumpTicks > 0);
    }

    /** 0..1: насколько сейчас "разогнан" разбег (плавный вход и выход). */
    public static float burstFactor() {
        if (burstTicks <= 0) return 0.0f;
        float ramp = ease(Mth.clamp(burstElapsed / (float) ParkourConfig.BURST_RAMP_TICKS, 0.0f, 1.0f));
        float fade = ease(Mth.clamp(burstTicks / (float) ParkourConfig.BURST_FADE_TICKS, 0.0f, 1.0f));
        return ramp * fade;
    }

    public static void toggle(LocalPlayer p) {
        enabled = !enabled;
        if (!enabled) {
            exitState();
            burstTicks = 0;
            burstCooldown = 0;
        }
        p.displayClientMessage(Component.translatable(
                enabled ? "message.parkourmod.enabled" : "message.parkourmod.disabled"), true);
    }

    public static void requestBurst(LocalPlayer p) {
        if (!enabled || burstTicks > 0) return;
        if (burstCooldown > 0) {
            p.displayClientMessage(Component.translatable("message.parkourmod.cooldown",
                    String.format("%.1f", burstCooldown / 20.0)), true);
            return;
        }
        if (!p.isSprinting()) {
            p.displayClientMessage(Component.translatable("message.parkourmod.need_sprint"), true);
            return;
        }
        burstTicks = ParkourConfig.burstTicks();
        burstElapsed = 0;
    }

    public static void showHud(LocalPlayer p) {
        if (enabled && burstTicks > 0 && p.tickCount % 4 == 0) {
            p.displayClientMessage(Component.translatable("message.parkourmod.burst",
                    String.format("%.1f", burstTicks / 20.0)), true);
        }
    }

    // ------------------------------------------------------------------ хуки тика

    /** В начале LocalPlayer.aiStep(): здесь задаём скорость на этот тик. */
    public static void before(LocalPlayer p) {
        if (p != tracked) {
            fullReset();
            tracked = p;
        }
        headPos = p.position();
        headVy = p.getDeltaMovement().y;
        if (!enabled) return;

        tickTimers();
        boolean ground = p.onGround();
        airTicks = ground ? 0 : airTicks + 1;

        Options o = Minecraft.getInstance().options;
        boolean jump = o.keyJump.isDown();
        boolean jumpPressed = jump && !prevJump;
        prevJump = jump;
        boolean shift = o.keyShift.isDown();
        boolean forward = o.keyUp.isDown();
        boolean sprintKey = o.keySprint.isDown();
        boolean shiftEdge = shift && !prevShift;
        boolean sprintEdge = sprintKey && !prevSprintKey;
        prevShift = shift;
        prevSprintKey = sprintKey;

        // Назначаемые клавиши: если своя клавиша назначена - работает она, иначе стандартная (Шифт/Прыжок/W).
        boolean dashBound = ParkourKeys.isBound(ParkourKeys.dash);
        boolean dashDown = dashBound
                ? ParkourKeys.dash.isDown()
                : (ParkourConfig.dashJumpShift && jump && shift);
        boolean slideBound = ParkourKeys.isBound(ParkourKeys.slide);
        boolean slideDown = slideBound && ParkourKeys.slide.isDown();
        boolean slideTrigger = slideBound ? (slideDown && !prevSlideKey) : (shift && (shiftEdge || sprintEdge));
        prevSlideKey = slideDown;
        boolean clingHeld = ParkourKeys.down(ParkourKeys.cling, shift);
        boolean wallJumpDown = ParkourKeys.down(ParkourKeys.wallJump, jump);
        boolean wallJumpPressed = wallJumpDown && !prevWallJump;
        prevWallJump = wallJumpDown;
        boolean climbHeld = ParkourKeys.down(ParkourKeys.climb, forward);

        if (!canParkour(p)) {
            exitState();
            return;
        }

        // Во время разбега держим спринт включённым, пока жмём W.
        if (burstTicks > 0 && forward && state == State.NONE
                && !p.horizontalCollision && !p.isUsingItem()) {
            p.setSprinting(true);
        }

        if (state == State.NONE) {
            // Сначала скольжение (нужен бег), потом рывок, потом стены.
            if (!tryStartSlide(p, slideTrigger, slideBound, jump, sprintKey)
                    && !tryStartDash(p, dashDown, !dashBound)) {
                tryStart(p, clingHeld, forward);
            }
        }
        switch (state) {
            case CLING -> tickCling(p, clingHeld, climbHeld, wallJumpPressed);
            case WALL_RUN -> tickWallRun(p, clingHeld, forward, wallJumpPressed);
            case MANTLE -> tickMantle(p);
            case DASH_CHARGE -> tickCharge(p, dashDown);
            case SLIDE -> tickSlide(p, jumpPressed);
            default -> { }
        }
    }

    /** В конце LocalPlayer.tick(): поза "лёжа" и частицы скольжения (после ванильного обновления позы). */
    public static void afterTick(LocalPlayer p) {
        if (p != tracked) return;

        // Анимация бега: играет, пока паркур-режим включён и игрок бежит в спринте по земле.
        AnimClip desired = null;
        if (enabled && ParkourConfig.runAnimation && canParkour(p)) {
            if (state == State.WALL_RUN) {
                boolean left = wallOnLeft(p) != ParkourConfig.swapWallRunAnimations;
                desired = left ? AnimClips.WALL_RUN_LEFT : AnimClips.WALL_RUN_RIGHT;
            } else if (state == State.NONE && p.isSprinting() && airTicks <= 2 && !p.isUsingItem()) {
                desired = AnimClips.RUN;
            }
        }
        EmoteAnimator.tick(desired, lastDisp);

        if (!enabled || state != State.SLIDE) return;
        // Ванильная логика ставит стоячую/присед-позу - принудительно кладём игрока (как при ползании).
        p.setPose(Pose.SWIMMING);
        // Замораживаем анимацию ног/рук, чтобы игрок именно скользил лёжа, а не "полз".
        p.walkAnimation.stop();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && p.tickCount % 2 == 0) {
            mc.level.addParticle(ParticleTypes.CLOUD,
                    p.getX() - slideDir.x * 0.4 + (p.getRandom().nextDouble() - 0.5) * 0.4,
                    p.getY() + 0.05,
                    p.getZ() - slideDir.z * 0.4 + (p.getRandom().nextDouble() - 0.5) * 0.4,
                    -slideDir.x * 0.04, 0.02, -slideDir.z * 0.04);
        }
    }

    /** В конце LocalPlayer.aiStep(): ускорение разбега и обновление камеры. */
    public static void after(LocalPlayer p) {
        if (p != tracked) return;

        Vec3 pos = p.position();
        double dx = pos.x - headPos.x;
        double dz = pos.z - headPos.z;

        if (enabled && burstTicks > 0 && state == State.NONE && wallJumpTicks == 0 && canParkour(p)) {
            double mult = 1.0 + (ParkourConfig.burstMultiplier - 1.0) * burstFactor();
            double hs = Math.sqrt(dx * dx + dz * dz);
            if (mult > 1.001 && hs > 1.0E-4) {
                // Догоняем смещение до нужного множителя. Небольшой "-y" на земле нужен,
                // чтобы игра не сбросила флаг onGround.
                double y = p.onGround() ? -1.0E-3 : 0.0;
                p.move(MoverType.SELF, new Vec3(dx * (mult - 1.0), y, dz * (mult - 1.0)));
                pos = p.position();
                dx = pos.x - headPos.x;
                dz = pos.z - headPos.z;
            }
        }

        lastDisp = Math.sqrt(dx * dx + dz * dz);
        recentSpeed = Math.max(lastDisp, recentSpeed * 0.9);
        if (lastDisp > 0.05) {
            lastMoveDir = new Vec3(dx / lastDisp, 0.0, dz / lastDisp);
        }

        boolean ground = p.onGround();
        CameraEffects.tick(p, ground && !wasOnGround, headVy);
        wasOnGround = ground;
    }

    // ------------------------------------------------------------------ старт состояний

    private static void tryStart(LocalPlayer p, boolean shift, boolean forward) {
        if (p.onGround() || noGrabTicks > 0) return;

        Vec3 look = horizontalLook(p);
        Vec3 prefer = lastMoveDir.lengthSqr() > 0.01
                ? new Vec3(lastMoveDir.x * 0.5 + look.x * 0.5, 0, lastMoveDir.z * 0.5 + look.z * 0.5)
                : look;
        Vec3 dir = findWall(p, prefer);
        if (dir == null) return;

        // Бег по стене: нужен активный разбег, W и достаточная скорость.
        if (burstTicks > 0 && forward && recentSpeed >= ParkourConfig.WALL_RUN_MIN_SPEED) {
            state = State.WALL_RUN;
            wallDir = dir;
            stateTicks = 0;
            Vec3 t1 = new Vec3(-dir.z, 0, dir.x);
            runDir = (t1.x * prefer.x + t1.z * prefer.z) >= 0 ? t1 : new Vec3(-t1.x, 0, -t1.z);
            // Бежим чуть медленнее, чем подлетели (множитель настраивается).
            runSpeed = Mth.clamp(recentSpeed * ParkourConfig.wallRunSpeedFactor, 0.18, 0.60);
            return;
        }
        // Фиксация на стене: в воздухе + шифт.
        if (shift && airTicks >= 3) {
            startCling(dir);
        }
    }

    private static void startCling(Vec3 dir) {
        state = State.CLING;
        wallDir = dir;
        stateTicks = 0;
    }

    // ------------------------------------------------------------------ тики состояний

    private static void tickCling(LocalPlayer p, boolean shift, boolean forward, boolean jumpPressed) {
        stateTicks++;
        if (p.onGround() || !shift || !touching(p, wallDir)) {
            exitState();
            return;
        }
        if (jumpPressed) {
            wallJump(p, false);
            return;
        }
        if (forward && hasLedge(p, wallDir)) {
            state = State.MANTLE;
            stateTicks = 0;
            mantleForwardTicks = 0;
            tickMantle(p);
            return;
        }
        Vec3 dm = p.getDeltaMovement();
        double y = dm.y + (-ParkourConfig.clingSlideSpeed - dm.y) * 0.5;
        p.setDeltaMovement(0.0, y, 0.0);
        p.resetFallDistance();
        p.setSprinting(false);
    }

    private static void tickWallRun(LocalPlayer p, boolean shift, boolean forward, boolean jumpPressed) {
        stateTicks++;
        if (p.onGround() || !forward || burstTicks <= 0
                || stateTicks > ParkourConfig.WALL_RUN_MAX_TICKS || !touching(p, wallDir)) {
            exitState();
            return;
        }
        if (jumpPressed) {
            wallJump(p, true);
            return;
        }
        if (shift) {
            startCling(wallDir);
            tickCling(p, true, forward, false);
            return;
        }
        // Высота регулируется взглядом: смотрим вверх - бежим выше, вниз - ниже,
        // прямо - держим высоту (лёгкая усталость со временем).
        float pitch = p.getXRot(); // отрицательный = взгляд вверх
        double look = Mth.clamp(-pitch / 45.0, -1.0, 1.0);
        if (Math.abs(look) < 0.1) look = 0.0;
        double progress = stateTicks / (double) ParkourConfig.WALL_RUN_MAX_TICKS;
        double targetY = look * ParkourConfig.wallRunVerticalSpeed - 0.004 - 0.02 * progress;
        Vec3 dm = p.getDeltaMovement();
        // В первые тики подстраиваемся быстрее, чтобы не "проваливаться" при контакте со стеной.
        double blend = stateTicks <= 3 ? 0.7 : 0.35;
        double y = dm.y + (targetY - dm.y) * blend;
        // Небольшой прижим к стене (0.02), чтобы не отлипать на неровностях.
        p.setDeltaMovement(
                runDir.x * runSpeed + wallDir.x * 0.02,
                y,
                runDir.z * runSpeed + wallDir.z * 0.02);
        p.resetFallDistance();
    }

    private static void tickMantle(LocalPlayer p) {
        stateTicks++;
        if (stateTicks > 30 || mantleForwardTicks >= 5 || (p.onGround() && stateTicks > 3)) {
            exitState();
            noGrabTicks = 6;
            return;
        }
        Level level = p.level();
        AABB b = p.getBoundingBox();
        boolean forwardFree = level.noCollision(b.move(wallDir.x * 0.5, 0.0, wallDir.z * 0.5));
        if (forwardFree) {
            // Мы уже выше края уступа - выталкиваемся вперёд.
            mantleForwardTicks++;
            p.setDeltaMovement(wallDir.x * 0.20, 0.0, wallDir.z * 0.20);
        } else {
            // Ещё ниже края - подтягиваемся вверх вдоль стены.
            p.setDeltaMovement(wallDir.x * 0.02, 0.34, wallDir.z * 0.02);
        }
        p.resetFallDistance();
        p.setSprinting(false);
    }

    /** Отскок от стены. fromRun=true: из бега по стене (сохраняем скорость). */
    private static void wallJump(LocalPlayer p, boolean fromRun) {
        Vec3 away = new Vec3(-wallDir.x, 0, -wallDir.z);
        Vec3 dir;
        double speed;
        if (fromRun) {
            dir = normalizeH(new Vec3(away.x * 0.85 + runDir.x * 0.5, 0, away.z * 0.85 + runDir.z * 0.5));
            speed = Math.max(runSpeed, 0.45);
        } else {
            Vec3 look = horizontalLook(p);
            double lookAway = look.x * away.x + look.z * away.z;
            dir = lookAway > 0.15
                    ? normalizeH(new Vec3(away.x * 0.6 + look.x * 0.4, 0, away.z * 0.6 + look.z * 0.4))
                    : away;
            speed = ParkourConfig.wallJumpSpeed;
        }
        p.setDeltaMovement(dir.x * speed, ParkourConfig.WALL_JUMP_VERTICAL, dir.z * speed);
        p.resetFallDistance();
        exitState();
        noGrabTicks = 10;
        wallJumpTicks = 18;
        if (ParkourConfig.runAnimation) {
            EmoteAnimator.playOnce(AnimClips.WALL_JUMP);
        }
    }

    // ------------------------------------------------------------------ рывок

    private static boolean tryStartDash(LocalPlayer p, boolean combo, boolean byCombo) {
        if (!combo || dashCooldown > 0 || !p.onGround()) return false;
        if (Minecraft.getInstance().screen != null) return false;
        dashByCombo = byCombo;
        state = State.DASH_CHARGE;
        stateTicks = 0;
        dashPhase = 0.0f;
        dashPrevPhase = 0.0f;
        return true;
    }

    private static void tickCharge(LocalPlayer p, boolean combo) {
        stateTicks++;
        if (!p.onGround() || Minecraft.getInstance().screen != null) {
            exitState();
            return;
        }
        if (!combo) {
            releaseDash(p);
            return;
        }
        dashPrevPhase = dashPhase;
        dashPhase += 1.0f;
        p.setSprinting(false);
    }

    /** Сочетание отпущено - прыгаем на дистанцию выбранной секции. */
    private static void releaseDash(LocalPlayer p) {
        int section = sectionOf(dashMarker(1.0f));
        double dist = section == 0 ? ParkourConfig.dashDistance1
                : section == 1 ? ParkourConfig.dashDistance2
                : ParkourConfig.dashDistance3;
        double vy = DASH_VY[section];
        double speed = dashSpeedFor(dist, vy);
        Vec3 look = horizontalLook(p);
        p.setDeltaMovement(look.x * speed, vy, look.z * speed);
        p.resetFallDistance();
        exitState();
        dashCooldown = ParkourConfig.dashCooldownTicks();
        wallJumpTicks = 30;
        suppressJumpTicks = dashByCombo ? 3 : 0;
        CameraEffects.onDash(section);
    }

    /**
     * Какая горизонтальная скорость нужна, чтобы при вертикальном импульсе vy0 приземлиться
     * примерно на distance блоков дальше (ровная поверхность). Моделируем ваниль: в первый тик
     * после прыжка с земли трение 0.546, дальше воздушное 0.91; гравитация 0.08 и сопротивление 0.98.
     */
    static double dashSpeedFor(double distance, double vy0) {
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

    // ------------------------------------------------------------------ скольжение

    private static boolean tryStartSlide(LocalPlayer p, boolean trigger, boolean customKey, boolean jump,
                                         boolean sprintKey) {
        // Запуск: своя клавиша скольжения (если назначена) либо Шифт на бегу (+ Спринт в любом порядке).
        // При стандартных клавишах Прыжок + Шифт - это рывок, поэтому со скольжением не конфликтует.
        if (!p.onGround() || !trigger || (!customKey && jump)) return false;
        boolean running = p.isSprinting() || recentSpeed >= 0.25;
        if (!running) {
            if (customKey || sprintKey) {
                p.displayClientMessage(Component.translatable("message.parkourmod.slide_need_run"), true);
            }
            return false;
        }
        if (slideCooldown > 0) return false;

        Vec3 dir = (lastDisp > 0.05 && lastMoveDir.lengthSqr() > 0.5) ? lastMoveDir : horizontalLook(p);
        // Скорость падает по геометрической прогрессии; подбираем знаменатель так,
        // чтобы до остановки (скорость < stop) пройти ровно slideDistance блоков.
        double s0 = 0.55;
        double stop = 0.05;
        double dInf = ParkourConfig.slideDistance / (1.0 - stop / s0);
        slideDir = dir;
        slideSpeed = s0;
        slideDecay = 1.0 - s0 / dInf;
        slideTraveled = 0.0;
        slideAirTicks = 0;
        state = State.SLIDE;
        stateTicks = 0;
        return true;
    }

    private static void tickSlide(LocalPlayer p, boolean jumpPressed) {
        stateTicks++;
        if (stateTicks > 1) slideTraveled += lastDisp;
        slideAirTicks = p.onGround() ? 0 : slideAirTicks + 1;

        boolean blocked = stateTicks > 2 && p.horizontalCollision;
        if (slideAirTicks > 3 || blocked || slideSpeed < 0.05 || slideTraveled >= ParkourConfig.slideDistance) {
            endSlide();
            return;
        }
        Vec3 dm = p.getDeltaMovement();
        if (jumpPressed) {
            // Прыжок из скольжения: сохраняем скорость, обычный прыжок сработает сам.
            p.setDeltaMovement(slideDir.x * slideSpeed, dm.y, slideDir.z * slideSpeed);
            endSlide();
            return;
        }
        p.setDeltaMovement(slideDir.x * slideSpeed, dm.y, slideDir.z * slideSpeed);
        slideSpeed *= slideDecay;
        p.setSprinting(false);
    }

    private static void endSlide() {
        exitState();
        slideCooldown = ParkourConfig.slideCooldownTicks();
    }

    // ------------------------------------------------------------------ геометрия

    /** Ищет стену рядом с игроком. Возвращает направление внутрь стены или null. */
    private static Vec3 findWall(LocalPlayer p, Vec3 prefer) {
        Level level = p.level();
        AABB torso = torsoBox(p);
        Vec3 best = null;
        double bestScore = -0.3;
        for (Vec3 d : DIRS) {
            double k = ParkourConfig.WALL_CONTACT_DISTANCE;
            if (level.noCollision(torso.move(d.x * k, 0.0, d.z * k))) continue;
            double score = d.x * prefer.x + d.z * prefer.z;
            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }
        return best;
    }

    private static boolean touching(LocalPlayer p, Vec3 dir) {
        double k = ParkourConfig.WALL_CONTACT_DISTANCE;
        return !p.level().noCollision(torsoBox(p).move(dir.x * k, 0.0, dir.z * k));
    }

    /** Область тела без самых ног: чтобы бордюр под ногами не считался стеной. */
    private static AABB torsoBox(LocalPlayer p) {
        AABB b = p.getBoundingBox();
        return new AABB(b.minX, b.minY + 0.4, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    /** Есть ли над нами уступ, на который можно забраться. */
    private static boolean hasLedge(LocalPlayer p, Vec3 dir) {
        Level level = p.level();
        AABB b = p.getBoundingBox();
        for (double dy = 0.05; dy <= ParkourConfig.LEDGE_MAX_HEIGHT; dy += 0.05) {
            if (!level.noCollision(b.move(0.0, dy, 0.0))) return false; // потолок мешает
            if (level.noCollision(b.move(dir.x * 0.5, dy, dir.z * 0.5))) return true;
        }
        return false;
    }

    /** Стена слева от направления взгляда игрока? */
    private static boolean wallOnLeft(LocalPlayer p) {
        double yaw = Math.toRadians(p.getYRot());
        return wallDir.x * Math.cos(yaw) + wallDir.z * Math.sin(yaw) > 0.0;
    }

    private static Vec3 horizontalLook(LocalPlayer p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static Vec3 normalizeH(Vec3 v) {
        double len = Math.sqrt(v.x * v.x + v.z * v.z);
        return len < 1.0E-6 ? Vec3.ZERO : new Vec3(v.x / len, 0.0, v.z / len);
    }

    // ------------------------------------------------------------------ служебное

    private static boolean canParkour(LocalPlayer p) {
        return p.isAlive() && !p.isSpectator() && !p.getAbilities().flying && !p.isFallFlying()
                && !p.isPassenger() && !p.isInWater() && !p.isInLava() && !p.isSwimming()
                && !p.onClimbable();
    }

    private static void tickTimers() {
        if (dashCooldown > 0) dashCooldown--;
        if (slideCooldown > 0) slideCooldown--;
        if (suppressJumpTicks > 0) suppressJumpTicks--;
        if (noGrabTicks > 0) noGrabTicks--;
        if (wallJumpTicks > 0) wallJumpTicks--;
        if (burstCooldown > 0) burstCooldown--;
        if (burstTicks > 0) {
            burstTicks--;
            burstElapsed++;
            if (burstTicks == 0) burstCooldown = ParkourConfig.burstCooldownTicks();
        }
    }

    private static void exitState() {
        state = State.NONE;
        stateTicks = 0;
    }

    private static void fullReset() {
        state = State.NONE;
        stateTicks = 0;
        airTicks = 0;
        noGrabTicks = 0;
        wallJumpTicks = 0;
        dashCooldown = 0;
        slideCooldown = 0;
        suppressJumpTicks = 0;
        prevShift = false;
        prevSprintKey = false;
        prevSlideKey = false;
        prevWallJump = false;
        burstTicks = 0;
        burstCooldown = 0;
        recentSpeed = 0.0;
        lastDisp = 0.0;
        lastMoveDir = Vec3.ZERO;
        prevJump = false;
        wasOnGround = true;
    }

    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
