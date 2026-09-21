package com.parkourmod.plugin;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

/**
 * Сопоставление жестов оригинального мода с тем, что реально видно серверу:
 *
 *  G (вкл/выкл режим)                -> команда /parkour
 *  R во время спринта (разбег)       -> клавиша "поменять предметы местами" (по умолчанию F) во время спринта
 *  Прыжок+Шифт, отпустить (рывок)    -> сидя (sneak) на земле нажать Прыжок — начнётся заряд; отпустить Шифт — рывок
 *  Шифт на бегу (скольжение)         -> начать sneak во время спринта (в любом порядке — тоже работает)
 *  Шифт в воздухе у стены (фиксация) -> начать/держать sneak в воздухе рядом со стеной (проверяется в тик-лупе)
 *  Прыжок при фиксации/беге по стене -> обычный прыжок в эти состояния
 *  W у края стены при фиксации       -> происходит автоматически, как только найден уступ
 *
 * "W (вперёд)" как отдельный сигнал серверу недоступен в принципе — оно нигде
 * не участвует напрямую, везде используется вычисленное направление движения.
 */
public final class ParkourListener implements Listener {

    private final ParkourEngine engine;

    public ParkourListener(ParkourEngine engine) {
        this.engine = engine;
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        ParkourState s = engine.stateOf(p);
        if (!s.enabled) return;

        switch (s.state) {
            case CLING -> {
                engine.wallJump(p, s, false);
                e.setCancelled(true);
            }
            case WALL_RUN -> {
                engine.wallJump(p, s, true);
                e.setCancelled(true);
            }
            case SLIDE -> engine.exitSlideOnJump(p, s); // прыжок НЕ отменяем
            case NONE -> {
                if (p.isOnGround() && p.isSneaking() && s.dashCooldown <= 0) {
                    engine.startDashCharge(p, s);
                    e.setCancelled(true);
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        ParkourState s = engine.stateOf(p);
        if (!s.enabled) return;

        if (e.isSneaking()) {
            if (s.state == ParkourState.State.NONE && p.isOnGround() && s.slideCooldown <= 0
                    && (p.isSprinting() || s.recentSpeed >= 0.25)) {
                engine.startSlide(p, s);
            }
            // Состояние CLING само проверяет p.isSneaking() каждый тик — отдельная
            // обработка старта фиксации здесь не нужна.
        } else {
            if (s.state == ParkourState.State.DASH_CHARGE) {
                engine.releaseDash(p, s);
            }
        }
    }

    @EventHandler
    public void onSprintToggle(PlayerToggleSprintEvent e) {
        Player p = e.getPlayer();
        ParkourState s = engine.stateOf(p);
        if (!s.enabled) return;

        // "Шифт и Спринт в любом порядке" — обратный порядок (сначала присел, потом заспринтил).
        if (e.isSprinting() && p.isSneaking() && s.state == ParkourState.State.NONE
                && p.isOnGround() && s.slideCooldown <= 0) {
            engine.startSlide(p, s);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ParkourState s = engine.stateOf(p);
        if (!s.enabled) return;

        // Эта клавиша (по умолчанию F) отдана под "разбег" и больше не меняет предметы местами.
        e.setCancelled(true);
        engine.requestBurst(p, s);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        engine.forget(e.getPlayer());
    }
}
