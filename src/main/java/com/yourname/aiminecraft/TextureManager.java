package com.yourname.aiminecraft;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads Minecraft block textures from the client jar (found in ./cache/).
 * Falls back to the flat color system in MapRenderer if textures are unavailable.
 *
 * Textures are cached as [top, side, bottom] face arrays per Material.
 */
public class TextureManager {

    public static final int FACE_TOP    = 0;
    public static final int FACE_SIDE   = 1;
    public static final int FACE_BOTTOM = 2;

    /** [top, side, bottom] texture per material */
    private static final Map<Material, BufferedImage[]> CACHE = new HashMap<>();
    private static boolean initialized = false;
    private static Logger log;

    // ---- Material → texture filename overrides ----
    // Format: MATERIAL_NAME -> { topTex, sideTex, bottomTex }
    // Most materials just use materialname.toLowerCase() for all 3 faces.
    private static final Map<String, String[]> TEX_OVERRIDES = new HashMap<>();
    static {
        // Ground blocks
        put("GRASS_BLOCK",          "grass_block_top",  "grass_block_side",       "dirt");
        put("MYCELIUM",             "mycelium_top",     "mycelium_side",          "dirt");
        put("PODZOL",               "podzol_top",       "podzol_side",            "dirt");
        put("COARSE_DIRT",          "coarse_dirt");
        put("FARMLAND",             "farmland_moist",   "dirt",                   "dirt");
        put("DIRT_PATH",            "dirt_path_top",    "dirt_path_side",         "dirt");
        put("SNOW_BLOCK",           "snow");
        put("MUD",                  "mud");
        put("PACKED_MUD",           "packed_mud");
        // Stone family
        put("STONE",                "stone");
        put("COBBLESTONE",          "cobblestone");
        put("MOSSY_COBBLESTONE",    "mossy_cobblestone");
        put("STONE_BRICKS",         "stone_bricks");
        put("MOSSY_STONE_BRICKS",   "mossy_stone_bricks");
        put("CRACKED_STONE_BRICKS", "cracked_stone_bricks");
        put("SMOOTH_STONE",         "smooth_stone");
        put("GRANITE",              "granite");
        put("DIORITE",              "diorite");
        put("ANDESITE",             "andesite");
        put("DEEPSLATE",            "deepslate_top",    "deepslate",              "deepslate_top");
        put("COBBLED_DEEPSLATE",    "cobbled_deepslate");
        put("POLISHED_DEEPSLATE",   "polished_deepslate");
        put("DEEPSLATE_BRICKS",     "deepslate_bricks");
        put("DEEPSLATE_TILES",      "deepslate_tiles");
        put("CALCITE",              "calcite");
        put("TUFF",                 "tuff");
        put("DRIPSTONE_BLOCK",      "dripstone_block");
        put("BEDROCK",              "bedrock");
        put("BLACKSTONE",           "blackstone_top",   "blackstone",             "blackstone_top");
        put("GILDED_BLACKSTONE",    "gilded_blackstone");
        put("BRICKS",               "bricks");
        // Sand / sandstone
        put("SAND",                 "sand");
        put("RED_SAND",             "red_sand");
        put("GRAVEL",               "gravel");
        put("SANDSTONE",            "sandstone_top",    "sandstone",              "sandstone_bottom");
        put("RED_SANDSTONE",        "red_sandstone_top","red_sandstone",          "red_sandstone_bottom");
        put("CHISELED_SANDSTONE",   "chiseled_sandstone");
        // Logs (top uses log_top, sides use log)
        put("OAK_LOG",              "oak_log_top",      "oak_log",                "oak_log_top");
        put("SPRUCE_LOG",           "spruce_log_top",   "spruce_log",             "spruce_log_top");
        put("BIRCH_LOG",            "birch_log_top",    "birch_log",              "birch_log_top");
        put("JUNGLE_LOG",           "jungle_log_top",   "jungle_log",             "jungle_log_top");
        put("ACACIA_LOG",           "acacia_log_top",   "acacia_log",             "acacia_log_top");
        put("DARK_OAK_LOG",         "dark_oak_log_top", "dark_oak_log",           "dark_oak_log_top");
        put("MANGROVE_LOG",         "mangrove_log_top", "mangrove_log",           "mangrove_log_top");
        put("CHERRY_LOG",           "cherry_log_top",   "cherry_log",             "cherry_log_top");
        put("CRIMSON_STEM",         "crimson_stem_top", "crimson_stem",           "crimson_stem_top");
        put("WARPED_STEM",          "warped_stem_top",  "warped_stem",            "warped_stem_top");
        put("BAMBOO_BLOCK",         "bamboo_block_top", "bamboo_block",           "bamboo_block_top");
        // Planks
        put("OAK_PLANKS",           "oak_planks");
        put("SPRUCE_PLANKS",        "spruce_planks");
        put("BIRCH_PLANKS",         "birch_planks");
        put("JUNGLE_PLANKS",        "jungle_planks");
        put("ACACIA_PLANKS",        "acacia_planks");
        put("DARK_OAK_PLANKS",      "dark_oak_planks");
        put("MANGROVE_PLANKS",      "mangrove_planks");
        put("CHERRY_PLANKS",        "cherry_planks");
        put("CRIMSON_PLANKS",       "crimson_planks");
        put("WARPED_PLANKS",        "warped_planks");
        // Leaves
        put("OAK_LEAVES",           "oak_leaves");
        put("SPRUCE_LEAVES",        "spruce_leaves");
        put("BIRCH_LEAVES",         "birch_leaves");
        put("JUNGLE_LEAVES",        "jungle_leaves");
        put("ACACIA_LEAVES",        "acacia_leaves");
        put("DARK_OAK_LEAVES",      "dark_oak_leaves");
        put("MANGROVE_LEAVES",      "mangrove_leaves");
        put("CHERRY_LEAVES",        "cherry_leaves");
        put("AZALEA_LEAVES",        "azalea_leaves");
        put("FLOWERING_AZALEA_LEAVES", "flowering_azalea_leaves");
        // Liquids
        put("WATER",                "water_still");
        put("LAVA",                 "lava_still");
        // Ores
        put("COAL_ORE",             "coal_ore");
        put("IRON_ORE",             "iron_ore");
        put("COPPER_ORE",           "copper_ore");
        put("GOLD_ORE",             "gold_ore");
        put("DIAMOND_ORE",          "diamond_ore");
        put("EMERALD_ORE",          "emerald_ore");
        put("LAPIS_ORE",            "lapis_ore");
        put("REDSTONE_ORE",         "redstone_ore");
        put("NETHER_QUARTZ_ORE",    "nether_quartz_ore");
        put("NETHER_GOLD_ORE",      "nether_gold_ore");
        put("ANCIENT_DEBRIS",       "ancient_debris_top", "ancient_debris_side",  "ancient_debris_top");
        // Deepslate ores
        put("DEEPSLATE_COAL_ORE",   "deepslate_coal_ore");
        put("DEEPSLATE_IRON_ORE",   "deepslate_iron_ore");
        put("DEEPSLATE_COPPER_ORE", "deepslate_copper_ore");
        put("DEEPSLATE_GOLD_ORE",   "deepslate_gold_ore");
        put("DEEPSLATE_DIAMOND_ORE","deepslate_diamond_ore");
        put("DEEPSLATE_EMERALD_ORE","deepslate_emerald_ore");
        put("DEEPSLATE_LAPIS_ORE",  "deepslate_lapis_ore");
        put("DEEPSLATE_REDSTONE_ORE","deepslate_redstone_ore");
        // Mineral blocks
        put("COAL_BLOCK",           "coal_block");
        put("IRON_BLOCK",           "iron_block");
        put("GOLD_BLOCK",           "gold_block");
        put("DIAMOND_BLOCK",        "diamond_block");
        put("EMERALD_BLOCK",        "emerald_block");
        put("LAPIS_BLOCK",          "lapis_block");
        put("REDSTONE_BLOCK",       "redstone_block");
        put("NETHERITE_BLOCK",      "netherite_block");
        put("COPPER_BLOCK",         "copper_block");
        put("AMETHYST_BLOCK",       "amethyst_block");
        put("RAW_IRON_BLOCK",       "raw_iron_block");
        put("RAW_COPPER_BLOCK",     "raw_copper_block");
        put("RAW_GOLD_BLOCK",       "raw_gold_block");
        // Functional
        put("CRAFTING_TABLE",       "crafting_table_top", "crafting_table_front", "oak_planks");
        put("FURNACE",              "furnace_top",      "furnace_side",           "furnace_top");
        put("BLAST_FURNACE",        "blast_furnace_top","blast_furnace_side",     "blast_furnace_top");
        put("SMOKER",               "smoker_top",       "smoker_side",            "smoker_bottom");
        put("CHEST",                "chest_top",        "chest_side",             "chest_bottom");
        put("TRAPPED_CHEST",        "chest_top",        "chest_side",             "chest_bottom");
        put("BARREL",               "barrel_top",       "barrel_side",            "barrel_bottom");
        put("BOOKSHELF",            "oak_planks",       "bookshelf",              "oak_planks");
        put("ENCHANTING_TABLE",     "enchanting_table_top","enchanting_table_side","enchanting_table_bottom");
        put("BEACON",               "beacon");
        put("SPAWNER",              "spawner");
        put("TNT",                  "tnt_top",          "tnt_side",               "tnt_bottom");
        put("OBSIDIAN",             "obsidian");
        put("CRYING_OBSIDIAN",      "crying_obsidian");
        put("PUMPKIN",              "pumpkin_top",      "pumpkin_side",           "pumpkin_top");
        put("JACK_O_LANTERN",       "pumpkin_top",      "jack_o_lantern",         "pumpkin_top");
        put("MELON",                "melon_top",        "melon_side",             "melon_top");
        put("NOTE_BLOCK",           "note_block");
        put("HAY_BLOCK",            "hay_block_top",    "hay_block_side",         "hay_block_top");
        put("BONE_BLOCK",           "bone_block_top",   "bone_block_side",        "bone_block_top");
        put("SPONGE",               "sponge");
        put("WET_SPONGE",           "wet_sponge");
        put("GLOWSTONE",            "glowstone");
        put("SEA_LANTERN",          "sea_lantern");
        put("SHROOMLIGHT",          "shroomlight");
        put("CLAY",                 "clay");
        put("ICE",                  "ice");
        put("PACKED_ICE",           "packed_ice");
        put("BLUE_ICE",             "blue_ice");
        put("MOSS_BLOCK",           "moss_block");
        put("SCULK",                "sculk");
        put("SCULK_CATALYST",       "sculk_catalyst_top","sculk_catalyst_side",  "sculk_catalyst_bottom");
        put("SCULK_SENSOR",         "sculk_sensor_top", "sculk_sensor_side",     "sculk_sensor_bottom");
        // Nether
        put("NETHERRACK",           "netherrack");
        put("NETHER_BRICKS",        "nether_bricks");
        put("RED_NETHER_BRICKS",    "red_nether_bricks");
        put("SOUL_SAND",            "soul_sand");
        put("SOUL_SOIL",            "soul_soil");
        put("MAGMA_BLOCK",          "magma");
        put("BASALT",               "basalt_top",       "basalt_side",            "basalt_top");
        put("POLISHED_BASALT",      "polished_basalt_top","polished_basalt_side", "polished_basalt_top");
        put("SMOOTH_BASALT",        "smooth_basalt");
        put("NETHER_WART_BLOCK",    "nether_wart_block");
        put("WARPED_WART_BLOCK",    "warped_wart_block");
        put("CRIMSON_NYLIUM",       "crimson_nylium",   "netherrack",             "netherrack");
        put("WARPED_NYLIUM",        "warped_nylium",    "netherrack",             "netherrack");
        // End
        put("END_STONE",            "end_stone");
        put("END_STONE_BRICKS",     "end_stone_bricks");
        put("PURPUR_BLOCK",         "purpur_block");
        put("PURPUR_PILLAR",        "purpur_pillar_top","purpur_pillar",          "purpur_pillar_top");
        // Quartz
        put("QUARTZ_BLOCK",         "quartz_block_top", "quartz_block_side",      "quartz_block_bottom");
        put("SMOOTH_QUARTZ",        "quartz_block_bottom");
        put("CHISELED_QUARTZ_BLOCK","chiseled_quartz_block_top","chiseled_quartz_block", "chiseled_quartz_block_top");
        // Glass
        put("GLASS",                "glass");
        put("TINTED_GLASS",         "tinted_glass");
        // Prismarine
        put("PRISMARINE",           "prismarine");
        put("PRISMARINE_BRICKS",    "prismarine_bricks");
        put("DARK_PRISMARINE",      "dark_prismarine");
    }

