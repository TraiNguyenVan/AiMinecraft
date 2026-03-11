package com.yourname.aiminecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.StructureType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans the 3D block environment around a player.
 * ALL methods must be called from the main Bukkit thread.
 */
public class WorldScanner {

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

    // =========================================================
    //  ScanResult — full snapshot of the player's environment
    // =========================================================

    public static class ScanResult {
        // Block grid
        public final Material[][][] blocks;
        public final int radius, layersBelow, layersAbove;

        // Player position & orientation
        public final int playerBlockX, playerBlockY, playerBlockZ;
        public final float playerYaw, playerPitch;

        // Environment
        public final String biome, dimension, timeOfDay, weather;
        public final int lightLevel;

        // Player status
        public final double health;
        public final int maxHealth, foodLevel;
        public final boolean isFlying, isSwimming, isSneaking;

        // Notable blocks / structures / entities
        public final Map<String, Integer> notableBlocks;
        public final List<String> nearbyStructures;
        public final List<String> hostileMobs;   // now includes distance, direction, hp%
        public final List<String> passiveMobs;
        public final List<String> nearbyPlayers;

        // ── NEW: Equipment ──────────────────────────────────────
        public final String mainHandItem;   // e.g. "Diamond Sword [Sharpness V, Looting III] (87% dur)"
        public final String offHandItem;    // e.g. "Shield" or null
        public final String armorHelmet;
        public final String armorChestplate;
        public final String armorLeggings;
        public final String armorBoots;
        public final List<String> hotbarItems; // non-empty slots 0-8
        public final int xpLevel;

        // ── NEW: Crosshair target ───────────────────────────────
        public final String targetBlock;   // block player is looking at (≤5m)
        public final String targetEntity;  // entity player is aiming at (≤5m)

        // ── NEW: Terrain context ────────────────────────────────
        public final String surfaceBlock;   // block directly under feet
        public final String facingCompass;  // "NNW", "SE", etc.
        public final String terrainTheme;   // "Underground", "Desert", "Nether", etc.
        public final boolean isInPlayerBuild; // majority of blocks are player-placed

        public ScanResult(
                Material[][][] blocks, int radius, int layersBelow, int layersAbove,
                int px, int py, int pz, float yaw, float pitch,
                String biome, String dimension, String timeOfDay, String weather, int lightLevel,
                double health, int maxHealth, int foodLevel,
                boolean isFlying, boolean isSwimming, boolean isSneaking,
                Map<String, Integer> notableBlocks, List<String> nearbyStructures,
                List<String> hostileMobs, List<String> passiveMobs, List<String> nearbyPlayers,
                // new params
                String mainHandItem, String offHandItem,
                String armorHelmet, String armorChestplate, String armorLeggings, String armorBoots,
                List<String> hotbarItems, int xpLevel,
                String targetBlock, String targetEntity,
                String surfaceBlock, String facingCompass, String terrainTheme, boolean isInPlayerBuild) {

            this.blocks = blocks;
            this.radius = radius; this.layersBelow = layersBelow; this.layersAbove = layersAbove;
            this.playerBlockX = px; this.playerBlockY = py; this.playerBlockZ = pz;
            this.playerYaw = yaw; this.playerPitch = pitch;
            this.biome = biome; this.dimension = dimension;
            this.timeOfDay = timeOfDay; this.weather = weather; this.lightLevel = lightLevel;
            this.health = health; this.maxHealth = maxHealth; this.foodLevel = foodLevel;
            this.isFlying = isFlying; this.isSwimming = isSwimming; this.isSneaking = isSneaking;
            this.notableBlocks = notableBlocks; this.nearbyStructures = nearbyStructures;
            this.hostileMobs = hostileMobs; this.passiveMobs = passiveMobs; this.nearbyPlayers = nearbyPlayers;
            this.mainHandItem = mainHandItem; this.offHandItem = offHandItem;
            this.armorHelmet = armorHelmet; this.armorChestplate = armorChestplate;
            this.armorLeggings = armorLeggings; this.armorBoots = armorBoots;
            this.hotbarItems = hotbarItems; this.xpLevel = xpLevel;
            this.targetBlock = targetBlock; this.targetEntity = targetEntity;
            this.surfaceBlock = surfaceBlock; this.facingCompass = facingCompass;
            this.terrainTheme = terrainTheme; this.isInPlayerBuild = isInPlayerBuild;
        }
    }

