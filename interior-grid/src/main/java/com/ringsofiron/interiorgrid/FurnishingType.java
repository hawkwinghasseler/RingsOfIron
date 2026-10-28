package com.ringsofiron.interiorgrid;

import com.ringsofiron.interiorgrid.crew.Suit;
import com.ringsofiron.moddingdata.defs.FurnishingDef;

import java.util.Locale;

/**
 * A kind of furnishing ({@code [furnishing.*]} in furnishings.toml): its size, what a fire makes
 * of it, its price and what the crew do with it in their downtime. A {@link Furnishing} is one
 * set down on a deck.
 */
public final class FurnishingType {

    /** What the crew do with a furnishing when they have nothing else to do. */
    public enum Use {
        /** Sit back on it: couches, armchairs, beanbags. */
        LOUNGE,
        /** Play at it: card and pool tables, arcade cabinets, dartboards. */
        GAME,
        /** Listen to or play music. */
        MUSIC,
        /** Watch something. */
        SCREEN,
        /** Work out on it. */
        EXERCISE,
        /** Read from it. */
        READING,
        /** Nothing: it only makes the room nicer. */
        DECOR,
        /** Holds things: a suit locker ({@link FurnishingType#holds}). The crew don't sit at it. */
        STORAGE;

        /** Whether the crew can use it at all. */
        public boolean usable() {
            return this != DECOR && this != STORAGE;
        }
    }

    public final String id;
    public final String name;
    /** Art region, drawn at rotation 0 with the front toward -y. */
    public final String art;
    /** Size across (x at rotation 0) and front to back (y), tiles. */
    public final float width, depth;
    /** Combustible mass when new, kg. */
    public final float fuel;
    /** Credits new. */
    public final double price;
    public final Use use;
    /** Crew who can use it at once; 0 for decor. */
    public final int seats;
    /** Morale gained per hour of use. */
    public final float relax;
    /** What a crew member using it is doing: "lounging on the couch". */
    public final String verb;
    /** Lies flat on the floor (a rug): other furnishings can stand on it, and it is drawn under them. */
    public boolean flat;
    /** The kind of suit it is a locker for ({@link SuitLockers}), or null for anything else. */
    public Suit.Kind holds;
    /** How many suits it holds, as a locker. */
    public int capacity;

    public FurnishingType(String id, String name, String art, float width, float depth, float fuel, double price,
                          Use use, int seats, float relax, String verb) {
        this.id = id;
        this.name = name == null ? id : name;
        this.art = art == null ? "decor/" + id : art;
        this.width = Math.max(0.1f, width);
        this.depth = Math.max(0.1f, depth);
        this.fuel = Math.max(0f, fuel);
        this.price = price;
        this.use = use;
        this.seats = use.usable() ? Math.max(1, seats) : 0;
        this.relax = Math.max(0f, relax);
        this.verb = verb != null ? verb : defaultVerb(use, this.name);
    }

    public static FurnishingType from(FurnishingDef d) {
        Use use;
        try {
            use = Use.valueOf(d.use == null ? "DECOR" : d.use.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            use = Use.DECOR;
        }
        FurnishingType t = new FurnishingType(d.id, d.name, d.art, d.width, d.depth, d.fuel, d.price, use, d.seats, d.relax, d.verb);
        t.flat = d.flat;
        t.holds(d.holds, d.capacity);
        return t;
    }

    /**
     * Makes it a locker for {@code capacity} suits of {@code kind} ("IVA" or "EVA"); nothing for a
     * null or unknown kind. Returns itself.
     */
    public FurnishingType holds(String kind, int capacity) {
        if (kind == null || capacity <= 0) return this;
        try {
            holds = Suit.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
            this.capacity = capacity;
        } catch (IllegalArgumentException e) {
            holds = null;
        }
        return this;
    }

    /** "the couch", for notices. */
    public String the() {
        return "the " + name.toLowerCase(Locale.ROOT);
    }

    private static String defaultVerb(Use use, String name) {
        String the = "the " + name.toLowerCase(Locale.ROOT);
        switch (use) {
            case LOUNGE: return "relaxing on " + the;
            case GAME: return "playing at " + the;
            case MUSIC: return "listening to " + the;
            case SCREEN: return "watching " + the;
            case EXERCISE: return "working out on " + the;
            case READING: return "reading at " + the;
            default: return "";
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
