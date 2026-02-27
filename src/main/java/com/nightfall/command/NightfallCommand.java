package com.nightfall.command;

import com.nightfall.main.NightfallPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class NightfallCommand implements CommandExecutor, TabCompleter {

    private final NightfallPlugin plugin;

    public NightfallCommand(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nightfall.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("[Nightfall] Config reloaded.", NamedTextColor.GREEN));
            }
            case "debug" -> {
                boolean newValue = !plugin.getNfConfig().debug;
                // Toggle debug in live config -- persisted only until next reload
                plugin.getNfConfig().debug = newValue;
                sender.sendMessage(Component.text("[Nightfall] Debug mode: " + (newValue ? "ON" : "OFF"),
                        newValue ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            }
            case "status" -> sendStatus(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[Nightfall] Commands:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /nightfall reload  - Reload config", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /nightfall debug   - Toggle debug logging", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /nightfall status  - Show current state", NamedTextColor.YELLOW));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("[Nightfall] Status:", NamedTextColor.GOLD));
        for (World world : plugin.getServer().getWorlds()) {
            boolean night = plugin.getSiegeManager().isNight(world);
            sender.sendMessage(Component.text("  " + world.getName() + ": "
                + (night ? "NIGHT" : "day"), night ? NamedTextColor.DARK_RED : NamedTextColor.AQUA));
        }
        sender.sendMessage(Component.text("  Active breaches: " + plugin.getBreachManager().getActiveBreachCount()
                + " blocks, " + plugin.getBreachManager().getActiveMobCount() + " mobs", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  Debug: " + plugin.getNfConfig().debug, NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "debug", "status");
        }
        return List.of();
    }
}
