package com.yourname.aiminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Date;

public class AiPlugin extends JavaPlugin {
    private GeminiClient client;
    private ChatListener listener;
    private String behavior;

    @Override
    public void onEnable() {
        loadResources();
        getCommand("ai").setExecutor(new AiCommand(this));
        getLogger().info("AI Minecraft Commands Registered!");
    }

    public void loadResources() {
        saveDefaultConfig();
        String apiKey = getConfig().getString("gemini-api-key");
        String model = getConfig().getString("gemini-model", "gemini-1.5-flash");
        
        this.behavior = "You are a helpful Minecraft server assistant.";
        try {
            File behaviorFile = new File(getDataFolder(), "behavior.txt");
            if (!behaviorFile.exists()) saveResource("behavior.txt", false);
            this.behavior = Files.readString(behaviorFile.toPath());
        } catch (Exception e) { getLogger().warning("Could not load behavior.txt."); }

        this.client = new GeminiClient(apiKey, model);
        
        // If listeners are already registered, we just update the client/behavior
        if (this.listener != null) {
            this.listener.updateConfig(client, behavior);
        } else {
            this.listener = new ChatListener(this, client, behavior);
            getServer().getPluginManager().registerEvents(listener, this);
        }

        if (getConfig().getBoolean("yapper.enabled")) {
            int min = getConfig().getInt("yapper.min-delay", 10);
            new YapTask(this, client, listener).runTaskLater(this, (long) min * 60 * 20);
        }
    }

    @Override
    public void onDisable() {
        saveChatSummary();
        getLogger().info("AI Minecraft Plugin Disabled.");
    }

    private void saveChatSummary() {
        if (listener == null || client == null) return;
        String context = listener.getHistory();
        if (context.isEmpty()) return;

        String prompt = "[TASK]: Below is the recent chat history from the Minecraft server. " +
                        "Summarize the main topics and events in 3 sentences for the server logs.\n\n" +
                        "[CHAT HISTORY]:\n" + context + "\n\nSummary:";

        client.generateResponse(prompt).thenAccept(summary -> {
            try {
                File brainFile = new File(getDataFolder(), "brain.log");
                String logEntry = "\n--- SESSION SUMMARY (" + new Date().toString() + ") ---\n" + 
                                  summary.trim() + "\n------------------------------------------\n";
                Files.writeString(brainFile.toPath(), logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}
