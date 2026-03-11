package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MemoryManager {

    private final AiPlugin plugin;
    private final File profilesDir;
    private final Gson gson;
    private final Map<UUID, PlayerProfile> profileCache = new HashMap<>();

    public MemoryManager(AiPlugin plugin) {
        this.plugin = plugin;
        this.profilesDir = new File(plugin.getDataFolder(), "profiles");
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public PlayerProfile getProfile(Player player) {
        UUID uuid = player.getUniqueId();
        if (profileCache.containsKey(uuid)) {
            return profileCache.get(uuid);
        }

        File file = new File(profilesDir, uuid + ".json");
        PlayerProfile profile;
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                profile = gson.fromJson(reader, PlayerProfile.class);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load profile for " + player.getName() + ": " + e.getMessage());
                profile = new PlayerProfile();
            }
        } else {
            profile = new PlayerProfile();
        }

        profileCache.put(uuid, profile);
        return profile;
    }

    public void incrementInteractions(Player player) {
        PlayerProfile profile = getProfile(player);
        profile.interactionCount++;
        saveProfile(player.getUniqueId(), profile);
    }

    public void saveProfile(UUID uuid, PlayerProfile profile) {
        File file = new File(profilesDir, uuid + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(profile, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save profile for " + uuid + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, PlayerProfile> entry : profileCache.entrySet()) {
            saveProfile(entry.getKey(), entry.getValue());
        }
    }

    public void addNote(String playerName, String note) {
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(playerName);
        if (op == null) {
            // Fallback for players not in cache; blocking call but okay for this context
            op = Bukkit.getOfflinePlayer(playerName);
        }
        
        if (op == null || op.getUniqueId() == null) return;
        
        UUID uuid = op.getUniqueId();
        
        PlayerProfile profile;
        if (profileCache.containsKey(uuid)) {
            profile = profileCache.get(uuid);
        } else {
            File file = new File(profilesDir, uuid + ".json");
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    profile = gson.fromJson(reader, PlayerProfile.class);
                } catch (IOException e) {
                    profile = new PlayerProfile();
                }
            } else {
                profile = new PlayerProfile();
            }
        }
        
        profile.aiNotes.add(note);
        saveProfile(uuid, profile);
        profileCache.put(uuid, profile);
    }

    public String buildPlayerSummary(Player player) {
        PlayerProfile profile = getProfile(player);

        long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        double playTimeHours = playTimeTicks / (20.0 * 60.0 * 60.0);
        int deaths = player.getStatistic(Statistic.DEATHS);
        int mobKills = player.getStatistic(Statistic.MOB_KILLS);
        int playerKills = player.getStatistic(Statistic.PLAYER_KILLS);

        long daysSinceFirstSeen = (System.currentTimeMillis() - profile.firstSeen) / (1000L * 60L * 60L * 24L);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "[PLAYER PROFILE OF %s]:\n" +
            "- Playtime: %.1f hours\n" +
            "- Server Veteran Status: First seen %d days ago\n" +
            "- Lifetime Deaths: %d\n" +
            "- Monsters Killed: %d\n" +
            "- Players Killed: %d\n" +
            "- Iteractions with AI: %d times\n",
            player.getName(), playTimeHours, daysSinceFirstSeen, deaths, mobKills, playerKills, profile.interactionCount
        ));

        if (!profile.aiNotes.isEmpty()) {
            sb.append("- AI Notes on player:\n");
            for (String note : profile.aiNotes) {
                sb.append("  * ").append(note).append("\n");
            }
        }

        return sb.toString();
    }
}
