package com.vitalisnw.galalore.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.vitalisnw.galalore.GalaIALore;
import com.vitalisnw.galalore.models.GeneratedItemData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gestiona la comunicación con Ollama.
 * - Un único hilo para no saturar la GPU local.
 * - Hasta 3 reintentos con backoff exponencial por si Ollama falla.
 * - Prompt preciso: netherite, encantamientos sobre el máximo vanilla, 1 maldición,
 *   materiales de identificación temáticos.
 */
public class OllamaManager {

    private static final String TAG_REGEX  = "<[^>]+>|&[0-9a-fk-orA-FK-OR]|\\[[^\\]]+\\]";
    private static final String PAGE_REGEX = "(?i)^\\s*(P[aá]gina|Cap[íi]tulo|Page|Chapter)\\s*\\d+[:\\-\\.\\s]*";

    private static final int  MAX_RETRIES   = 3;
    private static final long RETRY_BASE_MS = 5000L; // 5 s, 10 s, 20 s

    private final Gson           gson       = new Gson();
    private final HttpClient     httpClient;
    private final ExecutorService executor  = Executors.newSingleThreadExecutor();
    private final java.util.Random random   = new java.util.Random();

    public OllamaManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // ── API pública ───────────────────────────────────────────────────────

    public CompletableFuture<GeneratedItemData> generateItemData(
            String context, boolean isBook, String rareza, String material) {

        return CompletableFuture.supplyAsync(() -> {
            // 1. Pre-generar datos técnicos (Plugin Control)
            GeneratedItemData data = preGenerateTechnicalData(rareza, isBook, material);
            
            int maxRetries = GalaIALore.getInstance().getConfig().getInt("ollama.max_retries", 3);
            long retryMs = GalaIALore.getInstance().getConfig().getLong("ollama.retry_delay_ms", 5000L);

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    String response = doRequest(data, context, attempt);
                    if (response != null) {
                        fillAIContent(data, response);
                        sanitize(data);
                        return data;
                    }
                } catch (Exception e) {
                    GalaIALore.getInstance().getLogger().warning(
                            "[Gala-IA-LORE] Intento " + attempt + "/" + maxRetries + " falló: " + e.getMessage());
                }
                if (attempt < maxRetries) {
                    try { Thread.sleep(retryMs * attempt); } catch (InterruptedException ignored) {}
                }
            }
            
            // Fallback: Si falla la IA, al menos tenemos un item con datos técnicos básicos
            fillFallbackAIContent(data);
            return data;
        }, executor);
    }

    // ── Lógica principal ──────────────────────────────────────────────────

    private String doRequest(GeneratedItemData data, String context, int attempt) throws Exception {
        String apiUrl   = GalaIALore.getInstance().getConfig().getString("ollama.url", "http://127.0.0.1:11434/api/chat");
        String model    = GalaIALore.getInstance().getConfig().getString("ollama.model", "llama3.2");
        double temp     = GalaIALore.getInstance().getConfig().getDouble("ollama.temperature", 0.8);
        String baseRole = GalaIALore.getInstance().getConfig().getString("prompts.base_context", "");

        String prompt = data.getTipo().equals("LIBRO") 
                ? buildBookPrompt(baseRole, context, data.getRareza())
                : buildItemPrompt(data, context);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.addProperty("format", "json");
        
        JsonObject opts = new JsonObject();
        opts.addProperty("temperature", temp);
        
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);

        body.add("messages", messages);
        body.add("options", opts);

        int timeout = GalaIALore.getInstance().getConfig().getInt("ollama.timeout_seconds", 30);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        GalaIALore.getInstance().getLogger().info(
                "[Gala-IA-LORE] Consultando IA para " + data.getTipo() + " [" + data.getRareza() + "] (intento " + attempt + ")...");
        
        HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonObject resp  = JsonParser.parseString(response.body()).getAsJsonObject();
        return resp.getAsJsonObject("message").get("content").getAsString();
    }

    private GeneratedItemData preGenerateTechnicalData(String rareza, boolean isBook, String material) {
        GeneratedItemData data = new GeneratedItemData();
        data.setRareza(rareza.toUpperCase());
        data.setTipo(isBook ? "LIBRO" : "ITEM");
        data.setIdentificado(false);

        if (!isBook) {
            data.setMaterialSugerido(material);
            data.setEncantamientos(generateRandomEnchants(material, rareza));
            data.setMaterialesIdentificacion(generateRandomIdMaterials(rareza));
            
            if (rareza.equalsIgnoreCase("LEGENDARIO")) {
                data.setMaldita(true);
                data.setEfectosNegativos(List.of(getRandomMaldicionEffect()));
            }
        } else {
            data.setAutor("Antiguo Erudito");
            data.setMaterialesIdentificacion(generateRandomIdMaterials(rareza));
        }
        return data;
    }

    private void fillAIContent(GeneratedItemData data, String json) {
        try {
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start == -1 || end == -1) return;
            String cleanJson = json.substring(start, end + 1);

            JsonObject obj = JsonParser.parseString(cleanJson).getAsJsonObject();
            if (obj.has("nombre")) data.setNombre(obj.get("nombre").getAsString());
            if (obj.has("lore")) {
                List<String> lore = new ArrayList<>();
                if (obj.get("lore").isJsonArray()) {
                    obj.getAsJsonArray("lore").forEach(e -> lore.add(e.getAsString()));
                } else {
                    lore.add(obj.get("lore").getAsString());
                }
                data.setLore(lore);
            }
            if (data.getTipo().equals("LIBRO") && obj.has("paginas")) {
                List<String> paginas = new ArrayList<>();
                obj.getAsJsonArray("paginas").forEach(e -> paginas.add(e.getAsString()));
                data.setPaginas(paginas);
            }
        } catch (Exception e) {
            GalaIALore.getInstance().getLogger().warning("Error parseando JSON de la IA: " + e.getMessage());
        }
    }

    private void fillFallbackAIContent(GeneratedItemData data) {
        data.setNombre("Reliquia Antigua de " + data.getRareza());
        data.setLore(List.of("Una reliquia cuyo origen se ha perdido en el tiempo."));
        if (data.getTipo().equals("LIBRO")) {
            data.setPaginas(List.of("Las páginas están en blanco o son ilegibles..."));
        }
    }

    private String getRandomMaldicionEffect() {
        String[] effects = {"SLOWNESS:1", "HUNGER:1", "WEAKNESS:1", "MINING_FATIGUE:1"};
        return effects[random.nextInt(effects.length)];
    }

    private String buildItemPrompt(GeneratedItemData data, String context) {
        String base = GalaIALore.getInstance().getConfig().getString("prompts.base_context", "");
        String itemContext = GalaIALore.getInstance().getConfig().getString("prompts.item_context", "");
        String materialFriendly = data.getMaterialSugerido().replace("_", " ").toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append(base).append("\n");
        sb.append(itemContext).append("\n\n");
        sb.append("=== DATOS TÉCNICOS (NO CAMBIAR) ===\n");
        sb.append("- Objeto: ").append(materialFriendly).append("\n");
        sb.append("- Rareza: ").append(data.getRareza()).append("\n");
        
        if (data.getEncantamientos() != null && !data.getEncantamientos().isEmpty()) {
            sb.append("- Encantamientos incluidos: ");
            data.getEncantamientos().forEach(e -> sb.append(e.getId().replace("minecraft:", "")).append(" ").append(e.getNivel()).append(", "));
            sb.append("\n");
        }

        if (data.getMaterialesIdentificacion() != null && !data.getMaterialesIdentificacion().isEmpty()) {
            sb.append("- Requisitos de identificación: ");
            data.getMaterialesIdentificacion().forEach(m -> sb.append(m.getCantidad()).append("x ").append(m.getMaterial()).append(", "));
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("=== TU TAREA ===\n");
        sb.append("- Inventa un NOMBRE épico en español que encaje con el objeto y sus encantamientos.\n");
        sb.append("- Escribe un LORE (una frase breve) que cuente la historia de este objeto.\n");
        sb.append("- Contexto extra: ").append(context).append("\n\n");

        sb.append("=== FORMATO DE RESPUESTA ===\n");
        sb.append("Devuelve ÚNICAMENTE este JSON:\n");
        sb.append("{\n");
        sb.append("  \"nombre\": \"Nombre Épico\",\n");
        sb.append("  \"lore\": [\"Lore épico breve\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String buildBookPrompt(String base, String context, String rareza) {
        String bookContext = GalaIALore.getInstance().getConfig().getString("prompts.book_context." + rareza.toUpperCase());
        if (bookContext == null) bookContext = "Un libro antiguo con fragmentos de historia.";

        StringBuilder sb = new StringBuilder();
        sb.append(base).append("\n");
        sb.append(bookContext).append("\n\n");
        sb.append("=== TAREA ===\n");
        sb.append("Genera un LIBRO de rareza ").append(rareza).append(" para el mundo de VitalisNW.\n");
        sb.append("Contexto extra: ").append(context).append("\n\n");
        sb.append("=== REQUISITOS ===\n");
        sb.append("- Escribe una historia de 3 a 5 páginas.\n");
        sb.append("- Cada página es un párrafo narrativo (300-600 caracteres).\n");
        sb.append("- El 'nombre' es el título del libro.\n");
        sb.append("- El 'lore' es una frase corta de presentación.\n\n");
        sb.append("=== FORMATO DE RESPUESTA ===\n");
        sb.append("{\n");
        sb.append("  \"nombre\": \"Título del Libro\",\n");
        sb.append("  \"lore\": [\"Breve descripción\"],\n");
        sb.append("  \"paginas\": [\"Página 1...\", \"Página 2...\"]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ── Limpieza de datos ─────────────────────────────────────────────────

    private void sanitize(GeneratedItemData data) {
        if (data.getNombre() != null) {
            data.setNombre(data.getNombre().replaceAll(TAG_REGEX, "").trim());
        }

        // Normalizar materiales de identificación (IA a veces responde en español)
        if (data.getMaterialesIdentificacion() != null) {
            for (GeneratedItemData.MaterialRequerido req : data.getMaterialesIdentificacion()) {
                if (req.getMaterial() != null) {
                    req.setMaterial(normalizeMaterialName(req.getMaterial()));
                }
            }
        }

        // Lore: unir, limpiar y recortar a 200 chars
        if (data.getLore() != null) {
            StringBuilder sb = new StringBuilder();
            for (String l : data.getLore()) {
                if (l != null && !l.isBlank()) sb.append(l.replaceAll(TAG_REGEX, "").trim()).append(" ");
            }
            String text = sb.toString().trim();
            if (text.length() > 200) {
                int cut = text.lastIndexOf(' ', 197);
                text = (cut > 0 ? text.substring(0, cut) : text.substring(0, 197)) + "...";
            }
            data.setLore(List.of(text));
        }

        // Páginas: eliminar cabeceras de página
        if (data.getPaginas() != null) {
            List<String> clean = new ArrayList<>();
            for (String p : data.getPaginas()) {
                if (p == null || p.isBlank()) continue;
                String cp = p.trim();
                while (cp.matches(PAGE_REGEX + ".*")) {
                    cp = cp.replaceFirst(PAGE_REGEX, "").trim();
                }
                cp = cp.replaceAll(TAG_REGEX, "").trim();
                if (!cp.isEmpty()) clean.add(cp);
            }
            data.setPaginas(clean);
        }

        // Validar encantamientos según material
        if (data.getEncantamientos() != null && data.getMaterialSugerido() != null) {
            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(data.getMaterialSugerido().toUpperCase());
            if (mat != null) {
                org.bukkit.inventory.ItemStack temp = new org.bukkit.inventory.ItemStack(mat);
                List<GeneratedItemData.EncantamientoData> valid = new ArrayList<>();
                Set<String> categories = new java.util.HashSet<>();

                for (GeneratedItemData.EncantamientoData encData : data.getEncantamientos()) {
                    if (encData.getId() == null) continue;
                    String id = encData.getId().toLowerCase().replace("minecraft:", "");
                    
                    // FILTRAR MALDICIONES (no queremos encantamientos negativos en la pool)
                    if (id.contains("curse")) continue;

                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(id);
                    org.bukkit.enchantments.Enchantment e = org.bukkit.Registry.ENCHANTMENT.get(key);
                    
                    if (e != null) {
                        // 1. Verificar si el material acepta el encantamiento
                        if (e.canEnchantItem(temp) || isSpecialCompatibility(mat, e)) {
                            // 2. Verificar exclusividad (Sharpness vs Smite, etc)
                            String category = getEnchantmentCategory(id);
                            if (category == null || categories.add(category)) {
                                // 3. CAPAR NIVEL SEGÚN RAREZA
                                int cappedLevel = getCappedLevel(data.getRareza(), id, encData.getNivel(), e.getMaxLevel());
                                encData.setNivel(cappedLevel);
                                valid.add(encData);
                            }
                        }
                    }
                }
                data.setEncantamientos(valid);
            }
        }

        // Fallback: si la IA no devolvió materiales de identificación, asignar por defecto
        if (data.getMaterialesIdentificacion() == null || data.getMaterialesIdentificacion().isEmpty()) {
            GalaIALore.getInstance().getLogger().warning(
                    "[Gala-IA-LORE] La IA no generó materiales de identificación para '" + data.getNombre()
                    + "'. Asignando materiales por defecto.");
            String rareza = data.getRareza() != null ? data.getRareza().toUpperCase() : "COMUN";
            data.setMaterialesIdentificacion(defaultMaterials(rareza, data.getTipo()));
        }
    }

    /** Capa el nivel de un encantamiento según la rareza del objeto */
    private int getCappedLevel(String rareza, String id, int suggested, int vanillaMax) {
        String r = rareza != null ? rareza.toUpperCase() : "COMUN";
        
        int limit = switch (r) {
            case "LEGENDARIO" -> vanillaMax + 1;
            case "EPICO"      -> vanillaMax;
            case "RARO"       -> Math.max(1, vanillaMax - 1);
            default           -> Math.max(1, vanillaMax / 2);
        };

        // Casos especiales (mending, silk_touch siempre son 1)
        if (vanillaMax == 1) return 1;

        return Math.min(suggested, limit);
    }


    /** Casos especiales donde canEnchantItem falla pero queremos permitirlo (ej: hachas con Sharpness) */
    private boolean isSpecialCompatibility(org.bukkit.Material mat, org.bukkit.enchantments.Enchantment e) {
        String matName = mat.name();
        String encName = e.getKey().getKey();
        
        if (matName.contains("_AXE")) {
            return encName.equals("sharpness") || encName.equals("smite") || encName.equals("bane_of_arthropods") || encName.equals("cleaving");
        }
        return false;
    }

    private List<GeneratedItemData.EncantamientoData> generateRandomEnchants(String material, String rareza) {
        List<GeneratedItemData.EncantamientoData> list = new ArrayList<>();
        String category = getCategoryByMaterial(material);
        if (category == null) return list;

        List<String> pool = GalaIALore.getInstance().getConfig().getStringList("enchantments." + category);
        if (pool == null || pool.isEmpty()) return list;

        int count = switch (rareza.toUpperCase()) {
            case "LEGENDARIO" -> 4 + random.nextInt(2);
            case "EPICO"      -> 3 + random.nextInt(2);
            case "RARO"       -> 2 + random.nextInt(2);
            default           -> 1 + random.nextInt(2);
        };

        List<String> shuffPool = new ArrayList<>(pool);
        Collections.shuffle(shuffPool);
        Set<String> categories = new HashSet<>();

        for (String enchantId : shuffPool) {
            if (list.size() >= count) break;
            
            String cat = getEnchantmentCategory(enchantId);
            if (cat == null || categories.add(cat)) {
                GeneratedItemData.EncantamientoData ed = new GeneratedItemData.EncantamientoData();
                ed.setId("minecraft:" + enchantId);
                
                int vanillaMax = getVanillaMax(enchantId);
                int level = getCappedLevel(rareza, enchantId, vanillaMax, vanillaMax);
                ed.setNivel(level);
                list.add(ed);
            }
        }
        return list;
    }

    private String getCategoryByMaterial(String material) {
        String m = material.toUpperCase();
        if (m.contains("_SWORD")) return "ESPADAS";
        if (m.contains("_AXE")) return "HACHAS";
        if (m.contains("_PICKAXE") || m.contains("_SHOVEL") || m.contains("_HOE")) return "PICOS_PALAS";
        if (m.contains("BOW")) return "ARCOS";
        if (m.contains("CROSSBOW")) return "BALLESTAS";
        if (m.contains("CHESTPLATE") || m.contains("LEGGINGS")) return "ARMADURAS";
        if (m.contains("HELMET")) return "CASCOS";
        if (m.contains("BOOTS")) return "BOTAS";
        if (m.contains("TRIDENT")) return "TRIDENTE";
        return null;
    }

    private int getVanillaMax(String enchantId) {
        return switch (enchantId.toLowerCase()) {
            case "sharpness", "efficiency", "power", "protection" -> 5;
            case "fortune", "looting", "fire_aspect", "unbreaking" -> 3;
            case "knockback", "punch", "respiration" -> 2;
            default -> 1;
        };
    }

    private List<GeneratedItemData.MaterialRequerido> generateRandomIdMaterials(String rareza) {
        List<GeneratedItemData.MaterialRequerido> list = new ArrayList<>();
        List<String> pool = GalaIALore.getInstance().getConfig().getStringList("identification_materials." + rareza.toUpperCase());
        if (pool == null || pool.isEmpty()) pool = List.of("PAPER", "IRON_INGOT");

        int count = 2 + random.nextInt(2);
        List<String> shuffPool = new ArrayList<>(pool);
        Collections.shuffle(shuffPool);
        
        for (int i = 0; i < count && i < shuffPool.size(); i++) {
            list.add(mat(shuffPool.get(i), 1 + random.nextInt(10)));
        }
        return list;
    }



    private String getEnchantmentCategory(String id) {
        id = id.toLowerCase().replace("minecraft:", "");
        if (id.equals("sharpness") || id.equals("smite") || id.equals("bane_of_arthropods") || id.equals("cleaving")) return "damage";
        if (id.equals("protection") || id.equals("fire_protection") || id.equals("blast_protection") || id.equals("projectile_protection")) return "protection";
        if (id.equals("fortune") || id.equals("silk_touch")) return "mining_bonus";
        if (id.equals("infinity") || id.equals("mending")) return "infinite_repair";
        if (id.equals("loyalty") || id.equals("riptide")) return "trident_movement";
        if (id.equals("multishot") || id.equals("piercing")) return "crossbow_projectile";
        if (id.equals("depth_strider") || id.equals("frost_walker")) return "boots_water";
        return null;
    }


    /** Materiales de identificación por defecto si la IA los omite. */
    private List<GeneratedItemData.MaterialRequerido> defaultMaterials(String rareza, String tipo) {
        List<GeneratedItemData.MaterialRequerido> list = new ArrayList<>();
        if ("LIBRO".equalsIgnoreCase(tipo)) {
            list.add(mat("PAPER",   10));
            list.add(mat("FEATHER",  3));
            list.add(mat("INK_SAC",  5));
        } else {
            switch (rareza) {
                case "LEGENDARIO" -> { list.add(mat("NETHERITE_INGOT", 1)); list.add(mat("BLAZE_POWDER", 5)); }
                case "EPICO"      -> { list.add(mat("DIAMOND", 2)); list.add(mat("ENDER_PEARL", 3)); }
                case "RARO"       -> { list.add(mat("IRON_INGOT", 4)); list.add(mat("GOLD_INGOT", 2)); }
                default           -> { list.add(mat("PAPER", 5)); list.add(mat("STICK", 8)); }
            }
        }
        return list;
    }

    /** Convierte nombres en español o variantes a IDs de Material de Bukkit. */
    private String normalizeMaterialName(String input) {
        org.bukkit.Material m = com.vitalisnw.galalore.utils.MaterialUtils.match(input);
        return m != null ? m.name() : (input != null ? input.toUpperCase() : "PAPER");
    }

    private GeneratedItemData.MaterialRequerido mat(String material, int cantidad) {
        GeneratedItemData.MaterialRequerido m = new GeneratedItemData.MaterialRequerido();
        m.setMaterial(material);
        m.setCantidad(cantidad);
        return m;
    }
}
