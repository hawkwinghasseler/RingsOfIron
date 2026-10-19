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
 * color = "#7a8a9a"
 * </pre>
 */
public class TileTypeDef {
    public String id;
    public String name;
    /** The one character chassis rows draw this tile with. */
    public String symbol;
    /** VOID, FLOOR, CONDUIT, HULL or INTERNAL_WALL. */
    public String base = "FLOOR";
    /** For walls: LIGHT, MEDIUM or HEAVY. */
    public String weightClass = "MEDIUM";
    /** A material id, for walls. */
    public String material = "hull_steel";
    /** For floors: a FloorCovering name laid when the ship is built, e.g. CARPET. */
    public String covering = "NONE";
    /** How the editor draws it, "#rrggbb". */
    public String color;
}
