package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.StructureType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans the 3D block environment around a player.
 * ALL methods must be called from the main Bukkit thread.
 */
public class WorldScanner {

    // Notable blocks we specifically flag when found
    private static final Set<Material> NOTABLE_BLOCKS = Set.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
        Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
        Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.ANVIL,
        Material.BREWING_STAND, Material.LECTERN, Material.BEACON,
        Material.SPAWNER, Material.TNT, Material.END_PORTAL_FRAME,
        Material.NETHER_PORTAL, Material.RED_BED,
        Material.WHITE_BED, Material.BLACK_BED, Material.BLUE_BED,
        Material.BROWN_BED, Material.CYAN_BED, Material.GRAY_BED,
        Material.GREEN_BED, Material.LIGHT_BLUE_BED, Material.LIGHT_GRAY_BED,
        Material.LIME_BED, Material.MAGENTA_BED, Material.ORANGE_BED,
        Material.PINK_BED, Material.PURPLE_BED, Material.YELLOW_BED
    );

    // Structure types to search for nearby
    private static final Map<StructureType, String> STRUCTURE_SEARCH = Map.of(
        StructureType.VILLAGE, "Village",
        StructureType.STRONGHOLD, "Stronghold",
        StructureType.MINESHAFT, "Mineshaft",
        StructureType.OCEAN_MONUMENT, "Ocean Monument",
        StructureType.NETHER_FORTRESS, "Nether Fortress",
        StructureType.END_CITY, "End City",
        StructureType.DESERT_PYRAMID, "Desert Temple",
        StructureType.JUNGLE_PYRAMID, "Jungle Temple",
        StructureType.PILLAGER_OUTPOST, "Pillager Outpost",
        StructureType.RUINED_PORTAL, "Ruined Portal"
    );

    /**
     * Result object holding all scanned data.
     */
    public static class ScanResult {
        public final Material[][][] blocks; // [y][z][x] relative to scan origin
        public final int radius;
        public final int layersBelow;
        public final int layersAbove;
        public final int playerBlockX;
        public final int playerBlockY;
        public final int playerBlockZ;
        public final float playerYaw;
        public final float playerPitch;
        public final String biome;
        public final String dimension;
        public final String timeOfDay;
        public final String weather;
        public final int lightLevel;
        public final double health;
        public final int maxHealth;
        public final int foodLevel;
        public final boolean isFlying;
        public final boolean isSwimming;
        public final boolean isSneaking;
        public final Map<String, Integer> notableBlocks;
        public final List<String> nearbyStructures;
        public final List<String> hostileMobs;
        public final List<String> passiveMobs;
        public final List<String> nearbyPlayers;

        public ScanResult(Material[][][] blocks, int radius, int layersBelow, int layersAbove,
                          int px, int py, int pz, float yaw, float pitch, String biome, String dimension,
                          String timeOfDay, String weather, int lightLevel,
                          double health, int maxHealth, int foodLevel,
                          boolean isFlying, boolean isSwimming, boolean isSneaking,
                          Map<String, Integer> notableBlocks, List<String> nearbyStructures,
                          List<String> hostileMobs, List<String> passiveMobs,
                          List<String> nearbyPlayers) {
            this.blocks = blocks;
            this.radius = radius;
            this.layersBelow = layersBelow;
            this.layersAbove = layersAbove;
            this.playerBlockX = px;
            this.playerBlockY = py;
            this.playerBlockZ = pz;
            this.playerYaw = yaw;
            this.playerPitch = pitch;
            this.biome = biome;
            this.dimension = dimension;
            this.timeOfDay = timeOfDay;
            this.weather = weather;
            this.lightLevel = lightLevel;
            this.health = health;
            this.maxHealth = maxHealth;
            this.foodLevel = foodLevel;
            this.isFlying = isFlying;
            this.isSwimming = isSwimming;
            this.isSneaking = isSneaking;
            this.notableBlocks = notableBlocks;
            this.nearbyStructures = nearbyStructures;
            this.hostileMobs = hostileMobs;
            this.passiveMobs = passiveMobs;
            this.nearbyPlayers = nearbyPlayers;
        }
    }

    /**
     * Perform a full environment scan around a player. MUST be called on main thread.
     */
    public static ScanResult scan(Player player, int radius, int layersBelow, int layersAbove) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        int diameter = radius * 2 + 1;
        int totalLayers = layersBelow + layersAbove + 1;
        Material[][][] blocks = new Material[totalLayers][diameter][diameter];
        Map<String, Integer> notableBlocks = new LinkedHashMap<>();

        // Scan blocks in a cube around the player
        for (int dy = 0; dy < totalLayers; dy++) {
            int worldY = py - layersBelow + dy;
            for (int dz = 0; dz < diameter; dz++) {
                int worldZ = pz - radius + dz;
                for (int dx = 0; dx < diameter; dx++) {
                    int worldX = px - radius + dx;
                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    Material mat = block.getType();
                    blocks[dy][dz][dx] = mat;

                    // Track notable blocks
                    if (NOTABLE_BLOCKS.contains(mat) || mat.name().contains("BED")) {
                        String name = formatMaterialName(mat);
                        notableBlocks.merge(name, 1, Integer::sum);
                    }
                }
            }
        }

        // Environment info
        String biome = loc.getBlock().getBiome().name();
        String dimension = world.getEnvironment().name();
        String timeOfDay = getTimeDescription(world.getTime());
        String weather = world.hasStorm() ? (world.isThundering() ? "THUNDERING" : "RAINING") : "CLEAR";
        int lightLevel = loc.getBlock().getLightLevel();

        // Player status
        double health = player.getHealth();
        int maxHealth = (int) player.getMaxHealth();
        int foodLevel = player.getFoodLevel();
        boolean isFlying = player.isFlying();
        boolean isSwimming = player.isSwimming();
        boolean isSneaking = player.isSneaking();

        // Nearby structures
        List<String> nearbyStructures = new ArrayList<>();
        for (Map.Entry<StructureType, String> entry : STRUCTURE_SEARCH.entrySet()) {
            try {
                Location structLoc = world.locateNearestStructure(loc, entry.getKey(), 100, false);
                if (structLoc != null) {
                    int dist = (int) loc.distance(structLoc);
                    String dir = getDirection(loc, structLoc);
                    nearbyStructures.add(entry.getValue() + ": ~" + dist + " blocks " + dir);
                }
            } catch (Exception ignored) {
                // Some structure types may not exist in certain dimensions
            }
        }

        // Nearby entities
        List<String> hostileMobs = new ArrayList<>();
        List<String> passiveMobs = new ArrayList<>();
        List<String> nearbyPlayers = new ArrayList<>();
        Map<String, Integer> hostileCount = new LinkedHashMap<>();
        Map<String, Integer> passiveCount = new LinkedHashMap<>();

        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof Player other) {
                int dist = (int) loc.distance(other.getLocation());
                nearbyPlayers.add(other.getName() + " (" + dist + " blocks away)");
            } else if (entity instanceof Monster) {
                String name = formatEntityName(entity);
                hostileCount.merge(name, 1, Integer::sum);
            } else if (entity instanceof LivingEntity) {
                String name = formatEntityName(entity);
                passiveCount.merge(name, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : hostileCount.entrySet()) {
            hostileMobs.add(e.getKey() + " ×" + e.getValue());
        }
        for (Map.Entry<String, Integer> e : passiveCount.entrySet()) {
            passiveMobs.add(e.getKey() + " ×" + e.getValue());
        }

        return new ScanResult(blocks, radius, layersBelow, layersAbove,
            px, py, pz, yaw, pitch, biome, dimension, timeOfDay, weather, lightLevel,
            health, maxHealth, foodLevel, isFlying, isSwimming, isSneaking,
            notableBlocks, nearbyStructures, hostileMobs, passiveMobs, nearbyPlayers);
    }

    /**
     * Build a human/AI-readable text summary of the scan result.
     */
    public static String buildTextSummary(ScanResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("[SURROUNDINGS OF PLAYER]:\n");

        // Player status
        sb.append("- Health: ").append((int) r.health).append("/").append(r.maxHealth);
        sb.append(" | Hunger: ").append(r.foodLevel).append("/20");
        sb.append(" | Biome: ").append(r.biome);
        sb.append(" | Dimension: ").append(r.dimension);
        sb.append(" | Y-level: ").append(r.playerBlockY);
        sb.append(" | Light: ").append(r.lightLevel);
        sb.append(" | Time: ").append(r.timeOfDay);
        sb.append(" | Weather: ").append(r.weather).append("\n");

        if (r.isFlying) sb.append("- Player is FLYING\n");
        if (r.isSwimming) sb.append("- Player is SWIMMING\n");
        if (r.isSneaking) sb.append("- Player is SNEAKING\n");

        // Notable blocks
        if (!r.notableBlocks.isEmpty()) {
            sb.append("- Notable blocks nearby: ");
            sb.append(r.notableBlocks.entrySet().stream()
                .map(e -> e.getKey() + " ×" + e.getValue())
                .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        // Structures
        if (!r.nearbyStructures.isEmpty()) {
            sb.append("- Nearby structures: ");
            sb.append(String.join(", ", r.nearbyStructures));
            sb.append("\n");
        }

        // Entities
        if (!r.hostileMobs.isEmpty()) {
            sb.append("- Hostile mobs: ").append(String.join(", ", r.hostileMobs)).append("\n");
        }
        if (!r.passiveMobs.isEmpty()) {
            sb.append("- Passive mobs: ").append(String.join(", ", r.passiveMobs)).append("\n");
        }
        if (!r.nearbyPlayers.isEmpty()) {
            sb.append("- Nearby players: ").append(String.join(", ", r.nearbyPlayers)).append("\n");
        }

        return sb.toString();
    }

    /**
     * Convert scan result to JSON for the web viewer API.
     */
    public static String toJson(ScanResult r) {
        Gson gson = new Gson();
        JsonObject root = new JsonObject();

        // Player info
        JsonObject playerInfo = new JsonObject();
        playerInfo.addProperty("x", r.playerBlockX);
        playerInfo.addProperty("y", r.playerBlockY);
        playerInfo.addProperty("z", r.playerBlockZ);
        playerInfo.addProperty("yaw", r.playerYaw);
        playerInfo.addProperty("pitch", r.playerPitch);
        playerInfo.addProperty("health", r.health);
        playerInfo.addProperty("maxHealth", r.maxHealth);
        playerInfo.addProperty("foodLevel", r.foodLevel);
        playerInfo.addProperty("biome", r.biome);
        playerInfo.addProperty("dimension", r.dimension);
        playerInfo.addProperty("timeOfDay", r.timeOfDay);
        playerInfo.addProperty("weather", r.weather);
        playerInfo.addProperty("lightLevel", r.lightLevel);
        root.add("player", playerInfo);

        // Block grid
        JsonObject grid = new JsonObject();
        grid.addProperty("radius", r.radius);
        grid.addProperty("layersBelow", r.layersBelow);
        grid.addProperty("layersAbove", r.layersAbove);
        JsonArray layers = new JsonArray();
        for (int y = 0; y < r.blocks.length; y++) {
            JsonArray layer = new JsonArray();
            for (int z = 0; z < r.blocks[y].length; z++) {
                JsonArray row = new JsonArray();
                for (int x = 0; x < r.blocks[y][z].length; x++) {
                    row.add(r.blocks[y][z][x].name());
                }
                layer.add(row);
            }
            layers.add(layer);
        }
        grid.add("layers", layers);
        root.add("grid", grid);

        // Entities
        JsonObject entities = new JsonObject();
        JsonArray hostile = new JsonArray();
        r.hostileMobs.forEach(hostile::add);
        entities.add("hostile", hostile);
        JsonArray passive = new JsonArray();
        r.passiveMobs.forEach(passive::add);
        entities.add("passive", passive);
        JsonArray players = new JsonArray();
        r.nearbyPlayers.forEach(players::add);
        entities.add("players", players);
        root.add("entities", entities);

        // Notable blocks
        JsonObject notable = new JsonObject();
        r.notableBlocks.forEach(notable::addProperty);
        root.add("notableBlocks", notable);

        // Structures
        JsonArray structures = new JsonArray();
        r.nearbyStructures.forEach(structures::add);
        root.add("nearbyStructures", structures);

        return gson.toJson(root);
    }

    // ---- Helpers ----

    private static String getTimeDescription(long time) {
        if (time < 1000) return "DAWN";
        if (time < 6000) return "MORNING";
        if (time < 12000) return "AFTERNOON";
        if (time < 13000) return "SUNSET";
        if (time < 18000) return "NIGHT";
        if (time < 23000) return "MIDNIGHT";
        return "DAWN";
    }

    private static String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "S";
        if (angle < 67.5) return "SW";
        if (angle < 112.5) return "W";
        if (angle < 157.5) return "NW";
        if (angle < 202.5) return "N";
        if (angle < 247.5) return "NE";
        if (angle < 292.5) return "E";
        return "SE";
    }

    private static String formatMaterialName(Material mat) {
        return Arrays.stream(mat.name().split("_"))
            .map(s -> s.charAt(0) + s.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }

    private static String formatEntityName(Entity entity) {
        return Arrays.stream(entity.getType().name().split("_"))
            .map(s -> s.charAt(0) + s.substring(1).toLowerCase())
            .collect(Collectors.joining(" "));
    }
}
