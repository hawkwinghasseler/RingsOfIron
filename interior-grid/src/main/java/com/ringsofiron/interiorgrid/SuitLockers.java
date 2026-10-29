package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.crew.Suit;

import java.util.function.Predicate;

/**
 * Suit lockers: furnishings that hold space suits ({@link FurnishingType#holds}), pressure suits or
 * EVA suits, up to their {@link FurnishingType#capacity}. A suit not being worn hangs in one, or is
 * in someone's hands on its way to one; a crew member suits up at a locker and hangs the suit back
 * up when it comes off. A locker is plumbed to the ship's oxygen and propellant, and fills the suits
 * in it ({@code SuitLockerSystem} in ai-crew).
 *
 * <p>A crew member reaches into a locker from the tile its middle is over (it stands against a wall,
 * so that is the tile in front of the wall), standing at its {@link #front}; a hand seated at a post
 * reaches one whose front is within {@link #REACH}.
 */
public final class SuitLockers {
    /** Farthest a crew member's middle may be from a locker's front and still reach into it, tiles. */
    public static final float REACH = 1.25f;
    /** How far in front of a locker a crew member stands to use it, tiles from its front edge. */
    static final float STAND_OFF = 0.25f;

    private SuitLockers() {
    }

    /** True if {@code f} is a suit locker. */
    public static boolean isLocker(Furnishing f) {
        return f != null && f.type.holds != null;
    }

    /** True if {@code f} is a locker still fit to use: not burned past using. */
    public static boolean works(Furnishing f) {
        return isLocker(f) && !f.isBurnt() && f.condition() >= Furnishing.USABLE;
    }

    /** Every suit locker on {@code grid}, row by row from the bottom-left tile. */
    public static Array<Furnishing> all(ShipGridComponent grid) {
        Array<Furnishing> out = new Array<>();
        if (grid == null) return out;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Array<Furnishing> on = grid.tiles[y][x].furnishings;
                for (int i = 0; i < on.size; i++) if (isLocker(on.get(i))) out.add(on.get(i));
            }
        }
        return out;
    }

    /** True if {@code grid} has a suit locker of either kind. */
    public static boolean any(ShipGridComponent grid) {
        if (grid == null) return false;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Array<Furnishing> on = grid.tiles[y][x].furnishings;
                for (int i = 0; i < on.size; i++) if (isLocker(on.get(i))) return true;
            }
        }
        return false;
    }

    /** True if {@code f} is a working locker with a suit of {@code kind} in it. */
    public static boolean hasSuit(Furnishing f, Suit.Kind kind) {
        if (!works(f) || f.type.holds != kind) return false;
        for (int i = 0; i < f.suits.size; i++) if (f.suits.get(i).kind == kind) return true;
        return false;
    }

    /** True if {@code f} is a working locker with room for a suit of {@code kind}. */
    public static boolean hasRoom(Furnishing f, Suit.Kind kind) {
        return works(f) && f.type.holds == kind && f.suits.size < f.type.capacity;
    }

    /** Takes the fullest suit of {@code kind} out of {@code f}; null if it has none. */
    public static Suit take(Furnishing f, Suit.Kind kind) {
        if (!hasSuit(f, kind)) return null;
        int best = -1;
        for (int i = 0; i < f.suits.size; i++) {
            Suit s = f.suits.get(i);
            if (s.kind != kind) continue;
            if (best < 0 || s.air + s.jet > f.suits.get(best).air + f.suits.get(best).jet) best = i;
        }
        return f.suits.removeIndex(best);
    }

    /** Hangs {@code suit} in {@code f}; false if it has no room for it. */
    public static boolean stow(Furnishing f, Suit suit) {
        if (suit == null || !hasRoom(f, suit.kind)) return false;
        f.suits.add(suit);
        return true;
    }

    /** Suits of {@code kind} in working lockers on {@code grid}. */
    public static int count(ShipGridComponent grid, Suit.Kind kind) {
        int n = 0;
        for (Furnishing f : all(grid)) {
            if (!works(f)) continue;
            for (int i = 0; i < f.suits.size; i++) if (f.suits.get(i).kind == kind) n++;
        }
        return n;
    }

    /**
     * Where a crew member stands to use {@code f}, tiles from the grid's corner: {@link #STAND_OFF}
     * in front of its front edge, kept on its own tile so they never stand in the wall it is against.
     */
    public static Vector2 front(Furnishing f, Vector2 out) {
        float ly = -f.type.depth / 2f - STAND_OFF;
        float c = MathUtils.cosDeg(f.rotation), s = MathUtils.sinDeg(f.rotation);
        float x = f.x() - ly * s, y = f.y() + ly * c;
        return out.set(MathUtils.clamp(x, f.tileX + 0.1f, f.tileX + 0.9f), MathUtils.clamp(y, f.tileY + 0.1f, f.tileY + 0.9f));
    }

    /** True if a crew member at ({@code x}, {@code y}), tiles, can reach into {@code f}. */
    public static boolean reaches(Furnishing f, float x, float y) {
        Vector2 at = front(f, new Vector2());
        return at.dst2(x, y) <= REACH * REACH;
    }

    /** True if a crew member walking to tile (x, y) could use {@code f} from it: its own tile. */
    public static boolean usedFrom(Furnishing f, int x, int y) {
        return f.tileX == x && f.tileY == y;
    }

    /**
     * The locker within reach of a crew member at ({@code x}, {@code y}), tiles, that {@code test}
     * accepts, the nearest first; null if none.
     */
    public static Furnishing within(ShipGridComponent grid, float x, float y, Predicate<Furnishing> test) {
        if (grid == null) return null;
        int tx = MathUtils.floor(x), ty = MathUtils.floor(y);
        Vector2 at = new Vector2();
        Furnishing best = null;
        float bestD = REACH * REACH;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                Tile t = grid.getTile(tx + dx, ty + dy);
                if (t == null) continue;
                for (int i = 0; i < t.furnishings.size; i++) {
                    Furnishing f = t.furnishings.get(i);
                    if (!isLocker(f) || !test.test(f)) continue;
                    float d = front(f, at).dst2(x, y);
                    if (d <= bestD) {
                        bestD = d;
                        best = f;
                    }
                }
            }
        }
        return best;
    }

    /** The locker on tile (x, y) that {@code test} accepts, or null: where a crew member walked to use one. */
    public static Furnishing on(ShipGridComponent grid, int x, int y, Predicate<Furnishing> test) {
        Tile t = grid == null ? null : grid.getTile(x, y);
        if (t == null) return null;
        for (int i = 0; i < t.furnishings.size; i++) {
            Furnishing f = t.furnishings.get(i);
            if (isLocker(f) && test.test(f)) return f;
        }
        return null;
    }
}
