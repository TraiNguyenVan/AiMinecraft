# AiMinecraft

AiMinecraft is a lightweight, intelligent PaperMC plugin that transforms your Minecraft server into a responsive environment by connecting it directly to **Google's Gemini Generative AI API**. 

Instead of just commanding a bot, AiMinecraft acts as an intelligent observer and active participant. It "listens" to the server's pulse, remembers conversations, and organically joins in the fun based on real-time events.

---

## ✨ Features

- **Context-Aware Memory System:** The plugin permanently logs all player and AI chat to a local file (`brain.log`), providing the Gemini AI with rich, deep context of established lore and ongoing conversations.
- **Event-Driven AI Reactions:** The bot actively monitors major server events and chimes in intelligently:
  - Reactions to players joining/quitting.
  - "Hater" mode: The AI chaotically roasts players when they experience a death or complete an advancement.
  - Active chat participation: Replies organically when its name is mentioned or when asked a question, otherwise defaulting to a completely silent `SKIP` action to prevent chat spam.
- **Randomized Chatter (YapTask):** An async background task that randomly starts conversations using the current world context (e.g., in-game Time of Day and Weather).
- **Silent Failure Handling:** If the Gemini API rate-limits, hangs, or fails to parse a JSON response, the error is quietly written to the console while gracefully ignoring the message in-game, protecting player immersion.
- **Dynamic Configuration:** Quickly update the bot's core prompt instruction via `behavior.txt` and use the `/ai reload` command in-game to hot-swap personalities.

---

## 🏗️ Architecture

AiMinecraft relies on a structured, async architecture to prevent the heavy AI network requests from freezing the main Minecraft thread.

1. **`AiPlugin.java` (Core Entry Point):** Loads `config.yml` settings, establishes the default `behavior.txt` prompt, and registers events and commands.
2. **`GeminiClient.java` (Agnostic REST Client):** Uses standard `java.net.http.HttpClient` configured for async requests (`sendAsync`). Sends structured JSON Prompts to `generativelanguage.googleapis.com` and parses the generative response.
3. **`ChatListener.java` (Event Brain):** Manages all `@EventHandler` annotations (AsyncChatEvent, PlayerDeathEvent, PlayerJoinEvent). Dynamically pieces together context strings (time, online players, history) to craft precise prompts for the AI.
4. **`YapTask.java` (Autonomous Cron):** Scheduled BukkitRunnable that occasionally pokes the AI with environmental data to see if it wants to start a conversation.
5. **`LogUtils.java` (Memory Base):** Utility classes to seamlessly interact with `brain.log`. 

---

## 🛠️ Building the Plugin (Docker approach)

You don't need Java JDK 17 or Maven installed directly on your machine to build AiMinecraft! This project ships with a containerized build stage. 

Run the following command from the root directory. It will spin up a Maven/Alpine container, compile the project into a fat JAR file, and extract it natively back to your host machine's directory:

```bash
docker build -t aiminecraft-build . && docker run --rm -v "$PWD":/host aiminecraft-build cp /output/AiMinecraft-1.0.jar /host/
```

The compiled `AiMinecraft-1.0.jar` plugin will appear in the root of your project folder. Move it into your server's `/plugins` directory.

---

## ⚙️ Configuration & Usage

When the plugin first starts, it automatically generates a plugin folder containing two critical files:

### `config.yml`
Here you set your Google Gemini API Key, Model type (e.g., `gemini-1.5-flash`), UI strings, delays for the YapTask, and prompt templates.

### `behavior.txt`
This sets the overarching instruction (or "system prompt") for the AI. 

*Example:* `You are a chaotic demigod watching over a Minecraft server named 'Server'. You do not care for trivial matters, but enjoy roasting players.`

### Commands
Requires permission node: `aiminecraft.admin`
- `/ai reload` -> Reloads the `config.yml` and `behavior.txt` live.
- `/ai info` -> View current model connection status.
