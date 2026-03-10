package com.yourname.aiminecraft;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Embedded web server serving a Three.js 3D voxel viewer.
 * Uses Java's built-in com.sun.net.httpserver.HttpServer — zero external dependencies.
 */
public class MapWebServer {

    private final AiPlugin plugin;
    private HttpServer server;
    private final int port;
    private final int scanRadius;
    private final int layersAbove;
    private final int layersBelow;

    public MapWebServer(AiPlugin plugin, int port, int scanRadius, int layersAbove, int layersBelow) {
        this.plugin = plugin;
        this.port = port;
        this.scanRadius = scanRadius;
        this.layersAbove = layersAbove;
        this.layersBelow = layersBelow;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        server.createContext("/", this::handleIndex);
        server.createContext("/api/blocks", this::handleBlocks);
        server.createContext("/api/players", this::handlePlayers);

        server.start();
        plugin.getLogger().info("[MapViewer] Web viewer started at http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[MapViewer] Web viewer stopped.");
        }
    }

    // --- Handlers ---

    private void handleIndex(HttpExchange exchange) throws IOException {
        String html = getViewerHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleBlocks(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String playerName = null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("player")) {
                    playerName = kv[1];
                }
            }
        }

        final String targetName = playerName;
        // Must run scan on the main Bukkit thread
        CompletableFuture<String> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player player = null;
                if (targetName != null) {
                    player = Bukkit.getPlayer(targetName);
                }
                if (player == null) {
                    // Default to first online player
                    var online = Bukkit.getOnlinePlayers();
                    if (!online.isEmpty()) {
                        player = online.iterator().next();
                    }
                }
                if (player == null) {
                    future.complete("{\"error\": \"No players online\"}");
                    return;
                }
                WorldScanner.ScanResult result = WorldScanner.scan(player, scanRadius, layersBelow, layersAbove);
                future.complete(WorldScanner.toJson(result));
            } catch (Exception e) {
                future.complete("{\"error\": \"" + e.getMessage() + "\"}");
            }
        });

        try {
            String json = future.get();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void handlePlayers(HttpExchange exchange) throws IOException {
        String json = "[" + Bukkit.getOnlinePlayers().stream()
            .map(p -> "\"" + p.getName() + "\"")
            .collect(Collectors.joining(",")) + "]";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        String json = "{\"error\": \"" + message + "\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // --- HTML + Three.js Viewer ---

    private String getViewerHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AiMinecraft — 3D Map Viewer</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    background: #0d0d14;
    color: #e0e0e0;
    font-family: 'Segoe UI', 'Inter', system-ui, sans-serif;
    overflow: hidden;
    height: 100vh;
  }
  #hud {
    position: fixed; top: 0; left: 0; right: 0;
    background: linear-gradient(180deg, rgba(13,13,20,0.95) 0%, rgba(13,13,20,0) 100%);
    padding: 16px 24px;
    display: flex; align-items: center; gap: 20px;
    z-index: 100;
    pointer-events: none;
  }
  #hud > * { pointer-events: auto; }
  #hud h1 {
    font-size: 16px; font-weight: 700;
    background: linear-gradient(135deg, #6ee7b7, #3b82f6);
    -webkit-background-clip: text; -webkit-text-fill-color: transparent;
    white-space: nowrap;
  }
  select, button {
    background: rgba(255,255,255,0.08);
    border: 1px solid rgba(255,255,255,0.15);
    color: #e0e0e0;
    padding: 6px 14px;
    border-radius: 8px;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;
  }
  select:hover, button:hover {
    background: rgba(255,255,255,0.15);
    border-color: rgba(110,231,183,0.5);
  }
  #info-panel {
    position: fixed; bottom: 16px; left: 16px;
    background: rgba(13,13,20,0.85);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(255,255,255,0.1);
    border-radius: 12px;
    padding: 16px 20px;
    font-size: 12px;
    line-height: 1.6;
    max-width: 320px;
    z-index: 100;
  }
  #info-panel .label { color: #6ee7b7; font-weight: 600; }
  #info-panel .val { color: #ccc; }
  #loading {
    position: fixed; top: 50%; left: 50%;
    transform: translate(-50%, -50%);
    font-size: 14px; color: #6ee7b7;
    z-index: 200;
  }
  #canvas-container { width: 100vw; height: 100vh; }
</style>
</head>
<body>
<div id="hud">
  <h1>🗺️ AiMinecraft Map</h1>
  <select id="player-select"><option>Loading...</option></select>
  <button onclick="refresh()">⟳ Refresh</button>
  <span style="font-size:12px;color:#888;">Auto-refresh: 5s | Scroll to zoom | Drag to orbit</span>
</div>
<div id="info-panel">Waiting for data...</div>
<div id="loading">⏳ Loading world data...</div>
<div id="canvas-container"></div>

