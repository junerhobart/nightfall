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
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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

        getServer().getPluginManager().registerEvents(new BlockEventListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldListener(), this);

        NightfallCommand cmd = new NightfallCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                Commands.literal("nightfall")
                    .requires(src -> src.getSender().hasPermission("nightfall.admin"))
                    .executes(ctx -> { cmd.execute(ctx.getSource().getSender(), new String[0]); return 1; })
                    .then(Commands.literal("reload")
                        .executes(ctx -> { cmd.execute(ctx.getSource().getSender(), new String[]{"reload"}); return 1; }))
                    .then(Commands.literal("debug")
                        .executes(ctx -> { cmd.execute(ctx.getSource().getSender(), new String[]{"debug"}); return 1; }))
                    .then(Commands.literal("status")
                        .executes(ctx -> { cmd.execute(ctx.getSource().getSender(), new String[]{"status"}); return 1; }))
                    .build(),
                "Nightfall admin commands."
            );
        });

        heatManager.startTasks();
        siegeManager.startTasks();
        breachManager.startTasks();
    }

    @Override
    public void onDisable() {
        if (breachManager != null) breachManager.shutdown();
        if (siegeManager != null) siegeManager.shutdown();
        if (heatManager != null) heatManager.shutdown();
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
