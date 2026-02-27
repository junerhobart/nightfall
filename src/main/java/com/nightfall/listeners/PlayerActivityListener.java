package com.nightfall.listeners;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;

import java.util.Set;

public class PlayerActivityListener implements Listener {

    private final NightfallPlugin plugin;

    // Block types that generate interact heat
    private static final Set<String> INTERACT_SUFFIXES = Set.of(
        "_DOOR", "_TRAPDOOR", "_FENCE_GATE"
    );
    private static final Set<Material> CONTAINER_INTERACT = Set.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
        Material.CRAFTING_TABLE, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
        Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    public PlayerActivityListener(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        if (!plugin.getNfConfig().isWorldEnabled(event.getPlayer().getWorld().getName())) return;
        plugin.getHeatManager().addPlayerHeat(event.getPlayer(), plugin.getNfConfig().heatSprintStart);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Only fire once (main hand)
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!plugin.getNfConfig().isWorldEnabled(event.getPlayer().getWorld().getName())) return;

        Material type = event.getClickedBlock().getType();
        boolean isInteractable = CONTAINER_INTERACT.contains(type)
            || INTERACT_SUFFIXES.stream().anyMatch(s -> type.name().endsWith(s));

        if (isInteractable) {
            plugin.getHeatManager().addPlayerHeat(event.getPlayer(), plugin.getNfConfig().heatInteract);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getNfConfig().isWorldEnabled(player.getWorld().getName())) return;
        plugin.getHeatManager().addPlayerHeat(player, plugin.getNfConfig().heatShootBow);
    }
}
