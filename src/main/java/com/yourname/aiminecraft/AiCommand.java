package com.yourname.aiminecraft;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class AiCommand implements CommandExecutor {
    private final AiPlugin plugin;

    public AiCommand(AiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("aiminecraft.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6--- AI Minecraft Commands ---");
            sender.sendMessage("§e/ai reload §7- Reload config and behavior");
            sender.sendMessage("§e/ai info   §7- Show current AI status");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.loadResources(); // We'll add this method to AiPlugin
            sender.sendMessage("§a[AI] Configuration and Behavior reloaded!");
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            String model = plugin.getConfig().getString("gemini-model");
            sender.sendMessage("§a[AI] Current Model: §f" + model);
            sender.sendMessage("§a[AI] Status: §fReady");
            return true;
        }

        return false;
    }
}
