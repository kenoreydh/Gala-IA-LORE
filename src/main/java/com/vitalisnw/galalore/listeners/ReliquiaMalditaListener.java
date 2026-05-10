package com.vitalisnw.galalore.listeners;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.utils.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Aplica efectos de poción negativos a los jugadores que tengan reliquias malditas identificadas en su mano.
 */
public class ReliquiaMalditaListener implements Listener {

    private final Gson gson = new Gson();

    public ReliquiaMalditaListener(GalaIALore plugin) {
        // Tarea periódica para revisar el inventario de los jugadores online
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkCursedItems(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 40L); // Cada 2 segundos
    }

    private void checkCursedItems(Player player) {
        // Solo revisamos la mano principal y secundaria por simplicidad y rendimiento
        checkItem(player, player.getInventory().getItemInMainHand());
        checkItem(player, player.getInventory().getItemInOffHand());
        
        // También armadura
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            checkItem(player, armor);
        }
    }

    private void checkItem(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        
        if (!ItemFactory.isIdentified(item)) return;

        if (item.getItemMeta().getPersistentDataContainer().has(ItemFactory.KEY_CURSED, PersistentDataType.BOOLEAN)) {
            String effectsJson = item.getItemMeta().getPersistentDataContainer().get(ItemFactory.KEY_EFFECTS, PersistentDataType.STRING);
            if (effectsJson != null) {
                List<String> effects = gson.fromJson(effectsJson, new TypeToken<List<String>>(){}.getType());
                for (String effectStr : effects) {
                    applyEffect(player, effectStr);
                }
            }
        }
    }

    private void applyEffect(Player player, String effectStr) {
        try {
            String[] parts = effectStr.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            int level = Integer.parseInt(parts[1]) - 1;
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, 100, level, true, false, true));
            }
        } catch (Exception ignored) {}
    }
}
