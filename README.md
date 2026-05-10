# ✨ Gala-IA-LORE
> **"Every item has a story to tell. Let AI write it."**

**Gala-IA-LORE** is a revolutionary plugin for RPG and Survival servers that integrates Artificial Intelligence (Llama 3 / Ollama) to generate unique lore and names for in-game items. No more generic "Diamond Swords"; now your players will find "The Twilight Edge," a relic with centuries of history written by AI.

## 🔥 Key Features
*   🧠 **AI Integration (Ollama):** Direct connection with local models to generate creative and immersive text without lag.
*   💎 **Relic System:** Items are categorized by rarity (Common, Rare, Epic, Legendary) with custom stats.
*   🏺 **The Antique Dealer:** A dedicated trade system (GUI) where players can sell their discoveries for money.
*   🔍 **Item Identification:** Items drop as "Cursed Relics" and must be identified via an interactive menu to reveal their true power.
*   ⚡ **Smart Pool System:** To ensure performance, the plugin pre-generates item batches in the background.

## 📋 Commands
| Command | Function | Permission |
| :--- | :--- | :--- |
| `/galalore top` | Leaderboard of the best relic hunters. | `galalore.use` |
| `/anticuario` | Opens the antique shop GUI. | `galalore.anticuario` |
| `/identificar` | Opens the item identification menu. | `galalore.identificar` |
| `/galalore reload` | Reloads the plugin configuration. | `galalore.admin` |
| `/galalore give <R>` | Gives a random item of a specific rarity. | `galalore.admin` |

## ⚙️ Requirements
*   **Paper/Spigot:** 1.21.X.
*   **AI:** Requires an [Ollama](https://ollama.com/) server running locally or accessible via network.
*   **Dependencies:** Vault (Economy).
