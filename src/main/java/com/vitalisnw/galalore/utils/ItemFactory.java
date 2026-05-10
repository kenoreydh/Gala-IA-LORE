package com.vitalisnw.galalore.utils;

import com.google.gson.Gson;
import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.models.GeneratedItemData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte GeneratedItemData en ItemStack de Minecraft.
 *
 * Dos modos:
 *   - No identificado: muestra "Reliquia Desconocida" con los materiales requeridos.
 *                      Valor y lore ocultos. Guarda los datos completos en NBT.
 *   - Identificado:    muestra nombre, lore, rareza, encantamientos y valor.
 */
public class ItemFactory {

    // NBT keys
    public static final NamespacedKey KEY_AI        = new NamespacedKey(GalaIALore.getInstance(), "gala_ai_item");
    public static final NamespacedKey KEY_VALUE      = new NamespacedKey(GalaIALore.getInstance(), "gala_value");
    public static final NamespacedKey KEY_IDENTIFIED = new NamespacedKey(GalaIALore.getInstance(), "gala_identificado");
    public static final NamespacedKey KEY_DATA       = new NamespacedKey(GalaIALore.getInstance(), "gala_full_data");
    public static final NamespacedKey KEY_CURSED     = new NamespacedKey(GalaIALore.getInstance(), "gala_maldita");
    public static final NamespacedKey KEY_EFFECTS    = new NamespacedKey(GalaIALore.getInstance(), "gala_efectos");

    private static final String TAG_REGEX = "<[^>]+>|&[0-9a-fk-orA-FK-OR]|\\[[^\\]]+\\]";
    private static final Gson   GSON      = new Gson();

    // ── API pública ───────────────────────────────────────────────────────

    public static ItemStack createItemStack(GeneratedItemData data) {
        return data.isIdentificado()
                ? buildIdentifiedItem(data)
                : buildUnidentifiedItem(data);
    }

