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

    /**
     * Maps a point in this grid's tile coordinates that has just stepped over the edge onto the
     * far grid: the same distance along the edge (the far side runs the other way) and the same
     * distance past the edge, now inside the far tile. Writes {x, y} in the far grid's tile units.
     */
    public void carryAcross(float px, float py, float[] out) {
        float rx = px - (x + 0.5f), ry = py - (y + 0.5f);
        float along = -rx * side.dy + ry * side.dx;
        float past = Math.max(0f, rx * side.dx + ry * side.dy - 0.5f);
        float depth = 0.5f - Math.min(past, 0.45f) - 0.02f;
        float nx = otherSide.dx, ny = otherSide.dy;
        out[0] = ox + 0.5f + nx * depth + ny * along;
        out[1] = oy + 0.5f + ny * depth - nx * along;
    }
}
