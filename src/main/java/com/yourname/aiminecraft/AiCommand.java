package com.yourname.aiminecraft;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AiCommand implements CommandExecutor {
    private final AiPlugin plugin;

    public AiCommand(AiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Handle /server <msg>
        if (label.equalsIgnoreCase("server")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can talk to the AI.");
                return true;
            }
            if (!plugin.isAiEnabled()) {
                sender.sendMessage("§c[AI] The AI is currently disabled.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§cUsage: /server <message>");
                return true;
            }
            
            String message = String.join(" ", args);
            plugin.getChatListener().handleInteraction(player, message, true);
            return true;
        }

        // Handle /ai <args>
        if (!sender.hasPermission("aiminecraft.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.loadResources();
                sender.sendMessage("§a[AI] Configuration and Behavior reloaded!");
                return true;

            case "on":
                plugin.setAiEnabled(true);
                sender.sendMessage("§a[AI] Plugin turned §2ON§a.");
                return true;

            case "off":
                plugin.setAiEnabled(false);
                sender.sendMessage("§a[AI] Plugin turned §cOFF§a.");
                return true;

            case "toggle":
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("on")) {
                        plugin.setAiEnabled(true);
                        sender.sendMessage("§a[AI] Plugin turned §2ON§a.");
                    } else if (args[1].equalsIgnoreCase("off")) {
                        plugin.setAiEnabled(false);
                        sender.sendMessage("§a[AI] Plugin turned §cOFF§a.");
                    }
                } else {
                    boolean newState = !plugin.isAiEnabled();
                    plugin.setAiEnabled(newState);
                    sender.sendMessage("§a[AI] Plugin toggled " + (newState ? "§2ON" : "§cOFF") + "§a.");
                }
                return true;

            case "global":
                plugin.setInteractionMode("global");
                sender.sendMessage("§a[AI] Mode set to §2GLOBAL§a. AI will respond to all chat.");
                return true;

            case "whisper":
                plugin.setInteractionMode("whisper");
                sender.sendMessage("§a[AI] Mode set to §cWHISPER§a. AI will only respond via private message.");
                return true;

            case "mode":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ai mode <global|whisper>");
                    return true;
                }
                String mode = args[1].toLowerCase();
                if (mode.startsWith("g")) {
                    plugin.setInteractionMode("global");
                    sender.sendMessage("§a[AI] Mode set to §2GLOBAL§a.");
                } else if (mode.startsWith("w")) {
                    plugin.setInteractionMode("whisper");
                    sender.sendMessage("§a[AI] Mode set to §cWHISPER§a.");
                } else {
                    sender.sendMessage("§cInvalid mode. Use 'global' or 'whisper'.");
                }
                return true;

            case "status":
            case "info":
                String currentModel = plugin.getConfig().getString("gemini-model");
                sender.sendMessage("§6--- AI Status ---");
                sender.sendMessage("§aModel: §f" + currentModel);
                sender.sendMessage("§aState: " + (plugin.isAiEnabled() ? "§2ENABLED" : "§cDISABLED"));
                sender.sendMessage("§aMode:  §f" + plugin.getInteractionMode().toUpperCase());
                return true;

            case "notes":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ai notes <player>");
                    return true;
                }
                java.util.List<String> notes = plugin.getMemoryManager().listNotes(args[1]);
                if (notes.isEmpty()) {
                    sender.sendMessage("§e[AI] No notes found for §f" + args[1] + "§e.");
                } else {
                    sender.sendMessage("§6--- Notes for " + args[1] + " ---");
                    for (int i = 0; i < notes.size(); i++) {
                        sender.sendMessage("§e[" + i + "] §f" + notes.get(i));
                    }
                }
                return true;

            case "forget":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ai forget <player> <index|all>");
                    return true;
                }
                String targetPlayer = args[1];
                String indexArg = args[2];
                if (indexArg.equalsIgnoreCase("all")) {
                    plugin.getMemoryManager().clearNotes(targetPlayer);
                    sender.sendMessage("§a[AI] Cleared all notes for §f" + targetPlayer + "§a.");
                } else {
                    try {
                        int idx = Integer.parseInt(indexArg);
                        boolean ok = plugin.getMemoryManager().removeNote(targetPlayer, idx);
                        if (ok) {
                            sender.sendMessage("§a[AI] Removed note #" + idx + " for §f" + targetPlayer + "§a.");
                        } else {
                            sender.sendMessage("§c[AI] Note index #" + idx + " not found for " + targetPlayer + ".");
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid index. Use a number or 'all'.");
                    }
                }
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6--- AI Minecraft Commands ---");
        sender.sendMessage("§e/ai on/off      §7- Enable or disable the AI");
        sender.sendMessage("§e/ai toggle      §7- Toggle AI state");
        sender.sendMessage("§e/ai mode <g|w>  §7- Switch between Global and Whisper mode");
        sender.sendMessage("§e/ai reload      §7- Reload config and behavior");
        sender.sendMessage("§e/ai status      §7- Show current AI settings");
        sender.sendMessage("§e/ai notes <p>   §7- List AI notes for a player");
        sender.sendMessage("§e/ai forget <p> <i|all> §7- Delete a note (or all) for a player");
        sender.sendMessage("§e/server <msg>   §7- Directly talk to the AI");
    }
}
