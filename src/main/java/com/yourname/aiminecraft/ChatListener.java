package com.yourname.aiminecraft;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listens to server events and sends AI prompts with surroundings context.
 */
public class ChatListener implements Listener {
    private final JavaPlugin plugin;
    private GeminiClient aiClient;
    private final LinkedList<String> chatHistory = new LinkedList<>();
    private String behavior;
    private final File brainFile;
    private final MemoryManager memoryManager;

    // Scanner configuration (set by AiPlugin)
    private int scanRadius = 5;
    private int layersAbove = 4;
    private int layersBelow = 2;

    public ChatListener(JavaPlugin plugin, GeminiClient aiClient, String behavior, MemoryManager memoryManager) {
        this.plugin = plugin;
        this.aiClient = aiClient;
        this.behavior = behavior;
        this.memoryManager = memoryManager;
        this.brainFile = new File(plugin.getDataFolder(), "brain.log");
    }

    public void updateConfig(GeminiClient client, String behavior) {
        this.aiClient = client;
        this.behavior = behavior;
    }

    public void setScannerConfig(int radius, int layersAbove, int layersBelow) {
        this.scanRadius = radius;
        this.layersAbove = layersAbove;
        this.layersBelow = layersBelow;
    }

    // --- Event Handlers ---

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        AiPlugin aiPlugin = (AiPlugin) plugin;
        if (!aiPlugin.isAiEnabled()) return;

