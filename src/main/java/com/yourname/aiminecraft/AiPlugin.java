package com.yourname.aiminecraft;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.nio.file.Files;

/**
 * Main plugin entry point. Loads config, starts AI client, web viewer, and event listeners.
 */
public class AiPlugin extends JavaPlugin {
    private GeminiClient client;
    private ChatListener listener;
    private MapWebServer webServer;
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

        // Load behavior.txt
        this.behavior = "You are a helpful Minecraft server assistant.";
        try {
            File behaviorFile = new File(getDataFolder(), "behavior.txt");
            if (!behaviorFile.exists()) saveResource("behavior.txt", false);
            this.behavior = Files.readString(behaviorFile.toPath());
        } catch (Exception e) { getLogger().warning("Could not load behavior.txt."); }

        this.client = new GeminiClient(apiKey, model);

        // Scanner config
        int scanRadius = getConfig().getInt("scanner.radius", 5);
        int layersAbove = getConfig().getInt("scanner.layers-above", 4);
        int layersBelow = getConfig().getInt("scanner.layers-below", 2);

        // Setup ChatListener
        if (this.listener != null) {
            this.listener.updateConfig(client, behavior);
            this.listener.setScannerConfig(scanRadius, layersAbove, layersBelow);
        } else {
            this.listener = new ChatListener(this, client, behavior);
            this.listener.setScannerConfig(scanRadius, layersAbove, layersBelow);
            getServer().getPluginManager().registerEvents(listener, this);
        }

        // Start YapTask
        if (getConfig().getBoolean("yapper.enabled")) {
            int min = getConfig().getInt("yapper.min-delay", 10);
            new YapTask(this, client, listener, scanRadius, layersAbove, layersBelow)
                .runTaskLater(this, (long) min * 60 * 20);
        }

        // Start web viewer
        if (getConfig().getBoolean("web-viewer.enabled", true)) {
            if (webServer != null) webServer.stop();
            int port = getConfig().getInt("web-viewer.port", 25462);
            webServer = new MapWebServer(this, port, scanRadius, layersAbove, layersBelow);
            try {
                webServer.start();
            } catch (Exception e) {
                getLogger().warning("Could not start map web viewer: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
        getLogger().info("AI Minecraft Plugin Disabled.");
    }
}
