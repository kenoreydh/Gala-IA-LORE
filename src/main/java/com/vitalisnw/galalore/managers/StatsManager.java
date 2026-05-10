package com.vitalisnw.galalore.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vitalisnw.galalore.GalaIALore;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestiona las estadísticas globales de generación de Gala-IA-LORE.
 */
public class StatsManager {

    private final Gson gson;
    private final File statsFile;
    private StatsData data;

    public static class StatsData {
        public int totalGenerados = 0;
        public int totalErrores = 0;
        public long tiempoPromedioMs = 0;
        public Map<String, Integer> distribucionRareza = new HashMap<>();
        public int librosGenerados = 0;
        public int itemsGenerados = 0;
        
        public StatsData() {
            distribucionRareza.put("COMUN", 0);
            distribucionRareza.put("RARO", 0);
            distribucionRareza.put("EPICO", 0);
            distribucionRareza.put("LEGENDARIO", 0);
        }
    }

    public StatsManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.statsFile = new File(GalaIALore.getInstance().getDataFolder(), "data/stats.json");
        loadStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            this.data = new StatsData();
            return;
        }
        try (FileReader reader = new FileReader(statsFile)) {
            this.data = gson.fromJson(reader, StatsData.class);
            if (this.data == null) this.data = new StatsData();
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error cargando stats: " + e.getMessage());
            this.data = new StatsData();
        }
    }

    public void saveStats() {
        try (FileWriter writer = new FileWriter(statsFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error guardando stats: " + e.getMessage());
        }
    }

    /** Registra una generación exitosa. */
    public void recordGeneration(String tipo, String rareza, long ms) {
        data.totalGenerados++;
        if ("LIBRO".equalsIgnoreCase(tipo)) data.librosGenerados++;
        else data.itemsGenerados++;

        String r = rareza.toUpperCase();
        data.distribucionRareza.put(r, data.distribucionRareza.getOrDefault(r, 0) + 1);

        // Media móvil simple para el tiempo
        if (data.tiempoPromedioMs == 0) data.tiempoPromedioMs = ms;
        else data.tiempoPromedioMs = (data.tiempoPromedioMs + ms) / 2;

        saveStats();
    }

    /** Registra un error de comunicación con la IA. */
    public void recordError() {
        data.totalErrores++;
        saveStats();
    }

    public StatsData getData() {
        return data;
    }
}
