package com.vitalisnw.galalore.commands;

import com.vitalisnw.galalore.gui.IdentificacionGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Comando /identificar — abre el GUI de identificación de reliquias.
 * Permiso: galalore.identificar (para todos los jugadores)
 */
public class IdentificarCommand implements CommandExecutor {

    private final IdentificacionGUI gui;

    public IdentificarCommand(IdentificacionGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden usar este comando.");
            return true;
        }
        if (!player.hasPermission("galalore.identificar")) {
            player.sendMessage("§cNo tienes permiso para usar el identificador.");
            return true;
        }
        gui.openGUI(player);
        return true;
    }
}
