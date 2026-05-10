package com.vitalisnw.galalore.listeners;

import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.models.GeneratedItemData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

public class LootTableListener implements Listener {

    private final GalaIALore plugin;

    public LootTableListener(GalaIALore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        // Obtenemos la key del cofre (ej. minecraft:chests/abandoned_mineshaft)
        String context = event.getLootTable().getKey().getKey();

        // El PoolManager ahora saca un item aleatorio si decide que toca uno
        // Por simplicidad, sacamos uno del pool general (ahora segmentado)
        // Probabilidad de aparición desde config
        double chance = plugin.getConfig().getDouble("loot.global_spawn_chance", 20.0);
        if (new java.util.Random().nextDouble() * 100 < chance) {
            String rarity = rollRarity();
            GeneratedItemData data = plugin.getPoolManager().pullRandomItem(rarity);
            
            if (data != null) {
                // Generar el ItemStack físico (como reliquia no identificada)
                org.bukkit.inventory.ItemStack itemFisico = com.vitalisnw.galalore.utils.ItemFactory.createItemStack(data);
                event.getLoot().add(itemFisico);

                // Si hay un jugador involucrado, registrar el hallazgo
                if (event.getEntity() instanceof Player player) {
                    plugin.getPlayerStatsManager().recordFind(player.getUniqueId(), player.getName(), data.getRareza());
                }
            }
        }
    }

    private String rollRarity() {
        int roll = new java.util.Random().nextInt(100);
        if (roll < 5) return "LEGENDARIO";
        if (roll < 20) return "EPICO";
        if (roll < 50) return "RARO";
        return "COMUN";
    }
}
