package com.nightfall.ward;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WardManager {

    private final NightfallPlugin plugin;

    // chunkKey -> list of ward block locations in that chunk
    private final Map<Long, List<Location>> wardCache = new ConcurrentHashMap<>();

    private BukkitTask refreshTask;

    public WardManager(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        long refreshTicks = plugin.getNfConfig().wardRefreshMillis / 50;
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAllChunks,
                refreshTicks, refreshTicks);
    }

    public void shutdown() {
        if (refreshTask != null) refreshTask.cancel();
        wardCache.clear();
    }

    public void reload() {
        shutdown();
        startTasks();
        // Rebuild cache for all currently loaded chunks
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                indexChunk(chunk);
            }
        }
    }

    public void indexChunk(Chunk chunk) {
        if (!plugin.getNfConfig().wardEnabled) return;
        Material wardType = plugin.getNfConfig().wardBlockType;
        long key = chunkKey(chunk);
        List<Location> wards = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int minY = chunk.getWorld().getMinHeight();
                int maxY = chunk.getWorld().getMaxHeight();
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == wardType) {
                        wards.add(block.getLocation().clone());
                    }
                }
            }
        }

        if (wards.isEmpty()) {
            wardCache.remove(key);
        } else {
            wardCache.put(key, wards);
        }
    }

    public void onBlockPlaced(Block block) {
        if (block.getType() == plugin.getNfConfig().wardBlockType) {
            indexChunk(block.getChunk());
        }
    }

    public void onBlockBroken(Block block) {
        if (block.getType() == plugin.getNfConfig().wardBlockType) {
            indexChunk(block.getChunk());
        }
    }

    /**
     * Returns true if the given location is within ward radius of any ward block.
     */
    public boolean isProtected(Location loc) {
        if (!plugin.getNfConfig().wardEnabled) return false;
        double radius = plugin.getNfConfig().wardRadius;
        double radiusSq = radius * radius;

        // Check the chunk and its 8 neighbors (wards can be just outside the chunk boundary)
        Chunk center = loc.getChunk();
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                Chunk neighbor = loc.getWorld().getChunkAt(center.getX() + cx, center.getZ() + cz);
                List<Location> wards = wardCache.get(chunkKey(neighbor));
                if (wards == null) continue;
                for (Location ward : wards) {
                    if (ward.getWorld().equals(loc.getWorld())
                            && ward.distanceSquared(loc) <= radiusSq) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void refreshAllChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                indexChunk(chunk);
            }
        }
    }

    private static long chunkKey(Chunk chunk) {
        // Include world UID to avoid collisions across worlds
        long uid = chunk.getWorld().getUID().getLeastSignificantBits();
        return ((long) chunk.getX() << 32) ^ (chunk.getZ() & 0xFFFFFFFFL) ^ uid;
    }
}
