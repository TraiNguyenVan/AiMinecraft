package com.yourname.aiminecraft;

import org.bukkit.Material;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Renders a visual map image from WorldScanner data.
 * Produces a top-down view + two cross-sections (N-S and E-W slices) as a single PNG.
 */
public class MapRenderer {

    private static final int BLOCK_SIZE = 8; // pixels per block
    private static final int PADDING = 4;
    private static final int LEGEND_WIDTH = 120;

    // Block-to-color mapping for common materials
    private static final Map<Material, Color> BLOCK_COLORS = new HashMap<>();

    static {
        // Air
        BLOCK_COLORS.put(Material.AIR, new Color(0,0,0,0));
        BLOCK_COLORS.put(Material.CAVE_AIR, new Color(0,0,0,0));
        BLOCK_COLORS.put(Material.VOID_AIR, new Color(0,0,0,0));
        // Terrain
        BLOCK_COLORS.put(Material.GRASS_BLOCK, new Color(91,168,60));
        BLOCK_COLORS.put(Material.DIRT, new Color(134,96,67));
        BLOCK_COLORS.put(Material.COARSE_DIRT, new Color(119,85,59));
        BLOCK_COLORS.put(Material.ROOTED_DIRT, new Color(120,88,60));
        BLOCK_COLORS.put(Material.DIRT_PATH, new Color(148,121,65));
        BLOCK_COLORS.put(Material.FARMLAND, new Color(111,79,47));
        BLOCK_COLORS.put(Material.PODZOL, new Color(91,63,24));
        BLOCK_COLORS.put(Material.MYCELIUM, new Color(111,99,108));
        BLOCK_COLORS.put(Material.MUD, new Color(60,57,55));
        BLOCK_COLORS.put(Material.PACKED_MUD, new Color(142,106,79));
        BLOCK_COLORS.put(Material.MUD_BRICKS, new Color(137,104,76));
        BLOCK_COLORS.put(Material.MOSS_BLOCK, new Color(89,109,45));
        BLOCK_COLORS.put(Material.MOSS_CARPET, new Color(89,109,45));
        BLOCK_COLORS.put(Material.CLAY, new Color(160,166,179));
        BLOCK_COLORS.put(Material.GRAVEL, new Color(131,127,126));
        BLOCK_COLORS.put(Material.SAND, new Color(219,207,163));
        BLOCK_COLORS.put(Material.RED_SAND, new Color(190,102,33));
        BLOCK_COLORS.put(Material.SANDSTONE, new Color(216,202,155));
        BLOCK_COLORS.put(Material.RED_SANDSTONE, new Color(186,99,29));
        BLOCK_COLORS.put(Material.SNOW_BLOCK, new Color(249,254,254));
        BLOCK_COLORS.put(Material.SNOW, new Color(249,254,254));
        BLOCK_COLORS.put(Material.POWDER_SNOW, new Color(248,253,253));
        BLOCK_COLORS.put(Material.ICE, new Color(145,183,253));
        BLOCK_COLORS.put(Material.PACKED_ICE, new Color(141,180,250));
        BLOCK_COLORS.put(Material.BLUE_ICE, new Color(116,167,253));
        // Stone types
        BLOCK_COLORS.put(Material.STONE, new Color(125,125,125));
        BLOCK_COLORS.put(Material.COBBLESTONE, new Color(122,122,122));
        BLOCK_COLORS.put(Material.MOSSY_COBBLESTONE, new Color(110,138,94));
        BLOCK_COLORS.put(Material.SMOOTH_STONE, new Color(158,158,158));
        BLOCK_COLORS.put(Material.STONE_BRICKS, new Color(122,121,122));
        BLOCK_COLORS.put(Material.MOSSY_STONE_BRICKS, new Color(105,132,89));
        BLOCK_COLORS.put(Material.CRACKED_STONE_BRICKS, new Color(118,117,118));
        BLOCK_COLORS.put(Material.CHISELED_STONE_BRICKS, new Color(119,118,119));
        BLOCK_COLORS.put(Material.GRANITE, new Color(149,103,85));
        BLOCK_COLORS.put(Material.POLISHED_GRANITE, new Color(154,106,89));
        BLOCK_COLORS.put(Material.DIORITE, new Color(188,188,188));
        BLOCK_COLORS.put(Material.POLISHED_DIORITE, new Color(192,192,192));
        BLOCK_COLORS.put(Material.ANDESITE, new Color(136,136,136));
        BLOCK_COLORS.put(Material.POLISHED_ANDESITE, new Color(140,140,140));
        BLOCK_COLORS.put(Material.DEEPSLATE, new Color(75,75,80));
        BLOCK_COLORS.put(Material.COBBLED_DEEPSLATE, new Color(77,77,82));
        BLOCK_COLORS.put(Material.POLISHED_DEEPSLATE, new Color(72,72,76));
        BLOCK_COLORS.put(Material.DEEPSLATE_BRICKS, new Color(70,70,74));
        BLOCK_COLORS.put(Material.DEEPSLATE_TILES, new Color(54,54,58));
        BLOCK_COLORS.put(Material.TUFF, new Color(108,109,102));
        BLOCK_COLORS.put(Material.CALCITE, new Color(223,224,220));
        BLOCK_COLORS.put(Material.DRIPSTONE_BLOCK, new Color(134,107,92));
        BLOCK_COLORS.put(Material.BEDROCK, new Color(50,50,50));
        BLOCK_COLORS.put(Material.OBSIDIAN, new Color(15,10,24));
        BLOCK_COLORS.put(Material.CRYING_OBSIDIAN, new Color(32,10,60));
        BLOCK_COLORS.put(Material.BLACKSTONE, new Color(42,35,40));
        BLOCK_COLORS.put(Material.POLISHED_BLACKSTONE, new Color(53,48,56));
        BLOCK_COLORS.put(Material.POLISHED_BLACKSTONE_BRICKS, new Color(48,42,49));
        BLOCK_COLORS.put(Material.GILDED_BLACKSTONE, new Color(56,43,38));
        BLOCK_COLORS.put(Material.PRISMARINE, new Color(99,171,158));
        BLOCK_COLORS.put(Material.PRISMARINE_BRICKS, new Color(99,171,158));
        BLOCK_COLORS.put(Material.DARK_PRISMARINE, new Color(51,91,75));
        BLOCK_COLORS.put(Material.SEA_LANTERN, new Color(172,215,210));
        // Wood (all types)
        BLOCK_COLORS.put(Material.OAK_LOG, new Color(109,85,51));
        BLOCK_COLORS.put(Material.OAK_PLANKS, new Color(162,130,78));
        BLOCK_COLORS.put(Material.SPRUCE_LOG, new Color(58,37,16));
        BLOCK_COLORS.put(Material.SPRUCE_PLANKS, new Color(114,84,48));
        BLOCK_COLORS.put(Material.BIRCH_LOG, new Color(216,215,210));
        BLOCK_COLORS.put(Material.BIRCH_PLANKS, new Color(196,179,123));
        BLOCK_COLORS.put(Material.DARK_OAK_LOG, new Color(60,46,26));
        BLOCK_COLORS.put(Material.DARK_OAK_PLANKS, new Color(67,43,20));
        BLOCK_COLORS.put(Material.JUNGLE_LOG, new Color(85,68,25));
        BLOCK_COLORS.put(Material.JUNGLE_PLANKS, new Color(160,115,80));
        BLOCK_COLORS.put(Material.ACACIA_LOG, new Color(103,96,86));
        BLOCK_COLORS.put(Material.ACACIA_PLANKS, new Color(168,90,50));
        BLOCK_COLORS.put(Material.MANGROVE_LOG, new Color(84,66,41));
        BLOCK_COLORS.put(Material.MANGROVE_PLANKS, new Color(117,54,48));
        BLOCK_COLORS.put(Material.CHERRY_LOG, new Color(51,31,41));
        BLOCK_COLORS.put(Material.CHERRY_PLANKS, new Color(226,178,172));
        BLOCK_COLORS.put(Material.BAMBOO_BLOCK, new Color(145,133,42));
        BLOCK_COLORS.put(Material.BAMBOO_PLANKS, new Color(194,171,77));
        BLOCK_COLORS.put(Material.BAMBOO_MOSAIC, new Color(190,167,73));
        BLOCK_COLORS.put(Material.CRIMSON_STEM, new Color(92,26,33));
        BLOCK_COLORS.put(Material.CRIMSON_PLANKS, new Color(101,48,51));
        BLOCK_COLORS.put(Material.WARPED_STEM, new Color(58,86,81));
        BLOCK_COLORS.put(Material.WARPED_PLANKS, new Color(43,104,99));
        BLOCK_COLORS.put(Material.STRIPPED_OAK_LOG, new Color(177,144,86));
        BLOCK_COLORS.put(Material.STRIPPED_SPRUCE_LOG, new Color(115,89,52));
        BLOCK_COLORS.put(Material.STRIPPED_BIRCH_LOG, new Color(196,176,118));
        BLOCK_COLORS.put(Material.STRIPPED_DARK_OAK_LOG, new Color(96,76,49));
        BLOCK_COLORS.put(Material.STRIPPED_JUNGLE_LOG, new Color(171,132,84));
        BLOCK_COLORS.put(Material.STRIPPED_ACACIA_LOG, new Color(174,92,59));
        BLOCK_COLORS.put(Material.STRIPPED_MANGROVE_LOG, new Color(119,54,47));
        BLOCK_COLORS.put(Material.STRIPPED_CHERRY_LOG, new Color(215,163,157));
        // Leaves
        BLOCK_COLORS.put(Material.OAK_LEAVES, new Color(59,120,26));
        BLOCK_COLORS.put(Material.SPRUCE_LEAVES, new Color(48,75,48));
        BLOCK_COLORS.put(Material.BIRCH_LEAVES, new Color(80,121,42));
        BLOCK_COLORS.put(Material.DARK_OAK_LEAVES, new Color(34,90,12));
        BLOCK_COLORS.put(Material.JUNGLE_LEAVES, new Color(48,114,20));
        BLOCK_COLORS.put(Material.ACACIA_LEAVES, new Color(58,112,24));
        BLOCK_COLORS.put(Material.MANGROVE_LEAVES, new Color(57,107,31));
        BLOCK_COLORS.put(Material.CHERRY_LEAVES, new Color(228,175,185));
        BLOCK_COLORS.put(Material.AZALEA_LEAVES, new Color(75,108,48));
        BLOCK_COLORS.put(Material.FLOWERING_AZALEA_LEAVES, new Color(86,108,50));
        // Liquids
        BLOCK_COLORS.put(Material.WATER, new Color(47,67,244,180));
        BLOCK_COLORS.put(Material.LAVA, new Color(252,87,0));
        // Ores (overworld + deepslate)
        BLOCK_COLORS.put(Material.COAL_ORE, new Color(70,70,70));
        BLOCK_COLORS.put(Material.DEEPSLATE_COAL_ORE, new Color(60,60,65));
        BLOCK_COLORS.put(Material.IRON_ORE, new Color(183,155,129));
        BLOCK_COLORS.put(Material.DEEPSLATE_IRON_ORE, new Color(135,115,100));
        BLOCK_COLORS.put(Material.COPPER_ORE, new Color(124,153,117));
        BLOCK_COLORS.put(Material.DEEPSLATE_COPPER_ORE, new Color(95,120,92));
        BLOCK_COLORS.put(Material.GOLD_ORE, new Color(252,238,75));
        BLOCK_COLORS.put(Material.DEEPSLATE_GOLD_ORE, new Color(195,185,60));
        BLOCK_COLORS.put(Material.DIAMOND_ORE, new Color(93,236,218));
        BLOCK_COLORS.put(Material.DEEPSLATE_DIAMOND_ORE, new Color(75,190,175));
        BLOCK_COLORS.put(Material.EMERALD_ORE, new Color(17,221,62));
        BLOCK_COLORS.put(Material.DEEPSLATE_EMERALD_ORE, new Color(14,175,50));
        BLOCK_COLORS.put(Material.REDSTONE_ORE, new Color(255,0,0));
        BLOCK_COLORS.put(Material.DEEPSLATE_REDSTONE_ORE, new Color(200,0,0));
        BLOCK_COLORS.put(Material.LAPIS_ORE, new Color(38,67,168));
        BLOCK_COLORS.put(Material.DEEPSLATE_LAPIS_ORE, new Color(30,55,135));
        BLOCK_COLORS.put(Material.NETHER_GOLD_ORE, new Color(198,169,65));
        BLOCK_COLORS.put(Material.NETHER_QUARTZ_ORE, new Color(180,160,140));
        BLOCK_COLORS.put(Material.ANCIENT_DEBRIS, new Color(105,76,59));
        // Mineral blocks
        BLOCK_COLORS.put(Material.COAL_BLOCK, new Color(16,15,15));
        BLOCK_COLORS.put(Material.IRON_BLOCK, new Color(220,220,220));
        BLOCK_COLORS.put(Material.GOLD_BLOCK, new Color(246,208,61));
        BLOCK_COLORS.put(Material.DIAMOND_BLOCK, new Color(99,225,207));
        BLOCK_COLORS.put(Material.EMERALD_BLOCK, new Color(42,183,74));
        BLOCK_COLORS.put(Material.LAPIS_BLOCK, new Color(30,67,140));
        BLOCK_COLORS.put(Material.REDSTONE_BLOCK, new Color(171,27,3));
        BLOCK_COLORS.put(Material.NETHERITE_BLOCK, new Color(66,61,63));
        BLOCK_COLORS.put(Material.QUARTZ_BLOCK, new Color(235,229,222));
        BLOCK_COLORS.put(Material.AMETHYST_BLOCK, new Color(133,98,175));
        BLOCK_COLORS.put(Material.RAW_IRON_BLOCK, new Color(166,136,107));
        BLOCK_COLORS.put(Material.RAW_COPPER_BLOCK, new Color(154,105,79));
        BLOCK_COLORS.put(Material.RAW_GOLD_BLOCK, new Color(221,169,46));
        // Copper
        BLOCK_COLORS.put(Material.COPPER_BLOCK, new Color(192,107,79));
        BLOCK_COLORS.put(Material.EXPOSED_COPPER, new Color(161,125,103));
        BLOCK_COLORS.put(Material.WEATHERED_COPPER, new Color(108,153,110));
        BLOCK_COLORS.put(Material.OXIDIZED_COPPER, new Color(82,162,132));
        BLOCK_COLORS.put(Material.CUT_COPPER, new Color(191,106,80));
        BLOCK_COLORS.put(Material.EXPOSED_CUT_COPPER, new Color(154,121,101));
        BLOCK_COLORS.put(Material.WEATHERED_CUT_COPPER, new Color(109,145,107));
        BLOCK_COLORS.put(Material.OXIDIZED_CUT_COPPER, new Color(79,153,126));
        // Wool
        BLOCK_COLORS.put(Material.WHITE_WOOL, new Color(234,236,236));
        BLOCK_COLORS.put(Material.ORANGE_WOOL, new Color(240,118,19));
        BLOCK_COLORS.put(Material.MAGENTA_WOOL, new Color(189,68,179));
        BLOCK_COLORS.put(Material.LIGHT_BLUE_WOOL, new Color(58,175,217));
        BLOCK_COLORS.put(Material.YELLOW_WOOL, new Color(248,198,39));
        BLOCK_COLORS.put(Material.LIME_WOOL, new Color(112,185,25));
        BLOCK_COLORS.put(Material.PINK_WOOL, new Color(237,141,172));
        BLOCK_COLORS.put(Material.GRAY_WOOL, new Color(63,68,72));
        BLOCK_COLORS.put(Material.LIGHT_GRAY_WOOL, new Color(142,142,135));
        BLOCK_COLORS.put(Material.CYAN_WOOL, new Color(21,138,145));
        BLOCK_COLORS.put(Material.PURPLE_WOOL, new Color(121,42,173));
        BLOCK_COLORS.put(Material.BLUE_WOOL, new Color(53,57,157));
        BLOCK_COLORS.put(Material.BROWN_WOOL, new Color(114,72,41));
        BLOCK_COLORS.put(Material.GREEN_WOOL, new Color(84,109,27));
        BLOCK_COLORS.put(Material.RED_WOOL, new Color(160,39,34));
        BLOCK_COLORS.put(Material.BLACK_WOOL, new Color(20,21,25));
        // Concrete
        BLOCK_COLORS.put(Material.WHITE_CONCRETE, new Color(207,213,214));
        BLOCK_COLORS.put(Material.ORANGE_CONCRETE, new Color(224,97,1));
        BLOCK_COLORS.put(Material.MAGENTA_CONCRETE, new Color(169,48,159));
        BLOCK_COLORS.put(Material.LIGHT_BLUE_CONCRETE, new Color(36,137,199));
        BLOCK_COLORS.put(Material.YELLOW_CONCRETE, new Color(241,175,21));
        BLOCK_COLORS.put(Material.LIME_CONCRETE, new Color(94,169,24));
        BLOCK_COLORS.put(Material.PINK_CONCRETE, new Color(214,101,143));
        BLOCK_COLORS.put(Material.GRAY_CONCRETE, new Color(55,58,62));
        BLOCK_COLORS.put(Material.LIGHT_GRAY_CONCRETE, new Color(125,125,115));
        BLOCK_COLORS.put(Material.CYAN_CONCRETE, new Color(21,119,136));
        BLOCK_COLORS.put(Material.PURPLE_CONCRETE, new Color(100,32,156));
        BLOCK_COLORS.put(Material.BLUE_CONCRETE, new Color(45,47,143));
        BLOCK_COLORS.put(Material.BROWN_CONCRETE, new Color(96,60,32));
        BLOCK_COLORS.put(Material.GREEN_CONCRETE, new Color(73,91,36));
        BLOCK_COLORS.put(Material.RED_CONCRETE, new Color(142,33,33));
        BLOCK_COLORS.put(Material.BLACK_CONCRETE, new Color(8,10,15));
        // Terracotta
        BLOCK_COLORS.put(Material.TERRACOTTA, new Color(152,94,67));
        BLOCK_COLORS.put(Material.WHITE_TERRACOTTA, new Color(210,178,161));
        BLOCK_COLORS.put(Material.ORANGE_TERRACOTTA, new Color(162,84,38));
        BLOCK_COLORS.put(Material.MAGENTA_TERRACOTTA, new Color(150,88,109));
        BLOCK_COLORS.put(Material.LIGHT_BLUE_TERRACOTTA, new Color(113,109,138));
        BLOCK_COLORS.put(Material.YELLOW_TERRACOTTA, new Color(186,133,35));
        BLOCK_COLORS.put(Material.LIME_TERRACOTTA, new Color(104,118,53));
        BLOCK_COLORS.put(Material.PINK_TERRACOTTA, new Color(162,78,79));
        BLOCK_COLORS.put(Material.GRAY_TERRACOTTA, new Color(58,42,36));
        BLOCK_COLORS.put(Material.LIGHT_GRAY_TERRACOTTA, new Color(135,107,98));
        BLOCK_COLORS.put(Material.CYAN_TERRACOTTA, new Color(87,91,91));
        BLOCK_COLORS.put(Material.PURPLE_TERRACOTTA, new Color(118,70,86));
        BLOCK_COLORS.put(Material.BLUE_TERRACOTTA, new Color(74,60,91));
        BLOCK_COLORS.put(Material.BROWN_TERRACOTTA, new Color(77,51,36));
        BLOCK_COLORS.put(Material.GREEN_TERRACOTTA, new Color(76,83,42));
        BLOCK_COLORS.put(Material.RED_TERRACOTTA, new Color(143,61,47));
        BLOCK_COLORS.put(Material.BLACK_TERRACOTTA, new Color(37,23,16));
        // Glass
        BLOCK_COLORS.put(Material.GLASS, new Color(175,214,232,100));
        BLOCK_COLORS.put(Material.GLASS_PANE, new Color(175,214,232,100));
        BLOCK_COLORS.put(Material.TINTED_GLASS, new Color(44,38,50,160));
        // Building
        BLOCK_COLORS.put(Material.BRICKS, new Color(150,97,76));
        BLOCK_COLORS.put(Material.BOOKSHELF, new Color(111,88,55));
        BLOCK_COLORS.put(Material.CHISELED_BOOKSHELF, new Color(111,88,55));
        BLOCK_COLORS.put(Material.SCULK, new Color(12,37,42));
        BLOCK_COLORS.put(Material.SCULK_CATALYST, new Color(15,45,50));
        BLOCK_COLORS.put(Material.SCULK_SENSOR, new Color(7,52,56));
        BLOCK_COLORS.put(Material.SCULK_SHRIEKER, new Color(13,42,46));
        BLOCK_COLORS.put(Material.SCULK_VEIN, new Color(5,48,52));
        BLOCK_COLORS.put(Material.BONE_BLOCK, new Color(229,225,207));
        BLOCK_COLORS.put(Material.DRIED_KELP_BLOCK, new Color(50,58,38));
        BLOCK_COLORS.put(Material.HAY_BLOCK, new Color(166,139,30));
        BLOCK_COLORS.put(Material.HONEYCOMB_BLOCK, new Color(229,148,29));
        BLOCK_COLORS.put(Material.HONEY_BLOCK, new Color(235,174,58));
        BLOCK_COLORS.put(Material.SLIME_BLOCK, new Color(112,187,80));
        BLOCK_COLORS.put(Material.SPONGE, new Color(195,192,74));
        BLOCK_COLORS.put(Material.WET_SPONGE, new Color(171,181,70));
        BLOCK_COLORS.put(Material.MELON, new Color(111,145,32));
        BLOCK_COLORS.put(Material.PUMPKIN, new Color(198,118,24));
        BLOCK_COLORS.put(Material.JACK_O_LANTERN, new Color(210,139,34));
        // Nether
        BLOCK_COLORS.put(Material.NETHERRACK, new Color(111,54,53));
        BLOCK_COLORS.put(Material.NETHER_BRICKS, new Color(44,22,26));
        BLOCK_COLORS.put(Material.RED_NETHER_BRICKS, new Color(69,7,9));
        BLOCK_COLORS.put(Material.SOUL_SAND, new Color(81,62,50));
        BLOCK_COLORS.put(Material.SOUL_SOIL, new Color(75,57,46));
        BLOCK_COLORS.put(Material.GLOWSTONE, new Color(249,212,122));
        BLOCK_COLORS.put(Material.SHROOMLIGHT, new Color(240,146,70));
        BLOCK_COLORS.put(Material.BASALT, new Color(72,72,78));
        BLOCK_COLORS.put(Material.POLISHED_BASALT, new Color(90,90,96));
        BLOCK_COLORS.put(Material.SMOOTH_BASALT, new Color(72,72,73));
        BLOCK_COLORS.put(Material.MAGMA_BLOCK, new Color(142,63,31));
        BLOCK_COLORS.put(Material.NETHER_WART_BLOCK, new Color(114,2,2));
        BLOCK_COLORS.put(Material.WARPED_WART_BLOCK, new Color(22,119,121));
        BLOCK_COLORS.put(Material.CRIMSON_NYLIUM, new Color(130,32,32));
        BLOCK_COLORS.put(Material.WARPED_NYLIUM, new Color(43,114,101));
        // End
        BLOCK_COLORS.put(Material.END_STONE, new Color(219,223,158));
        BLOCK_COLORS.put(Material.END_STONE_BRICKS, new Color(218,224,162));
        BLOCK_COLORS.put(Material.PURPUR_BLOCK, new Color(170,126,170));
        BLOCK_COLORS.put(Material.PURPUR_PILLAR, new Color(172,129,172));
        BLOCK_COLORS.put(Material.CHORUS_PLANT, new Color(93,57,93));
        BLOCK_COLORS.put(Material.CHORUS_FLOWER, new Color(151,120,151));
        // Coral
        BLOCK_COLORS.put(Material.TUBE_CORAL_BLOCK, new Color(49,88,207));
        BLOCK_COLORS.put(Material.BRAIN_CORAL_BLOCK, new Color(207,91,159));
        BLOCK_COLORS.put(Material.BUBBLE_CORAL_BLOCK, new Color(165,26,162));
        BLOCK_COLORS.put(Material.FIRE_CORAL_BLOCK, new Color(163,35,46));
        BLOCK_COLORS.put(Material.HORN_CORAL_BLOCK, new Color(216,199,66));
        // Functional
        BLOCK_COLORS.put(Material.CHEST, new Color(190,140,40));
        BLOCK_COLORS.put(Material.TRAPPED_CHEST, new Color(190,140,40));
        BLOCK_COLORS.put(Material.BARREL, new Color(134,106,62));
        BLOCK_COLORS.put(Material.FURNACE, new Color(130,130,130));
        BLOCK_COLORS.put(Material.BLAST_FURNACE, new Color(90,90,95));
        BLOCK_COLORS.put(Material.SMOKER, new Color(100,85,60));
        BLOCK_COLORS.put(Material.CRAFTING_TABLE, new Color(150,110,55));
        BLOCK_COLORS.put(Material.ENCHANTING_TABLE, new Color(100,18,18));
        BLOCK_COLORS.put(Material.ANVIL, new Color(68,68,68));
        BLOCK_COLORS.put(Material.BREWING_STAND, new Color(120,100,60));
        BLOCK_COLORS.put(Material.BEACON, new Color(118,237,218));
        BLOCK_COLORS.put(Material.SPAWNER, new Color(30,50,60));
        BLOCK_COLORS.put(Material.TNT, new Color(219,48,32));
        BLOCK_COLORS.put(Material.REDSTONE_LAMP, new Color(181,100,44));
        BLOCK_COLORS.put(Material.NOTE_BLOCK, new Color(100,68,50));
        BLOCK_COLORS.put(Material.JUKEBOX, new Color(100,68,50));
        BLOCK_COLORS.put(Material.BELL, new Color(246,208,61));
        BLOCK_COLORS.put(Material.LODESTONE, new Color(147,149,152));
        BLOCK_COLORS.put(Material.RESPAWN_ANCHOR, new Color(35,10,50));
        BLOCK_COLORS.put(Material.CONDUIT, new Color(163,143,118));
        BLOCK_COLORS.put(Material.LECTERN, new Color(162,130,78));
        BLOCK_COLORS.put(Material.COMPOSTER, new Color(105,77,41));
        BLOCK_COLORS.put(Material.SMITHING_TABLE, new Color(57,58,72));
        BLOCK_COLORS.put(Material.FLETCHING_TABLE, new Color(188,170,123));
        BLOCK_COLORS.put(Material.CARTOGRAPHY_TABLE, new Color(104,89,71));
        BLOCK_COLORS.put(Material.LOOM, new Color(157,137,108));
        BLOCK_COLORS.put(Material.STONECUTTER, new Color(120,120,120));
        BLOCK_COLORS.put(Material.GRINDSTONE, new Color(140,138,138));
        // Redstone
        BLOCK_COLORS.put(Material.OBSERVER, new Color(100,100,105));
        BLOCK_COLORS.put(Material.PISTON, new Color(153,127,96));
        BLOCK_COLORS.put(Material.STICKY_PISTON, new Color(130,140,90));
        BLOCK_COLORS.put(Material.DISPENSER, new Color(130,130,130));
        BLOCK_COLORS.put(Material.DROPPER, new Color(130,130,130));
        BLOCK_COLORS.put(Material.HOPPER, new Color(74,74,74));
        BLOCK_COLORS.put(Material.TARGET, new Color(216,196,180));
    }