<script type="importmap">
{
  "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/"
  }
}
</script>
<script type="module">
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const BLOCK_COLORS = {
  GRASS_BLOCK: 0x5ba83c, DIRT: 0x866043, COARSE_DIRT: 0x77553b,
  STONE: 0x7d7d7d, COBBLESTONE: 0x7a7a7a, DEEPSLATE: 0x4b4b50,
  BEDROCK: 0x323232, SAND: 0xdbcfa3, RED_SAND: 0xbe6621,
  GRAVEL: 0x837f7e, CLAY: 0xa0a6b3,
  OAK_LOG: 0x6d5533, OAK_PLANKS: 0xa2824e, SPRUCE_LOG: 0x3a2510,
  SPRUCE_PLANKS: 0x725430, BIRCH_LOG: 0xd8d7d2, BIRCH_PLANKS: 0xc4b37b,
  DARK_OAK_LOG: 0x3c2e1a, DARK_OAK_PLANKS: 0x432b14,
  OAK_LEAVES: 0x3b781a, SPRUCE_LEAVES: 0x304b30, BIRCH_LEAVES: 0x50792a,
  DARK_OAK_LEAVES: 0x225a0c,
  WATER: 0x2f43f4, LAVA: 0xfc5700,
  COAL_ORE: 0x464646, IRON_ORE: 0xb79b81, GOLD_ORE: 0xfcee4b,
  DIAMOND_ORE: 0x5decda, EMERALD_ORE: 0x11dd3e, REDSTONE_ORE: 0xff0000,
  LAPIS_ORE: 0x2643a8,
  BRICKS: 0x96614c, STONE_BRICKS: 0x7a797a,
  GLASS: 0xafd6e8, GLASS_PANE: 0xafd6e8, IRON_BLOCK: 0xdcdcdc,
  NETHERRACK: 0x6f3635, SOUL_SAND: 0x513e32, NETHER_BRICKS: 0x2c161a,
  GLOWSTONE: 0xf9d47a, BASALT: 0x48484e,
  END_STONE: 0xdbdf9e, PURPUR_BLOCK: 0xaa7eaa,
  CHEST: 0xbe8c28, TRAPPED_CHEST: 0xbe8c28, BARREL: 0x8b6d3c,
  FURNACE: 0x828282, BLAST_FURNACE: 0x5a5a5a, SMOKER: 0x6b5a3c,
  CRAFTING_TABLE: 0x966e37, ENCHANTING_TABLE: 0x641212,
  ANVIL: 0x444444, BREWING_STAND: 0x78643c, BEACON: 0x76edda,
  SPAWNER: 0x1e323c, TNT: 0xdb3020,
};

const AIR_BLOCKS = new Set(['AIR', 'CAVE_AIR', 'VOID_AIR']);

let scene, camera, renderer, controls, blockGroup;

function init() {
  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0d0d14);
  scene.fog = new THREE.Fog(0x0d0d14, 60, 120);

  camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 500);
  camera.position.set(20, 25, 20);

  renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(window.devicePixelRatio);
  document.getElementById('canvas-container').appendChild(renderer.domElement);

  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxPolarAngle = Math.PI * 0.85;

  // Lighting
  const ambient = new THREE.AmbientLight(0xffffff, 0.5);
  scene.add(ambient);
  const sun = new THREE.DirectionalLight(0xffeedd, 1.2);
  sun.position.set(30, 50, 20);
  scene.add(sun);
  const fill = new THREE.DirectionalLight(0x8888ff, 0.3);
  fill.position.set(-20, 10, -20);
  scene.add(fill);

  // Grid helper
  const grid = new THREE.GridHelper(100, 100, 0x222233, 0x111122);
  grid.position.y = -0.5;
  scene.add(grid);

  blockGroup = new THREE.Group();
  scene.add(blockGroup);

  window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  });

  animate();
}

function animate() {
  requestAnimationFrame(animate);
  controls.update();
  renderer.render(scene, camera);
}