    // =========================================================
    //  Main scan — MUST be called on main thread
    // =========================================================

    public static ScanResult scan(Player player, int radius, int layersBelow, int layersAbove) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int px = loc.getBlockX(), py = loc.getBlockY(), pz = loc.getBlockZ();
        float yaw = loc.getYaw(), pitch = loc.getPitch();

        int diameter    = radius * 2 + 1;
        int totalLayers = layersBelow + layersAbove + 1;
        Material[][][] blocks = new Material[totalLayers][diameter][diameter];
        Map<String, Integer> notableBlocks = new LinkedHashMap<>();

        int constructedCount = 0, totalNonAir = 0;

        for (int dy = 0; dy < totalLayers; dy++) {
            int worldY = py - layersBelow + dy;
            for (int dz = 0; dz < diameter; dz++) {
                int worldZ = pz - radius + dz;
                for (int dx = 0; dx < diameter; dx++) {
                    int worldX = px - radius + dx;
                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    Material mat = block.getType();
                    blocks[dy][dz][dx] = mat;

                    if (mat != Material.AIR && mat != Material.CAVE_AIR && mat != Material.VOID_AIR) {
                        totalNonAir++;
                        if (isConstructed(mat)) constructedCount++;
                    }

                    if (NOTABLE_BLOCKS.contains(mat) || mat.name().contains("BED")) {
                        notableBlocks.merge(formatMaterialName(mat), 1, Integer::sum);
                    }
                }
            }
        }

        // Environment
        String biome     = loc.getBlock().getBiome().name();
        String dimension = world.getEnvironment().name();
        String timeOfDay = getTimeDescription(world.getTime());
        String weather   = world.hasStorm() ? (world.isThundering() ? "THUNDERING" : "RAINING") : "CLEAR";
        int lightLevel   = loc.getBlock().getLightLevel();

        // Player status
        double health    = player.getHealth();
        int maxHealth    = (int) player.getMaxHealth();
        int foodLevel    = player.getFoodLevel();
        boolean isFlying    = player.isFlying();
        boolean isSwimming  = player.isSwimming();
        boolean isSneaking  = player.isSneaking();

        // ── Structures ──
        List<String> nearbyStructures = new ArrayList<>();
        for (Map.Entry<StructureType, String> entry : STRUCTURE_SEARCH.entrySet()) {
            try {
                Location structLoc = world.locateNearestStructure(loc, entry.getKey(), 100, false);
                if (structLoc != null) {
                    int dist = (int) loc.distance(structLoc);
                    nearbyStructures.add(entry.getValue() + ": ~" + dist + "m " + getDirection(loc, structLoc));
                }
            } catch (Exception ignored) {}
        }

