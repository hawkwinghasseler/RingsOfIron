package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;

/**
 * A passage from the outer edge of one tile to the outer edge of a tile on another craft's grid:
 * how two docked craft's port openings join (a "DockLink", one per pair of opening tiles). Crew
 * walk across it and air flows through it, as long as the edge walls on both sides (the port
 * doors) are open. While it exists, the edge does not vent to space.
 *
 * <p>Links come in pairs, one on each grid, each the other's {@link #mate}; {@link #join} makes
 * them and {@link #unjoin} removes them. Runtime only: the docking code makes them again after a
 * load.
 */
public final class GridLink {
    /** The tile on this grid whose {@link #side} edge is the passage. */
    public final int x, y;
    public final Side side;
    /** The craft and grid on the far side, and the tile there. */
    public final Entity otherShip;
    public final ShipGridComponent other;
    public final int ox, oy;
    public final Side otherSide;
    /** The same passage seen from the other grid. */
    public GridLink mate;
    /** One of each pair is primary, so cross-grid work runs once per pair. */
    public final boolean primary;
    /**
     * How far apart the two edges are, in tiles: the walkway crew cross between the craft (the
     * two port collars, half a tile each, make one tile). The same for both of the pair.
     */
    public float gap;

    private GridLink(int x, int y, Side side, Entity otherShip, ShipGridComponent other, int ox, int oy,
                     Side otherSide, boolean primary) {
        this.x = x;
        this.y = y;
        this.side = side;
        this.otherShip = otherShip;
        this.other = other;
        this.ox = ox;
        this.oy = oy;
        this.otherSide = otherSide;
        this.primary = primary;
    }

    /**
     * Joins the {@code side} edge of tile (x, y) on {@code a} to the {@code otherSide} edge of tile
     * (ox, oy) on {@code b}. Returns the link on {@code a}'s side.
     */
    public static GridLink join(Entity shipA, ShipGridComponent a, int x, int y, Side side,
                                Entity shipB, ShipGridComponent b, int ox, int oy, Side otherSide) {
        GridLink here = new GridLink(x, y, side, shipB, b, ox, oy, otherSide, true);
        GridLink there = new GridLink(ox, oy, otherSide, shipA, a, x, y, side, false);
        here.mate = there;
        there.mate = here;
        a.links.add(here);
        b.links.add(there);
        return here;
    }

    /** Sets {@link #gap} on this link and its mate. */
    public void setGap(float tiles) {
        gap = tiles;
        mate.gap = tiles;
    }

    /** Removes this link and its mate from both grids. */
    public void unjoin() {
        mate.other.links.removeValue(this, true);
        other.links.removeValue(mate, true);
    }

    /** The grid this link belongs to. */
    public ShipGridComponent grid() {
        return mate.other;
    }

    /** This side's edge wall (the port door), or null. */
    public EdgeWall edge() {
        return grid().getEdge(x, y, side);
    }

    /** True if someone can walk across: both tiles open and neither edge wall closed. */
    public boolean canCross() {
        Tile a = grid().getTile(x, y), b = other.getTile(ox, oy);
        if (a == null || b == null || !a.isOpen() || !b.isOpen()) return false;
        EdgeWall e = edge(), f = mate.edge();
        return (e == null || !e.blocks()) && (f == null || !f.blocks());
    }

    /**
     * How wide the passage is for air, in tile widths: 1 when both doors are open, the leak of a
     * damaged closed door, 0 when sealed or when either tile holds no air.
     */
    public float flowWidth() {
        Tile a = grid().getTile(x, y), b = other.getTile(ox, oy);
        if (a == null || b == null || !a.holdsAir() || !b.holdsAir()) return 0f;
        return Math.min(width(edge()), width(mate.edge()));
    }

    private static float width(EdgeWall e) {
        return e == null || !e.blocks() ? 1f : e.leakFactor();
    }

    /** How far a point (this grid's tile coordinates) is out past this link's edge, tiles; below 0 it is inside the tile. */
    public float past(float px, float py) {
        return (px - (x + 0.5f)) * side.dx + (py - (y + 0.5f)) * side.dy - 0.5f;
    }

    /** How far a point (this grid's tile coordinates) is along this link's edge from its middle, tiles. */
    public float along(float px, float py) {
        return -(px - (x + 0.5f)) * side.dy + (py - (y + 0.5f)) * side.dx;
    }

    /** The point {@code past} out from this link's edge and {@code along} it, in this grid's tile coordinates. */
    public void point(float past, float along, float[] out) {
        out[0] = x + 0.5f + side.dx * (0.5f + past) - side.dy * along;
        out[1] = y + 0.5f + side.dy * (0.5f + past) + side.dx * along;
    }

    /**
     * Maps a point in this grid's tile coordinates out on the walkway onto the far grid: the same
     * spot, so the same distance along the edge (the far side runs the other way) and the rest of
     * the {@link #gap} short of the far edge. Writes {x, y} in the far grid's tile units.
     */
    public void carryAcross(float px, float py, float[] out) {
        mate.point(gap - past(px, py), -along(px, py), out);
    }
}
