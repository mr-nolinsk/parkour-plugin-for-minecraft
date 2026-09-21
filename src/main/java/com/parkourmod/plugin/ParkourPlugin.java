package com.parkourmod.plugin;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ParkourPlugin extends JavaPlugin {

    private ParkourConfig config;
    private ParkourEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new ParkourConfig(getConfig());
        this.engine = new ParkourEngine(this, config);

        getServer().getPluginManager().registerEvents(new ParkourListener(engine), this);

        PluginCommand cmd = getCommand("parkour");
        if (cmd != null) {
            ParkourCommand executor = new ParkourCommand(this, engine, config);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        engine.start();
        getLogger().info("ParkourPlugin включён. /parkour — включить/выключить режим игроку.");
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.shutdown();
    }
}
