package com.vitalisnw.galalore.gui;

import com.vitalisnw.galalore.models.GeneratedItemData;
import com.vitalisnw.galalore.utils.ItemFactory;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de identificación de reliquias.
 *
 * Layout (3 filas × 9 columnas = 27 slots):
 *   Fila 0: [G][G][G][G][G][G][G][G][G]  ← decoración
 *   Fila 1: [G][R][G][→][M1][M2][M3][G][✓]
 *   Fila 2: [G][G][G][G][G][G][G][G][G]  ← decoración
 *
 *   R  = slot 10 → coloca la reliquia aquí
 *   →  = slot 12 → panel informativo (qué materiales se necesitan)
 *   M1 = slot 13 → material 1
 *   M2 = slot 14 → material 2
 *   M3 = slot 15 → material 3
 *   ✓  = slot 17 → botón identificar
 */
public class IdentificacionGUI implements Listener {

    private static final int SLOT_RELIC   = 10;
    private static final int SLOT_INFO    = 12;
    private static final int SLOT_MAT1    = 13;
    private static final int SLOT_MAT2    = 14;
    private static final int SLOT_MAT3    = 15;
    private static final int SLOT_BUTTON  = 17;

    // Slots donde el jugador puede colocar ítems
    private static final List<Integer> INPUT_SLOTS = List.of(SLOT_RELIC, SLOT_MAT1, SLOT_MAT2, SLOT_MAT3);

    // ── InventoryHolder interno para identificar nuestro inventario ────────

