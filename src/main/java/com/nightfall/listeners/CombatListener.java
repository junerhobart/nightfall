package com.nightfall.listeners;

import com.nightfall.main.NightfallPlugin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatListener implements Listener {

    private final NightfallPlugin plugin;

    public CombatListener(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getSiegeManager().isNight(event.getEntity().getWorld())) return;

        // Mob hits player: apply outgoing mob damage multiplier and heat
        if (event.getDamager() instanceof Mob && event.getEntity() instanceof Player player) {
            double outgoing = plugin.getNfConfig().mobDamageMultiplier;
            double incoming = plugin.getNfConfig().playerIncomingMultiplier;
            event.setDamage(event.getDamage() * outgoing * incoming);
            plugin.getHeatManager().addPlayerHeat(player, plugin.getNfConfig().heatPlayerHurt);
            return;
        }

        // Player hits mob: record last-seen so the mob remembers where the player is
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Mob mob) {
            if (plugin.getNfConfig().isWorldEnabled(player.getWorld().getName())) {
                plugin.getHeatManager().recordLastSeen(mob.getUniqueId(), player.getLocation());
            }
        }
    }
}
