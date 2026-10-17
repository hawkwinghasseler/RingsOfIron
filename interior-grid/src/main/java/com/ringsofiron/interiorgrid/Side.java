package com.ringsofiron.interiorgrid;

/**
 * One of the four edges of a tile. The grid follows Box2D's orientation, so
 * {@link #TOP} is +y and {@link #RIGHT} is +x in the ship body's local frame.
 */
public enum Side {
    BOTTOM(0, -1),
    TOP(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    /** Offset from a tile to the neighbour that shares this edge. */
    public final int dx, dy;

    Side(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Side opposite() {
        switch (this) {
            case BOTTOM: return TOP;
            case TOP:    return BOTTOM;
            case LEFT:   return RIGHT;
            default:     return LEFT;
        }
    }

    /** Returns the side of (x, y) that faces the orthogonal neighbour (nx, ny), or null if not adjacent. */
    public static Side between(int x, int y, int nx, int ny) {
        int dx = nx - x, dy = ny - y;
        for (Side s : values()) {
            if (s.dx == dx && s.dy == dy) return s;
        }
        return null;
    }
}
