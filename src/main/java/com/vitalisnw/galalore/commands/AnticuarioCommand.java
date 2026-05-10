package com.vitalisnw.galalore.commands;

import com.vitalisnw.galalore.gui.AnticuarioGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AnticuarioCommand implements CommandExecutor {

    private final AnticuarioGUI gui;

    public AnticuarioCommand(AnticuarioGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar el anticuario.");
            return true;
        }

        if (!player.hasPermission("galalore.anticuario")) {
            player.sendMessage("§cNo tienes permiso para abrir este menú.");
            return true;
        }

        gui.openGUI(player);
        return true;
    }
}