    private static void put(String mat, String all) {
        TEX_OVERRIDES.put(mat, new String[]{all, all, all});
    }
    private static void put(String mat, String top, String side, String bottom) {
        TEX_OVERRIDES.put(mat, new String[]{top, side, bottom});
    }

    // ---- Biome tints for foliage / grass ----
    // Applied as a multiplicative overlay when rendering these materials
    private static boolean isGrassTinted(Material m) {
        String n = m.name();
        return n.equals("GRASS_BLOCK") || n.contains("GRASS") || n.equals("FERN") || n.equals("LARGE_FERN");
    }
    private static boolean isLeafTinted(Material m) {
        String n = m.name();
        return n.contains("OAK_LEAVES") || n.contains("BIRCH_LEAVES") || n.contains("JUNGLE_LEAVES")
            || n.contains("ACACIA_LEAVES") || n.contains("DARK_OAK_LEAVES") || n.contains("MANGROVE_LEAVES");
    }

    // ---- Public API ----

    public static void initialize(JavaPlugin plugin) {
        log = plugin.getLogger();
        log.info("[TextureManager] Searching for Minecraft client jar...");

        ZipFile jar = findClientJar(plugin.getDataFolder());
        if (jar == null) {
            log.warning("[TextureManager] Client jar not found — renderer will use flat colors.");
            log.warning("[TextureManager] Tip: drop a Minecraft client jar into plugins/AiMinecraft/ named 'client.jar'");
            initialized = true;
            return;
        }

        try {
            int loaded = 0;
            for (Material mat : Material.values()) {
                if (!mat.isBlock()) continue;
                if (loadMaterial(jar, mat)) loaded++;
            }
            log.info("[TextureManager] Loaded textures for " + loaded + " block types.");
        } catch (Exception e) {
            log.warning("[TextureManager] Error during texture load: " + e.getMessage());
        } finally {
            try { jar.close(); } catch (IOException ignored) {}
        }
        initialized = true;
    }

