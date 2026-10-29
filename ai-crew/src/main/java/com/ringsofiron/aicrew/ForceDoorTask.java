package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * An order to force a shut door: walk to tile (x, y) of {@link #ship}, the spot the door is worked
 * from, and work at it until the {@link Job} says it is open. What forcing means (prying, cutting,
 * hacking, and what it costs) is the job's business; the crew only get there and stay at it. Posted
 * on the board of the craft whose crew are ordered.
 */
public class ForceDoorTask extends CrewTask {

    /** An order: below hazards and casualties, above manning consoles and carrying. */
    public static final float PRIORITY = 30f;

    /** The work on the door. */
    public interface Job {
        /** Works on the door for {@code dt} seconds, by {@code crew} standing at the spot; true once it is open. */
        boolean work(Entity crew, float dt);

        /** False once the order no longer stands (the door opened some other way, undocked, called off). */
        boolean isOpen();

        /** For the crew member's activity line, e.g. "prying a door". */
        String what();

        /** Share of {@code crew}'s work on the door done, 0 to 1, for the bar over them; negative if the job can't say. */
        default float progress(Entity crew) {
            return -1f;
        }
    }

    /** The craft the spot is on. */
    public final Entity ship;
    public final Job job;

    public ForceDoorTask(Entity ship, int x, int y, Job job) {
        super(Kind.FORCE_DOOR, x, y, PRIORITY);
        this.ship = ship;
        this.job = job;
    }
}
