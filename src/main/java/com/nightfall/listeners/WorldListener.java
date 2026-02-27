package com.nightfall.listeners;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class WorldListener implements Listener {

    private final NightfallPlugin plugin;

    public WorldListener(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getNfConfig().isWorldEnabled(event.getWorld().getName())) return;
        plugin.getWardManager().indexChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        // No explicit cleanup needed; stale chunk entries are harmless until overwritten.
    }
}
