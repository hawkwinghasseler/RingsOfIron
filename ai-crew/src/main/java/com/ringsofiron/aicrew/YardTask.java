package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Yard work on one tile of a craft, usually one docked at the worker's station: repairing a damaged
 * hull tile, wall or system, or installing part of a refit. Posted on the station's board; the
 * worker walks onto {@link #ship}, to the tile or beside it, and works until the {@link Job} says
 * the tile is done. The poster posts the next tile.
 */
public class YardTask extends CrewTask {

    /** Below hazards and casualties, above carrying. */
    public static final float PRIORITY = 5f;

    /** What the work does, tile by tile. */
    public interface Job {
        /** Puts {@code amount} of work (a technician's repair rate times the seconds) into the tile; true once it is done. */
        boolean work(float amount);

        /** False once the work is no longer wanted (finished, called off, or the craft undocked). */
        boolean isOpen();

        /** For the crew member's activity line, e.g. "repairing hull". */
        String what();
    }

    /** The craft the tile is on. */
    public final Entity ship;
    /** Tiles the work covers from (x, y): a system's whole footprint, say. Worked from inside or beside it. */
    public final int w, h;
    public final Job job;

    public YardTask(Entity ship, int x, int y, Job job) {
        this(ship, x, y, 1, 1, job);
    }

    public YardTask(Entity ship, int x, int y, int w, int h, Job job) {
        super(Kind.YARD, x, y, PRIORITY);
        this.ship = ship;
        this.w = w;
        this.h = h;
        this.job = job;
    }

    /** Every tile is its own job, so several technicians can work at once. */
    @Override
    public boolean sameJob(CrewTask other) {
        return other == this;
    }
}
