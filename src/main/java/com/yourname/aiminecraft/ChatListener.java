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

// This class "listens" to what's happening on the server (like people talking).
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
        // brain.log is the long-term memory of the bot.
        this.brainFile = new File(plugin.getDataFolder(), "brain.log");
    }

    public void updateConfig(GeminiClient client, String behavior) {
        this.aiClient = client;
        this.behavior = behavior;
    }

    // This runs whenever a player sends a chat message.
    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        // Convert the Minecraft message into simple text.
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        
        // Add this message to the short-term memory (chat history).
        addHistory(playerName + ": " + message);

        String botName = plugin.getConfig().getString("bot-name", "Server");
        boolean isMentioned = message.toLowerCase().contains(botName.toLowerCase());
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Prepare the prompt for the AI to decide if it should speak.
        String prompt = buildThinkingPrompt(playerName, isMentioned, onlinePlayers);
        sendAiResponse(prompt, isMentioned);
    }

    // This runs when someone JOINS the server.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerName = event.getPlayer().getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        // Wait 2 seconds (40 ticks) before the bot says anything.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String prompt = buildEventPrompt(playerName, "JOIN", onlinePlayers);
            sendAiResponse(prompt, false);
        }, 40L);
    }

    // This runs when someone LEAVES the server.
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String playerName = event.getPlayer().getName();
        int onlinePlayers = Bukkit.getOnlinePlayers().size() - 1;

        String prompt = buildEventPrompt(playerName, "LEAVE", onlinePlayers);
        sendAiResponse(prompt, false);
    }

    // NEW: This runs when someone DIES on the server.
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null) return;

        // Ask the AI to roast them for dying.
        String prompt = buildHaterPrompt(player.getName(), deathMessage, "DEATH");
        sendAiResponse(prompt, false);
    }

    // NEW: This runs when someone gets an ACHIEVEMENT (Advancement).
    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        // We only care about "real" advancements that have a display title.
        if (event.getAdvancement().getDisplay() == null) return;
        
        String player = event.getPlayer().getName();
        String title = PlainTextComponentSerializer.plainText().serialize(event.getAdvancement().getDisplay().title());

        // Ask the AI to comment on their "achievement".
        String prompt = buildHaterPrompt(player, title, "ADVANCEMENT");
        sendAiResponse(prompt, false);
    }

    private void sendAiResponse(String prompt, boolean forceMention) {
        // Send the prompt to Gemini AI and wait for a response.
        aiClient.generateResponse(prompt).thenAccept(response -> {
            if (response == null || response.isBlank()) return;
            // If the AI says 'SKIP', we don't broadcast anything to the server.
            if (response.toUpperCase().contains("SKIP") && !forceMention) return;

            // Go back to the main Minecraft thread to send the message.
            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefix = plugin.getConfig().getString("bot-prefix", "§6[Server]§f ");
                Bukkit.broadcast(Component.text(prefix + response.trim()));
                // Add what the bot said to the memory too.
                addHistory("Server: " + response.trim());
            });
        });
    }

    // This builds the detailed instructions for the AI when someone chats.
    private String buildThinkingPrompt(String user, boolean mentioned, int online) {
        // Get the last 30 lines from the brain.log file.
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        String template = plugin.getConfig().getString("prompts.chat-decision");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Speaker: " + user + "\n" +
                   "- Total players online: " + online + "\n" +
                   "- Was your name mentioned? " + (mentioned ? "Yes" : "No") + "\n\n" +
                   "[RECENT CHAT HISTORY]:\n" + getHistory() + "\n\n" +
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
                .replace("{history}", getHistory());
    }

    // This builds the instructions for the AI when someone joins or leaves.
    private String buildEventPrompt(String user, String eventType, int online) {
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        String action = eventType.equals("JOIN") ? "just joined the server" : "just left the server";
        String template = plugin.getConfig().getString("prompts.event-decision");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Player: " + user + "\n" +
                   "- Action: " + action + "\n" +
                   "- Total players now online: " + online + "\n\n" +
                   "[TASK]: Decide if this event is worth commenting on. You don't need to greet everyone. " +
                   "If this player is a regular or if something interesting happened last time they were on, say something short. " +
                   "Otherwise, respond exactly with 'SKIP'. Keep it under 2 sentences.";
        }

        return template.replace("{personality}", behavior)
                .replace("{brain}", brainContext)
                .replace("{user}", user)
                .replace("{action}", action)
                .replace("{online}", String.valueOf(online));
    }

    // NEW: This builds the "Hater" prompt for deaths and achievements.
    private String buildHaterPrompt(String user, String detail, String type) {
        // Get the last 30 lines from the brain.log file.
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        String eventDescription = type.equals("DEATH") ? "just died: " + detail : "just completed the advancement: " + detail;
        String template = plugin.getConfig().getString("prompts.hater-prompt");

        if (template == null) {
            return "[PERSONALITY]:\n" + behavior + "\n\n" +
                   "[PREVIOUS KNOWLEDGE]:\n" + brainContext + "\n\n" +
                   "[SOCIAL CONTEXT]:\n" +
                   "- Player: " + user + "\n" +
                   "- Event: " + eventDescription + "\n\n" +
                   "[TASK]: You are the 'Hater'. Based on your personality, roast " + user + " for this event in one short, informal sentence. " +
                   "Be chaotic, use slang (W/L/fr), and don't be too nice. If you have nothing funny to say, respond 'SKIP'.";
        }

        return template.replace("{personality}", behavior)
                .replace("{brain}", brainContext)
                .replace("{user}", user)
                .replace("{event}", eventDescription);
    }

    // This adds a line of chat to the bot's temporary memory.
    public void addHistory(String line) {
        chatHistory.add(line);
        // Keep only the last 10 messages (default).
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
