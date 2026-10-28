package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Fetch {@link #castaway}, adrift off the ship with no way back on their own, within a line's reach
 * of a docking port's reel: a shipmate goes out on EVA on a line, pushes off the hull to them, takes
 * hold, and brings them back in ({@link EvaSystem}). Posted by {@link EvaSystem}; (x, y) is the ring
 * tile of the hull nearest them when it was posted. Not at battle stations.
 */
public class RetrieveTask extends CrewTask {

    /** A life outside, as a casualty's inside is: ahead of every other job but a rescue from a fire. */
    public static final float DEFAULT_PRIORITY = 85f;

    public final Entity castaway;

    public RetrieveTask(Entity castaway, int x, int y) {
        super(Kind.RETRIEVE, x, y, DEFAULT_PRIORITY);
        this.castaway = castaway;
    }

    /** One fetch per castaway, wherever they have drifted to. */
    @Override
    public boolean sameJob(CrewTask other) {
        return other instanceof RetrieveTask && ((RetrieveTask) other).castaway == castaway;
    }
}
