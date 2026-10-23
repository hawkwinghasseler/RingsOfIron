package com.ringsofiron.moddingdata.defs;

/**
 * A furnishing: a piece of furniture or decor a station's yard sells and sets down anywhere on a
 * ship's deck, at any offset within a tile and any angle (interior-grid's {@code Furnishing}). It
 * is fuel for a fire, and the crew use it in their downtime. Enum values are their names as
 * strings so this module does not depend on interior-grid.
 *
 * <pre>
 * [furnishing.couch]
 * name = "Couch"
 * art = "decor/couch"
 * width = 1.5
 * depth = 0.75
 * fuel = 6.0
 * price = 900.0
 * use = "LOUNGE"
 * seats = 3
 * relax = 0.04
 * verb = "lounging on the couch"
 * </pre>
 */
public class FurnishingDef {
    public String id;
    public String name;
    /** The art region it draws with, e.g. {@code decor/couch}; drawn at rotation 0 with its front toward -y. */
    public String art;
    /** Size across (x at rotation 0) and front to back (y), tiles. */
    public float width = 1f, depth = 1f;
    /** Combustible mass, kg: what a fire on its tile burns. 0 for something that does not burn. */
    public float fuel = 2f;
    /** Credits new at a yard. */
    public double price = 500;
    /**
     * What the crew do with it in their downtime: LOUNGE, GAME, MUSIC, SCREEN, EXERCISE, READING,
     * or DECOR (nothing; it only makes the room nicer).
     */
    public String use = "DECOR";
    /** How many crew can use it at once. */
    public int seats = 1;
    /** Morale gained per hour of use, 0 to 1. */
    public float relax = 0f;
    /** What a crew member using it is doing, e.g. "lounging on the couch"; left out, made from its name. */
    public String verb;
    /** Lies flat on the floor (a rug): other furnishings can stand on it. */
    public boolean flat;
}