        // If in whisper mode, ignore all public chat messages.
        if (!aiPlugin.isGlobalMode()) return;

        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        handleInteraction(player, message, false);
    }

    /**
     * Common logic for handling an interaction with the AI.
     * @param player The player interacting.
     * @param message The message sent by the player.
     * @param isDirect If this was a direct command like /server (bypasses mention check and mode restrictions).
     */
    public void handleInteraction(Player player, String message, boolean isDirect) {
        AiPlugin aiPlugin = (AiPlugin) plugin;
        String playerName = player.getName();

        // Save to long-term memory
        try {
            java.nio.file.Files.writeString(brainFile.toPath(), playerName + ": " + message + "\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Failed to save chat to brain.log: " + e.getMessage());
        }

        addHistory(playerName + ": " + message);

        String botName = plugin.getConfig().getString("bot-name", "Server");
        boolean isMentioned = message.toLowerCase().contains(botName.toLowerCase());
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Must scan on main thread, then send prompt async
        Bukkit.getScheduler().runTask(plugin, () -> {
            WorldScanner.ScanResult scan = WorldScanner.scan(player, scanRadius, layersBelow, layersAbove);
            String surroundings = WorldScanner.buildTextSummary(scan);
            String profileInfo = memoryManager.buildPlayerSummary(player);
            byte[] mapImage = MapRenderer.render(scan);
            String prompt = buildThinkingPrompt(playerName, isMentioned || isDirect, onlinePlayers, surroundings, profileInfo);
            
            // If in whisper mode or direct message, reply directly to player.
            Player replyTarget = (!aiPlugin.isGlobalMode() || isDirect) ? player : null;
            sendAiResponse(prompt, mapImage, isMentioned || isDirect, replyTarget);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!((AiPlugin) plugin).isAiEnabled()) return;
        Player player = event.getPlayer();
        String playerName = player.getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            WorldScanner.ScanResult scan = WorldScanner.scan(player, scanRadius, layersBelow, layersAbove);
            String surroundings = WorldScanner.buildTextSummary(scan);
            String profileInfo = memoryManager.buildPlayerSummary(player);
            byte[] mapImage = MapRenderer.render(scan);
            String prompt = buildEventPrompt(playerName, "JOIN", onlinePlayers, surroundings, profileInfo);
            sendAiResponse(prompt, mapImage, false, null);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!((AiPlugin) plugin).isAiEnabled()) return;
        String playerName = event.getPlayer().getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size() - 1;

        // Player is leaving, so we can't scan their surroundings — send text-only
        String profileInfo = memoryManager.buildPlayerSummary(event.getPlayer());
        String prompt = buildEventPrompt(playerName, "LEAVE", onlinePlayers, "(Player has left, no surroundings available)", profileInfo);
        sendAiResponse(prompt, null, false, null);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!((AiPlugin) plugin).isAiEnabled()) return;
        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            WorldScanner.ScanResult scan = WorldScanner.scan(player, scanRadius, layersBelow, layersAbove);
            String surroundings = WorldScanner.buildTextSummary(scan);
            String profileInfo = memoryManager.buildPlayerSummary(player);
            byte[] mapImage = MapRenderer.render(scan);
            String prompt = buildHaterPrompt(player.getName(), deathMessage, "DEATH", surroundings, profileInfo);
            sendAiResponse(prompt, mapImage, false, null);
        });
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!((AiPlugin) plugin).isAiEnabled()) return;
        if (event.getAdvancement().getDisplay() == null) return;

        Player player = event.getPlayer();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getAdvancement().getDisplay().title());

        Bukkit.getScheduler().runTask(plugin, () -> {
            WorldScanner.ScanResult scan = WorldScanner.scan(player, scanRadius, layersBelow, layersAbove);
            String surroundings = WorldScanner.buildTextSummary(scan);
            String profileInfo = memoryManager.buildPlayerSummary(player);
            byte[] mapImage = MapRenderer.render(scan);
            String prompt = buildHaterPrompt(player.getName(), title, "ADVANCEMENT", surroundings, profileInfo);
            sendAiResponse(prompt, mapImage, false, null);
        });
    }

    // --- Response Sender ---

    public void sendAiResponse(String prompt, byte[] imageBytes, boolean forceMention, Player replyTo) {
        aiClient.generateResponse(prompt, imageBytes).thenAccept(response -> {
            if (response == null || response.isBlank()) return;
            
            String trimmedResponse = response.trim();
            if (trimmedResponse.equalsIgnoreCase("SKIP") || trimmedResponse.toUpperCase().startsWith("SKIP ")) return;
            if (trimmedResponse.toUpperCase().contains("SKIP") && !forceMention) return;

            // Extract memory notes: [REMEMBER: PlayerName] note content
            Pattern memoryPattern = Pattern.compile("\\[REMEMBER:\\s*(.*?)\\]\\s*(.*)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = memoryPattern.matcher(trimmedResponse);
            
            String finalResponse = trimmedResponse;
            if (matcher.find()) {
                String targetPlayer = matcher.group(1).trim();
                String note = matcher.group(2).trim();
                
                // Add the note asynchronously or correctly inside the main thread wrapper
                Bukkit.getScheduler().runTask(plugin, () -> {
                    memoryManager.addNote(targetPlayer, note);
                    plugin.getLogger().info("AI saved memory for " + targetPlayer + ": " + note);
                });
                
                // Remove the tag from the final string we broadcast
                finalResponse = matcher.replaceAll("").trim();
            }
            
            if (finalResponse.isBlank()) return;
            
            final String messageToSend = finalResponse;

            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefixText = plugin.getConfig().getString("bot-prefix", "§6[Server]§f ");
                Component prefix = Component.text(prefixText);
                Component message = prefix.append(Component.text(messageToSend));

                if (replyTo != null) {
                    // Send private message (Whisper)
                    replyTo.sendMessage(message);
                } else {
                    // Broadcast
                    Bukkit.broadcast(message);
                }
                
                addHistory("Server: " + messageToSend);

                try {
                    java.nio.file.Files.writeString(brainFile.toPath(), "Server: " + messageToSend + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("Failed to save AI response to brain.log.");
                }
            });
        });
    }

    // --- Prompt Builders ---

    private String buildThinkingPrompt(String user, boolean mentioned, int online, String surroundings, String profileInfo) {
        String brainContext = LogUtils.getAllLines(brainFile);
        String template = plugin.getConfig().getString("prompts.chat-decision");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   profileInfo + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Speaker: " + user + "\n" +
                   "- Total players online: " + online + "\n" +
                   "- Was your name mentioned? " + (mentioned ? "Yes" : "No") + "\n\n" +
                   surroundings + "\n\n" +
                   "[RECENT CHAT HISTORY]:\n" + getHistory() + "\n\n" +
                   "[VISUAL MAP]: An image of the player's surroundings is attached. Use it to understand terrain, buildings, and nearby blocks.\n\n" +
                   "[TASK]: Determine if you should respond. You are NOT a human; you are a server bot. " +
                   "Do not try too hard to be helpful. Most messages should be ignored with 'SKIP'. " +
                   "Only respond if: \n" +
                   "1. You were explicitly mentioned.\n" +
                   "2. The speaker is clearly asking you a direct question.\n" +
                   "3. You have something genuinely witty or important to add to a major event.\n\n" +
                   "Otherwise, respond exactly with 'SKIP'.";
        }

        return template.replace("{personality}", behavior)
                .replace("{brain}", brainContext)
                .replace("{user}", user)
                .replace("{online}", String.valueOf(online))
                .replace("{mentioned}", mentioned ? "Yes" : "No")
                .replace("{history}", getHistory())
                .replace("{player_profile}", profileInfo)
                .replace("{surroundings}", surroundings);
    }

    private String buildEventPrompt(String user, String eventType, int online, String surroundings, String profileInfo) {
        String brainContext = LogUtils.getAllLines(brainFile);
        String action = eventType.equals("JOIN") ? "just joined the server" : "just left the server";
        String template = plugin.getConfig().getString("prompts.event-decision");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   profileInfo + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Player: " + user + "\n" +
                   "- Action: " + action + "\n" +
                   "- Total players now online: " + online + "\n\n" +
                   surroundings + "\n\n" +
                   "[TASK]: Decide if this event is worth commenting on. You don't need to greet everyone. " +
                   "If this player is a regular or if something interesting happened last time they were on, say something short. " +
                   "Otherwise, respond exactly with 'SKIP'. Keep it under 2 sentences.";
        }

        return template.replace("{personality}", behavior)
                .replace("{brain}", brainContext)
                .replace("{user}", user)
                .replace("{action}", action)
                .replace("{online}", String.valueOf(online))
                .replace("{player_profile}", profileInfo)
                .replace("{surroundings}", surroundings);
    }

    private String buildHaterPrompt(String user, String detail, String type, String surroundings, String profileInfo) {
        String brainContext = LogUtils.getAllLines(brainFile);
        String eventDescription = type.equals("DEATH") ? "just died: " + detail : "just completed the advancement: " + detail;
        String template = plugin.getConfig().getString("prompts.hater-prompt");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   profileInfo + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Player: " + user + "\n" +
                   "- Event: " + eventDescription + "\n\n" +
                   surroundings + "\n\n" +
                   "[VISUAL MAP]: An image of where the event happened is attached.\n\n" +
                   "[TASK]: You are the 'Hater'. Based on your personality, roast " + user + " for this event in one short, informal sentence. " +
                   "Be chaotic, use slang (hell/hell nahh/lmao/fr), and don't be too nice. If you have nothing funny to say, respond 'SKIP'.";
        }

        return template.replace("{personality}", behavior)
                .replace("{brain}", brainContext)
                .replace("{user}", user)
                .replace("{event}", eventDescription)
                .replace("{player_profile}", profileInfo)
                .replace("{surroundings}", surroundings);
    }

    // --- Memory Management ---

    public void addHistory(String line) {
        chatHistory.add(line);
        int limit = plugin.getConfig().getInt("history-limit", 10);
        while (chatHistory.size() > limit) chatHistory.removeFirst();
    }

    public String getHistory() {
        return String.join("\n", chatHistory);
    }

    public String getBehavior() {
        return behavior;
    }
}
