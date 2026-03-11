# AiMinecraft

AiMinecraft is a PaperMC plugin that makes your Minecraft server genuinely intelligent. It connects directly to **Google Gemini** and gives the AI *real eyes* — a full first-person rendered view of what each player sees, their equipment, what they're looking at, who's nearby, and a deep memory of everything that's happened on the server.

---

## ✨ Features

### 🧠 Context-Aware AI
- Sends Gemini a rich text snapshot on every interaction: player health, hunger, XP level, armor, held items, hotbar contents, biome, dimension, time, weather, facing direction, terrain theme, and whether the player is in a player-built area.
- Crosshair awareness: the AI knows exactly which **block or entity** the player is currently looking at.
- Per-mob detail: each nearby mob is reported with its direction, distance, health percentage, and whether it's **targeting the player**.
- Nearby players reported with what they're holding and what they're doing (sneaking, flying).

### 👁️ AI Vision (Textured Voxel Renderer)
The plugin renders a **true first-person screenshot** of the player's surroundings and sends it to Gemini as an image alongside every prompt.

- **3D DDA raycasting** — exact voxel traversal, no gaps or artifacts
- **Real Minecraft textures** — automatically extracted from the Minecraft client jar at startup (drop `client.jar` into `plugins/AiMinecraft/` to activate)
- **Minecraft-style face shading** — top faces brighter, sides darker, bottom darkest
- **Biome tints** — grass and leaves get the correct green tint
- **Crosshair overlay** — center `+` so the AI knows what the player is aiming at
- **Facing arrow** on the top-down minimap
- Renders at **854×480** alongside a top-down map, two cross-sections, and a legend

### � Live Web Viewer
An embedded web server (default port `25462`) serves two views:

| Tab | What it shows |
|---|---|
| 🌍 3D Map | Interactive Three.js voxel scene, orbit + zoom, auto-refreshes every 5s |
| 👁️ AI Vision | **Exactly what Gemini sees** — the rendered PNG, live, auto-refreshing every 3s |

Access vision preview directly at: `http://localhost:25462/?tab=vision`

### 🗣️ Event-Driven Reactions
- **Chat** — replies when mentioned or directly asked; silently `SKIP`s everything else
- **Join/Leave** — comments on regulars; skips strangers
- **Death** — roasts the player in character
- **Advancements** — celebrates or roasts based on personality
- **Yapper** — autonomous background task that randomly starts conversations using the live world context

### 🧩 Player Memory System
- `brain.log` — every chat message ever sent is appended; the full log is sent to Gemini every prompt for long-term context
- `MemoryManager` — per-player dossiers updated live via `[REMEMBER: PlayerName] note` tags the AI can emit
- Player profiles: last seen, total messages, notes — summarised and injected into every prompt

---

## 🏗️ Architecture

```
AiPlugin           — entry point, config, async TextureManager init
GeminiClient       — async REST calls to generativelanguage.googleapis.com
ChatListener       — all event handlers, prompt assembly, memory writes
WorldScanner       — main-thread block + entity + equipment scan → ScanResult
TextureManager     — loads real Minecraft 16×16 textures from client jar at startup
MapRenderer        — 3D DDA voxel renderer → PNG bytes sent to Gemini + served via web
MapWebServer       — embedded HttpServer: /api/blocks, /api/players, /api/vision, /
MemoryManager      — per-player notes and profile persistence
YapTask            — scheduled autonomous chat task
LogUtils           — brain.log read helper
```

---

## 🛠️ Building

No local Java or Maven needed — Docker handles it:

```bash
docker run --rm \
  -v "$(pwd)":/app \
  -v maven-repo:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn package -q
```

Output: `target/AiMinecraft-1.0.jar` — drop into your server's `plugins/` folder.

---

## ⚙️ Setup

### 1. API Key
Edit `plugins/AiMinecraft/config.yml` after first run:
```yaml
gemini-api-key: "YOUR_GEMINI_API_KEY_HERE"
gemini-model: "gemini-2.5-flash"
```
Get a free key at [aistudio.google.com](https://aistudio.google.com).

### 2. Textures (optional but recommended)
Get the Minecraft client jar for your server version and drop it in:
```
plugins/AiMinecraft/client.jar
```
The plugin will auto-detect it on next restart. Without it, the renderer uses flat colors.

```bash
# Download the client jar automatically (replace 1.21.4 with your version)
curl -s "$(curl -s https://launchermeta.mojang.com/mc/game/version_manifest_v2.json \
  | python3 -c "import sys,json; vs=json.load(sys.stdin)['versions']; print(next(v['url'] for v in vs if v['id']=='1.21.4'))" \
  | xargs curl -s \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['downloads']['client']['url'])")" \
  -o plugins/AiMinecraft/client.jar
```

### 3. Personality
Edit `plugins/AiMinecraft/behavior.txt` — this is the system prompt for the AI.

### 4. Scanner Range
Increase in `config.yml` for more render distance (impacts performance):
```yaml
scanner:
  radius: 30       # blocks in each direction (30 = 61×61 grid)
  layers-above: 30
  layers-below: 30
```

---

## 💬 Commands

Requires permission: `aiminecraft.admin`

| Command | Description |
|---|---|
| `/ai reload` | Hot-reload `config.yml` and `behavior.txt` |
| `/ai info` | Show current model and connection status |
| `/ai on` / `/ai off` | Toggle AI responses |
| `/server <message>` | Send a direct message to the AI (whisper mode) |