function getColor(n) {
  if (BLOCK_COLORS[n]) return BLOCK_COLORS[n];
  // Dye color prefixes
  const DYE = {WHITE_:0xeaecec,ORANGE_:0xf07613,MAGENTA_:0xbd44b3,LIGHT_BLUE_:0x3aafd9,YELLOW_:0xf8c627,LIME_:0x70b919,PINK_:0xed8dac,LIGHT_GRAY_:0x8e8e87,GRAY_:0x3f4448,CYAN_:0x158a91,PURPLE_:0x792aad,BLUE_:0x35399d,BROWN_:0x724829,GREEN_:0x546d1b,RED_:0xa02722,BLACK_:0x141519};
  for (const [pre,col] of Object.entries(DYE)) { if (n.startsWith(pre) && (n.includes('WOOL')||n.includes('CONCRETE')||n.includes('TERRACOTTA')||n.includes('BED')||n.includes('CARPET')||n.includes('GLASS')||n.includes('CANDLE')||n.includes('BANNER')||n.includes('SHULKER'))) return col; }
  // Wood prefixes
  if (n.startsWith('OAK_')) return 0xa2824e;
  if (n.startsWith('SPRUCE_')) return 0x725430;
  if (n.startsWith('BIRCH_')) return 0xc4b37b;
  if (n.startsWith('DARK_OAK_')) return 0x432b14;
  if (n.startsWith('JUNGLE_')) return 0xa07350;
  if (n.startsWith('ACACIA_')) return 0xa85a32;
  if (n.startsWith('MANGROVE_')) return 0x753630;
  if (n.startsWith('CHERRY_')) return 0xe2b2ac;
  if (n.startsWith('BAMBOO_')) return 0xc2ab4d;
  if (n.startsWith('CRIMSON_')) return 0x653033;
  if (n.startsWith('WARPED_')) return 0x2b6863;
  // Stone families
  if (n.includes('DEEPSLATE')) return 0x4b4b50;
  if (n.includes('BLACKSTONE')) return 0x2a2328;
  if (n.includes('SANDSTONE')) return n.includes('RED') ? 0xba631d : 0xd8ca9b;
  if (n.includes('PRISMARINE')) return 0x63ab9e;
  if (n.includes('PURPUR')) return 0xaa7eaa;
  if (n.includes('END_STONE')) return 0xdbdf9e;
  if (n.includes('NETHER_BRICK')) return 0x2c161a;
  if (n.includes('QUARTZ')) return 0xebe5de;
  if (n.includes('COPPER')) return 0xc06b4f;
  if (n.includes('AMETHYST')) return 0x8562af;
  // Generic
  if (n.includes('PLANKS')) return 0xa2824e;
  if (n.includes('LOG')||n.includes('WOOD')||n.includes('STEM')||n.includes('HYPHAE')) return 0x6d5533;
  if (n.includes('LEAVES')) return 0x3b781a;
  if (n.includes('ORE')) return 0xaa8c64;
  if (n.includes('STONE')||n.includes('BRICK')) return 0x7d7d7d;
  if (n.includes('SAND')) return 0xdbcfa3;
  if (n.includes('GLASS')||n.includes('PANE')) return 0xafd6e8;
  if (n.includes('IRON')||n.includes('CHAIN')||n.includes('RAIL')) return 0xb4b4b4;
  if (n.includes('GOLD')) return 0xf6d03d;
  if (n.includes('GRASS')||n.includes('FERN')||n.includes('VINE')||n.includes('MOSS')) return 0x5ba83c;
  if (n.includes('FLOWER')||n.includes('ROSE')||n.includes('TULIP')||n.includes('ALLIUM')||n.includes('LILAC')) return 0xc85050;
  if (n.includes('SAPLING')||n.includes('BAMBOO')||n.includes('SUGAR_CANE')||n.includes('KELP')) return 0x468c28;
  if (n.includes('MUSHROOM')) return 0xb48c64;
  if (n.includes('CORAL')) return 0xc86496;
  if (n.includes('DIRT')||n.includes('MUD')) return 0x866043;
  if (n.includes('SNOW')||n.includes('ICE')) return 0xdce6fa;
  if (n.includes('SCULK')) return 0x0c252a;
  if (n.includes('SOUL')) return 0x513e32;
  if (n.includes('LAVA')||n.includes('MAGMA')||n.includes('FIRE')) return 0xfc5700;
  if (n.includes('WATER')||n.includes('BUBBLE')) return 0x2f43f4;
  if (n.includes('TORCH')||n.includes('LANTERN')||n.includes('CANDLE')) return 0xffc850;
  if (n.includes('REDSTONE')||n.includes('REPEATER')||n.includes('COMPARATOR')) return 0xab1b03;
  return 0x505050;
}

