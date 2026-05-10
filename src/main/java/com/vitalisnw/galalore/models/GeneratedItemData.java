package com.vitalisnw.galalore.models;

import java.util.List;

/**
 * Modelo de datos de una reliquia generada por la IA.
 * Puede estar en estado "no identificado" (aparece como Reliquia Desconocida)
 * o "identificado" (muestra nombre, lore y valor completos).
 */
public class GeneratedItemData {

    // ── Campos comunes ─────────────────────────────────────────────────────
    private String tipo;       // "ITEM" o "LIBRO"
    private String nombre;
    private List<String> lore;
    private String rareza;     // COMUN, RARO, EPICO, LEGENDARIO
    private boolean identificado = false; // false = Reliquia Desconocida
    private boolean maldita = false;      // true = Reliquia Maldita
    private List<String> efectos_negativos; // ej: ["SLOWNESS:1", "HUNGER:1"]

    // ── Solo para ITEM ─────────────────────────────────────────────────────
    private String material_sugerido;
    private List<EncantamientoData> encantamientos;
    private List<MaterialRequerido> materiales_identificacion; // Max 3

    // ── Solo para LIBRO ────────────────────────────────────────────────────
    private String autor;
    private List<String> paginas;

    // ── Cache en memoria (no se serializa) ─────────────────────────────────
    private transient org.bukkit.inventory.ItemStack builtItem;

    /** Construye o devuelve del caché el ItemStack de Minecraft. */
    public org.bukkit.inventory.ItemStack getPrebuiltItem() {
        if (builtItem == null) {
            builtItem = com.vitalisnw.galalore.utils.ItemFactory.createItemStack(this);
        }
        return builtItem;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getTipo()                              { return tipo; }
    public void   setTipo(String t)                      { this.tipo = t; }

    public String getNombre()                            { return nombre; }
    public void   setNombre(String n)                    { this.nombre = n; }

    public List<String> getLore()                        { return lore; }
    public void         setLore(List<String> l)          { this.lore = l; }

    public String getRareza()                            { return rareza; }
    public void   setRareza(String r)                    { this.rareza = r; }

    public boolean isIdentificado()                      { return identificado; }
    public void    setIdentificado(boolean v)            { this.identificado = v; builtItem = null; }

    public boolean isMaldita()                           { return maldita; }
    public void    setMaldita(boolean v)                 { this.maldita = v; }

    public List<String> getEfectosNegativos()            { return efectos_negativos; }
    public void         setEfectosNegativos(List<String> e){ this.efectos_negativos = e; }

    public String getMaterialSugerido()                  { return material_sugerido; }
    public void   setMaterialSugerido(String m)          { this.material_sugerido = m; }

    public List<EncantamientoData> getEncantamientos()               { return encantamientos; }
    public void setEncantamientos(List<EncantamientoData> lista)     { this.encantamientos = lista; }

    public List<MaterialRequerido> getMaterialesIdentificacion()          { return materiales_identificacion; }
    public void setMaterialesIdentificacion(List<MaterialRequerido> lista) { this.materiales_identificacion = lista; }

    public String getAutor()                             { return autor; }
    public void   setAutor(String a)                     { this.autor = a; }

    public List<String> getPaginas()                     { return paginas; }
    public void         setPaginas(List<String> p)       { this.paginas = p; }

    // ── Clases internas ────────────────────────────────────────────────────

    public static class EncantamientoData {
        private String id;    // ej: "minecraft:sharpness"
        private int    nivel;

        public String getId()          { return id; }
        public void   setId(String i)  { this.id = i; }
        public int    getNivel()       { return nivel; }
        public void   setNivel(int n)  { this.nivel = n; }
    }

    /** Material requerido para identificar la reliquia. */
    public static class MaterialRequerido {
        private String material; // ej: "IRON_INGOT"
        private int    cantidad; // ej: 2

        public String getMaterial()        { return material; }
        public void   setMaterial(String m){ this.material = m; }
        public int    getCantidad()        { return cantidad; }
        public void   setCantidad(int c)   { this.cantidad = c; }
    }
}
