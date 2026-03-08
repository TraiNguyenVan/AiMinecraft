package com.yourname.aiminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.concurrent.TimeUnit;

// This is the main "brain" of the plugin. It's the first thing that runs when the server starts.
public class AiPlugin extends JavaPlugin {
    private GeminiClient client;
    private ChatListener listener;
    private String behavior;

    @Override
    public void onEnable() {
        // This runs when the plugin is turned ON.
        loadResources();
        // Register the "/ai" command so you can use it in-game.
        getCommand("ai").setExecutor(new AiCommand(this));
        getLogger().info("AI Minecraft Commands Registered!");
    }

    public void loadResources() {
        // Create the folder for the plugin and the config.yml if they don't exist.
        saveDefaultConfig();
        
        // Grab your API key and model name from the config.yml file.
        String apiKey = getConfig().getString("gemini-api-key");
        String model = getConfig().getString("gemini-model", "gemini-1.5-flash");
        
        // Load the "personality" (behavior.txt) for the bot.
        this.behavior = "You are a helpful Minecraft server assistant.";
        try {
            File behaviorFile = new File(getDataFolder(), "behavior.txt");
            if (!behaviorFile.exists()) saveResource("behavior.txt", false);
            this.behavior = Files.readString(behaviorFile.toPath());
        } catch (Exception e) { getLogger().warning("Could not load behavior.txt."); }

        // Initialize the connection to Google's Gemini AI.
        this.client = new GeminiClient(apiKey, model);
        
        // Setup the "ChatListener" which watches what players type.
        if (this.listener != null) {
            this.listener.updateConfig(client, behavior);
        } else {
            this.listener = new ChatListener(this, client, behavior);
            getServer().getPluginManager().registerEvents(listener, this);
        }

        // If "yapper" is enabled, start the timer for random bot messages.
        if (getConfig().getBoolean("yapper.enabled")) {
            int min = getConfig().getInt("yapper.min-delay", 10);
            new YapTask(this, client, listener).runTaskLater(this, (long) min * 60 * 20);
        }
    }

    @Override
    public void onDisable() {
        // This runs when the server SHUTS DOWN.
        saveChatSummary();
        getLogger().info("AI Minecraft Plugin Disabled.");
    }

    private void saveChatSummary() {
        // This function asks the AI to summarize everything that happened in the chat
        // and saves it to "brain.log" so the bot "remembers" it next time.
        if (listener == null || client == null) return;
        String context = listener.getHistory();
        if (context.isEmpty()) return;

        String template = getConfig().getString("prompts.session-summary");
        String prompt;
        if (template != null) {
            prompt = template.replace("{history}", context);
        } else {
            prompt = "[TASK]: Below is the recent chat history from the Minecraft server. " +
                     "Summarize the main topics and events in 3 sentences for the server logs.\n\n" +
                     "[CHAT HISTORY]:\n" + context + "\n\nSummary:";
        }

        try {
            // We force the server to WAIT here for up to 5 seconds.
            // If we don't wait, the server will close before the AI can finish writing!
            String summary = client.generateResponse(prompt).get(5, TimeUnit.SECONDS);
            if (summary != null && !summary.isBlank()) {
                File brainFile = new File(getDataFolder(), "brain.log");
                String logEntry = "\n--- SESSION SUMMARY (" + new Date().toString() + ") ---\n" + 
                                  summary.trim() + "\n------------------------------------------\n";
                // Append the summary to the end of brain.log
                Files.writeString(brainFile.toPath(), logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                getLogger().info("Successfully saved session summary to brain.log");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to save chat summary on shutdown: " + e.getMessage());
        }
    }
}
