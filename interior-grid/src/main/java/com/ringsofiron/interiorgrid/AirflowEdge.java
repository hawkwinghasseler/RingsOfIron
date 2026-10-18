package com.ringsofiron.interiorgrid;

/**
 * Air moving through one opening, seen from one side: the compartment of tile (x, y).
 * {@link GridHazardSystem} rebuilds the list on {@link ShipGridComponent#airflow} every
 * hazard tick. An opening between two rooms appears twice, once from each side, with
 * opposite signs; an opening to space appears once.
 *
 * Openings are open edges (an open door, a gap, a breach), leaking walls and edge walls,
 * and leaking or holed deck plates. Use this for effects and for anything that reacts to
 * draughts, such as crew being pulled toward a breach.
 */
public class AirflowEdge {

    /** The air tile on this side of the opening. */
    public final int x, y;
    /** Direction from the tile toward the opening, or null for a leak through its own deck. */
    public final Side side;
    /** Compartment of tile (x, y). */
    public final Compartment room;
    /** Compartment on the far side, or null for space. */
    public final Compartment other;
    /** Size of the opening in tile widths (1 for an open edge, the leak factor for a leak). */
    public final float width;
    /** Air leaving {@link #room} through this opening, mol/s. Negative when air comes in. */
    public float flow;
    /** Smoke leaving {@link #room} through this opening, kg/s. Negative when smoke comes in. */
    public float smokeFlow;

    public AirflowEdge(int x, int y, Side side, Compartment room, Compartment other, float width) {
        this.x = x;
        this.y = y;
        this.side = side;
        this.room = room;
        this.other = other;
        this.width = width;
    }
}