        // ── Entities (with per-entity detail) ──
        List<String> hostileMobs   = new ArrayList<>();
        List<String> passiveMobs   = new ArrayList<>();
        List<String> nearbyPlayers = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(32, 32, 32)) {
            double dist = loc.distance(entity.getLocation());
            String dir  = getDirection(loc, entity.getLocation());

            if (entity instanceof Player other) {
                StringBuilder sb = new StringBuilder();
                sb.append(other.getName()).append(" — ").append((int) dist).append("m ").append(dir);
                String hand = formatItem(other.getInventory().getItemInMainHand());
                if (hand != null) sb.append(", holding ").append(hand);
                if (other.isSneaking()) sb.append(" [sneaking]");
                if (other.isFlying())   sb.append(" [flying]");
                nearbyPlayers.add(sb.toString());

            } else if (entity instanceof Monster monster) {
                StringBuilder sb = new StringBuilder();
                String name = formatEntityName(entity);
                if (entity.getCustomName() != null)
                    name = "\"" + entity.getCustomName() + "\" (" + name + ")";
                sb.append(name).append(" — ").append((int) dist).append("m ").append(dir);
                int hpPct = (int)(monster.getHealth() / monster.getMaxHealth() * 100);
                sb.append(" (").append(hpPct).append("% hp)");
                if (monster.getTarget() != null && monster.getTarget().equals(player))
                    sb.append(" ⚠ targeting you");
                hostileMobs.add(sb.toString());

            } else if (entity instanceof LivingEntity le) {
                StringBuilder sb = new StringBuilder();
                String name = formatEntityName(entity);
                if (entity.getCustomName() != null)
                    name = "\"" + entity.getCustomName() + "\" (" + name + ")";
                sb.append(name).append(" — ").append((int) dist).append("m ").append(dir);
                passiveMobs.add(sb.toString());
            }
        }

        // ── Equipment ──
        String mainHandItem   = formatItem(player.getInventory().getItemInMainHand());
        String offHandItem    = formatItem(player.getInventory().getItemInOffHand());
        ItemStack[] armorArr  = player.getInventory().getArmorContents();
        // armorContents: [0]=boots, [1]=leggings, [2]=chestplate, [3]=helmet
        String armorBoots      = (armorArr.length > 0) ? formatItem(armorArr[0]) : null;
        String armorLeggings   = (armorArr.length > 1) ? formatItem(armorArr[1]) : null;
        String armorChestplate = (armorArr.length > 2) ? formatItem(armorArr[2]) : null;
        String armorHelmet     = (armorArr.length > 3) ? formatItem(armorArr[3]) : null;
        int xpLevel = player.getLevel();

        List<String> hotbarItems = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String item = formatItem(player.getInventory().getItem(i));
            if (item != null) hotbarItems.add("Slot " + (i + 1) + ": " + item);
        }

        // ── Crosshair target ──
        String targetBlockStr = null;
        try {
            Block tb = player.getTargetBlockExact(5);
            if (tb != null && tb.getType() != Material.AIR)
                targetBlockStr = formatMaterialName(tb.getType()) + " at " + tb.getX() + "," + tb.getY() + "," + tb.getZ();
        } catch (Exception ignored) {}

        String targetEntityStr = null;
        try {
            Entity te = player.getTargetEntity(5);
            if (te != null) {
                targetEntityStr = formatEntityName(te);
                if (te instanceof LivingEntity le)
                    targetEntityStr += " (" + (int)(le.getHealth() / le.getMaxHealth() * 100) + "% hp)";
            }
        } catch (Exception ignored) {}

        // ── Terrain context ──
        Block blockBelow = world.getBlockAt(px, py - 1, pz);
        String surfaceBlock = formatMaterialName(blockBelow.getType());
        String facingCompass = getFacingCompass(yaw);
        String terrainTheme  = detectTerrainTheme(biome, dimension, py);
        boolean isInPlayerBuild = totalNonAir > 0 && (double) constructedCount / totalNonAir > 0.15;

        return new ScanResult(
                blocks, radius, layersBelow, layersAbove,
                px, py, pz, yaw, pitch,
                biome, dimension, timeOfDay, weather, lightLevel,
                health, maxHealth, foodLevel, isFlying, isSwimming, isSneaking,
                notableBlocks, nearbyStructures, hostileMobs, passiveMobs, nearbyPlayers,
                mainHandItem, offHandItem,
                armorHelmet, armorChestplate, armorLeggings, armorBoots,
                hotbarItems, xpLevel,
                targetBlockStr, targetEntityStr,
                surfaceBlock, facingCompass, terrainTheme, isInPlayerBuild);
    }

    // =========================================================
    //  Text summary for the AI prompt
    // =========================================================

    public static String buildTextSummary(ScanResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("[SURROUNDINGS OF PLAYER]:\n");

        // ── Status ──
        sb.append("- Health: ").append((int) r.health).append("/").append(r.maxHealth);
        sb.append(" | Hunger: ").append(r.foodLevel).append("/20");
        sb.append(" | XP Level: ").append(r.xpLevel).append("\n");
        sb.append("- Biome: ").append(r.biome);
        sb.append(" | Dimension: ").append(r.dimension);
        sb.append(" | Y: ").append(r.playerBlockY);
        sb.append(" | Light: ").append(r.lightLevel);
        sb.append(" | Time: ").append(r.timeOfDay);
        sb.append(" | Weather: ").append(r.weather).append("\n");
        sb.append("- Facing: ").append(r.facingCompass);
        sb.append(" | Standing on: ").append(r.surfaceBlock);
        sb.append(" | Terrain: ").append(r.terrainTheme);
        if (r.isInPlayerBuild) sb.append(" (player-built area)");
        sb.append("\n");

        if (r.isFlying)   sb.append("- Player is FLYING\n");
        if (r.isSwimming) sb.append("- Player is SWIMMING\n");
        if (r.isSneaking) sb.append("- Player is SNEAKING\n");

        // ── Crosshair ──
        if (r.targetBlock  != null) sb.append("- Looking at block: ").append(r.targetBlock).append("\n");
        if (r.targetEntity != null) sb.append("- Aiming at entity: ").append(r.targetEntity).append("\n");

        // ── Equipment ──
        sb.append("- Main hand: ").append(r.mainHandItem != null ? r.mainHandItem : "(empty)").append("\n");
        if (r.offHandItem != null) sb.append("- Off hand: ").append(r.offHandItem).append("\n");

        // Armor (only list pieces that are worn)
        List<String> armorParts = new ArrayList<>();
        if (r.armorHelmet     != null) armorParts.add("Helmet: " + r.armorHelmet);
        if (r.armorChestplate != null) armorParts.add("Chest: " + r.armorChestplate);
        if (r.armorLeggings   != null) armorParts.add("Legs: " + r.armorLeggings);
        if (r.armorBoots      != null) armorParts.add("Boots: " + r.armorBoots);
        if (!armorParts.isEmpty()) sb.append("- Armor: ").append(String.join(", ", armorParts)).append("\n");
        else sb.append("- Armor: (none)\n");

        if (!r.hotbarItems.isEmpty()) {
            sb.append("- Hotbar: ").append(String.join(" | ", r.hotbarItems)).append("\n");
        }

        // ── Notable blocks ──
        if (!r.notableBlocks.isEmpty()) {
            sb.append("- Notable blocks nearby: ");
            sb.append(r.notableBlocks.entrySet().stream()
                .map(e -> e.getKey() + " ×" + e.getValue())
                .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        // ── Structures ──
        if (!r.nearbyStructures.isEmpty()) {
            sb.append("- Nearby structures: ").append(String.join(", ", r.nearbyStructures)).append("\n");
        }

        // ── Entities ──
        if (!r.hostileMobs.isEmpty()) {
            sb.append("- Hostile mobs:\n");
            r.hostileMobs.forEach(m -> sb.append("  • ").append(m).append("\n"));
        }
        if (!r.passiveMobs.isEmpty()) {
            sb.append("- Passive mobs:\n");
            r.passiveMobs.forEach(m -> sb.append("  • ").append(m).append("\n"));
        }
        if (!r.nearbyPlayers.isEmpty()) {
            sb.append("- Nearby players:\n");
            r.nearbyPlayers.forEach(p -> sb.append("  • ").append(p).append("\n"));
        }

        return sb.toString();
    }

    // =========================================================
    //  JSON export for web viewer (unchanged structure)
    // =========================================================

    public static String toJson(ScanResult r) {
        Gson gson = new Gson();
        JsonObject root = new JsonObject();

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

        JsonObject entities = new JsonObject();
        JsonArray hostile = new JsonArray(); r.hostileMobs.forEach(hostile::add);
        JsonArray passive = new JsonArray(); r.passiveMobs.forEach(passive::add);
        JsonArray players = new JsonArray(); r.nearbyPlayers.forEach(players::add);
        entities.add("hostile", hostile);
        entities.add("passive", passive);
        entities.add("players", players);
        root.add("entities", entities);

        JsonObject notable = new JsonObject();
        r.notableBlocks.forEach(notable::addProperty);
        root.add("notableBlocks", notable);

        JsonArray structures = new JsonArray();
        r.nearbyStructures.forEach(structures::add);
        root.add("nearbyStructures", structures);

        return gson.toJson(root);
    }

    // =========================================================
    //  Helpers
    // =========================================================

    /** Format an ItemStack into a readable string with enchants and durability. */
    private static String formatItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        StringBuilder sb = new StringBuilder(formatMaterialName(item.getType()));
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
                sb.insert(0, "\"" + displayName + "\" (").append(")");
            }
            if (meta.hasEnchants()) {
                String enchants = meta.getEnchants().entrySet().stream()
                    .map(e -> e.getKey().getKey().getKey().replace("_", " ") + " " + toRoman(e.getValue()))
                    .collect(Collectors.joining(", "));
                sb.append(" [").append(enchants).append("]");
            }
            if (item.getType().getMaxDurability() > 0 && meta instanceof org.bukkit.inventory.meta.Damageable dmg) {
                if (dmg.getDamage() > 0) {
                    int pct = (int)(100.0 * (item.getType().getMaxDurability() - dmg.getDamage()) / item.getType().getMaxDurability());
                    sb.append(" (").append(pct).append("% dur)");
                }
            }
        }
        if (item.getAmount() > 1) sb.append(" ×").append(item.getAmount());
        return sb.toString();
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    /** True if this material is almost certainly player-placed (processed/crafted). */
    private static boolean isConstructed(Material m) {
        String n = m.name();
        return n.contains("PLANKS") || n.contains("_BRICKS") || m == Material.BRICKS
            || n.contains("GLASS") || n.contains("WOOL") || n.contains("CONCRETE")
            || n.contains("_SLAB") || n.contains("_STAIRS") || n.contains("_FENCE")
            || n.contains("_DOOR") || n.contains("_TRAPDOOR")
            || n.contains("CRAFTING_TABLE") || n.contains("FURNACE") || n.contains("CHEST")
            || n.contains("ANVIL") || n.contains("ENCHANTING") || n.contains("BOOKSHELF")
            || n.contains("HOPPER") || n.contains("DISPENSER") || n.contains("DROPPER")
            || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("BEACON")
            || m == Material.COBBLESTONE || m == Material.SMOOTH_STONE
            || n.contains("IRON_BLOCK") || n.contains("GOLD_BLOCK");
    }

    /** Convert MC yaw to a 16-point compass label. */
    private static String getFacingCompass(float yaw) {
        // MC yaw: 0=S, 90=W, 180/−180=N, −90/270=E
        float normalized = ((yaw % 360) + 360) % 360;
        // Offset by half a segment (11.25°) so cardinal labels are centred
        String[] dirs = {"S","SSW","SW","WSW","W","WNW","NW","NNW","N","NNE","NE","ENE","E","ESE","SE","SSE"};
        int idx = (int)((normalized + 11.25f) / 22.5f) % 16;
        return dirs[idx];
    }

    private static String detectTerrainTheme(String biome, String dimension, int y) {
        if (dimension.equals("NETHER"))  return "The Nether";
        if (dimension.equals("THE_END")) return "The End";
        if (y < 0)  return "Bedrock layer";
        if (y < 30) return "Deep underground";
        if (y < 60) return "Underground";
        if (biome.contains("OCEAN") || biome.contains("BEACH")) return "Ocean / Coastal";
        if (biome.contains("DESERT")) return "Desert";
        if (biome.contains("SNOW") || biome.contains("ICE") || biome.contains("FROZEN")) return "Frozen / Snowy";
        if (biome.contains("JUNGLE")) return "Jungle";
        if (biome.contains("MUSHROOM")) return "Mushroom Island";
        if (biome.contains("SWAMP")) return "Swamp";
        if (biome.contains("BADLANDS") || biome.contains("MESA")) return "Badlands";
        if (biome.contains("CHERRY")) return "Cherry Grove";
        return "Surface";
    }

    private static String getTimeDescription(long time) {
        if (time < 1000)  return "DAWN";
        if (time < 6000)  return "MORNING";
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
        if (angle < 67.5)  return "SW";
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
