package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.Vector2;

/**
 * A hull or armor piece that covers one or more tiles but is a single object
 * with one shared integrity pool. Every tile it covers is a {@link Tile.WallType#HULL}
 * tile that points back here through {@link Tile#piece}.
 *
 * A {@link Shape#WEDGE} fills the triangle below the diagonal of its footprint and
 * presents a sloped outer face. Combat uses {@link #faceNormal} for incidence and
 * ricochet checks instead of the tile's square edge.
 */
public class HullPiece {

    public enum Shape {
        /** Fills its whole rectangular footprint. Faces are the tile edges. */
        BLOCK,
        /**
         * Right triangle. At rotation 0 its legs run along the footprint's left and
         * bottom edges and the sloped face looks toward +x/+y.
         */
        WEDGE
    }

    public final Shape shape;
    /** Bottom-left tile of the footprint. */
    public final int originX, originY;
    /** Unrotated footprint size in tiles, e.g. 1x2 for the standard wedge. */
    public final int baseWidth, baseHeight;
    /** Counter-clockwise quarter turns (0-3). */
    public final int rotation;
    public final WeightClass weightClass;
    public final String material;
    /** Shared integrity for every covered tile: 0.0 destroyed, 1.0 intact. */
    public float integrity = 1.0f;

    public HullPiece(Shape shape, int originX, int originY, int baseWidth, int baseHeight,
                     int rotation, WeightClass weightClass, String material) {
        if (baseWidth < 1 || baseHeight < 1) throw new IllegalArgumentException("Footprint must be at least 1x1");
        this.shape = shape;
        this.originX = originX;
        this.originY = originY;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.rotation = Math.floorMod(rotation, 4);
        this.weightClass = weightClass;
        this.material = material;
    }

    /** Footprint width in tiles after rotation. */
    public int width() {
        return rotation % 2 == 0 ? baseWidth : baseHeight;
    }

    /** Footprint height in tiles after rotation. */
    public int height() {
        return rotation % 2 == 0 ? baseHeight : baseWidth;
    }

    public int tileCount() {
        return width() * height();
    }

    public boolean covers(int x, int y) {
        return x >= originX && x < originX + width() && y >= originY && y < originY + height();
    }

    public boolean hasSlopedFace() {
        return shape == Shape.WEDGE;
    }

    /** Fraction of the footprint area that is solid material (a wedge is half). */
    public float fillFraction() {
        return shape == Shape.WEDGE ? 0.5f : 1.0f;
    }

    /**
     * Outward unit normal of the sloped face in grid-local (= body-local) axes,
     * or null for a {@link Shape#BLOCK}.
     */
    public Vector2 faceNormal() {
        if (!hasSlopedFace()) return null;
        // Hypotenuse runs from (w, 0) to (0, h); its outward normal is (h, w).
        Vector2 n = new Vector2(baseHeight, baseWidth).nor();
        for (int i = 0; i < rotation; i++) n.rotate90(1);
        return n;
    }

    public boolean isDestroyed() {
        return integrity <= 0f;
    }
}