    private static class IdentificacionHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory i)      { this.inv = i; }
    }

    // ── Abrir GUI ─────────────────────────────────────────────────────────

    public void openGUI(Player player) {
        IdentificacionHolder holder = new IdentificacionHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Identificar Reliquia").color(NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        fillGlass(inv);
        inv.setItem(SLOT_INFO, buildInfoPanel(null)); // panel inicial vacío
        inv.setItem(SLOT_BUTTON, buildButton(false));

        player.openInventory(inv);
    }

    // ── Eventos ───────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof IdentificacionHolder)) return;

        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();

        // Shift-click desde inventario del jugador: cancelar
        if (event.isShiftClick() && slot >= 27) {
            event.setCancelled(true);
            return;
        }

        // Clicks dentro del GUI
        if (slot < 27) {
            if (!INPUT_SLOTS.contains(slot)) {
                // Slot de cristal o botón → cancelar a menos que sea el botón de identificar
                if (slot == SLOT_BUTTON) {
                    event.setCancelled(true);
                    handleIdentify(player, top);
                } else {
                    event.setCancelled(true);
                }
                return;
            }
            // Es un slot de input: dejar que el jugador interactúe
            // Actualizamos el panel informativo en el siguiente tick
            Bukkit.getScheduler().runTask(
                    com.vitalisnw.galalore.GalaIALore.getInstance(),
                    () -> updateGUI(top));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof IdentificacionHolder)) return;
        Player player = (Player) event.getPlayer();

        // Devolver todos los ítems que el jugador haya dejado en los slots de input
        for (int s : INPUT_SLOTS) {
            ItemStack it = event.getInventory().getItem(s);
            if (it != null && it.getType() != Material.AIR) {
                player.getInventory().addItem(it);
            }
        }
    }

    // ── Lógica de identificación ──────────────────────────────────────────

    private void handleIdentify(Player player, Inventory inv) {
        ItemStack relicSlot = inv.getItem(SLOT_RELIC);
        if (relicSlot == null || relicSlot.getType() == Material.AIR) {
            player.sendMessage("§c[Identificar] Coloca una reliquia en el slot de la izquierda.");
            return;
        }
        if (ItemFactory.isIdentified(relicSlot)) {
            player.sendMessage("§e[Identificar] Esta reliquia ya está identificada.");
            return;
        }

        GeneratedItemData data = ItemFactory.readDataFromItem(relicSlot);
        if (data == null) {
            player.sendMessage("§c[Identificar] Esta reliquia no tiene datos válidos.");
            return;
        }

        List<GeneratedItemData.MaterialRequerido> reqs = data.getMaterialesIdentificacion();
        if (reqs == null || reqs.isEmpty()) {
            // Sin requisitos: identificar directamente
            identify(player, inv, data);
            return;
        }

        // Verificar que los materiales en los slots coincidan con los requisitos
        List<ItemStack> materialSlots = new ArrayList<>();
        for (int s : List.of(SLOT_MAT1, SLOT_MAT2, SLOT_MAT3)) {
            ItemStack it = inv.getItem(s);
            if (it != null && it.getType() != Material.AIR) materialSlots.add(it);
        }

        for (GeneratedItemData.MaterialRequerido req : reqs) {
            Material mat = com.vitalisnw.galalore.utils.MaterialUtils.match(req.getMaterial());
            
            if (mat == null) {
                player.sendMessage("§c[Error] El material '" + req.getMaterial() + "' no es reconocido por el sistema. Contacta con un admin.");
                return;
            }

            int      needed = req.getCantidad();
            Material finalMat = mat;
            int      found  = materialSlots.stream()
                    .filter(it -> it != null && it.getType() == finalMat)
                    .mapToInt(ItemStack::getAmount)
                    .sum();
            if (found < needed) {
                String display = req.getMaterial().replace("_", " ").toLowerCase();
                player.sendMessage("§c[Identificar] Faltan materiales. Necesitas " + needed + "x " + display + ".");
                return;
            }
        }

        // Todos los materiales están: consumir y identificar
        for (GeneratedItemData.MaterialRequerido req : reqs) {
            Material mat = com.vitalisnw.galalore.utils.MaterialUtils.match(req.getMaterial());
            int toConsume = req.getCantidad();
            Material finalMat = mat;
            for (int s : List.of(SLOT_MAT1, SLOT_MAT2, SLOT_MAT3)) {
                ItemStack it = inv.getItem(s);
                if (it != null && it.getType() == finalMat) {
                    int take = Math.min(toConsume, it.getAmount());
                    it.setAmount(it.getAmount() - take);
                    toConsume -= take;
                    if (toConsume <= 0) break;
                }
            }
        }

        identify(player, inv, data);
    }

    private void identify(Player player, Inventory inv, GeneratedItemData data) {
        data.setIdentificado(true);
        ItemStack identified = ItemFactory.createItemStack(data);

        // Quitar la reliquia no identificada del slot
        inv.setItem(SLOT_RELIC, new ItemStack(Material.AIR));

        // Dar el ítem identificado al jugador (cierra el inventario primero)
        player.closeInventory();
        player.getInventory().addItem(identified);
        player.sendMessage("§a[Identificar] ✦ ¡Has identificado: §e" + data.getNombre() + "§a!");
    }

    // ── Actualización del GUI ─────────────────────────────────────────────

    private void updateGUI(Inventory inv) {
        ItemStack relicSlot = inv.getItem(SLOT_RELIC);
        boolean hasRelic = relicSlot != null && relicSlot.getType() != Material.AIR
                && ItemFactory.isAIItem(relicSlot) && !ItemFactory.isIdentified(relicSlot);

        if (hasRelic) {
            GeneratedItemData data = ItemFactory.readDataFromItem(relicSlot);
            inv.setItem(SLOT_INFO, buildInfoPanel(data));
            inv.setItem(SLOT_BUTTON, buildButton(true));
        } else {
            inv.setItem(SLOT_INFO, buildInfoPanel(null));
            inv.setItem(SLOT_BUTTON, buildButton(false));
        }
    }

    // ── Constructores de ítems del GUI ────────────────────────────────────

    private ItemStack fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        if (meta != null) { meta.displayName(Component.empty()); glass.setItemMeta(meta); }
        for (int i = 0; i < 27; i++) {
            if (!INPUT_SLOTS.contains(i) && i != SLOT_INFO && i != SLOT_BUTTON)
                inv.setItem(i, glass);
        }
        return glass;
    }

    private ItemStack buildInfoPanel(GeneratedItemData data) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta  meta = info.getItemMeta();
        if (meta == null) return info;

        meta.displayName(Component.text("Materiales Necesarios")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (data == null || data.getMaterialesIdentificacion() == null || data.getMaterialesIdentificacion().isEmpty()) {
            lore.add(Component.text("Coloca una reliquia en el slot ←")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            for (GeneratedItemData.MaterialRequerido req : data.getMaterialesIdentificacion()) {
                String name = req.getMaterial().replace("_", " ").toLowerCase();
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                lore.add(Component.text("• " + req.getCantidad() + "x " + name)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack buildButton(boolean active) {
        ItemStack btn  = new ItemStack(active ? Material.EMERALD : Material.BARRIER);
        ItemMeta  meta = btn.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(active ? "✦ Identificar ✦" : "Sin reliquia")
                    .color(active ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            btn.setItemMeta(meta);
        }
        return btn;
    }
}
