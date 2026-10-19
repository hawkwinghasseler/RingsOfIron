package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.BoardTask;
import com.ringsofiron.coreengine.EntityRef;

/**
 * On a craft another ship's crew are boarding, or have taken ({@link Boarding}). Saved with the
 * craft; the boarding party's orders in hand are not, and are posted again.
 */
public class BoardedComponent implements Component {
    /** The ship whose crew board it. */
    public EntityRef by;
    /** No one aboard fights for it any more: its crew are held and it is the boarders' to strip. */
    public boolean taken;
    /** Sim seconds when the boarding began. */
    public double since;
    /** Prisoners held aboard once it was taken. */
    public int prisoners;

    /** Spots to sweep, one in each room, as (x, y) pairs; worked through in turn. */
    public transient int[] sweep;
    public transient int next;
    /** The boarding party's orders in hand. */
    public transient Array<BoardTask> orders = new Array<>();

    public BoardedComponent() {}

    public BoardedComponent(EntityRef by, double since) {
        this.by = by;
        this.since = since;
    }
}
