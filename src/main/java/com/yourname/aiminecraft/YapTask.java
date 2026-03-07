package com.yourname.aiminecraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.Random;

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
        if (!plugin.getConfig().getBoolean("yapper.enabled")) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleNext();
            return;
        }

        String prompt = buildYapPrompt();

        aiClient.generateResponse(prompt).thenAccept(response -> {
            if (response == null || response.isBlank() || response.toUpperCase().contains("SKIP")) {
                scheduleNext();
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                String prefix = plugin.getConfig().getString("bot-prefix", "§6[Server]§f ");
                Bukkit.broadcast(Component.text(prefix + response.trim()));
                chatListener.addHistory("Server: " + response.trim());
                scheduleNext();
            });
        });
    }

    private void scheduleNext() {
        int min = plugin.getConfig().getInt("yapper.min-delay", 10);
        int max = plugin.getConfig().getInt("yapper.max-delay", 15);
        long delayTicks = (long) (random.nextInt(max - min + 1) + min) * 60 * 20;

        new YapTask(plugin, aiClient, chatListener).runTaskLater(plugin, delayTicks);
    }

    private String buildYapPrompt() {
        String brainContext = LogUtils.getLastLines(brainFile, 30);
        return "[PERSONALITY]:\n" + chatListener.getBehavior() + "\n\n" +
               "[PREVIOUS KNOWLEDGE (BRAIN LOGS)]:\n" + brainContext + "\n\n" +
               "[RECENT CHAT]:\n" + chatListener.getHistory() + "\n\n" +
               "[TASK]: Start a conversation or make a random observation about the server. " +
               "Refer to past events if relevant. Keep it short.";
    }
}
