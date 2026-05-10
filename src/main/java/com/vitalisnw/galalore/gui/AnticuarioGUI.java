package com.vitalisnw.galalore.gui;

import com.vitalisnw.galalore.utils.ItemFactory;
import com.vitalisnw.galalore.utils.VaultHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * GUI del Anticuario: permite al jugador vender una reliquia IA a cambio de dinero.
 *
 * Diseño: inventario de 9 slots.
 *   Slots 0-3 y 5-8 → cristales decorativos (no interactuables).
 *   Slot 4           → espacio para colocar la reliquia a vender.
 *
 * Flujo:
 *   1. El jugador abre /anticuario.
 *   2. Arrastra su reliquia al slot 4.
 *   3. Si es un ítem IA válido, se deposita dinero y se cierra el inventario.
 *   4. Si cierra sin vender, el ítem le es devuelto automáticamente.
 */
public class AnticuarioGUI implements Listener {

    private static final Component TITLE = Component.text("El Anticuario")
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true);

    /** Abre el GUI del Anticuario para un jugador. */
    public void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);

        // Rellenar con cristales decorativos en todos los slots menos el central (4)
        ItemStack glass = buildGlass();
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, glass);
        }

        player.openInventory(inv);
    }

    // ── Listeners ─────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isAnticuario(event.getView().title()))             return;

        Inventory top = event.getView().getTopInventory();

        // Bloquear shift-click desde el inventario del jugador (evita duplicados)
        if (event.getClickedInventory() != null && !event.getClickedInventory().equals(top)) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        // Solo permitir interacción en slot 4
        if (event.getSlot() != 4) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType() == Material.AIR) return;

        // Verificar que sea una reliquia IA
        if (!ItemFactory.isAIItem(cursor)) {
            event.setCancelled(true);
            player.sendMessage("§c[Anticuario] Solo acepto reliquias generadas por la IA.");
            return;
        }

        // Verificar que esté identificada
        if (!ItemFactory.isIdentified(cursor)) {
            event.setCancelled(true);
            player.sendMessage("§e[Anticuario] Esta reliquia aún no ha sido identificada.");
            player.sendMessage("§7Usa §b/identificar §7para revelar su verdadero valor.");
            return;
        }

        // Vender
        if (VaultHook.getEconomy() == null) {
            event.setCancelled(true);
            player.sendMessage("§c[Anticuario] Error: la economía no está disponible.");
            return;
        }

        double value = ItemFactory.getItemValue(cursor);
        VaultHook.getEconomy().depositPlayer(player, value);
        com.vitalisnw.galalore.GalaIALore.getInstance().getPlayerStatsManager().recordSale(player.getUniqueId(), player.getName(), value);
        player.sendMessage("§a[Anticuario] ¡Venta completada! Has recibido §e" + value + "$§a.");

        event.setCursor(new ItemStack(Material.AIR));
        event.setCancelled(true);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!isAnticuario(event.getView().title())) return;

        // Si el jugador cerró con una reliquia en el slot 4, devolvérsela
        ItemStack inSlot = event.getInventory().getItem(4);
        if (inSlot != null && inSlot.getType() != Material.AIR && !isGlass(inSlot)) {
            event.getPlayer().getInventory().addItem(inSlot);
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private ItemStack buildGlass() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private boolean isAnticuario(Component title) {
        return title.equals(TITLE);
    }

    private boolean isGlass(ItemStack item) {
        return item.getType() == Material.GRAY_STAINED_GLASS_PANE;
    }
}
