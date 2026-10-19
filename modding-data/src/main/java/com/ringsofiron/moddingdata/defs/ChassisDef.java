package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A ship hull's interior layout, mirroring interior-grid's {@code ChassisBlueprint} field
 * for field. Enum values are their names as strings ({@code "TOP"}, {@code "HEAVY"},
 * {@code "REACTOR"}) so this module does not depend on interior-grid.
 *
 * <pre>
 * [chassis.corvette]
 * name = "Corvette"
 * tile_size = 1.0
 * origin_x = -5.0
 * origin_y = -3.0
 * rows = [
 *   "##########",
 *   "#HH....RR#",
 *   ...
 * ]
 * edge_walls  = [ { x = 4, y = 2, side = "TOP", weight_class = "LIGHT", bulkhead = true } ]
 * hull_pieces = [ { shape = "WEDGE", x = 0, y = 0, width = 1, height = 2, rotation = 0, weight_class = "MEDIUM" } ]
 * mounts      = [ { type = "TURRET_MOUNT", size = "S", x = 3, y = 3, rotation = 0 },
 *                 { component = "fusion_core", size = "M", x = 5, y = 1 } ]
 * coverings   = [ { covering = "CARPET", x = 2, y = 1, width = 4, height = 1 } ]
 * ports       = [ { x = 4, y = 5, active = true } ]
 * lights      = [ { x = 2, y = 1, side = "BOTTOM" } ]
 * </pre>
 * Grid coordinates have (0, 0) at the bottom-left tile and +y up; {@code rows} is written
 * top row first, as it reads.
 *
 * <p>A chassis marked {@code station = true} is a station's layout, named by a station type's
 * {@code blueprint}; ships should not use one.
 */
public class ChassisDef {
    public String id;
    public String name;
    public float tileSize = 1f;
    public float originX, originY;
    public String[] rows = new String[0];
    public List<EdgeWall> edgeWalls = new ArrayList<>();
    public List<HullPiece> hullPieces = new ArrayList<>();
    public List<Mount> mounts = new ArrayList<>();
    public List<Covering> coverings = new ArrayList<>();
    /** True for a station's layout. */
    public boolean station;
    /** Options for docking ports, each naming any tile of the port it applies to. */
    public List<Port> ports = new ArrayList<>();
    /**
     * Lights hung on walls. Null (no {@code lights} key) leaves them to the game, which works
     * them out from the layout; an empty list means none.
     */
    public List<Light> lights;

    public static class EdgeWall {
        public int x, y;
        /** BOTTOM, TOP, LEFT or RIGHT. */
        public String side;
        public String weightClass = "MEDIUM";
        public String material = "hull_steel";
        public boolean bulkhead;
    }

    public static class HullPiece {
        /** BLOCK or WEDGE. */
        public String shape = "BLOCK";
        public int x, y;
        public int width = 1, height = 1;
        public int rotation;
        public String weightClass = "MEDIUM";
        public String material = "hull_steel";
    }

    public static class Mount {
        /** A SubsystemType name, e.g. REACTOR, for that behavior's built-in component. Its mount kind follows from the type. */
        public String type;
        /** A component id, e.g. fusion_core, instead of {@link #type}. */
        public String component;
        /** S, M, L or XL. */
        public String size = "S";
        public int x, y;
        public int rotation;
    }

    /** Settings for the docking port that covers tile (x, y). Ports not listed are passive. */
    public static class Port {
        public int x, y;
        /** An active port extends a telescoping latch collar when armed; a passive one only has magnets. */
        public boolean active;
    }

    /** A light on the {@code side} edge of tile (x, y), shining into the tile. It takes up no room on it. */
    public static class Light {
        public int x, y;
        /** BOTTOM, TOP, LEFT or RIGHT: the wall it hangs on. */
        public String side;
    }

    public static class Covering {
        /** A FloorCovering name, e.g. CARPET. */
        public String covering;
        public int x, y, width = 1, height = 1;
    }
}
