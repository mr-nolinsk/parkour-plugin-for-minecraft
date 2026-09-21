package com.parkourmod.plugin;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Поиск стен/уступов вокруг игрока.
 *
 * В оригинальном клиентском моде это делалось точной проверкой AABB игрока
 * против коллизии мира (level.noCollision(box)). На сервере у нас нет такого
 * же прямого доступа к форме блоков "как в клиенте за один вызов", поэтому
 * здесь используется более простая, но вполне надёжная на практике проверка
 * по сетке блоков (isSolid() в паре точек на уровне пояса/груди игрока).
 * Это приближение, а не побитовая копия оригинальной геометрии.
 */
final class WallUtils {

    private static final double[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private WallUtils() {}

    /** Ищет стену рядом с игроком, возвращает единичный горизонтальный вектор внутрь стены или null. */
    static Vector findWall(Player p, Vector prefer, double contactDistance) {
        Vector best = null;
        double bestScore = -0.3;
        for (double[] d : DIRS) {
            if (!blocked(p, d[0], d[1], contactDistance)) continue;
            double score = d[0] * prefer.getX() + d[1] * prefer.getZ();
            if (score > bestScore) {
                bestScore = score;
                best = new Vector(d[0], 0, d[1]);
            }
        }
        return best;
    }

    static boolean touching(Player p, Vector dir, double contactDistance) {
        return blocked(p, dir.getX(), dir.getZ(), contactDistance);
    }

    private static boolean blocked(Player p, double dx, double dz, double dist) {
        Location loc = p.getLocation();
        double px = loc.getX() + dx * (0.35 + dist);
        double pz = loc.getZ() + dz * (0.35 + dist);
        World w = p.getWorld();
        // Пояс и грудь — чтобы бордюр под ногами не считался стеной (как torsoBox в оригинале).
        return solidAt(w, px, loc.getY() + 0.9, pz) || solidAt(w, px, loc.getY() + 1.5, pz);
    }

    /** Есть ли над нами уступ, на который можно забраться, в направлении dir. */
    static boolean hasLedge(Player p, Vector dir, double maxHeight) {
        Location loc = p.getLocation();
        World w = p.getWorld();
        double x = loc.getX();
        double z = loc.getZ();
        for (double dy = 0.2; dy <= maxHeight; dy += 0.2) {
            double headY = loc.getY() + 1.9 + dy;
            if (solidAt(w, x, headY, z)) return false; // сверху потолок — не пролезем
            double fx = x + dir.getX() * 0.6;
            double fz = z + dir.getZ() * 0.6;
            if (!solidAt(w, fx, loc.getY() + dy, fz) && !solidAt(w, fx, loc.getY() + dy + 1.0, fz)) {
                return true;
            }
        }
        return false;
    }

    static boolean solidAt(World w, double x, double y, double z) {
        return w.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).getType().isSolid();
    }
}
