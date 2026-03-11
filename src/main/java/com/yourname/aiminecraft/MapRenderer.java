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
 *
 * Layout:
 *   Row 1 — Top-down view | E-W cross-section | Legend
 *   Row 2 — N-S cross-section
 *   Row 3 — First-person perspective (proper 3D DDA + real Minecraft textures)
 */
public class MapRenderer {

    private static final int BLOCK_SIZE   = 8;   // pixels per block in overview maps
    private static final int PADDING      = 4;
    private static final int LEGEND_WIDTH = 120;
    private static final int FOV_W        = 854;  // wider for better quality
    private static final int FOV_H        = 480;

    // ---- Result of a single DDA ray ----
    private static class RayHit {
        Material mat;
        int face; // TextureManager.FACE_TOP / SIDE / BOTTOM
        double u, v; // UV within face [0,1]
        double dist; // distance travlled
        boolean xzFace; // side-face on X axis? (affects shadow)
        boolean zFace;
    }

    // =====================================================================
    //  Public entry point
    // =====================================================================

    public static byte[] render(WorldScanner.ScanResult result) {
        int diameter    = result.radius * 2 + 1;
        int totalLayers = result.blocks.length;

        int topDownW = diameter * BLOCK_SIZE;
        int topDownH = diameter * BLOCK_SIZE;
        int crossW   = diameter * BLOCK_SIZE;
        int crossH   = totalLayers * BLOCK_SIZE;

        int contentW = topDownW + PADDING + crossW + PADDING + LEGEND_WIDTH;
        int totalW   = Math.max(contentW, FOV_W + 8);

        int nsBottomY  = 22 + topDownH + PADDING + 14 + crossH + 10;
        int fovYOffset = nsBottomY + 20;
        int totalH     = fovYOffset + FOV_H + 30;

        BufferedImage img = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Background
        g.setColor(new Color(18, 18, 26));
        g.fillRect(0, 0, totalW, totalH);

        // Title bar
        g.setColor(new Color(240, 240, 255));
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.drawString("Surroundings — " + result.biome + " | Y:" + result.playerBlockY
                + " | " + result.timeOfDay + " | " + result.weather, 4, 14);

        int yOffset = 22;

        // ── Top-down view ──────────────────────────────────────────────────
        drawTopDown(g, result, diameter, totalLayers, topDownW, topDownH, yOffset);

        // ── E-W cross-section ──────────────────────────────────────────────
        int crossXOffset = topDownW + PADDING;
        drawCrossSection(g, result, diameter, totalLayers, crossW, crossH,
                crossXOffset, yOffset, false);

        // ── N-S cross-section ──────────────────────────────────────────────
        int nsYOffset = yOffset + topDownH + PADDING + 14;
        drawCrossSection(g, result, diameter, totalLayers, crossW, crossH,
                0, nsYOffset, true);

        // ── Legend ─────────────────────────────────────────────────────────
        drawLegend(g, crossXOffset + crossW + PADDING, yOffset);

        // ── First-person perspective (3D DDA + textures) ───────────────────
        BufferedImage fov = renderFOV(result, diameter, totalLayers);
        g.drawImage(fov, 0, fovYOffset, null);

        // FOV border + label
        g.setColor(new Color(60, 60, 90));
        g.drawRect(-1, fovYOffset - 1, FOV_W + 1, FOV_H + 1);
        g.setColor(new Color(180, 180, 210));
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.drawString("Player Perspective — 90° FOV (3D DDA, Textured)", 2, fovYOffset + FOV_H + 12);

        g.dispose();

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    // =====================================================================
    //  FOV renderer — 3D DDA with real textures
    // =====================================================================

    private static BufferedImage renderFOV(WorldScanner.ScanResult r, int diameter, int totalLayers) {
        // Camera position in grid-space (player eye level)
        double camX = r.radius + 0.5;
        double camY = r.layersBelow + 1.62;
        double camZ = r.radius + 0.5;

        // --- Minecraft coordinate convention ---
        // Yaw:   0=South(+Z), 90=West(-X), 180=North(-Z), 270=East(+X) — clockwise
        // Pitch: 0=horizontal, positive=look DOWN, negative=look UP
        // We do NOT negate yaw; we negate pitch so positive pitch = downward ray.
        double yawRad   =  Math.toRadians(r.playerYaw);
        double pitchRad = -Math.toRadians(r.playerPitch); // flip: MC +pitch = down

        // Forward vector
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy =  Math.sin(pitchRad);
        double fz =  Math.cos(yawRad)  * Math.cos(pitchRad);

        // Right  = WorldUp(0,1,0) × Forward
        double rx = fz;
        double ry = 0.0;
        double rz = -fx;

        // Up = Forward × Right  (gives true camera-up, tilts with pitch)
        double ux = fy * rz;
        double uy = fz * rx - fx * rz;
        double uz = -fy * rx;

        // Horizontal FOV = 90°, aspect scales vertical FOV
        double aspectRatio = (double) FOV_H / FOV_W;

        // Sky colors interpolated top → bottom
        int[] skyTop    = skyTopColor(r.timeOfDay);
        int[] skyBottom = skyBottomColor(r.timeOfDay);

        BufferedImage fovImg = new BufferedImage(FOV_W, FOV_H, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[FOV_W * FOV_H];

        for (int fpy = 0; fpy < FOV_H; fpy++) {
            // Vertical screen-space coordinate [-1 .. 1]
            double vpY = ((double) fpy / FOV_H - 0.5) * 2.0 * aspectRatio;

            for (int fpx = 0; fpx < FOV_W; fpx++) {
                // Horizontal screen-space coordinate [-1 .. 1]
                double vpX = ((double) fpx / FOV_W - 0.5) * 2.0;

                // Ray direction
                double dx = fx + rx * vpX - ux * vpY;
                double dy = fy + ry * vpX - uy * vpY;
                double dz = fz + rz * vpX - uz * vpY;

                // Normalise
                double invLen = 1.0 / Math.sqrt(dx*dx + dy*dy + dz*dz);
                dx *= invLen; dy *= invLen; dz *= invLen;

                RayHit hit = ddaRayCast(r, diameter, totalLayers, camX, camY, camZ, dx, dy, dz);

                int color;
                if (hit == null) {
                    // Sky gradient
                    double t = (double) fpy / FOV_H; // 0=top, 1=bottom
                    color = lerpColor(skyTop, skyBottom, t);
                } else {
                    color = shadeHit(hit, r.timeOfDay, r.lightLevel);
                }
                pixels[fpy * FOV_W + fpx] = color;
            }
        }

        fovImg.setRGB(0, 0, FOV_W, FOV_H, pixels, 0, FOV_W);

        // Draw crosshair
        drawCrosshair(fovImg);

        return fovImg;
    }

    /**
     * 3D Digital Differential Analyser (Amanatides & Woo).
     * Traces a ray through the voxel grid and returns the first solid block hit.
     */
    private static RayHit ddaRayCast(WorldScanner.ScanResult r, int diameter, int totalLayers,
                                      double ox, double oy, double oz,
                                      double dx, double dy, double dz) {
        // Starting voxel
        int ix = (int) Math.floor(ox);
        int iy = (int) Math.floor(oy);
        int iz = (int) Math.floor(oz);

        // Step direction per axis
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        // tMax: distance to next voxel boundary on each axis
        double tMaxX = dx == 0 ? Double.MAX_VALUE
                : (dx > 0 ? (ix + 1 - ox) : (ox - ix)) / Math.abs(dx);
        double tMaxY = dy == 0 ? Double.MAX_VALUE
                : (dy > 0 ? (iy + 1 - oy) : (oy - iy)) / Math.abs(dy);
        double tMaxZ = dz == 0 ? Double.MAX_VALUE
                : (dz > 0 ? (iz + 1 - oz) : (oz - iz)) / Math.abs(dz);

        // tDelta: step size in t per axis
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);

        // Track which face we entered (for UV + lighting)
        int enteredAxis = -1; // 0=Y, 1=X, 2=Z
        double t = 0;
        int maxSteps = (diameter + totalLayers) * 2;

        for (int step = 0; step < maxSteps; step++) {
            // Out-of-bounds exit
            if (ix < 0 || ix >= diameter || iz < 0 || iz >= diameter
                    || iy < 0 || iy >= totalLayers) {
                if (t > 0) break;
            } else {
                Material mat = r.blocks[iy][iz][ix];
                if (isSolid(mat)) {
                    RayHit hit = new RayHit();
                    hit.mat  = mat;
                    hit.dist = t;

                    // Compute hit position for UV
                    double hx = ox + dx * t;
                    double hy = oy + dy * t;
                    double hz = oz + dz * t;

                    switch (enteredAxis) {
                        case 0: // Y face (top or bottom)
                            hit.face   = stepY > 0 ? TextureManager.FACE_BOTTOM : TextureManager.FACE_TOP;
                            hit.u      = hx - Math.floor(hx);
                            hit.v      = hz - Math.floor(hz);
                            hit.xzFace = false;
                            hit.zFace  = false;
                            break;
                        case 1: // X face (side)
                            hit.face   = TextureManager.FACE_SIDE;
                            hit.u      = hz - Math.floor(hz);
                            hit.v      = 1.0 - (hy - Math.floor(hy));
                            hit.xzFace = true;
                            hit.zFace  = false;
                            break;
                        default: // Z face (side)
                            hit.face   = TextureManager.FACE_SIDE;
                            hit.u      = hx - Math.floor(hx);
                            hit.v      = 1.0 - (hy - Math.floor(hy));
                            hit.xzFace = false;
                            hit.zFace  = true;
                            break;
                    }
                    return hit;
                }
            }

            // Advance to next voxel boundary
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX; tMaxX += tDeltaX; ix += stepX; enteredAxis = 1;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY; tMaxY += tDeltaY; iy += stepY; enteredAxis = 0;
            } else {
                t = tMaxZ; tMaxZ += tDeltaZ; iz += stepZ; enteredAxis = 2;
            }
        }
        return null;
    }

