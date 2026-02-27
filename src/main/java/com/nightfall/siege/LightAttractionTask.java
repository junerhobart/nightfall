package com.nightfall.siege;

import com.nightfall.heat.HeatManager;
import com.nightfall.main.NightfallPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Set;

public final class LightAttractionTask {

    private LightAttractionTask() {}

    public static void run(NightfallPlugin plugin, World world) {
        HeatManager heatManager = plugin.getHeatManager();
        double scanRadius = plugin.getNfConfig().lightScanRadius;
        Set<Material> lightBlocks = plugin.getNfConfig().lightBlocks;

        for (Mob mob : world.getEntitiesByClass(Mob.class)) {
            // Only attract mobs that have no player target and no heat/last-seen target
            if (mob.getTarget() instanceof Player) continue;
            if (heatManager.getLastSeen(mob.getUniqueId()) != null) continue;
            if (heatManager.getNearestHeat(mob.getLocation(), plugin.getNfConfig().heatMobScanRadius) != null) continue;

            Location mobLoc = mob.getLocation();
            Location bestLight = findNearestLight(world, mobLoc, scanRadius, lightBlocks);
            if (bestLight != null) {
                try {
                    mob.getPathfinder().moveTo(bestLight, 1.1);
                } catch (Exception ignored) {
                    // Some mobs may not support pathfinding
                }
            }
        }
    }

    private static Location findNearestLight(World world, Location origin, double radius, Set<Material> lightBlocks) {
        int r = (int) Math.ceil(radius);
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        double radiusSq = radius * radius;

        Location best = null;
        double bestDistSq = Double.MAX_VALUE;

        // Scan only every 2 blocks to reduce load
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > radiusSq) continue;
                    if (distSq >= bestDistSq) continue;
                    int bx = ox + dx;
                    int by = oy + dy;
                    int bz = oz + dz;
                    Material type = world.getBlockAt(bx, by, bz).getType();
                    if (lightBlocks.contains(type)) {
                        bestDistSq = distSq;
                        best = new Location(world, bx + 0.5, by, bz + 0.5);
                    }
                }
            }
        }
        return best;
    }
}
