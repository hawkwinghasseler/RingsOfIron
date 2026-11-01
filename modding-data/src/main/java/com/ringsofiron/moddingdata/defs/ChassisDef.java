package com.ringsofiron.moddingdata.defs;

import java.util.ArrayList;
import java.util.List;

/**
 * A ship hull's interior layout, in layers. Enum values are their names as strings
 * ({@code "TOP"}, {@code "HEAVY"}) so this module does not depend on interior-grid.
 *
 * <pre>
 * [chassis.corvette]
 * name = "Corvette"
 * tile_size = 1.0
 * origin_x = -5.0
 * origin_y = -3.0
 * rows = [
 *   "##########",
 *   "#........#",
 *   ...
 * ]
 * mounts        = [ { component = "reactor", size = "M", x = 5, y = 1 },
 *                   { component = "docking_port", size = "S", x = 3, y = 5, active = true } ]
 * conduits      = [ [[1, 2], [6, 2]], [[6, 2], [6, 4]] ]
 * edge_conduits = [ [[4, 1], [4, 5]] ]
 * pipes         = [ [[2, 1], [2, 3]] ]
 * edge_pipes    = [ [[2, 1], [7, 1]] ]
 * edge_walls    = [ { path = [[4, 1], [4, 5]], weight_class = "LIGHT" } ]
 * doors         = [ { x = 3, y = 2, side = "RIGHT" } ]
 * hull_pieces   = [ { shape = "WEDGE", x = 0, y = 0, width = 1, height = 2, rotation = 0, weight_class = "MEDIUM" } ]
 * coverings     = [ { covering = "CARPET", x = 2, y = 1, width = 4, height = 1 } ]
 * lights        = [ { x = 2, y = 1, side = "BOTTOM" } ]
 * rcs           = [ { x = 0, y = 4, side = "TOP", kind = "QUAD" }, { x = 7, y = 0, side = "BOTTOM", thrust = 2.0e5 } ]
 * </pre>
 * Grid coordinates have (0, 0) at the bottom-left tile and +y up; {@code rows} is written
 * top row first, as it reads, and holds tile types only.
 *
 * <p>Conduits, pipes and walls along edges are written as <em>runs</em>: a list of points, each
 * step from one to the next straight across or up and down. A run of {@code conduits} or
 * {@code pipes} goes from tile to tile and lies on every tile it passes. A run of
 * {@code edge_conduits}, {@code edge_pipes} or {@code edge_walls} goes from corner to corner along
 * tile edges, where corner (x, y) is the bottom-left corner of tile (x, y), and lies on every edge
 * it passes. A run of one point is a single tile.
 *
 * <p>A chassis marked {@code station = true} is a station's layout, named by a station type's
 * {@code blueprint}; ships should not use one.
 */
public class ChassisDef {
    public String id;
    public String name;
    public float tileSize = 1f;
    public float originX, originY;
    /** Tile types only, one character each, top row first. */
    public String[] rows = new String[0];
    /** Every system the chassis installs. */
    public List<Mount> mounts = new ArrayList<>();
    /** Power conduits across tiles, as runs of tile points. */
    public List<int[][]> conduits = new ArrayList<>();
    /** Power conduits along tile edges, which may lie inside walls, as runs of corner points. */
    public List<int[][]> edgeConduits = new ArrayList<>();
    /** Pipes from tanks to the systems that draw on them across tiles, as runs of tile points. */
    public List<int[][]> pipes = new ArrayList<>();
    /** Pipes along tile edges, as runs of corner points. */
    public List<int[][]> edgePipes = new ArrayList<>();
    /** Thin walls along tile edges. */
    public List<EdgeWall> edgeWalls = new ArrayList<>();
    /** Doors in thin walls, each on one edge an edge wall covers. */
    public List<Door> doors = new ArrayList<>();
    public List<HullPiece> hullPieces = new ArrayList<>();
    public List<Covering> coverings = new ArrayList<>();
    /** True for a station's layout. */
    public boolean station;
    /**
     * Lights hung on walls. Null (no {@code lights} key) leaves them to the game, which works
     * them out from the layout; an empty list means none.
     */
    public List<Light> lights;
    /** RCS thrusters on the outside of the hull; a craft with none keeps its ship's turning and strafing figures. */
    public List<Rcs> rcs = new ArrayList<>();

    /** A run of thin wall along tile edges, of one weight class and material. */
    public static class EdgeWall {
        /** Corner points; see the class comment. */
        public int[][] path;
        public String weightClass = "MEDIUM";
        public String material = "hull_steel";
    }

    /** A door in the thin wall on the {@code side} edge of tile (x, y). */
    public static class Door {
        public int x, y;
        /** BOTTOM, TOP, LEFT or RIGHT. */
        public String side;
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

    /** A system: the component installed and the mount it sits in. */
    public static class Mount {
        /** A component id, e.g. reactor or fusion_core. */
        public String component;
        /** S, M, L or XL. */
        public String size = "S";
        /** The mount's bottom-left tile. */
        public int x, y;
        /** Quarter turns counter-clockwise; for a docking port, engine or escape pod, which way it faces. */
        public int rotation;
        /** Docking ports: an active port extends a telescoping latch collar when armed; a passive one only has magnets. */
        public boolean active;
    }

    /** A light on the {@code side} edge of tile (x, y), shining into the tile. It takes up no room on it. */
    public static class Light {
        public int x, y;
        /** BOTTOM, TOP, LEFT or RIGHT: the wall it hangs on. */
        public String side;
    }

    /**
     * An RCS thruster block on the {@code side} edge of hull tile (x, y), on the outside: that edge
     * must face space. A {@code LINEAR} block has one nozzle, firing out through that edge, so it
     * pushes the ship the other way; a {@code QUAD} block adds a nozzle each way along the hull.
     */
    public static class Rcs {
        public int x, y;
        /** BOTTOM, TOP, LEFT or RIGHT: the hull edge it sits on, which faces space. */
        public String side;
        /** LINEAR or QUAD. */
        public String kind = "LINEAR";
        /** Newtons from each nozzle; 0 for the standard thrust of the kind. */
        public double thrust;
    }

    public static class Covering {
        /** A FloorCovering name, e.g. CARPET. */
        public String covering;
        public int x, y, width = 1, height = 1;
    }
}
