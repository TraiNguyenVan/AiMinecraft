package com.yourname.aiminecraft;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedList;

public class ChatListener implements Listener {
    private final JavaPlugin plugin;
    private GeminiClient aiClient;
    private final LinkedList<String> chatHistory = new LinkedList<>();
    private String behavior;
    private final File brainFile;

    public ChatListener(JavaPlugin plugin, GeminiClient aiClient, String behavior) {
        this.plugin = plugin;
        this.aiClient = aiClient;
        this.behavior = behavior;
        this.brainFile = new File(plugin.getDataFolder(), "brain.log");
    }

    public void updateConfig(GeminiClient client, String behavior) {
        this.aiClient = client;
        this.behavior = behavior;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        addHistory(playerName + ": " + message);

        String botName = plugin.getConfig().getString("bot-name", "Server");
        boolean isMentioned = message.toLowerCase().contains(botName.toLowerCase());
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        String prompt = buildThinkingPrompt(playerName, isMentioned, onlinePlayers);
        sendAiResponse(prompt, isMentioned);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Small delay (2 seconds) to let the player fully load in
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String prompt = buildEventPrompt(playerName, "JOIN", onlinePlayers);
            sendAiResponse(prompt, false);
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size() - 1;

        String prompt = buildEventPrompt(playerName, "LEAVE", onlinePlayers);
        sendAiResponse(prompt, false);
    }

    private void sendAiResponse(String prompt, boolean forceMention) {
        aiClient.generateResponse(prompt).thenAccept(response -> {
            if (response == null || response.isBlank()) return;
            if (response.toUpperCase().contains("SKIP") && !forceMention) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefix = plugin.getConfig().getString("bot-prefix", "§6[Server]§f ");
                Bukkit.broadcast(Component.text(prefix + response.trim()));
                addHistory("Server: " + response.trim());
            });
        });
    }

    private String buildThinkingPrompt(String user, boolean mentioned, int online) {
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        return "[PERSONALITY]:\n" + behavior + "\n\n" +
               "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
               "[SOCIAL CONTEXT]:\n" +
               "- Speaker: " + user + "\n" +
               "- Total players online: " + online + "\n" +
               "- Was your name mentioned? " + (mentioned ? "Yes" : "No") + "\n\n" +
               "[RECENT CHAT HISTORY]:\n" + getHistory() + "\n\n" +
               "[TASK]: Decide if " + user + " is talking to YOU. If not, respond 'SKIP'. Otherwise, write a short response.";
    }

    private String buildEventPrompt(String user, String eventType, int online) {
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        String action = eventType.equals("JOIN") ? "just joined the server" : "just left the server";
        
        return "[PERSONALITY]:\n" + behavior + "\n\n" +
               "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
               "[SOCIAL CONTEXT]:\n" +
               "- Player: " + user + "\n" +
               "- Action: " + action + "\n" +
               "- Total players now online: " + online + "\n\n" +
               "[TASK]: Based on your personality and history with this player, make a short, random observation about them joining or leaving. " +
               "Be informal and keep it under 2 sentences. If you have nothing interesting to say, respond 'SKIP'.";
    }

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
