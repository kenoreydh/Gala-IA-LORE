package com.vitalisnw.galalore.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vitalisnw.galalore.GalaIALore;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestiona las estadísticas de los jugadores y el ranking.
 */
public class PlayerStatsManager {

    private final Gson gson;
    private final File playerFile;
    private Map<UUID, PlayerData> players = new HashMap<>();

    public static class PlayerData {
        public String nombre;
        public int reliquiasEncontradas = 0;
        public double valorTotalVendido = 0;
        public int legendariosEncontrados = 0;

        public PlayerData(String nombre) {
            this.nombre = nombre;
        }
    }

    public PlayerStatsManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.playerFile = new File(GalaIALore.getInstance().getDataFolder(), "data/player_stats.json");
        loadPlayers();
    }

    private void loadPlayers() {
        if (!playerFile.exists()) return;
        try (FileReader reader = new FileReader(playerFile)) {
            Type type = new TypeToken<Map<UUID, PlayerData>>(){}.getType();
            Map<UUID, PlayerData> loaded = gson.fromJson(reader, type);
            if (loaded != null) this.players = loaded;
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error cargando player stats: " + e.getMessage());
        }
    }

    public void savePlayers() {
        try (FileWriter writer = new FileWriter(playerFile)) {
            gson.toJson(players, writer);
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error guardando player stats: " + e.getMessage());
        }
    }

    private PlayerData getOrCreate(UUID uuid, String name) {
        return players.computeIfAbsent(uuid, k -> new PlayerData(name));
    }

    public void recordFind(UUID uuid, String name, String rareza) {
        PlayerData data = getOrCreate(uuid, name);
        data.nombre = name;
        data.reliquiasEncontradas++;
        if ("LEGENDARIO".equalsIgnoreCase(rareza)) data.legendariosEncontrados++;
        savePlayers();
    }

    public void recordSale(UUID uuid, String name, double value) {
        PlayerData data = getOrCreate(uuid, name);
        data.nombre = name;
        data.valorTotalVendido += value;
        savePlayers();
    }

    /** Devuelve el top 10 de jugadores por valor vendido. */
    public List<Map.Entry<UUID, PlayerData>> getTopPlayers() {
        return players.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().valorTotalVendido, e1.getValue().valorTotalVendido))
                .limit(10)
                .collect(Collectors.toList());
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.get(uuid);
    }
}
