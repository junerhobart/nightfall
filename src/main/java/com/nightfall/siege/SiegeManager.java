package com.nightfall.siege;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SiegeManager {

    private final NightfallPlugin plugin;

    // Worlds currently in night-siege state
    private final Set<String> activeWorlds = ConcurrentHashMap.newKeySet();

    private BukkitTask siegeTask;
    private BukkitTask lightAttractionTask;

    public SiegeManager(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        // Main siege tick: check every 20 ticks (1 second)
        siegeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::siegeTick, 20L, 20L);
        // Light attraction check: every 40 ticks (2 seconds)
        lightAttractionTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::lightAttractionTick, 40L, 40L);
    }

    public void shutdown() {
        if (siegeTask != null) siegeTask.cancel();
        if (lightAttractionTask != null) lightAttractionTask.cancel();
        activeWorlds.clear();
    }

    public void reload() {
        shutdown();
        startTasks();
    }

    public boolean isNight(World world) {
        return activeWorlds.contains(world.getName());
    }

    private void siegeTick() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.getNfConfig().isWorldEnabled(world.getName())) continue;
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            long time = world.getTime();
            long start = plugin.getNfConfig().nightStart;
            long end = plugin.getNfConfig().nightEnd;
            boolean shouldBeNight = time >= start && time <= end;
            boolean wasNight = activeWorlds.contains(world.getName());

            if (shouldBeNight && !wasNight) {
                activeWorlds.add(world.getName());
                onNightBegin(world);
            } else if (!shouldBeNight && wasNight) {
                activeWorlds.remove(world.getName());
                onDayBegin(world);
            }
        }
    }

    private void onNightBegin(World world) {
        plugin.getBreachManager().onNightBegin(world);
        if (plugin.getNfConfig().debug) {
            plugin.getLogger().info("[Nightfall] Night began in world: " + world.getName());
        }
    }

    private void onDayBegin(World world) {
        plugin.getBreachManager().onDayBegin(world);
        plugin.getHeatManager().clearLastSeenForWorld(world);
        if (plugin.getNfConfig().debug) {
            plugin.getLogger().info("[Nightfall] Day began in world: " + world.getName());
        }
    }

    private void lightAttractionTick() {
        if (!plugin.getNfConfig().lightAttractionEnabled) return;

        for (World world : plugin.getServer().getWorlds()) {
            if (!isNight(world)) continue;
            LightAttractionTask.run(plugin, world);
        }
    }
}
