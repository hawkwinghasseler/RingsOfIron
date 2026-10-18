package com.ringsofiron.orbital.events;

import com.ringsofiron.orbital.math.KeplerOrbit;
import com.ringsofiron.orbital.math.StateVector;

/**
 * A body (or the bubble anchor) moved from one sphere of influence to another. Dispatched by
 * {@link OrbitalEventSystem#soiChanges} after the change has been applied.
 */
public class SoiChange {
    /** The rails entity's UUID, or {@link OrbitalEventSystem#ANCHOR_ID} for the bubble anchor. */
    public String entityUuid;
    /** True when this is the bubble anchor; the bubble side re-parents its anchor to {@link #newOrbit}. */
    public boolean anchor;
    public String oldParentUuid;
    public String newParentUuid;
    /** Sim seconds of the crossing. */
    public double time;
    /** Position and velocity relative to the new parent at {@link #time}. */
    public final StateVector state = new StateVector();
    /** The conic around the new parent, epoch {@link #time}. */
    public KeplerOrbit newOrbit;

    @Override
    public String toString() {
        return (anchor ? "anchor" : entityUuid) + " " + oldParentUuid + " -> " + newParentUuid + " @" + time;
    }
}
