package com.vitalisnw.galalore.utils;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class MaterialUtils {

    private static final Map<String, Material> MAPPINGS = new HashMap<>();

    static {
        // Papelería y Conocimiento
        add("PAPEL", Material.PAPER);
        add("PAPER", Material.PAPER);
        add("PLUMA", Material.FEATHER);
        add("FEATHER", Material.FEATHER);
        add("LIBRO", Material.BOOK);
        add("BOOK", Material.BOOK);
        add("TINTA", Material.INK_SAC);
        add("SACO DE TINTA", Material.INK_SAC);
        add("INK_SAC", Material.INK_SAC);
        
        // Minerales y Metales
        add("HIERRO", Material.IRON_INGOT);
        add("LINGOTE DE HIERRO", Material.IRON_INGOT);
        add("IRON_INGOT", Material.IRON_INGOT);
        add("ORO", Material.GOLD_INGOT);
        add("LINGOTE DE ORO", Material.GOLD_INGOT);
        add("GOLD_INGOT", Material.GOLD_INGOT);
        add("DIAMANTE", Material.DIAMOND);
        add("DIAMOND", Material.DIAMOND);
        add("ESMERALDA", Material.EMERALD);
        add("EMERALD", Material.EMERALD);
        add("LAPISLAZULI", Material.LAPIS_LAZULI);
        add("LAPIS LAZULI", Material.LAPIS_LAZULI);
        add("LAPIS_LAZULI", Material.LAPIS_LAZULI);
        add("CARBON", Material.COAL);
        add("COAL", Material.COAL);
        add("NETHERITE", Material.NETHERITE_INGOT);
        add("LINGOTE DE NETHERITE", Material.NETHERITE_INGOT);
        add("NETHERITE_INGOT", Material.NETHERITE_INGOT);
        add("FRAGMENTO DE NETHERITE", Material.NETHERITE_SCRAP);
        add("NETHERITE_SCRAP", Material.NETHERITE_SCRAP);
        add("ANCIENT DEBRIS", Material.ANCIENT_DEBRIS);
        add("DEBRIS", Material.ANCIENT_DEBRIS);

        // Combate y Utilidad
        add("CUERDA", Material.STRING);
        add("HILO", Material.STRING);
        add("STRING", Material.STRING);
        add("CUERO", Material.LEATHER);
        add("LEATHER", Material.LEATHER);
        add("PALO", Material.STICK);
        add("STICK", Material.STICK);
        add("PEDERNAL", Material.FLINT);
        add("FLINT", Material.FLINT);

        // Magia y Rarezas
        add("POLVO DE BLAZE", Material.BLAZE_POWDER);
        add("BLAZE_POWDER", Material.BLAZE_POWDER);
        add("VARA DE BLAZE", Material.BLAZE_ROD);
        add("VARITA DE BLAZE", Material.BLAZE_ROD);
        add("BLAZE_ROD", Material.BLAZE_ROD);
        add("LAGRIMA DE GHAST", Material.GHAST_TEAR);
        add("GHAST_TEAR", Material.GHAST_TEAR);
        add("ESTRELLA DEL NETHER", Material.NETHER_STAR);
        add("NETHER_STAR", Material.NETHER_STAR);
        add("TOTEM", Material.TOTEM_OF_UNDYING);
        add("TOTEM_OF_UNDYING", Material.TOTEM_OF_UNDYING);

        // Bloques y Otros
        add("PIEDRA", Material.STONE);
        add("ROCA", Material.COBBLESTONE);
        add("PIEDRA DEL END", Material.END_STONE);
        add("PIEDRA DE ENDER", Material.END_STONE);
        add("PIEDRA ENDER", Material.END_STONE);
        add("END_STONE", Material.END_STONE);
        add("PERLA DE ENDER", Material.ENDER_PEARL);
        add("PERLA DE END", Material.ENDER_PEARL);
        add("ENDER_PEARL", Material.ENDER_PEARL);
        add("OJO DE ENDER", Material.ENDER_EYE);
        add("ENDER_EYE", Material.ENDER_EYE);
        add("OBSIDIANA", Material.OBSIDIAN);
        add("OBSIDIAN", Material.OBSIDIAN);
        add("CUARZO", Material.QUARTZ);
        add("QUARTZ", Material.QUARTZ);
        add("SLIME", Material.SLIME_BALL);
        add("BOLA DE SLIME", Material.SLIME_BALL);
    }

    private static void add(String name, Material mat) {
        MAPPINGS.put(name.toUpperCase(), mat);
    }

    public static Material match(String input) {
        if (input == null || input.isEmpty()) return null;
        
        String normalized = input.toUpperCase().trim()
                .replace("Á", "A").replace("É", "E")
                .replace("Í", "I").replace("Ó", "O")
                .replace("Ú", "U")
                .replace(" ", "_");

        // 1. Intento directo con el mapa
        Material m = MAPPINGS.get(normalized.replace("_", " "));
        if (m != null) return m;

        // 2. Intento con guiones bajos
        m = MAPPINGS.get(normalized);
        if (m != null) return m;

        // 3. Intento directo con Bukkit
        return Material.matchMaterial(normalized);
    }
}
