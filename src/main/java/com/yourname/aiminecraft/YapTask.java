package com.yourname.aiminecraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.Random;

// This is a "Task" that runs in the background to make the bot talk randomly.
public class YapTask extends BukkitRunnable {
    private final AiPlugin plugin;
    private final GeminiClient aiClient;
    private final ChatListener chatListener;
    private final Random random = new Random();
    private final File brainFile;

    public YapTask(AiPlugin plugin, GeminiClient aiClient, ChatListener chatListener) {
        this.plugin = plugin;
        this.aiClient = aiClient;
        this.chatListener = chatListener;
        this.brainFile = new File(plugin.getDataFolder(), "brain.log");
    }

    @Override
    public void run() {
        // If the "yapper" is turned off in config.yml, stop here.
        if (!plugin.getConfig().getBoolean("yapper.enabled")) return;
        
        // If no one is on the server, don't talk to yourself.
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleNext();
            return;
        }

        // NEW: Get information about the world (time and weather)
        org.bukkit.World world = Bukkit.getWorlds().get(0); // Get the main world
        String timeOfDay = getTimeDescription(world.getTime());
        String weather = world.hasStorm() ? (world.isThundering() ? "THUNDERING" : "RAINING") : "CLEAR";

        // Create the instructions for the AI to start a conversation.
        String prompt = buildYapPrompt(timeOfDay, weather);

        aiClient.generateResponse(prompt).thenAccept(response -> {
            // If the AI says 'SKIP' or gives no response, just wait for the next time.
            if (response == null || response.isBlank() || response.toUpperCase().contains("SKIP")) {
                scheduleNext();
                return;
            }

            // Go back to the main Minecraft thread to broadcast the message.
            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefix = plugin.getConfig().getString("bot-prefix", "§6[Server]§f ");
                Bukkit.broadcast(Component.text(prefix + response.trim()));
                // Add the bot's message to its memory.
                chatListener.addHistory("Server: " + response.trim());
                // Schedule the next random message.
                scheduleNext();
            });
        });
    }

    private void scheduleNext() {
        // Get the min and max wait times from config.yml (in minutes).
        int min = plugin.getConfig().getInt("yapper.min-delay", 10);
        int max = plugin.getConfig().getInt("yapper.max-delay", 15);
        // Pick a random number between min and max.
        // Then convert minutes to "ticks" (1 minute = 60 seconds * 20 ticks).
        long delayTicks = (long) (random.nextInt(max - min + 1) + min) * 60 * 20;

        // Run this task again after the random delay.
        new YapTask(plugin, aiClient, chatListener).runTaskLater(plugin, delayTicks);
    }

    // Helper to turn Minecraft time (0-24000) into human words
    private String getTimeDescription(long time) {
        if (time < 1000) return "DAWN";
        if (time < 6000) return "MORNING";
        if (time < 12000) return "AFTERNOON";
        if (time < 13000) return "SUNSET";
        if (time < 18000) return "NIGHT";
        if (time < 23000) return "MIDNIGHT";
        return "DAWN";
    }

    private String buildYapPrompt(String time, String weather) {
        // Get the long-term memory (brain.log).
        String brainContext = LogUtils.getAllLines(brainFile);
        String template = plugin.getConfig().getString("prompts.yap-prompt");

        if (template == null) {
            return "[PERSONALITY]:\n" + chatListener.getBehavior() + "\n\n" +
                   "[PREVIOUS KNOWLEDGE (BRAIN LOGS)]:\n" + brainContext + "\n\n" +
                   "[WORLD CONTEXT]:\n" +
                   "- Current Time: " + time + "\n" +
                   "- Weather: " + weather + "\n\n" +
                   "[RECENT CHAT]:\n" + chatListener.getHistory() + "\n\n" +
                   "[TASK]: Start a conversation or make a random observation about the server. " +
                   "Use the WORLD CONTEXT (time/weather) to make your comment feel real. " +
                   "Refer to past events if relevant. Keep it short.";
        }

        return template.replace("{personality}", chatListener.getBehavior())
                .replace("{brain}", brainContext)
                .replace("{time}", time)
                .replace("{weather}", weather)
                .replace("{history}", chatListener.getHistory());
    }
}
