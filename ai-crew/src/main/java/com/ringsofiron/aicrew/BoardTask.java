package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * An order to go aboard another craft as one of a boarding party: cross the dock onto
 * {@link #ship} and walk to tile (x, y) of it, a spot in one of its rooms, fighting whoever stands
 * in the way (a hostile in sight always comes first). The task is done on arrival; the poster
 * posts the next room to sweep while the boarding goes on. Posted on the board of the boarders' craft.
 */
public class BoardTask extends CrewTask {

    /** An order: as pressing as forcing a door. */
    public static final float PRIORITY = 30f;

    /** The boarding behind the order. */
    public interface Job {
        /** False once the order no longer stands (the craft is taken, undocked, called off). */
        boolean isOpen();

        /** {@code crew} reached the spot. */
        void arrived(Entity crew);

        /** For the crew member's activity line, e.g. "boarding the Raider". */
        String what();
    }

    /** The craft being boarded. */
    public final Entity ship;
    public final Job job;

    public BoardTask(Entity ship, int x, int y, Job job) {
        super(Kind.BOARD, x, y, PRIORITY);
        this.ship = ship;
        this.job = job;
    }
}