    public static double  getItemValue(ItemStack item)  {
        if (item == null || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(KEY_VALUE, PersistentDataType.DOUBLE, 0.0);
    }

    public static boolean isAIItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_AI, PersistentDataType.BOOLEAN);
    }

    public static boolean isIdentified(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(KEY_IDENTIFIED, PersistentDataType.BOOLEAN, false);
    }

    /**
     * Lee los datos completos almacenados en el NBT del ítem no identificado
     * y devuelve el GeneratedItemData. Devuelve null si el ítem no tiene datos.
     */
    public static GeneratedItemData readDataFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String json = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_DATA, PersistentDataType.STRING);
        if (json == null) return null;
        return GSON.fromJson(json, GeneratedItemData.class);
    }

    // ── Construcción: ítem no identificado ───────────────────────────────

    private static ItemStack buildUnidentifiedItem(GeneratedItemData data) {
        // Material: usamos el real pero el jugador no sabe de qué es
        Material mat = resolveMaterial(data);
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String rarezaNorm = normalizeRarity(data.getRareza());
        TextColor rarityColor = parseColor(
                GalaIALore.getInstance().getConfig().getString("rarities." + rarezaNorm + ".color", "<gray>"));

        // Nombre: misterioso
        // Nombre: misterioso (desde config)
        String unidentifiedName = GalaIALore.getInstance().getConfig().getString("formatting.unidentified_name", "&k??&6 Reliquia Desconocida &k??");
        meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(unidentifiedName)
                .decoration(TextDecoration.ITALIC, false));

        // Lore: mostrar rareza (?), materiales requeridos y mensaje de identificación
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rareza: ???").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Esta reliquia aguarda ser identificada.")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Materiales para identificar:")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        if (data.getMaterialesIdentificacion() != null) {
            for (GeneratedItemData.MaterialRequerido req : data.getMaterialesIdentificacion()) {
                String nombre = formatMaterialName(req.getMaterial());
                lore.add(Component.text("  • " + req.getCantidad() + "x " + nombre)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Usa /identificar para revelarla.")
                .color(NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        // NBT: guardar datos completos y flags
        double value = calculateValue(data);
        meta.getPersistentDataContainer().set(KEY_AI,        PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(KEY_IDENTIFIED, PersistentDataType.BOOLEAN, false);
        meta.getPersistentDataContainer().set(KEY_VALUE,     PersistentDataType.DOUBLE, value);
        meta.getPersistentDataContainer().set(KEY_DATA,      PersistentDataType.STRING, GSON.toJson(data));

        item.setItemMeta(meta);
        return item;
    }

    // ── Construcción: ítem identificado ──────────────────────────────────

    private static ItemStack buildIdentifiedItem(GeneratedItemData data) {
        Material mat  = resolveMaterial(data);
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        double    value      = calculateValue(data);
        String    rarezaNorm = normalizeRarity(data.getRareza());
        TextColor color      = parseColor(
                GalaIALore.getInstance().getConfig().getString("rarities." + rarezaNorm + ".color", "<gray>"));

        // Nombre
        String nombre = strip(data.getNombre() != null ? data.getNombre() : "Reliquia Desconocida");
        meta.displayName(Component.text(nombre).color(color).decoration(TextDecoration.ITALIC, false));

        // Lore
        int wrapAt = GalaIALore.getInstance().getConfig().getInt("formatting.lore_max_chars_per_line", 40);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rareza: " + rarezaNorm).color(color).decoration(TextDecoration.ITALIC, false));
        
        if (data.isMaldita()) {
            lore.add(Component.text("✦ Reliquia Maldita ✦").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        }
        
        lore.add(Component.empty());

        if (data.getLore() != null) {
            String desc = strip(String.join(" ", data.getLore())).trim();
            if (desc.length() > 200) {
                int cut = desc.lastIndexOf(' ', 197);
                desc = (cut > 0 ? desc.substring(0, cut) : desc.substring(0, 197)) + "...";
            }
            for (String line : wrapText(desc, wrapAt)) {
                lore.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Valor estimado: " + value + "$")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // NBT
        meta.getPersistentDataContainer().set(KEY_AI,        PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(KEY_IDENTIFIED, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(KEY_VALUE,     PersistentDataType.DOUBLE, value);
        
        if (data.isMaldita()) {
            meta.getPersistentDataContainer().set(KEY_CURSED, PersistentDataType.BOOLEAN, true);
            if (data.getEfectosNegativos() != null && !data.getEfectosNegativos().isEmpty()) {
                String effectsJson = GSON.toJson(data.getEfectosNegativos());
                meta.getPersistentDataContainer().set(KEY_EFFECTS, PersistentDataType.STRING, effectsJson);
            }
        }

        // Libro
        if (meta instanceof BookMeta bookMeta) {
            buildBook(bookMeta, data, nombre);
        } else {
            applyEnchantments(meta, data);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ── Libros ────────────────────────────────────────────────────────────

    private static void buildBook(BookMeta bookMeta, GeneratedItemData data, String titulo) {
        bookMeta.setTitle(titulo);
        bookMeta.setAuthor(data.getAutor() != null ? strip(data.getAutor()) : "Desconocido");

        int charsPerLine = GalaIALore.getInstance().getConfig().getInt("formatting.book_max_chars_per_line", 19);
        int linesPerPage = GalaIALore.getInstance().getConfig().getInt("formatting.book_max_lines_per_page", 14);

        List<String> rawPages = data.getPaginas();
        if (rawPages == null || rawPages.isEmpty()) {
            rawPages = data.getLore() != null ? data.getLore() : List.of("...");
        }

        for (String rawPage : rawPages) {
            String pageText = strip(rawPage).trim();
            if (pageText.isEmpty()) continue;
            List<String> lines = wrapText(pageText, charsPerLine);
            StringBuilder page = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                page.append(line).append("\n");
                if (++count >= linesPerPage) {
                    bookMeta.addPages(Component.text(page.toString().trim()));
                    page  = new StringBuilder();
                    count = 0;
                }
            }
            if (!page.isEmpty()) bookMeta.addPages(Component.text(page.toString().trim()));
        }
    }

    // ── Encantamientos ─────────────────────────────────────────────────────

    private static void applyEnchantments(ItemMeta meta, GeneratedItemData data) {
        if (data.getEncantamientos() == null) return;
        for (GeneratedItemData.EncantamientoData enc : data.getEncantamientos()) {
            if (enc.getId() == null) continue;
            try {
                String id = enc.getId().toLowerCase().replace("minecraft:", "");
                Enchantment e = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
                if (e != null) meta.addEnchant(e, enc.getNivel(), true); // true = ignora límite vanilla
            } catch (Exception ignored) {}
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private static Material resolveMaterial(GeneratedItemData data) {
        if ("LIBRO".equalsIgnoreCase(data.getTipo())) return Material.WRITTEN_BOOK;
        Material m = data.getMaterialSugerido() != null
                ? Material.matchMaterial(data.getMaterialSugerido().toUpperCase()) : null;
        return m != null ? m : Material.PAPER;
    }

    private static double calculateValue(GeneratedItemData data) {
        String key    = normalizeRarity(data.getRareza());
        double base   = GalaIALore.getInstance().getConfig().getDouble("economy.base_values." + key, 100.0);
        double bonus  = GalaIALore.getInstance().getConfig().getDouble("economy.enchantment_value_per_level", 500.0);
        double extra  = 0;
        if (data.getEncantamientos() != null)
            for (GeneratedItemData.EncantamientoData e : data.getEncantamientos())
                extra += e.getNivel() * bonus;
        double variation = GalaIALore.getInstance().getConfig().getDouble("economy.price_variation", 0.15);
        double factor = 1.0 + (new java.util.Random().nextDouble() * 2 - 1) * variation; // entre (1-v) y (1+v)
        
        return Math.round((base + extra) * factor * 100.0) / 100.0;
    }

    private static String normalizeRarity(String r) {
        if (r == null) return "COMUN";
        return r.toUpperCase()
                .replace("RARA", "RARO").replace("EPICA", "EPICO")
                .replace("LEGENDARIA", "LEGENDARIO").replace("COMUNA", "COMUN");
    }

    private static TextColor parseColor(String tag) {
        return switch (tag.replace("<","").replace(">","").toLowerCase()) {
            case "gold"          -> NamedTextColor.GOLD;
            case "purple", "light_purple" -> NamedTextColor.LIGHT_PURPLE;
            case "blue"          -> NamedTextColor.BLUE;
            case "aqua"          -> NamedTextColor.AQUA;
            case "green"         -> NamedTextColor.GREEN;
            case "yellow"        -> NamedTextColor.YELLOW;
            case "red"           -> NamedTextColor.RED;
            default              -> NamedTextColor.GRAY;
        };
    }

    private static String strip(String text) {
        return text == null ? "" : text.replaceAll(TAG_REGEX, "").trim();
    }

    /** Convierte "IRON_INGOT" → "Lingote de Hierro" usando un mapa de traducción. */
    private static String formatMaterialName(String material) {
        if (material == null) return "?";
        String upper = material.toUpperCase();
        
        // Mapa de traducciones comunes
        switch (upper) {
            case "IRON_INGOT": return "Lingote de Hierro";
            case "GOLD_INGOT": return "Lingote de Oro";
            case "NETHERITE_INGOT": return "Lingote de Netherite";
            case "DIAMOND": return "Diamante";
            case "EMERALD": return "Esmeralda";
            case "LAPIS_LAZULI": return "Lapislázuli";
            case "COAL": return "Carbón";
            case "PAPER": return "Papel";
            case "BOOK": return "Libro";
            case "FEATHER": return "Pluma";
            case "INK_SAC": return "Saco de Tinta";
            case "BLAZE_POWDER": return "Polvo de Blaze";
            case "BLAZE_ROD": return "Vara de Blaze";
            case "GHAST_TEAR": return "Lágrima de Ghast";
            case "NETHER_STAR": return "Estrella del Nether";
            case "ANCIENT_DEBRIS": return "Escombros Ancestrales";
            case "ENDER_PEARL": return "Perla de Ender";
            case "OBSIDIAN": return "Obsidiana";
            case "STICK": return "Palo";
            case "STRING": return "Hilo";
            case "LEATHER": return "Cuero";
            case "GOLD_NUGGET": return "Pepita de Oro";
            case "IRON_NUGGET": return "Pepita de Hierro";
            case "GLOWSTONE_DUST": return "Polvo de Piedra Luminosa";
            case "QUARTZ": return "Cuarzo del Nether";
            case "BONE": return "Hueso";
            case "GUNPOWDER": return "Pólvora";
            case "MAGMA_CREAM": return "Crema de Magma";
            case "PRISMARINE_SHARD": return "Fragmento de Prismarina";
            case "PRISMARINE_CRYSTALS": return "Cristales de Prismarina";
            case "PHANTOM_MEMBRANE": return "Membrana de Fantasma";
            case "SCUTE": return "Escama de Tortuga";
            case "NETHER_BRICK": return "Ladrillo del Nether";
            case "FLINT": return "Pedernal";
            case "CLAY_BALL": return "Bola de Arcilla";
            case "SLIME_BALL": return "Bola de Slime";
            case "EGG": return "Huevo";
            case "APPLE": return "Manzana";
        }

        // Fallback genérico
        return upper.replace("_", " ").toLowerCase()
                .substring(0, 1).toUpperCase()
                + upper.replace("_", " ").toLowerCase().substring(1);
    }

    private static List<String> wrapText(String text, int limit) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() + word.length() + 1 > limit) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                if (!cur.isEmpty()) cur.append(' ');
                cur.append(word);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }
}