function buildWorld(data) {
  // Clear previous
  while (blockGroup.children.length > 0) {
    const c = blockGroup.children[0];
    if (c.geometry) c.geometry.dispose();
    if (c.material) c.material.dispose();
    blockGroup.remove(c);
  }

  const grid = data.grid;
  const layers = grid.layers;
  const radius = grid.radius;
  const layersBelow = grid.layersBelow;

  // Use instanced meshes for performance — group by color
  const colorBuckets = {};

  for (let y = 0; y < layers.length; y++) {
    const layer = layers[y];
    for (let z = 0; z < layer.length; z++) {
      const row = layer[z];
      for (let x = 0; x < row.length; x++) {
        const mat = row[x];
        if (AIR_BLOCKS.has(mat)) continue;

        const color = getColor(mat);
        const key = color.toString(16);
        if (!colorBuckets[key]) colorBuckets[key] = [];
        colorBuckets[key].push({
          x: x - radius,
          y: y - layersBelow,
          z: z - radius
        });
      }
    }
  }

  const geo = new THREE.BoxGeometry(1, 1, 1);
  for (const [hexColor, positions] of Object.entries(colorBuckets)) {
    const material = new THREE.MeshLambertMaterial({
      color: parseInt(hexColor, 16),
      transparent: hexColor === 'afd6e8',
      opacity: hexColor === 'afd6e8' ? 0.3 : 1,
    });
    const mesh = new THREE.InstancedMesh(geo, material, positions.length);
    const dummy = new THREE.Object3D();
    positions.forEach((pos, i) => {
      dummy.position.set(pos.x, pos.y, pos.z);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    });
    blockGroup.add(mesh);
  }

  // Player marker — glowing sphere
  const playerGeo = new THREE.SphereGeometry(0.5, 16, 16);
  const playerMat = new THREE.MeshBasicMaterial({ color: 0xff3333 });
  const playerMesh = new THREE.Mesh(playerGeo, playerMat);
  playerMesh.position.set(0, 1.5, 0);
  blockGroup.add(playerMesh);

  // Player glow ring
  const ringGeo = new THREE.RingGeometry(0.6, 0.9, 32);
  const ringMat = new THREE.MeshBasicMaterial({ color: 0xff3333, side: THREE.DoubleSide, transparent: true, opacity: 0.4 });
  const ring = new THREE.Mesh(ringGeo, ringMat);
  ring.rotation.x = -Math.PI / 2;
  ring.position.set(0, 0.1, 0);
  blockGroup.add(ring);

  // Center camera on player
  controls.target.set(0, 2, 0);
  controls.update();

  document.getElementById('loading').style.display = 'none';
}

function updateInfoPanel(data) {
  const p = data.player;
  const e = data.entities;
  let html = '';
  html += '<span class="label">Position:</span> <span class="val">X:' + p.x + ' Y:' + p.y + ' Z:' + p.z + '</span><br>';
  html += '<span class="label">Health:</span> <span class="val">' + Math.round(p.health) + '/' + p.maxHealth + '</span> ';
  html += '<span class="label">Hunger:</span> <span class="val">' + p.foodLevel + '/20</span><br>';
  html += '<span class="label">Biome:</span> <span class="val">' + p.biome + '</span><br>';
  html += '<span class="label">Dimension:</span> <span class="val">' + p.dimension + '</span><br>';
  html += '<span class="label">Time:</span> <span class="val">' + p.timeOfDay + '</span> ';
  html += '<span class="label">Weather:</span> <span class="val">' + p.weather + '</span><br>';
  if (e.hostile.length > 0) html += '<span class="label">⚔ Hostile:</span> <span class="val">' + e.hostile.join(', ') + '</span><br>';
  if (e.passive.length > 0) html += '<span class="label">🐄 Passive:</span> <span class="val">' + e.passive.join(', ') + '</span><br>';
  if (e.players.length > 0) html += '<span class="label">👤 Players:</span> <span class="val">' + e.players.join(', ') + '</span><br>';
  if (data.nearbyStructures.length > 0) html += '<span class="label">🏛 Structures:</span> <span class="val">' + data.nearbyStructures.join(', ') + '</span><br>';
  document.getElementById('info-panel').innerHTML = html;
}

async function loadPlayers() {
  try {
    const res = await fetch('/api/players');
    const players = await res.json();
    const sel = document.getElementById('player-select');
    sel.innerHTML = '';
    if (players.length === 0) {
      sel.innerHTML = '<option>No players online</option>';
      return;
    }
    players.forEach(name => {
      const opt = document.createElement('option');
      opt.value = name;
      opt.textContent = name;
      sel.appendChild(opt);
    });
  } catch (e) { console.error('Failed to load players:', e); }
}

async function refresh() {
  const player = document.getElementById('player-select').value;
  try {
    const res = await fetch('/api/blocks?player=' + encodeURIComponent(player));
    const data = await res.json();
    if (data.error) {
      document.getElementById('info-panel').innerHTML = '<span class="label">Error:</span> ' + data.error;
      return;
    }
    buildWorld(data);
    updateInfoPanel(data);
  } catch (e) {
    console.error('Failed to load blocks:', e);
    document.getElementById('info-panel').innerHTML = '<span class="label">Error:</span> Could not connect to server';
  }
}

window.refresh = refresh;

// Startup
init();
loadPlayers();
refresh();

// Auto-refresh every 5 seconds
setInterval(() => { loadPlayers(); refresh(); }, 5000);

// Refresh when player changes
document.getElementById('player-select').addEventListener('change', refresh);
</script>
</body>
</html>
""";
    }
}
