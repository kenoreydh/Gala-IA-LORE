package com.vitalisnw.galalore;

import com.vitalisnw.galalore.commands.AnticuarioCommand;
import com.vitalisnw.galalore.commands.GalaLoreCommand;
import com.vitalisnw.galalore.commands.IdentificarCommand;
import com.vitalisnw.galalore.gui.AnticuarioGUI;
import com.vitalisnw.galalore.gui.IdentificacionGUI;
import com.vitalisnw.galalore.listeners.LootTableListener;
import com.vitalisnw.galalore.listeners.ReliquiaMalditaListener;
import com.vitalisnw.galalore.managers.OllamaManager;
import com.vitalisnw.galalore.managers.PlayerStatsManager;
import com.vitalisnw.galalore.managers.PoolManager;
import com.vitalisnw.galalore.managers.StatsManager;
import com.vitalisnw.galalore.utils.GalaPlaceholderExpansion;
import com.vitalisnw.galalore.utils.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.List;
import java.util.Random;

public class GalaIALore extends JavaPlugin {

    private static GalaIALore instance;
    private OllamaManager ollamaManager;
    private PoolManager poolManager;
    private StatsManager statsManager;
    private PlayerStatsManager playerStatsManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Crear carpetas de datos
        File dataFolder = new File(getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        getLogger().info("========================================");
        getLogger().info("  Gala-IA-LORE v" + getDescription().getVersion());
        getLogger().info("========================================");

        ollamaManager = new OllamaManager();
        poolManager = new PoolManager();
        statsManager = new StatsManager();
        playerStatsManager = new PlayerStatsManager();

        if (!VaultHook.setupEconomy()) {
            getLogger().warning("[Gala-IA-LORE] Vault no encontrado. La venta estará desactivada.");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GalaPlaceholderExpansion(this).register();
            getLogger().info("[Gala-IA-LORE] PlaceholderAPI detectado y expansión registrada.");
        }

        // ── Comandos ──────────────────────────────────────────────────────
        GalaLoreCommand galaCmd = new GalaLoreCommand(this);
        getCommand("galalore").setExecutor(galaCmd);
        getCommand("galalore").setTabCompleter(galaCmd);

        AnticuarioGUI anticuarioGUI = new AnticuarioGUI();
        getCommand("anticuario").setExecutor(new AnticuarioCommand(anticuarioGUI));

        IdentificacionGUI identGUI = new IdentificacionGUI();
        getCommand("identificar").setExecutor(new IdentificarCommand(identGUI));

        // ── Listeners ─────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new LootTableListener(this), this);
        getServer().getPluginManager().registerEvents(anticuarioGUI, this);
        getServer().getPluginManager().registerEvents(identGUI, this);
        getServer().getPluginManager().registerEvents(new ReliquiaMalditaListener(this), this);

        // ── Comprobación inicial del pool (startup) ────────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndGeneratePool("STARTUP");
            }
        }.runTaskLater(this, 100L);

        // ── Tarea periódica (cada hora) ────────────────────────────────────
        startAutoGenerationTask();

        getLogger().info("[Gala-IA-LORE] Listo. Pool total: " + poolManager.getPoolSize() + " ítems.");
    }

    @Override
    public void onDisable() {
        if (poolManager != null) poolManager.savePools();
        if (statsManager != null) statsManager.saveStats();
        if (playerStatsManager != null) playerStatsManager.savePlayers();
        getLogger().info("[Gala-IA-LORE] Apagado. Datos guardados.");
    }

    private void startAutoGenerationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndGeneratePool("AUTO");
            }
        }.runTaskTimer(this, 72000L, 72000L); // cada hora
    }

    private void checkAndGeneratePool(String reason) {
        long now = System.currentTimeMillis();
        long last = poolManager.getLastGenerationTime();
        int days = getConfig().getInt("generation.auto_generation_interval_days", 3);
        long intervalMs = days * 24L * 60 * 60 * 1000L;
        
        int minSize = getConfig().getInt("generation.min_pool_size_trigger", 5);
        boolean poolBajo = poolManager.getPoolSize() < minSize;
        boolean tiempoSuperado = (now - last) > intervalMs;

        if (poolBajo || tiempoSuperado) {
            getLogger().info("[Gala-IA-LORE] [" + reason + "] Generando lote... pool bajo=" + poolBajo + " tiempo=" + tiempoSuperado);
            generateBatch(reason);
        }
    }

    public void generateBatch(String reason) {
        int numItems = getConfig().getInt("generation.items_per_batch", 5);
        int numBooks = getConfig().getInt("generation.books_per_batch", 5);
        String context = getConfig().getString("prompts.structures.default", "Una antigua leyenda del mundo.");
        Random rand = new Random();

        poolManager.setLastGenerationTime(System.currentTimeMillis());

        for (int i = 0; i < numItems; i++) {
            String rareza = rollRarity();
            String material = getMaterialForRarity(rareza, rand);
            long start = System.currentTimeMillis();
            ollamaManager.generateItemData(context, false, rareza, material)
                    .thenAccept(data -> {
                        if (data != null) {
                            poolManager.addItem(data);
                            statsManager.recordGeneration("ITEM", rareza, System.currentTimeMillis() - start);
                            getLogger().info("[Gala-IA-LORE] ✓ Ítem: " + data.getNombre() + " [" + data.getRareza() + "]");
                        } else {
                            statsManager.recordError();
                        }
                    });
        }

        for (int i = 0; i < numBooks; i++) {
            String rareza = rollRarity();
            long start = System.currentTimeMillis();
            ollamaManager.generateItemData(context, true, rareza, "WRITTEN_BOOK")
                    .thenAccept(data -> {
                        if (data != null) {
                            poolManager.addItem(data);
                            statsManager.recordGeneration("LIBRO", rareza, System.currentTimeMillis() - start);
                            getLogger().info("[Gala-IA-LORE] ✓ Libro: " + data.getNombre() + " [" + data.getRareza() + "]");
                        } else {
                            statsManager.recordError();
                        }
                    });
        }
    }

    private String getMaterialForRarity(String rareza, Random rand) {
        List<String> mats = getConfig().getStringList("materials." + rareza);
        if (mats != null && !mats.isEmpty()) {
            return mats.get(rand.nextInt(mats.size()));
        }
        return switch (rareza) {
            case "LEGENDARIO" -> "NETHERITE_SWORD";
            case "EPICO" -> "DIAMOND_SWORD";
            case "RARO" -> "IRON_SWORD";
            default -> "STONE_SWORD";
        };
    }

    private String rollRarity() {
        int roll = new Random().nextInt(100);
        int legen = getConfig().getInt("rarities.LEGENDARIO.chance", 5);
        int epic = getConfig().getInt("rarities.EPICO.chance", 15);
        int rare = getConfig().getInt("rarities.RARO.chance", 30);
        if (roll < legen) return "LEGENDARIO";
        if (roll < legen + epic) return "EPICO";
        if (roll < legen + epic + rare) return "RARO";
        return "COMUN";
    }

    public static GalaIALore getInstance() { return instance; }
    public OllamaManager getOllamaManager() { return ollamaManager; }
    public PoolManager getPoolManager() { return poolManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
}
