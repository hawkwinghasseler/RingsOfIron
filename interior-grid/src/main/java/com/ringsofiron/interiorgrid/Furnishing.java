package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * One piece of furniture or decor set down on a deck: a couch, a card table, a plant. It belongs
 * to the tile its middle is over ({@link Tile#furnishings}), but sits anywhere within it, at
 * {@link #offsetX}, {@link #offsetY} from the tile's middle and turned by {@link #rotation}, and
 * may reach over the tiles around (never through a wall). Place one with {@link Furnishings#place}.
 *
 * <p>It burns: a fire on its tile burns its {@link #fuel} with the tile's covering, and once the fire
 * has taken all but {@link #BURNT} of it, it is gone ({@link GridHazardSystem}).
 */
public final class Furnishing {
    /** Share of its fuel left at which a fire has burned it to nothing. */
    public static final float BURNT = 0.1f;
    /** Below this condition it is too scorched to use. */
    public static final float USABLE = 0.5f;

    public final FurnishingType type;
    /** The tile its middle is over. */
    public final int tileX, tileY;
    /** Where its middle is from the middle of its tile, tiles, each in [-0.5, 0.5). */
    public final float offsetX, offsetY;
    /** Counter-clockwise turn from its art's rotation 0 (front toward -y), degrees in [0, 360). */
    public final float rotation;
    /** Combustible mass left, kg: {@link FurnishingType#fuel} when new, less what fires burned. */
    public float fuel;
    /** The crew using it now (their entities), at most {@link FurnishingType#seats}. Not saved. */
    public final transient Array<Object> users = new Array<>(false, 2);

    public Furnishing(FurnishingType type, int tileX, int tileY, float offsetX, float offsetY, float rotation) {
        this.type = type;
        this.tileX = tileX;
        this.tileY = tileY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        float r = rotation % 360f;
        this.rotation = r < 0f ? r + 360f : r;
        this.fuel = type.fuel;
    }

    /** One with its middle at ({@code x}, {@code y}), tiles from the grid's corner. */
    public static Furnishing at(FurnishingType type, float x, float y, float rotation) {
        int tx = MathUtils.floor(x), ty = MathUtils.floor(y);
        return new Furnishing(type, tx, ty, x - tx - 0.5f, y - ty - 0.5f, rotation);
    }

    /** Its middle, tiles from the grid's corner. */
    public float x() {
        return tileX + 0.5f + offsetX;
    }

    public float y() {
        return tileY + 0.5f + offsetY;
    }

    /** What is left of it, 1 new to 0 burned away. Something that doesn't burn stays at 1. */
    public float condition() {
        return type.fuel <= 0f ? 1f : Math.max(0f, Math.min(1f, fuel / type.fuel));
    }

    /** True once a fire has burned it to nothing. */
    public boolean isBurnt() {
        return type.fuel > 0f && condition() <= BURNT;
    }

    /** Whether the crew can use it: something to do on it, and not too scorched. */
    public boolean isUsable() {
        return type.use.usable() && condition() >= USABLE;
    }

    /** True while someone else could use it too. */
    public boolean hasRoom() {
        return users.size < type.seats;
    }

    /**
     * Its four corners, tiles from the grid's corner, counter-clockwise from the back left:
     * {x0, y0, x1, y1, x2, y2, x3, y3}, grown by {@code grow} tiles on every side.
     */
    public float[] corners(float grow, float[] out) {
        return corners(x(), y(), type.width + 2f * grow, type.depth + 2f * grow, rotation, out);
    }

    static float[] corners(float cx, float cy, float w, float d, float degrees, float[] out) {
        float c = MathUtils.cosDeg(degrees), s = MathUtils.sinDeg(degrees);
        float hw = w / 2f, hd = d / 2f;
        float[] lx = {-hw, -hw, hw, hw}, ly = {hd, -hd, -hd, hd};
        for (int i = 0; i < 4; i++) {
            out[2 * i] = cx + lx[i] * c - ly[i] * s;
            out[2 * i + 1] = cy + lx[i] * s + ly[i] * c;
        }
        return out;
    }

    /**
     * Where the {@code seat}th of its users settles while using it, tiles from the grid's corner:
     * along its width, at its middle for something to sit on and just in front of it for the rest,
     * kept inside its own tile so they never stand in a wall.
     */
    public Vector2 seat(int seat, Vector2 out) {
        int n = Math.max(1, type.seats);
        float along = n == 1 ? 0f : (seat + 0.5f) / n - 0.5f;
        float lx = along * type.width;
        float ly = type.use == FurnishingType.Use.LOUNGE ? 0f : -type.depth / 2f - 0.25f;
        float c = MathUtils.cosDeg(rotation), s = MathUtils.sinDeg(rotation);
        float x = x() + lx * c - ly * s, y = y() + lx * s + ly * c;
        return out.set(MathUtils.clamp(x, tileX + 0.1f, tileX + 0.9f), MathUtils.clamp(y, tileY + 0.1f, tileY + 0.9f));
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "Furnishing(%s at %.2f, %.2f, %.0f deg)", type.id, x(), y(), rotation);
    }
}
