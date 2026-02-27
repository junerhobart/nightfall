package com.nightfall.command;

import com.nightfall.main.NightfallPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.List;

public class NightfallCommand {

    private final NightfallPlugin plugin;

    public NightfallCommand(NightfallPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("[Nightfall] Config reloaded.", NamedTextColor.GREEN));
            }
            case "debug" -> {
                boolean newValue = !plugin.getNfConfig().debug;
                plugin.getNfConfig().debug = newValue;
                sender.sendMessage(Component.text("[Nightfall] Debug: " + (newValue ? "ON" : "OFF"),
                        newValue ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
            }
            case "status" -> sendStatus(sender);
            default -> sendHelp(sender);
        }
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

}
