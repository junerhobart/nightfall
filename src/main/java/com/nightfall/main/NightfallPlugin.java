package com.nightfall.main;

import com.nightfall.breach.BreachManager;
import com.nightfall.command.NightfallCommand;
import com.nightfall.config.NightfallConfig;
import com.nightfall.heat.HeatManager;
import com.nightfall.listeners.BlockEventListener;
import com.nightfall.listeners.CombatListener;
import com.nightfall.listeners.PlayerActivityListener;
import com.nightfall.listeners.WorldListener;
import com.nightfall.siege.SiegeManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class NightfallPlugin extends JavaPlugin {

    private static NightfallPlugin instance;

    private NightfallConfig nfConfig;
    private HeatManager heatManager;
    private SiegeManager siegeManager;
    private BreachManager breachManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        nfConfig = new NightfallConfig(this);
        heatManager = new HeatManager(this);
        siegeManager = new SiegeManager(this);
        breachManager = new BreachManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new BlockEventListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this), this);

        // Register command
        NightfallCommand cmd = new NightfallCommand(this);
        getCommand("nightfall").setExecutor(cmd);
        getCommand("nightfall").setTabCompleter(cmd);

        // Start tasks
        heatManager.startTasks();
        siegeManager.startTasks();
        breachManager.startTasks();

        getLogger().info("Nightfall enabled.");
    }

    @Override
    public void onDisable() {
        if (breachManager != null) breachManager.shutdown();
        if (siegeManager != null) siegeManager.shutdown();
        if (heatManager != null) heatManager.shutdown();
        getLogger().info("Nightfall disabled.");
    }

    public void reload() {
        reloadConfig();
        nfConfig.reload();
        heatManager.reload();
        siegeManager.reload();
        breachManager.reload();
    }

    public static NightfallPlugin getInstance() { return instance; }
    public NightfallConfig getNfConfig() { return nfConfig; }
    public HeatManager getHeatManager() { return heatManager; }
    public SiegeManager getSiegeManager() { return siegeManager; }
    public BreachManager getBreachManager() { return breachManager; }
}
