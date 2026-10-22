package com.ringsofiron.interiorgrid;

/**
 * An RCS thruster block on the outside of the hull: on the {@code side} edge of hull tile (x, y),
 * an edge that faces space. Its nozzles fire out into space, so each pushes the ship the opposite
 * way, from where it sits: a {@link Kind#LINEAR} block has one nozzle, out through that edge; a
 * {@link Kind#QUAD} block has that one and one each way along the hull. It works while the hull
 * tile it is bolted to stands. It takes no room on the tile.
 */
public final class RcsThruster {
    public enum Kind {
        /** One nozzle, out through the hull edge it sits on. */
        LINEAR(1.5e5),
        /** Three nozzles: out through its edge, and each way along the hull. */
        QUAD(1.0e5);

        /** Newtons each nozzle of a standard block gives. */
        public final double standardThrust;

        Kind(double standardThrust) {
            this.standardThrust = standardThrust;
        }
    }

    /** How far past the hull edge a nozzle's mouth is, tiles: the block stands proud of the hull. */
    public static final float STANDOFF = 0.2f;
    /** Half the block's length along the hull, tiles, to the mouths of a quad's side nozzles. */
    public static final float HALF_LENGTH = 0.25f;

    public final int x, y;
    /** The hull edge it sits on, which faces space; its main nozzle fires out through it. */
    public final Side side;
    public final Kind kind;
    /** Newtons each nozzle gives; 0 for the kind's standard thrust. */
    public final double thrust;

    public RcsThruster(int x, int y, Side side, Kind kind, double thrust) {
        this.x = x;
        this.y = y;
        this.side = side;
        this.kind = kind;
        this.thrust = thrust;
    }

    public RcsThruster(int x, int y, Side side, Kind kind) {
        this(x, y, side, kind, 0);
    }

    /** Newtons each nozzle gives. */
    public double nozzleThrust() {
        return thrust > 0 ? thrust : kind.standardThrust;
    }

    /**
     * The sides its nozzles fire out of, in the grid's frame: its own edge first, then (a quad's)
     * the two along the hull, counter-clockwise from it and then clockwise.
     */
    public Side[] nozzles() {
        return kind == Kind.LINEAR ? new Side[]{side} : new Side[]{side, turn(side, 1), turn(side, 3)};
    }

    /**
     * Where nozzle {@code exhaust} (one of {@link #nozzles}) fires from, tiles from the grid's
     * corner: {@link #STANDOFF} out from the hull for its own edge, else halfway out at the
     * block's end on that side ({@link #HALF_LENGTH} along the hull).
     */
    public float nozzleX(Side exhaust) {
        return exhaust == side ? edgeX() + side.dx * STANDOFF
                : edgeX() + side.dx * STANDOFF * 0.5f + exhaust.dx * HALF_LENGTH;
    }

    public float nozzleY(Side exhaust) {
        return exhaust == side ? edgeY() + side.dy * STANDOFF
                : edgeY() + side.dy * STANDOFF * 0.5f + exhaust.dy * HALF_LENGTH;
    }

    /** The middle of the hull edge it sits on, tiles from the grid's corner. */
    public float edgeX() {
        return x + 0.5f + side.dx * 0.5f;
    }

    public float edgeY() {
        return y + 0.5f + side.dy * 0.5f;
    }

    /** Counter-clockwise quarter turns of its art, whose main nozzle points -x at rotation 0. */
    public int rotation() {
        switch (side) {
            case LEFT:   return 0;
            case BOTTOM: return 1;
            case RIGHT:  return 2;
            default:     return 3;
        }
    }

    /** Whether it still fires: the hull tile it is bolted to stands. */
    public boolean works(ShipGridComponent grid) {
        Tile t = grid.getTile(x, y);
        return t != null && t.isIntactWall();
    }

    /**
     * Why a block can't go on the {@code side} edge of tile (x, y), or null when it can: it needs a
     * hull tile with space (or the grid's edge) beyond that edge.
     */
    public static String placement(ShipGridComponent grid, int x, int y, Side side) {
        Tile t = grid.getTile(x, y);
        if (t == null || t.wallType != Tile.WallType.HULL) return "RCS goes on a hull tile";
        Tile out = grid.getTile(x + side.dx, y + side.dy);
        if (out != null && !out.isVoid()) return "RCS goes on a hull edge that faces space";
        return null;
    }

    /** {@code s} turned {@code quarters} quarter turns counter-clockwise. */
    static Side turn(Side s, int quarters) {
        Side out = s;
        for (int i = 0; i < Math.floorMod(quarters, 4); i++) {
            switch (out) {
                case RIGHT:  out = Side.TOP; break;
                case TOP:    out = Side.LEFT; break;
                case LEFT:   out = Side.BOTTOM; break;
                default:     out = Side.RIGHT; break;
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RcsThruster)) return false;
        RcsThruster r = (RcsThruster) o;
        return r.x == x && r.y == y && r.side == side && r.kind == kind && r.thrust == thrust;
    }

    @Override
    public int hashCode() {
        return ((x * 31 + y) * 4 + side.ordinal()) * 2 + kind.ordinal();
    }

    @Override
    public String toString() {
        return "RcsThruster(" + x + ", " + y + ", " + side + ", " + kind + ")";
    }
}