    /**
     * Render scan result as a PNG image. Returns raw PNG bytes.
     * Layout: Top-down view (left) | E-W cross-section (right)
     *         N-S cross-section (bottom-left) | Legend (bottom-right)
     */
    public static byte[] render(WorldScanner.ScanResult result) {
        int diameter = result.radius * 2 + 1;
        int totalLayers = result.blocks.length;

        int topDownW = diameter * BLOCK_SIZE;
        int topDownH = diameter * BLOCK_SIZE;
        int crossW = diameter * BLOCK_SIZE;
        int crossH = totalLayers * BLOCK_SIZE;

        int totalW = topDownW + PADDING + crossW + PADDING + LEGEND_WIDTH;
        int totalH = topDownH + PADDING + crossH + 30; // 30 for title

        BufferedImage img = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(24, 24, 32));
        g.fillRect(0, 0, totalW, totalH);

        // Title
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.drawString("Surroundings — " + result.biome + " | Y:" + result.playerBlockY + " | " + result.timeOfDay, 4, 14);

        int yOffset = 22;

        // === TOP-DOWN VIEW (highest non-air block at each X/Z) ===
        g.setColor(new Color(60, 60, 80));
        g.drawRect(0, yOffset - 1, topDownW + 1, topDownH + 1);
        for (int z = 0; z < diameter; z++) {
            for (int x = 0; x < diameter; x++) {
                // Find the highest non-air block in this column
                Material topBlock = Material.AIR;
                for (int y = totalLayers - 1; y >= 0; y--) {
                    Material mat = result.blocks[y][z][x];
                    if (mat != Material.AIR && mat != Material.CAVE_AIR && mat != Material.VOID_AIR) {
                        topBlock = mat;
                        break;
                    }
                }
                Color c = getBlockColor(topBlock);
                g.setColor(c);
                g.fillRect(x * BLOCK_SIZE, yOffset + z * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);

                // Grid lines
                g.setColor(new Color(40, 40, 50, 80));
                g.drawRect(x * BLOCK_SIZE, yOffset + z * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }

        // Player marker on top-down view
        int playerGridX = result.radius;
        int playerGridZ = result.radius;
        g.setColor(Color.RED);
        int px = playerGridX * BLOCK_SIZE + BLOCK_SIZE / 2;
        int py = yOffset + playerGridZ * BLOCK_SIZE + BLOCK_SIZE / 2;
        g.fillOval(px - 3, py - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.drawOval(px - 3, py - 3, 6, 6);

        // Label
        g.setColor(new Color(180, 180, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString("Top-Down View", 2, yOffset + topDownH + 10);

        // === EAST-WEST CROSS-SECTION (slice through player's Z) ===
        int crossXOffset = topDownW + PADDING;
        g.setColor(new Color(60, 60, 80));
        g.drawRect(crossXOffset - 1, yOffset - 1, crossW + 1, crossH + 1);
        int playerZSlice = result.radius; // center of grid
        for (int y = 0; y < totalLayers; y++) {
            for (int x = 0; x < diameter; x++) {
                Material mat = result.blocks[totalLayers - 1 - y][playerZSlice][x];
                Color c = getBlockColor(mat);
                g.setColor(c);
                g.fillRect(crossXOffset + x * BLOCK_SIZE, yOffset + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);

                g.setColor(new Color(40, 40, 50, 80));
                g.drawRect(crossXOffset + x * BLOCK_SIZE, yOffset + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }
        // Player marker on cross-section
        int crossPlayerX = crossXOffset + result.radius * BLOCK_SIZE + BLOCK_SIZE / 2;
        int crossPlayerY = yOffset + (totalLayers - 1 - result.layersBelow) * BLOCK_SIZE + BLOCK_SIZE / 2;
        g.setColor(Color.RED);
        g.fillOval(crossPlayerX - 3, crossPlayerY - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.drawOval(crossPlayerX - 3, crossPlayerY - 3, 6, 6);

        g.setColor(new Color(180, 180, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString("E-W Cross Section", crossXOffset + 2, yOffset + crossH + 10);

        // === NORTH-SOUTH CROSS-SECTION (below the top-down view) ===
        int nsYOffset = yOffset + topDownH + PADDING + 14;
        g.setColor(new Color(60, 60, 80));
        g.drawRect(-1, nsYOffset - 1, crossW + 1, crossH + 1);
        int playerXSlice = result.radius;
        for (int y = 0; y < totalLayers; y++) {
            for (int z = 0; z < diameter; z++) {
                Material mat = result.blocks[totalLayers - 1 - y][z][playerXSlice];
                Color c = getBlockColor(mat);
                g.setColor(c);
                g.fillRect(z * BLOCK_SIZE, nsYOffset + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);

                g.setColor(new Color(40, 40, 50, 80));
                g.drawRect(z * BLOCK_SIZE, nsYOffset + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }
        // Player marker
        int nsPlayerX = result.radius * BLOCK_SIZE + BLOCK_SIZE / 2;
        int nsPlayerY = nsYOffset + (totalLayers - 1 - result.layersBelow) * BLOCK_SIZE + BLOCK_SIZE / 2;
        g.setColor(Color.RED);
        g.fillOval(nsPlayerX - 3, nsPlayerY - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.drawOval(nsPlayerX - 3, nsPlayerY - 3, 6, 6);

        g.setColor(new Color(180, 180, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString("N-S Cross Section", 2, nsYOffset + crossH + 10);

        // === LEGEND ===
        int legendX = crossXOffset + crossW + PADDING;
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.drawString("LEGEND", legendX, yOffset + 10);

        String[][] legendEntries = {
            {"Grass", "91,168,60"}, {"Dirt", "134,96,67"}, {"Stone", "125,125,125"},
            {"Sand", "219,207,163"}, {"Water", "47,67,244"}, {"Lava", "252,87,0"},
            {"Oak Log", "109,85,51"}, {"Planks", "162,130,78"}, {"Leaves", "59,120,26"},
            {"Coal Ore", "70,70,70"}, {"Iron Ore", "183,155,129"}, {"Diamond", "93,236,218"},
            {"Chest", "190,140,40"}, {"Furnace", "130,130,130"}, {"Netherrack", "111,54,53"},
            {"Player ●", "255,0,0"}
        };

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        for (int i = 0; i < legendEntries.length; i++) {
            String[] parts = legendEntries[i][1].split(",");
            Color c = new Color(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            int ly = yOffset + 18 + i * 12;
            g.setColor(c);
            g.fillRect(legendX, ly, 8, 8);
            g.setColor(new Color(200, 200, 210));
            g.drawString(legendEntries[i][0], legendX + 12, ly + 8);
        }

        g.dispose();

        // Encode to PNG
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private static Color getBlockColor(Material mat) {
        Color c = BLOCK_COLORS.get(mat);
        if (c != null) return c;

        String n = mat.name();
        // Air types
        if (n.contains("AIR")) return new Color(0,0,0,0);
        // Colored blocks — derive color from prefix
        Color dyeColor = getDyeColor(n);
        if (dyeColor != null && (n.contains("WOOL") || n.contains("CARPET") || n.contains("CONCRETE")
            || n.contains("TERRACOTTA") || n.contains("GLAZED") || n.contains("BED")
            || n.contains("BANNER") || n.contains("CANDLE") || n.contains("SHULKER"))) return dyeColor;
        if (n.contains("STAINED_GLASS")) return dyeColor != null ? new Color(dyeColor.getRed(), dyeColor.getGreen(), dyeColor.getBlue(), 100) : new Color(175,214,232,100);
        if (n.contains("CONCRETE_POWDER")) return dyeColor != null ? dyeColor.brighter() : new Color(180,180,180);
        // Wood family — stairs, slabs, fences, doors, signs, etc.
        if (n.startsWith("OAK_")) return new Color(162,130,78);
        if (n.startsWith("SPRUCE_")) return new Color(114,84,48);
        if (n.startsWith("BIRCH_")) return new Color(196,179,123);
        if (n.startsWith("DARK_OAK_")) return new Color(67,43,20);
        if (n.startsWith("JUNGLE_")) return new Color(160,115,80);
        if (n.startsWith("ACACIA_")) return new Color(168,90,50);
        if (n.startsWith("MANGROVE_")) return new Color(117,54,48);
        if (n.startsWith("CHERRY_")) return new Color(226,178,172);
        if (n.startsWith("BAMBOO_")) return new Color(194,171,77);
        if (n.startsWith("CRIMSON_")) return new Color(101,48,51);
        if (n.startsWith("WARPED_")) return new Color(43,104,99);
        // Stone family variants
        if (n.contains("DEEPSLATE")) return new Color(75,75,80);
        if (n.contains("BLACKSTONE")) return new Color(42,35,40);
        if (n.contains("SANDSTONE")) return n.contains("RED") ? new Color(186,99,29) : new Color(216,202,155);
        if (n.contains("PRISMARINE")) return new Color(99,171,158);
        if (n.contains("PURPUR")) return new Color(170,126,170);
        if (n.contains("END_STONE")) return new Color(219,223,158);
        if (n.contains("NETHER_BRICK")) return new Color(44,22,26);
        if (n.contains("QUARTZ")) return new Color(235,229,222);
        if (n.contains("COPPER")) return new Color(192,107,79);
        if (n.contains("AMETHYST")) return new Color(133,98,175);
        // Generic patterns
        if (n.contains("PLANKS")) return new Color(162,130,78);
        if (n.contains("LOG") || n.contains("WOOD") || n.contains("STEM") || n.contains("HYPHAE")) return new Color(109,85,51);
        if (n.contains("LEAVES")) return new Color(59,120,26);
        if (n.contains("ORE")) return new Color(170,140,100);
        if (n.contains("STONE") || n.contains("BRICK")) return new Color(125,125,125);
        if (n.contains("SAND")) return new Color(219,207,163);
        if (n.contains("GLASS") || n.contains("PANE")) return new Color(175,214,232,100);
        if (n.contains("IRON") || n.contains("CHAIN") || n.contains("RAIL")) return new Color(180,180,180);
        if (n.contains("GOLD")) return new Color(246,208,61);
        // Nature
        if (n.contains("GRASS") || n.contains("FERN") || n.contains("VINE") || n.contains("MOSS")) return new Color(91,168,60);
        if (n.contains("FLOWER") || n.contains("ROSE") || n.contains("TULIP") || n.contains("ORCHID") || n.contains("ALLIUM") || n.contains("LILAC") || n.contains("PEONY") || n.contains("SUNFLOWER")) return new Color(200,80,80);
        if (n.contains("DANDELION") || n.contains("CORNFLOWER") || n.contains("BLUEBELL")) return new Color(255,220,50);
        if (n.contains("SAPLING") || n.contains("BAMBOO") || n.contains("SUGAR_CANE") || n.contains("KELP") || n.contains("SEAGRASS")) return new Color(70,140,40);
        if (n.contains("MUSHROOM")) return new Color(180,140,100);
        if (n.contains("CORAL")) return new Color(200,100,150);
        if (n.contains("WHEAT") || n.contains("CROP")) return new Color(180,160,60);
        if (n.contains("POTATO") || n.contains("CARROT") || n.contains("BEETROOT")) return new Color(120,100,40);
        // Misc
        if (n.contains("DIRT") || n.contains("MUD")) return new Color(134,96,67);
        if (n.contains("SNOW") || n.contains("ICE")) return new Color(220,230,250);
        if (n.contains("SCULK")) return new Color(12,37,42);
        if (n.contains("SOUL")) return new Color(81,62,50);
        if (n.contains("LAVA") || n.contains("MAGMA") || n.contains("FIRE")) return new Color(252,87,0);
        if (n.contains("WATER") || n.contains("BUBBLE")) return new Color(47,67,244,180);
        if (n.contains("TORCH") || n.contains("LANTERN") || n.contains("CANDLE")) return new Color(255,200,80);
        if (n.contains("REDSTONE") || n.contains("REPEATER") || n.contains("COMPARATOR")) return new Color(171,27,3);
        if (n.contains("COMMAND_BLOCK")) return new Color(190,130,80);
        if (n.contains("BARRIER") || n.contains("STRUCTURE") || n.contains("JIGSAW") || n.contains("LIGHT")) return new Color(0,0,0,0);
        // True unknown
        return new Color(80,80,80);
    }

    /** Derive a base dye color from a material name prefix. */
    private static Color getDyeColor(String name) {
        if (name.startsWith("WHITE_")) return new Color(234,236,236);
        if (name.startsWith("ORANGE_")) return new Color(240,118,19);
        if (name.startsWith("MAGENTA_")) return new Color(189,68,179);
        if (name.startsWith("LIGHT_BLUE_")) return new Color(58,175,217);
        if (name.startsWith("YELLOW_")) return new Color(248,198,39);
        if (name.startsWith("LIME_")) return new Color(112,185,25);
        if (name.startsWith("PINK_")) return new Color(237,141,172);
        if (name.startsWith("LIGHT_GRAY_")) return new Color(142,142,135);
        if (name.startsWith("GRAY_")) return new Color(63,68,72);
        if (name.startsWith("CYAN_")) return new Color(21,138,145);
        if (name.startsWith("PURPLE_")) return new Color(121,42,173);
        if (name.startsWith("BLUE_")) return new Color(53,57,157);
        if (name.startsWith("BROWN_")) return new Color(114,72,41);
        if (name.startsWith("GREEN_")) return new Color(84,109,27);
        if (name.startsWith("RED_")) return new Color(160,39,34);
        if (name.startsWith("BLACK_")) return new Color(20,21,25);
        return null;
    }
}
