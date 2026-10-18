package com.ringsofiron.moddingdata.defs;

/**
 * A tradeable good. {@code commodity.<id>} in TOML, or in JSON:
 * <pre>
 * { "commodity": { "water": { "name": "Water", "category": "consumable", "base_price": 12.0,
 *                             "mass_per_unit": 1000.0, "volume_per_unit": 1.0 } } }
 * </pre>
 */
public class CommodityDef {
    public String id;
    public String name;
    public String category = "general";
    /** Credits per unit at balanced supply and demand. */
    public float basePrice;
    /** kg per unit. */
    public float massPerUnit;
    /** m^3 of cargo space per unit. */
    public float volumePerUnit;
    public boolean illegal;
}
