package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;

/**
 * The game's magnified insets (its picture-in-picture bubbles) as it laid them this frame: a round
 * window onto the player's ship, the locked target or the navigation target while the outside view
 * draws that craft too small to make out, or not at all. The game draws the magnified picture under
 * the HUD; a variant that draws the insets' rims and names itself
 * ({@link com.ringsofiron.uihud.variants.HudVariant#drawsInsets}) reads here where each one is and
 * what it shows.
 *
 * <p>The game fills this each frame before the HUD updates, and empties it in the deck view, so it
 * is never a frame behind the pictures. Positions are UI units, y up, as the UI camera has them.
 */
public final class Insets {
    /** At most this many at once: the player's ship and two targets. */
    public static final int MAX = 3;

    /** What an inset is for. */
    public enum Role {
        /** The player's own ship. */
        OWN,
        /** The locked target. */
        LOCK,
        /** The navigation target. */
        NAV
    }

    /** One inset. */
    public static final class Inset {
        public Entity entity;
        /** What it is for: the player's ship; the lock and the nav target, which may be one craft. */
        public boolean own, locked, nav;
        /** The craft's marker label as the game has it, tags and all ("Raider 6 (surrendered)"); "" for the player's ship. */
        public String label = "";
        /** The inset's middle and its radius. */
        public float x, y, radius;
        /** Where its craft is: off the stage when it is out of view. */
        public float craftX, craftY;
        /** The craft is in view; the inset sits right over it, so it has nothing to point at. */
        public boolean onScreen, centered;
        /** How far the craft reaches as the main view draws it: a line to it stops this far short. */
        public float reach;
        /** How far it is from the player's ship, m; NaN for the ship itself. */
        public float distance = Float.NaN;

        void reset() {
            entity = null;
            own = locked = nav = onScreen = centered = false;
            label = "";
            x = y = radius = craftX = craftY = reach = 0f;
            distance = Float.NaN;
        }
    }

    /**
     * How far a HUD's own rim, marks and name reach past the edge of an inset's picture, UI units:
     * {@link #side} either side, {@link #above} over it, {@link #below} under it
     * ({@link com.ringsofiron.uihud.variants.HudVariant#insetRoom}).
     */
    public static final class Room {
        public float side, above, below;

        public Room set(float side, float above, float below) {
            this.side = side;
            this.above = above;
            this.below = below;
            return this;
        }
    }

    private final Inset[] insets = new Inset[MAX];
    private int count;

    public Insets() {
        for (int i = 0; i < MAX; i++) insets[i] = new Inset();
    }

    /** How many insets show this frame. */
    public int count() {
        return count;
    }

    /** The {@code i}th of this frame's insets, {@code 0 <= i < count()}. */
    public Inset get(int i) {
        if (i < 0 || i >= count) throw new IndexOutOfBoundsException(i + " of " + count);
        return insets[i];
    }

    /** The inset onto {@code e} this frame, or null when it has none. */
    public Inset of(Entity e) {
        if (e == null) return null;
        for (int i = 0; i < count; i++) if (insets[i].entity == e) return insets[i];
        return null;
    }

    /** Empties the frame: before the game adds this frame's insets, and in the deck view, which has none. */
    public void clear() {
        count = 0;
    }

    /** A cleared inset for the game to fill, or null when the frame holds {@link #MAX} already. */
    public Inset add() {
        if (count == MAX) return null;
        Inset in = insets[count++];
        in.reset();
        return in;
    }
}
