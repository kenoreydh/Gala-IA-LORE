package com.vitalisnw.galalore.utils;

import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.managers.PlayerStatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expansión para PlaceholderAPI.
 * %galalore_pool_size%
 * %galalore_player_found%
 * %galalore_player_value%
 * %galalore_top_N_name%
 * %galalore_top_N_value%
 */
public class GalaPlaceholderExpansion extends PlaceholderExpansion {

    private final GalaIALore plugin;

    public GalaPlaceholderExpansion(GalaIALore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "galalore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AntiGravity";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("pool_size")) {
            return String.valueOf(plugin.getPoolManager().getPoolSize());
        }

        if (player != null) {
            PlayerStatsManager.PlayerData data = plugin.getPlayerStatsManager().getPlayerData(player.getUniqueId());
            if (params.equalsIgnoreCase("player_found")) {
                return data != null ? String.valueOf(data.reliquiasEncontradas) : "0";
            }
            if (params.equalsIgnoreCase("player_value")) {
                return data != null ? String.format("%.2f", data.valorTotalVendido) : "0.00";
            }
        }

        // Ranking: %galalore_top_N_name% o %galalore_top_N_value%
        if (params.startsWith("top_")) {
            String[] parts = params.split("_");
            if (parts.length >= 3) {
                try {
                    int rank = Integer.parseInt(parts[1]) - 1; // 1-indexed to 0-indexed
                    List<Map.Entry<UUID, PlayerStatsManager.PlayerData>> top = plugin.getPlayerStatsManager().getTopPlayers();
                    
                    if (rank >= 0 && rank < top.size()) {
                        Map.Entry<UUID, PlayerStatsManager.PlayerData> entry = top.get(rank);
                        if (parts[2].equalsIgnoreCase("name")) return entry.getValue().nombre;
                        if (parts[2].equalsIgnoreCase("value")) return String.format("%.2f", entry.getValue().valorTotalVendido);
                    } else {
                        return "---";
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return null;
    }
}
