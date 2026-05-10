package com.vitalisnw.galalore.commands;

import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.managers.PlayerStatsManager;
import com.vitalisnw.galalore.managers.StatsManager;
import com.vitalisnw.galalore.models.GeneratedItemData;
import com.vitalisnw.galalore.utils.ItemFactory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comando /galalore para administración y utilidades.
 */
public class GalaLoreCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("forcegen", "stats", "give", "top", "pool", "reload");
    private final GalaIALore plugin;

    public GalaLoreCommand(GalaIALore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // Comandos públicos
        if (sub.equals("top")) return cmdTop(sender);

        // Comandos administrativos
        if (!sender.hasPermission("galalore.admin")) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        return switch (sub) {
            case "forcegen" -> cmdForceGen(sender);
            case "stats"    -> cmdStats(sender);
            case "give"     -> cmdGive(sender, args);
            case "pool"     -> cmdPool(sender);
            case "reload"   -> cmdReload(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Arrays.asList("COMUN", "RARO", "EPICO", "LEGENDARIO");
        }
        return List.of();
    }

    // ── Subcomandos ───────────────────────────────────────────────────────

    private boolean cmdForceGen(CommandSender sender) {
        sender.sendMessage("§a[Gala-IA-LORE] Iniciando generación forzada...");
        plugin.generateBatch("MANUAL");
        return true;
    }

    private boolean cmdStats(CommandSender sender) {
        StatsManager.StatsData data = plugin.getStatsManager().getData();
        sender.sendMessage("§6§l=== Estadísticas Globales IA ===");
        sender.sendMessage("§eTotal Generados: §f" + data.totalGenerados);
        sender.sendMessage("§eTotal Errores: §f" + data.totalErrores);
        sender.sendMessage("§eTiempo Promedio IA: §f" + (data.tiempoPromedioMs / 1000.0) + "s");
        sender.sendMessage("§eDistribución:");
        data.distribucionRareza.forEach((r, c) -> sender.sendMessage("  §7- " + r + ": §f" + c));
        return true;
    }

    private boolean cmdPool(CommandSender sender) {
        sender.sendMessage("§b§l=== Estado del Pool ===");
        for (String r : Arrays.asList("COMUN", "RARO", "EPICO", "LEGENDARIO")) {
            sender.sendMessage("§7- " + r + ": §f" + plugin.getPoolManager().getPoolSize(r));
        }
        return true;
    }

    private boolean cmdGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String rarity = args.length > 1 ? args[1].toUpperCase() : "COMUN";
        GeneratedItemData data = plugin.getPoolManager().pullRandomItem(rarity);
        if (data == null) {
            sender.sendMessage("§cPool vacío para rareza: " + rarity);
            return true;
        }
        ItemStack item = ItemFactory.createItemStack(data);
        player.getInventory().addItem(item);
        sender.sendMessage("§aRecibido: §e" + data.getNombre());
        return true;
    }

    private boolean cmdTop(CommandSender sender) {
        List<Map.Entry<UUID, PlayerStatsManager.PlayerData>> top = plugin.getPlayerStatsManager().getTopPlayers();
        sender.sendMessage("§6§l=== TOP BUSCADORES DE RELIQUIAS ===");
        if (top.isEmpty()) {
            sender.sendMessage("§7Nadie ha vendido reliquias aún.");
            return true;
        }
        int i = 1;
        for (Map.Entry<UUID, PlayerStatsManager.PlayerData> entry : top) {
            sender.sendMessage("§e" + i + ". §f" + entry.getValue().nombre + " §7- §6" + String.format("%.2f", entry.getValue().valorTotalVendido) + "$");
            i++;
        }
        return true;
    }

    private boolean cmdReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage("§a[Gala-IA-LORE] Configuración recargada correctamente.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== Gala-IA-LORE ===");
        sender.sendMessage("§e/galalore top §7- Ver el ranking de buscadores.");
        if (sender.hasPermission("galalore.admin")) {
            sender.sendMessage("§e/galalore forcegen §7- Generar lote.");
            sender.sendMessage("§e/galalore pool     §7- Ver tamaño de pools.");
            sender.sendMessage("§e/galalore stats    §7- Ver estadísticas de IA.");
            sender.sendMessage("§e/galalore give <R> §7- Dar ítem aleatorio.");
            sender.sendMessage("§e/galalore reload   §7- Recargar configuración.");
        }
    }
}
