package com.nightfall.listeners;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockEventListener implements Listener {

    private final NightfallPlugin plugin;

    public BlockEventListener(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getNfConfig().isWorldEnabled(event.getPlayer().getWorld().getName())) return;
        // Update ward cache if a ward block was removed
        plugin.getWardManager().onBlockBroken(event.getBlock());
        // Add heat
        plugin.getHeatManager().addPlayerHeat(event.getPlayer(), plugin.getNfConfig().heatBlockBreak);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getNfConfig().isWorldEnabled(event.getPlayer().getWorld().getName())) return;
        // Update ward cache if a ward block was placed
        plugin.getWardManager().onBlockPlaced(event.getBlockPlaced());
        // Add heat
        plugin.getHeatManager().addPlayerHeat(event.getPlayer(), plugin.getNfConfig().heatBlockPlace);
    }
}