    /** Sample a texture at UV [0,1] coordinates. Returns ARGB int. */
    public static int sample(BufferedImage tex, double u, double v) {
        int tw = tex.getWidth();
        int th = tex.getHeight();
        int tx = ((int)(u * tw)) % tw;
        int ty = ((int)(v * th)) % th;
        if (tx < 0) tx += tw;
        if (ty < 0) ty += th;
        return tex.getRGB(tx, ty);
    }

    /**
     * Get the appropriate texture for a material face.
     * Returns null if textures are not loaded or the material has no texture.
     */
    public static BufferedImage get(Material mat, int face) {
        BufferedImage[] arr = CACHE.get(mat);
        if (arr == null) return null;
        return arr[face];
    }

    /** True if this material's grass/leaf texture should have a green tint applied. */
    public static boolean needsGrassTint(Material mat) { return isGrassTinted(mat); }
    public static boolean needsLeafTint(Material mat)  { return isLeafTinted(mat); }

    public static boolean isInitialized() { return initialized; }

    // ---- Private helpers ----

    private static ZipFile findClientJar(File pluginDataFolder) {
        File cwd = new File(".").getAbsoluteFile();
        log.info("[TextureManager] CWD = " + cwd.getPath());

        // ── Priority 1: manual drop-in — plugins/AiMinecraft/client.jar ──
        if (pluginDataFolder != null && pluginDataFolder.isDirectory()) {
            // Accept any *.jar in the plugin folder (client.jar is the recommended name)
            File[] pluginJars = pluginDataFolder.listFiles(
                    (dir, name) -> name.endsWith(".jar"));
            if (pluginJars != null) {
                for (File f : pluginJars) {
                    try {
                        ZipFile zf = new ZipFile(f);
                        if (zf.getEntry("assets/minecraft/textures/block/stone.png") != null) {
                            log.info("[TextureManager] Using manual client jar: " + f.getName());
                            return zf;
                        }
                        zf.close();
                    } catch (IOException ignored) {}
                }
            }
        }

        // Build a list of candidate directories to search
        java.util.List<File> searchDirs = new java.util.ArrayList<>();

        // Walk up from CWD up to 4 levels — covers servers launched from sub-dirs
        File cur = cwd;
        for (int i = 0; i < 5; i++) {
            if (cur == null) break;
            searchDirs.add(new File(cur, "cache"));
            searchDirs.add(cur);
            cur = cur.getParentFile();
        }

        // Common Minecraft launcher / server locations
        String home = System.getProperty("user.home", "");
        searchDirs.add(new File(home, ".minecraft/versions"));
        searchDirs.add(new File(home, "minecraft/cache"));
        searchDirs.add(new File("/opt/minecraft/cache"));

        for (File dir : searchDirs) {
            if (!dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (!f.isFile() || !f.getName().endsWith(".jar")) continue;
                String n = f.getName().toLowerCase();
                if (!n.contains("client") && !n.contains("minecraft") && !n.contains("mojang")) continue;
                try {
                    ZipFile zf = new ZipFile(f);
                    if (zf.getEntry("assets/minecraft/textures/block/stone.png") != null) {
                        log.info("[TextureManager] Found client jar: " + f.getAbsolutePath());
                        return zf;
                    }
                    zf.close();
                } catch (IOException ignored) {}
            }
        }

        log.warning("[TextureManager] Could not find client jar. Searched:");
        searchDirs.stream().filter(File::isDirectory)
                .forEach(d -> log.warning("[TextureManager]   " + d.getAbsolutePath()));
        return null;
    }

