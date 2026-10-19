package com.ringsofiron.interiorgrid;

/**
 * A light hung on a wall: on the {@code side} edge of tile (x, y), shining into that tile. It
 * takes no room on the tile, so systems and anything else can still go there. Its art is drawn
 * with its top (the wall) against that edge: rotation 0 on a wall to +y, then counter-clockwise
 * quarter turns as for components (1 on a wall to -x, 2 to -y, 3 to +x).
 */
public final class WallLight {
    public final int x, y;
    public final Side side;

    public WallLight(int x, int y, Side side) {
        this.x = x;
        this.y = y;
        this.side = side;
    }

    /** Counter-clockwise quarter turns of the art, whose wall is up at rotation 0. */
    public int rotation() {
        switch (side) {
            case LEFT:   return 1;
            case BOTTOM: return 2;
            case RIGHT:  return 3;
            default:     return 0;
        }
    }

    /** The middle of the wall edge it hangs on, tiles from the grid's corner. */
    public float edgeX() {
        return x + 0.5f + side.dx * 0.5f;
    }

    public float edgeY() {
        return y + 0.5f + side.dy * 0.5f;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WallLight)) return false;
        WallLight w = (WallLight) o;
        return w.x == x && w.y == y && w.side == side;
    }

    @Override
    public int hashCode() {
        return (x * 31 + y) * 4 + side.ordinal();
    }

    @Override
    public String toString() {
        return "WallLight(" + x + ", " + y + ", " + side + ")";
    }
}
