package com.nightfall.heat;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeatManager {

    private final NightfallPlugin plugin;

    // worldName -> list of heat nodes
    private final Map<String, List<HeatNode>> worldNodes = new ConcurrentHashMap<>();

    // Last seen player location per mob UUID, with timestamp
    private final Map<UUID, LastSeen> lastSeenMap = new ConcurrentHashMap<>();

    private BukkitTask decayTask;

    public record LastSeen(Location location, long timestamp) {}

    public HeatManager(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        // Decay every 4 seconds
        decayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runDecay, 80L, 80L);
    }

    public void shutdown() {
        if (decayTask != null) decayTask.cancel();
        worldNodes.clear();
        lastSeenMap.clear();
    }

    public void reload() {
        shutdown();
        startTasks();
    }

    public void addHeat(Location location, int intensity) {
        String world = location.getWorld().getName();
        List<HeatNode> nodes = worldNodes.computeIfAbsent(world, k -> new ArrayList<>());

        double mergeRadius = plugin.getNfConfig().heatMergeRadius;
        for (HeatNode node : nodes) {
            if (node.getLocation().getWorld().equals(location.getWorld())
                    && node.getLocation().distanceSquared(location) <= mergeRadius * mergeRadius) {
                node.merge(intensity);
                return;
            }
        }
        nodes.add(new HeatNode(location, intensity));
    }

    /**
     * Returns the highest-intensity heat node within scanRadius for the given world,
     * or null if none.
     */
    public HeatNode getNearestHeat(Location origin, double scanRadius) {
        List<HeatNode> nodes = worldNodes.get(origin.getWorld().getName());
        if (nodes == null || nodes.isEmpty()) return null;

        HeatNode best = null;
        double bestScore = -1;
        double radiusSq = scanRadius * scanRadius;

        for (HeatNode node : nodes) {
            if (!node.getLocation().getWorld().equals(origin.getWorld())) continue;
            double distSq = node.getLocation().distanceSquared(origin);
            if (distSq > radiusSq) continue;
            // Score: intensity / sqrt(dist) -- closer and hotter wins
            double score = node.getIntensity() / (Math.sqrt(distSq) + 1.0);
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }

    public void recordLastSeen(UUID mobId, Location location) {
        lastSeenMap.put(mobId, new LastSeen(location.clone(), System.currentTimeMillis()));
    }

    public LastSeen getLastSeen(UUID mobId) {
        LastSeen ls = lastSeenMap.get(mobId);
        if (ls == null) return null;
        long memoryMillis = plugin.getNfConfig().lastSeenMemoryMillis;
        if (System.currentTimeMillis() - ls.timestamp() > memoryMillis) {
            lastSeenMap.remove(mobId);
            return null;
        }
        return ls;
    }

    public void clearLastSeen(UUID mobId) {
        if (mobId != null) lastSeenMap.remove(mobId);
    }

    /** Remove last-seen entries for all mobs in the given world (called on day begin). */
    public void clearLastSeenForWorld(World world) {
        lastSeenMap.keySet().removeIf(id -> {
            LastSeen ls = lastSeenMap.get(id);
            return ls != null && ls.location().getWorld().equals(world);
        });
    }

    private void runDecay() {
        long decayMillis = plugin.getNfConfig().heatDecayMillis;
        for (List<HeatNode> nodes : worldNodes.values()) {
            nodes.removeIf(node -> node.decay(decayMillis));
        }
    }

    /** Convenience: add heat for a player event at their location. */
    public void addPlayerHeat(Player player, int intensity) {
        if (!plugin.getNfConfig().isWorldEnabled(player.getWorld().getName())) return;
        addHeat(player.getLocation(), intensity);
    }
}