    private static boolean loadMaterial(ZipFile jar, Material mat) {
        String[] names = TEX_OVERRIDES.get(mat.name());
        if (names == null) {
            // Auto-derive: try the lowercase material name
            String guess = mat.name().toLowerCase();
            BufferedImage tex = readTexture(jar, guess);
            if (tex == null) return false;
            CACHE.put(mat, new BufferedImage[]{tex, tex, tex});
            return true;
        }

        BufferedImage top  = readTexture(jar, names[0]);
        BufferedImage side = readTexture(jar, names[1]);
        BufferedImage bot  = readTexture(jar, names[2]);

        // Need at least one face
        BufferedImage any = top != null ? top : side != null ? side : bot;
        if (any == null) return false;

        CACHE.put(mat, new BufferedImage[]{
            top  != null ? top  : any,
            side != null ? side : any,
            bot  != null ? bot  : any
        });
        return true;
    }

    private static BufferedImage readTexture(ZipFile jar, String name) {
        ZipEntry entry = jar.getEntry("assets/minecraft/textures/block/" + name + ".png");
        if (entry == null) return null;
        try (InputStream is = jar.getInputStream(entry)) {
            BufferedImage img = ImageIO.read(is);
            if (img == null) return null;
            // Animated textures are tall (height > width) — take only the first frame
            if (img.getHeight() > img.getWidth()) {
                img = img.getSubimage(0, 0, img.getWidth(), img.getWidth());
            }
            return img;
        } catch (IOException e) {
            return null;
        }
    }
}
