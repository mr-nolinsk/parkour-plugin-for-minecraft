package com.parkourmod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Полоска рывка: 3 секции (зелёная/жёлтая/красная) и ползунок, бегающий туда-обратно. */
public final class DashHud {
    private DashHud() {}

    private static final int GREEN = 0xFF4CAF50;
    private static final int YELLOW = 0xFFFFC107;
    private static final int RED = 0xFFE53935;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!ParkourController.isEnabled() || ParkourController.state() != ParkourController.State.DASH_CHARGE) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int seg = 40;
        int barW = seg * 3;
        int barH = 8;
        int x = (screenW - barW) / 2;
        int y = screenH / 2 + 26; // чуть ниже прицела, прямо перед глазами

        float t = ParkourController.dashMarker(partial);
        int active = ParkourController.sectionOf(t);

        // рамка и три секции
        g.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, BLACK);
        g.fill(x, y, x + seg, y + barH, GREEN);
        g.fill(x + seg, y, x + 2 * seg, y + barH, YELLOW);
        g.fill(x + 2 * seg, y, x + 3 * seg, y + barH, RED);

        // подсветка активной секции
        g.renderOutline(x + active * seg, y, seg, barH, WHITE);

        // подписи с дистанцией под каждой секцией
        String[] labels = {
                format(ParkourConfig.dashDistance1),
                format(ParkourConfig.dashDistance2),
                format(ParkourConfig.dashDistance3)
        };
        for (int i = 0; i < 3; i++) {
            int w = mc.font.width(labels[i]);
            g.drawString(mc.font, labels[i], x + i * seg + (seg - w) / 2, y + barH + 4, WHITE, true);
        }

        // ползунок поверх всего
        int mx = x + Math.round(t * (barW - 3));
        g.fill(mx - 1, y - 4, mx + 4, y + barH + 4, BLACK);
        g.fill(mx, y - 3, mx + 3, y + barH + 3, WHITE);
    }

    private static String format(float d) {
        return d == Math.rint(d) ? String.valueOf((int) d) : String.format("%.1f", d);
    }
}
