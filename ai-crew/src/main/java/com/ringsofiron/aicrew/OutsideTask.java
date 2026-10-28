package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Work outside the hull that the game posts, as the crew module knows nothing of what it is for: a
 * docking collar to free ({@link Kind#FREE_COLLAR}), a part to mount ({@link Kind#MOUNT_PART}). It is
 * done on EVA like any job outside ({@link com.ringsofiron.aicrew.btree.EvaTasks}): out through an
 * airlock, along the hull to the ring tile its {@link Job} names, and the work there for as long as
 * the job says. What the work does is the job's.
 */
public class OutsideTask extends CrewTask {

    /** Below an RCS block and a holed hull. */
    public static final float PRIORITY = 25f;

    /** What the work is. */
    public interface Job {
        /** False once the work is no longer wanted (done, called off, or no longer possible). */
        boolean isOpen();

        /** The ring tile outside the hull it is worked from: {@code out = {x, y}}; false if there is none. */
        boolean workTile(HullSurface hull, int[] out);

        /** Seconds of work it takes at a steady pace (an EVA suit's gloves slow it). */
        float seconds(CrewDef def);

        /** The work is finished, by {@code worker}. */
        void done(Entity worker);

        /** What they are doing at it, for their activity line: "freeing the docking collar". */
        String doing();

        /** What they crawl to: "the docking collar". */
        String where();
    }

    public final Job job;

    /** Posted against tile (x, y) of the craft's grid: the thing worked on, for the board's distance and dedup. */
    public OutsideTask(Kind kind, int x, int y, Job job) {
        this(kind, x, y, PRIORITY, job);
    }

    public OutsideTask(Kind kind, int x, int y, float priority, Job job) {
        super(kind, x, y, priority);
        if (!kind.isEva()) throw new IllegalArgumentException(kind + " is not work outside");
        this.job = job;
    }
}
