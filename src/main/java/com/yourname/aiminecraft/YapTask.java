package com.yourname.aiminecraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Background task that randomly starts conversations using player surroundings.
 */
public class YapTask extends BukkitRunnable {
    private final AiPlugin plugin;
    private final GeminiClient aiClient;
    private final ChatListener chatListener;
    private final Random random = new Random();
    private final File brainFile;

    // Scanner config
    private final int scanRadius;
    private final int layersAbove;
    private final int layersBelow;
    
    private final MemoryManager memoryManager;

    public YapTask(AiPlugin plugin, GeminiClient aiClient, ChatListener chatListener,
                   int scanRadius, int layersAbove, int layersBelow, MemoryManager memoryManager) {
        this.plugin = plugin;
        this.aiClient = aiClient;
        this.chatListener = chatListener;
        this.brainFile = new File(plugin.getDataFolder(), "brain.log");
        this.scanRadius = scanRadius;
        this.layersAbove = layersAbove;
        this.layersBelow = layersBelow;
        this.memoryManager = memoryManager;
    }

    @Override
    public void run() {
        if (!plugin.isAiEnabled() || !plugin.getConfig().getBoolean("yapper.enabled")) {
            scheduleNext();
            return;
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            scheduleNext();
            return;
        }

        // Pick a random online player to observe
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        Player target = online.get(random.nextInt(online.size()));

        // Scan must happen on the main thread (this task already runs on main thread via runTaskLater)
        WorldScanner.ScanResult scan = WorldScanner.scan(target, scanRadius, layersBelow, layersAbove);
        String surroundings = WorldScanner.buildTextSummary(scan);
        String profileInfo = memoryManager.buildPlayerSummary(target);
        byte[] mapImage = MapRenderer.render(scan);

        String prompt = buildYapPrompt(target.getName(), surroundings, profileInfo);

        aiClient.generateResponse(prompt, mapImage).thenAccept(response -> {
            if (response == null || response.isBlank()) {
                scheduleNext();
                return;
            }

            String trimmedResponse = response.trim();
            if (trimmedResponse.equalsIgnoreCase("SKIP") || trimmedResponse.toUpperCase().startsWith("SKIP ")) {
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
        new YapTask(plugin, aiClient, chatListener, scanRadius, layersAbove, layersBelow, memoryManager)
            .runTaskLater(plugin, delayTicks);
    }

    private String buildYapPrompt(String observedPlayer, String surroundings, String profileInfo) {
        String brainContext = LogUtils.getAllLines(brainFile);
        String template = plugin.getConfig().getString("prompts.yap-prompt");

        if (template == null) {
            return "[PERSONALITY]:\n" + chatListener.getBehavior() + "\n\n" +
                   "[PREVIOUS KNOWLEDGE (BRAIN LOGS)]:\n" + brainContext + "\n\n" +
                   profileInfo + "\n\n" +
                   surroundings + "\n" +
                   "- Currently observing player: " + observedPlayer + "\n\n" +
                   "[VISUAL MAP]: An image of " + observedPlayer + "'s surroundings is attached.\n\n" +
                   "[RECENT CHAT]:\n" + chatListener.getHistory() + "\n\n" +
                   "[TASK]: Start a conversation or make a random observation about the server. " +
                   "Use the SURROUNDINGS data and the visual map to make your comment feel real. " +
                   "Comment on what " + observedPlayer + " is doing, where they are, or what's around them. " +
                   "Refer to past events if relevant. Keep it short.";
        }

        return template.replace("{personality}", chatListener.getBehavior())
                .replace("{brain}", brainContext)
                .replace("{surroundings}", surroundings)
                .replace("{player_profile}", profileInfo)
                .replace("{observed_player}", observedPlayer)
                .replace("{history}", chatListener.getHistory());
    }
}
