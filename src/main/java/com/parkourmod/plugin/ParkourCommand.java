package com.parkourmod.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ParkourCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ParkourEngine engine;
    private final ParkourConfig config;

    public ParkourCommand(JavaPlugin plugin, ParkourEngine engine, ParkourConfig config) {
        this.plugin = plugin;
        this.engine = engine;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("parkourmod.reload")) {
                sender.sendMessage(Component.text("Недостаточно прав.").color(NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            config.reload(plugin.getConfig());
            sender.sendMessage(Component.text("Конфиг ParkourPlugin перезагружен.").color(NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Эта команда — только для игроков.").color(NamedTextColor.RED));
            return true;
        }
        if (!p.hasPermission("parkourmod.use")) {
            p.sendMessage(Component.text("Недостаточно прав.").color(NamedTextColor.RED));
            return true;
        }

        ParkourState s = engine.stateOf(p);
        s.enabled = !s.enabled;
        if (!s.enabled) {
            engine.disable(p, s);
        }
        p.sendMessage(s.enabled
                ? Component.text("Паркур-режим включён.").color(NamedTextColor.GREEN)
                : Component.text("Паркур-режим выключен.").color(NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("parkourmod.reload")) {
            return List.of("reload");
        }
        return List.of();
    }
}
