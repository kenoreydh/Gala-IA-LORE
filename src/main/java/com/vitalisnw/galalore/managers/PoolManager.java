package com.vitalisnw.galalore.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.models.GeneratedItemData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Gestiona el pool de ítems generados, segmentado por rareza.
 */
public class PoolManager {

    private final Gson gson;
    private final Map<String, List<GeneratedItemData>> pools = new HashMap<>();
    private long lastGenerationTime = 0;

    public PoolManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        Arrays.asList("COMUN", "RARO", "EPICO", "LEGENDARIO").forEach(r -> pools.put(r, new ArrayList<>()));
        loadPools();
    }

    private void loadPools() {
        pools.keySet().forEach(this::loadPool);
        
        File timeFile = new File(GalaIALore.getInstance().getDataFolder(), "data/last_gen.txt");
        if (timeFile.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(timeFile.toPath()));
                lastGenerationTime = Long.parseLong(content.trim());
            } catch (Exception ignored) {}
        }
    }

    private void loadPool(String rarity) {
        File file = new File(GalaIALore.getInstance().getDataFolder(), "data/pool_" + rarity.toLowerCase() + ".json");
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<GeneratedItemData>>(){}.getType();
            List<GeneratedItemData> loaded = gson.fromJson(reader, type);
            if (loaded != null) pools.get(rarity).addAll(loaded);
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error cargando pool " + rarity + ": " + e.getMessage());
        }
    }

    public void savePools() {
        pools.keySet().forEach(this::savePool);
        
        File timeFile = new File(GalaIALore.getInstance().getDataFolder(), "data/last_gen.txt");
        try {
            java.nio.file.Files.write(timeFile.toPath(), String.valueOf(lastGenerationTime).getBytes());
        } catch (IOException ignored) {}
    }

    private void savePool(String rarity) {
        File file = new File(GalaIALore.getInstance().getDataFolder(), "data/pool_" + rarity.toLowerCase() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(pools.get(rarity), writer);
        } catch (IOException e) {
            GalaIALore.getInstance().getLogger().severe("Error guardando pool " + rarity + ": " + e.getMessage());
        }
    }

    public void addItem(GeneratedItemData data) {
        String rarity = normalizeRarity(data.getRareza());
        pools.get(rarity).add(data);
        savePool(rarity);
    }

    public GeneratedItemData getRandomItem(String rarity) {
        List<GeneratedItemData> pool = pools.get(normalizeRarity(rarity));
        if (pool.isEmpty()) return null;
        return pool.get(new Random().nextInt(pool.size()));
    }

    public GeneratedItemData pullRandomItem(String rarity) {
        List<GeneratedItemData> pool = pools.get(normalizeRarity(rarity));
        if (pool.isEmpty()) return null;
        GeneratedItemData item = pool.remove(new Random().nextInt(pool.size()));
        savePool(rarity);
        return item;
    }

    public int getPoolSize() {
        return pools.values().stream().mapToInt(List::size).sum();
    }

    public int getPoolSize(String rarity) {
        return pools.get(normalizeRarity(rarity)).size();
    }

    public long getLastGenerationTime() { return lastGenerationTime; }
    public void setLastGenerationTime(long time) { this.lastGenerationTime = time; }

    private String normalizeRarity(String r) {
        if (r == null) return "COMUN";
        String upper = r.toUpperCase();
        if (upper.startsWith("COMUN")) return "COMUN";
        if (upper.startsWith("RARA") || upper.startsWith("RARO")) return "RARO";
        if (upper.startsWith("EPIC")) return "EPICO";
        if (upper.startsWith("LEGEN")) return "LEGENDARIO";
        return "COMUN";
    }
}