    private static boolean isSolid(Material m) {
        if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) return false;
        // Treat transparent / plant blocks as non-solid for ray purposes
        String n = m.name();
        if (n.contains("GRASS") && !n.equals("GRASS_BLOCK")) return false;
        if (n.contains("FLOWER") || n.equals("DANDELION") || n.contains("TULIP")
                || n.equals("ALLIUM") || n.equals("OXEYE_DAISY") || n.equals("CORNFLOWER")
                || n.equals("AZURE_BLUET") || n.equals("LILAC") || n.equals("ROSE_BUSH")
                || n.equals("PEONY") || n.equals("LARGE_FERN") || n.equals("FERN")
                || n.contains("SAPLING") || n.equals("DEAD_BUSH") || n.contains("VINE")
                || n.equals("SUGAR_CANE") || n.contains("MUSHROOM") && !n.contains("BLOCK")) {
            return false;
        }
        return true;
    }

    /**
     * Convert a RayHit to an RGB pixel — flat color, no fog, no shading.
     * Fast and clear for AI analysis.
     */
    private static int shadeHit(RayHit hit, String timeOfDay, int lightLevel) {
        int rgb = getRawColor(hit);
        rgb = applyBiomeTint(rgb, hit.mat);

        // Minimal Minecraft-style face shading (top bright, sides slightly darker)
        // No fog, no distance darkening.
        double shade;
        if (hit.face == TextureManager.FACE_TOP)         shade = 1.00;
        else if (hit.face == TextureManager.FACE_BOTTOM) shade = 0.60;
        else if (hit.xzFace)                             shade = 0.75;
        else                                             shade = 0.85;

        int r = (int)(((rgb >> 16) & 0xFF) * shade);
        int g = (int)(((rgb >>  8) & 0xFF) * shade);
        int b = (int)(( rgb        & 0xFF) * shade);
        return clampRgb(r, g, b);
    }

    private static int getRawColor(RayHit hit) {
        BufferedImage tex = TextureManager.get(hit.mat, hit.face);
        if (tex != null) {
            return TextureManager.sample(tex, hit.u, hit.v);
        }
        // Flat color fallback
        Color c = getBlockColor(hit.mat);
        return c.getRGB();
    }

    private static int applyBiomeTint(int rgb, Material mat) {
        if (TextureManager.needsGrassTint(mat)) {
            // Multiply with a pleasant mid-latitude grass green
            return multiplyColor(rgb, 0.61, 0.87, 0.23);
        }
        if (TextureManager.needsLeafTint(mat)) {
            return multiplyColor(rgb, 0.54, 0.78, 0.18);
        }
        return rgb;
    }

    private static int multiplyColor(int rgb, double r, double g, double b) {
        int cr = (rgb >> 16) & 0xFF;
        int cg = (rgb >>  8) & 0xFF;
        int cb =  rgb        & 0xFF;
        return clampRgb((int)(cr * r), (int)(cg * g), (int)(cb * b));
    }

    private static int applyShadeAndFog(int rgb, double shade, double fog, int[] skyRgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;

        r = (int)(r * shade);
        g = (int)(g * shade);
        b = (int)(b * shade);

        // Lerp toward sky
        r = (int)(r * (1 - fog) + skyRgb[0] * fog);
        g = (int)(g * (1 - fog) + skyRgb[1] * fog);
        b = (int)(b * (1 - fog) + skyRgb[2] * fog);

        return clampRgb(r, g, b);
    }

    private static int clampRgb(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    // ---- Sky colours by time -------------------------------------------------

    private static int[] skyTopColor(String time) {
        return switch (time) {
            case "DAWN"      -> new int[]{255, 140,  80};
            case "MORNING"   -> new int[]{ 80, 160, 230};
            case "AFTERNOON" -> new int[]{ 60, 140, 220};
            case "SUNSET"    -> new int[]{240,  80,  30};
            case "NIGHT"     -> new int[]{  5,  10,  40};
            case "MIDNIGHT"  -> new int[]{  0,   2,  15};
            default          -> new int[]{ 80, 160, 230};
        };
    }

    private static int[] skyBottomColor(String time) {
        return switch (time) {
            case "DAWN"      -> new int[]{255, 200, 120};
            case "MORNING"   -> new int[]{155, 210, 250};
            case "AFTERNOON" -> new int[]{140, 200, 255};
            case "SUNSET"    -> new int[]{255, 130,  50};
            case "NIGHT"     -> new int[]{ 15,  20,  55};
            case "MIDNIGHT"  -> new int[]{  5,   5,  25};
            default          -> new int[]{140, 200, 255};
        };
    }

    private static int[] skyMidColor(String time) {
        int[] top = skyTopColor(time);
        int[] bot = skyBottomColor(time);
        return new int[]{(top[0]+bot[0])/2, (top[1]+bot[1])/2, (top[2]+bot[2])/2};
    }

    private static int lerpColor(int[] a, int[] b, double t) {
        int r = (int)(a[0] * (1-t) + b[0] * t);
        int g = (int)(a[1] * (1-t) + b[1] * t);
        int bl = (int)(a[2] * (1-t) + b[2] * t);
        return clampRgb(r, g, bl);
    }

    private static double getDayLightFactor(String time, int lightLevel) {
        double base = switch (time) {
            case "NIGHT"    -> 0.25;
            case "MIDNIGHT" -> 0.15;
            case "DAWN", "SUNSET" -> 0.65;
            default -> 1.0;
        };
        // Blend with block light level [0..15]
        double blockLight = lightLevel / 15.0;
        return Math.max(base, blockLight * 0.9);
    }

    private static void drawCrosshair(BufferedImage img) {
        int cx = FOV_W / 2;
        int cy = FOV_H / 2;
        int len = 8;
        for (int i = -len; i <= len; i++) {
            if (i == 0) continue;
            safeSetRGB(img, cx + i, cy, 0xFFFFFFFF);
            safeSetRGB(img, cx, cy + i, 0xFFFFFFFF);
            // Dark outline
            safeSetRGB(img, cx + i, cy - 1, 0xFF000000);
            safeSetRGB(img, cx + i, cy + 1, 0xFF000000);
            safeSetRGB(img, cx - 1, cy + i, 0xFF000000);
            safeSetRGB(img, cx + 1, cy + i, 0xFF000000);
        }
    }

    private static void safeSetRGB(BufferedImage img, int x, int y, int color) {
        if (x >= 0 && x < img.getWidth() && y >= 0 && y < img.getHeight()) img.setRGB(x, y, color);
    }

    // =====================================================================
    //  Overview map helpers (unchanged from original, just cleaned up)
    // =====================================================================

    private static void drawTopDown(Graphics2D g, WorldScanner.ScanResult r,
                                    int diameter, int totalLayers,
                                    int w, int h, int yOffset) {
        g.setColor(new Color(50, 50, 75));
        g.drawRect(0, yOffset - 1, w + 1, h + 1);

        for (int z = 0; z < diameter; z++) {
            for (int x = 0; x < diameter; x++) {
                Material top = Material.AIR;
                for (int y = totalLayers - 1; y >= 0; y--) {
                    Material m = r.blocks[y][z][x];
                    if (m != Material.AIR && m != Material.CAVE_AIR && m != Material.VOID_AIR) {
                        top = m; break;
                    }
                }
                g.setColor(getBlockColor(top));
                g.fillRect(x * BLOCK_SIZE, yOffset + z * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                g.setColor(new Color(30, 30, 45, 60));
                g.drawRect(x * BLOCK_SIZE, yOffset + z * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }

        // Player dot
        int px = r.radius * BLOCK_SIZE + BLOCK_SIZE / 2;
        int py = yOffset + r.radius * BLOCK_SIZE + BLOCK_SIZE / 2;
        g.setColor(Color.RED);
        g.fillOval(px - 4, py - 4, 8, 8);
        g.setColor(Color.WHITE);
        g.drawOval(px - 4, py - 4, 8, 8);

        // Facing arrow
        double yawRad = Math.toRadians(r.playerYaw);
        int arrowLen = 10;
        int ax = (int)(px + Math.sin(yawRad) * arrowLen);
        int ay = (int)(py - Math.cos(yawRad) * arrowLen);  // screen Y is flipped
        g.setColor(Color.YELLOW);
        g.setStroke(new BasicStroke(2));
        g.drawLine(px, py, ax, ay);
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(160, 160, 190));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString("Top-Down View", 2, yOffset + h + 10);
    }

    private static void drawCrossSection(Graphics2D g, WorldScanner.ScanResult r,
                                         int diameter, int totalLayers,
                                         int crossW, int crossH,
                                         int xOff, int yOff, boolean northSouth) {
        g.setColor(new Color(50, 50, 75));
        g.drawRect(xOff - 1, yOff - 1, crossW + 1, crossH + 1);

        int sliceIndex = r.radius;
        for (int y = 0; y < totalLayers; y++) {
            for (int i = 0; i < diameter; i++) {
                Material m = northSouth
                        ? r.blocks[totalLayers - 1 - y][i][sliceIndex]
                        : r.blocks[totalLayers - 1 - y][sliceIndex][i];
                g.setColor(getBlockColor(m));
                g.fillRect(xOff + i * BLOCK_SIZE, yOff + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
                g.setColor(new Color(30, 30, 45, 60));
                g.drawRect(xOff + i * BLOCK_SIZE, yOff + y * BLOCK_SIZE, BLOCK_SIZE, BLOCK_SIZE);
            }
        }

        // Player marker
        int mx = xOff + r.radius * BLOCK_SIZE + BLOCK_SIZE / 2;
        int my = yOff + (totalLayers - 1 - r.layersBelow) * BLOCK_SIZE + BLOCK_SIZE / 2;
        g.setColor(Color.RED);
        g.fillOval(mx - 3, my - 3, 6, 6);
        g.setColor(Color.WHITE);
        g.drawOval(mx - 3, my - 3, 6, 6);

        g.setColor(new Color(160, 160, 190));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.drawString(northSouth ? "N-S Cross Section" : "E-W Cross Section",
                xOff + 2, yOff + crossH + 10);
    }

    private static void drawLegend(Graphics2D g, int legendX, int yOffset) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.drawString("LEGEND", legendX, yOffset + 10);

        String[][] entries = {
            {"Grass",       "91,168,60" },
            {"Dirt",        "134,96,67" },
            {"Stone",       "125,125,125"},
            {"Sand",        "219,207,163"},
            {"Water",       "47,67,244" },
            {"Lava",        "252,87,0"  },
            {"Oak Log",     "109,85,51" },
            {"Planks",      "162,130,78"},
            {"Leaves",      "59,120,26" },
            {"Coal Ore",    "70,70,70"  },
            {"Iron Ore",    "183,155,129"},
            {"Diamond",     "93,236,218"},
            {"Gold Ore",    "252,238,75"},
            {"Redstone",    "255,0,0"   },
            {"Chest",       "190,140,40"},
            {"Netherrack",  "111,54,53" },
            {"Player ●",    "255,0,0"   },
        };

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        for (int i = 0; i < entries.length; i++) {
            String[] vals = entries[i][1].split(",");
            Color c = new Color(Integer.parseInt(vals[0].trim()),
                                Integer.parseInt(vals[1].trim()),
                                Integer.parseInt(vals[2].trim()));
            int ly = yOffset + 18 + i * 12;
            g.setColor(c);
            g.fillRect(legendX, ly, 8, 8);
            g.setColor(new Color(200, 200, 215));
            g.drawString(entries[i][0], legendX + 12, ly + 8);
        }
    }

    // =====================================================================
    //  Flat-color fallback (kept for overview maps + texture miss)
    // =====================================================================

    private static final Map<Material, Color> BLOCK_COLORS = new HashMap<>();
    static {
        BLOCK_COLORS.put(Material.AIR,            new Color(0,0,0,0));
        BLOCK_COLORS.put(Material.CAVE_AIR,       new Color(0,0,0,0));
        BLOCK_COLORS.put(Material.VOID_AIR,       new Color(0,0,0,0));
        BLOCK_COLORS.put(Material.GRASS_BLOCK,    new Color(91,168,60));
        BLOCK_COLORS.put(Material.DIRT,           new Color(134,96,67));
        BLOCK_COLORS.put(Material.COARSE_DIRT,    new Color(119,85,59));
        BLOCK_COLORS.put(Material.ROOTED_DIRT,    new Color(120,88,60));
        BLOCK_COLORS.put(Material.PODZOL,         new Color(91,63,24));
        BLOCK_COLORS.put(Material.MYCELIUM,       new Color(111,99,108));
        BLOCK_COLORS.put(Material.MUD,            new Color(60,57,55));
        BLOCK_COLORS.put(Material.PACKED_MUD,     new Color(142,106,79));
        BLOCK_COLORS.put(Material.FARMLAND,       new Color(111,79,47));
        BLOCK_COLORS.put(Material.GRAVEL,         new Color(131,127,126));
        BLOCK_COLORS.put(Material.SAND,           new Color(219,207,163));
        BLOCK_COLORS.put(Material.RED_SAND,       new Color(190,102,33));
        BLOCK_COLORS.put(Material.CLAY,           new Color(160,166,179));
        BLOCK_COLORS.put(Material.STONE,          new Color(125,125,125));
        BLOCK_COLORS.put(Material.COBBLESTONE,    new Color(122,122,122));
        BLOCK_COLORS.put(Material.SMOOTH_STONE,   new Color(158,158,158));
        BLOCK_COLORS.put(Material.STONE_BRICKS,   new Color(122,121,122));
        BLOCK_COLORS.put(Material.GRANITE,        new Color(149,103,85));
        BLOCK_COLORS.put(Material.DIORITE,        new Color(188,188,188));
        BLOCK_COLORS.put(Material.ANDESITE,       new Color(136,136,136));
        BLOCK_COLORS.put(Material.DEEPSLATE,      new Color(75,75,80));
        BLOCK_COLORS.put(Material.BEDROCK,        new Color(50,50,50));
        BLOCK_COLORS.put(Material.OBSIDIAN,       new Color(15,10,24));
        BLOCK_COLORS.put(Material.SNOW_BLOCK,     new Color(249,254,254));
        BLOCK_COLORS.put(Material.ICE,            new Color(145,183,253));
        BLOCK_COLORS.put(Material.PACKED_ICE,     new Color(141,180,250));
        BLOCK_COLORS.put(Material.BLUE_ICE,       new Color(116,167,253));
        BLOCK_COLORS.put(Material.WATER,          new Color(47,67,244,180));
        BLOCK_COLORS.put(Material.LAVA,           new Color(252,87,0));
        BLOCK_COLORS.put(Material.NETHERRACK,     new Color(111,54,53));
        BLOCK_COLORS.put(Material.SOUL_SAND,      new Color(81,62,50));
        BLOCK_COLORS.put(Material.GLOWSTONE,      new Color(249,212,122));
        BLOCK_COLORS.put(Material.END_STONE,      new Color(219,223,158));
        BLOCK_COLORS.put(Material.OAK_LOG,        new Color(109,85,51));
        BLOCK_COLORS.put(Material.OAK_PLANKS,     new Color(162,130,78));
        BLOCK_COLORS.put(Material.OAK_LEAVES,     new Color(59,120,26));
        BLOCK_COLORS.put(Material.SPRUCE_LOG,     new Color(58,37,16));
        BLOCK_COLORS.put(Material.SPRUCE_PLANKS,  new Color(114,84,48));
        BLOCK_COLORS.put(Material.BIRCH_LOG,      new Color(216,215,210));
        BLOCK_COLORS.put(Material.BIRCH_PLANKS,   new Color(196,179,123));
        BLOCK_COLORS.put(Material.DARK_OAK_LOG,   new Color(60,46,26));
        BLOCK_COLORS.put(Material.DARK_OAK_PLANKS,new Color(67,43,20));
        BLOCK_COLORS.put(Material.COAL_ORE,       new Color(70,70,70));
        BLOCK_COLORS.put(Material.IRON_ORE,       new Color(183,155,129));
        BLOCK_COLORS.put(Material.GOLD_ORE,       new Color(252,238,75));
        BLOCK_COLORS.put(Material.DIAMOND_ORE,    new Color(93,236,218));
        BLOCK_COLORS.put(Material.EMERALD_ORE,    new Color(17,221,62));
        BLOCK_COLORS.put(Material.REDSTONE_ORE,   new Color(255,0,0));
        BLOCK_COLORS.put(Material.LAPIS_ORE,      new Color(38,67,168));
        BLOCK_COLORS.put(Material.ANCIENT_DEBRIS, new Color(105,76,59));
        BLOCK_COLORS.put(Material.CHEST,          new Color(190,140,40));
        BLOCK_COLORS.put(Material.CRAFTING_TABLE, new Color(150,110,55));
        BLOCK_COLORS.put(Material.FURNACE,        new Color(130,130,130));
        BLOCK_COLORS.put(Material.TNT,            new Color(219,48,32));
        BLOCK_COLORS.put(Material.BEACON,         new Color(118,237,218));
        BLOCK_COLORS.put(Material.SPAWNER,        new Color(30,50,60));
        BLOCK_COLORS.put(Material.GLASS,          new Color(175,214,232,100));
        BLOCK_COLORS.put(Material.SANDSTONE,      new Color(216,202,155));
        BLOCK_COLORS.put(Material.RED_SANDSTONE,  new Color(186,99,29));
        BLOCK_COLORS.put(Material.BRICKS,         new Color(150,97,76));
        BLOCK_COLORS.put(Material.BOOKSHELF,      new Color(111,88,55));
        BLOCK_COLORS.put(Material.IRON_BLOCK,     new Color(220,220,220));
        BLOCK_COLORS.put(Material.GOLD_BLOCK,     new Color(246,208,61));
        BLOCK_COLORS.put(Material.DIAMOND_BLOCK,  new Color(99,225,207));
        BLOCK_COLORS.put(Material.EMERALD_BLOCK,  new Color(42,183,74));
        BLOCK_COLORS.put(Material.NETHERITE_BLOCK,new Color(66,61,63));
    }

    static Color getBlockColor(Material mat) {
        Color c = BLOCK_COLORS.get(mat);
        if (c != null) return c;

        String n = mat.name();
        if (n.contains("AIR"))                                     return new Color(0,0,0,0);
        if (n.startsWith("OAK_"))                                  return new Color(162,130,78);
        if (n.startsWith("SPRUCE_"))                               return new Color(114,84,48);
        if (n.startsWith("BIRCH_"))                                return new Color(196,179,123);
        if (n.startsWith("DARK_OAK_"))                            return new Color(67,43,20);
        if (n.startsWith("JUNGLE_"))                               return new Color(160,115,80);
        if (n.startsWith("ACACIA_"))                               return new Color(168,90,50);
        if (n.startsWith("MANGROVE_"))                             return new Color(117,54,48);
        if (n.startsWith("CHERRY_"))                               return new Color(226,178,172);
        if (n.startsWith("CRIMSON_"))                              return new Color(101,48,51);
        if (n.startsWith("WARPED_"))                               return new Color(43,104,99);
        if (n.contains("LEAVES"))                                  return new Color(59,120,26);
        if (n.contains("DEEPSLATE"))                               return new Color(75,75,80);
        if (n.contains("BLACKSTONE"))                              return new Color(42,35,40);
        if (n.contains("SANDSTONE"))   return n.contains("RED") ? new Color(186,99,29) : new Color(216,202,155);
        if (n.contains("PRISMARINE"))                              return new Color(99,171,158);
        if (n.contains("PURPUR"))                                  return new Color(170,126,170);
        if (n.contains("NETHER_BRICK"))                            return new Color(44,22,26);
        if (n.contains("QUARTZ"))                                  return new Color(235,229,222);
        if (n.contains("COPPER"))                                  return new Color(192,107,79);
        if (n.contains("AMETHYST"))                                return new Color(133,98,175);
        if (n.contains("PLANKS"))                                  return new Color(162,130,78);
        if (n.contains("LOG") || n.contains("WOOD"))               return new Color(109,85,51);
        if (n.contains("ORE"))                                     return new Color(170,140,100);
        if (n.contains("STONE") || n.contains("BRICK"))            return new Color(125,125,125);
        if (n.contains("SAND"))                                    return new Color(219,207,163);
        if (n.contains("GLASS"))                                   return new Color(175,214,232,100);
        if (n.contains("GRASS") || n.contains("FERN"))             return new Color(91,168,60);
        if (n.contains("FLOWER") || n.contains("ROSE"))            return new Color(200,80,80);
        if (n.contains("SNOW") || n.contains("ICE"))               return new Color(220,230,250);
        if (n.contains("LAVA") || n.contains("MAGMA"))             return new Color(252,87,0);
        if (n.contains("WATER"))                                   return new Color(47,67,244,180);
        if (n.contains("TORCH") || n.contains("LANTERN"))          return new Color(255,200,80);
        if (n.contains("REDSTONE") || n.contains("REPEATER"))      return new Color(171,27,3);
        if (n.contains("DIRT") || n.contains("MUD"))               return new Color(134,96,67);
        if (n.contains("SCULK"))                                   return new Color(12,37,42);
        if (n.contains("SOUL"))                                    return new Color(81,62,50);
        return new Color(80, 80, 80);
    }
}
