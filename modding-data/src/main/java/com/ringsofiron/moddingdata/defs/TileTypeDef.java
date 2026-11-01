package com.ringsofiron.moddingdata.defs;

/**
 * A tile type: one character of a chassis map, and the tile it builds. Enum values are
 * their names as strings so this module does not depend on interior-grid.
 *
 * <pre>
 * [tile.armor_plate]
 * name = "Armor plate"
 * symbol = "X"
 * base = "HULL"
 * weight_class = "HEAVY"
 * material = "composite_armor"
 * art = "walls/composite_armor_heavy"
 * color = "#7a8a9a"
 * </pre>
 */
public class TileTypeDef {
    public String id;
    public String name;
    /** The one character chassis rows draw this tile with. */
    public String symbol;
    /** VOID, FLOOR, HULL or INTERNAL_WALL. */
    public String base = "FLOOR";
    /** For walls: LIGHT, MEDIUM or HEAVY. */
    public String weightClass = "MEDIUM";
    /** A material id, for walls. */
    public String material = "hull_steel";
    /** For floors: a FloorCovering name laid when the ship is built, e.g. CARPET. */
    public String covering = "NONE";
    /**
     * The art it draws with: a region for floors ({@code tiles/floor}), or a wall family whose
     * regions add the join sides ({@code walls/composite_armor_heavy} for
     * {@code walls/composite_armor_heavy_nesw}). Null to go by base, weight and material.
     */
    public String art;
    /** How the editor draws it without the art pack, "#rrggbb". */
    public String color;
}
