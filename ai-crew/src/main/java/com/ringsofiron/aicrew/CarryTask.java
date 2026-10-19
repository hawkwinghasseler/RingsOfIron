package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * One trip with a load of goods: picked up beside a cargo bay of {@link #from} and set down beside
 * one of {@link #to}'s, walking across a docked link when the two are different craft. The poster's
 * {@link Job} says how much each trip carries and settles it, so the goods only change hands when
 * a load arrives. Each task is one trip; the poster posts another while the job wants more.
 */
public class CarryTask extends CrewTask {

    /** Priority of a carry job: below hazards and casualties, which take the crew off it. */
    public static final float PRIORITY = 4f;

    /** The goods being moved and what happens to each load. */
    public interface Job {
        /** Commodity id, for the bays to pick and the carrier to show. */
        String commodity();

        /**
         * Units to carry on one trip of at most {@code volume} m³, set aside until the load
         * arrives or is dropped; 0 when nothing more can go.
         */
        double take(double volume);

        /** A load of {@code units} reached {@link CarryTask#to}. */
        void deliver(double units);

        /** A load of {@code units} never arrived: the carrier was stopped, and what was set aside goes back. */
        void abandon(double units);

        /** False once the job is finished or called off. */
        boolean isOpen();
    }

    /** The craft whose bays the goods come from, and the craft they go to. */
    public final Entity from, to;
    public final Job job;

    /** Posted with (x, y) a bay tile on {@code from}, where the pick-up is. */
    public CarryTask(Entity from, int x, int y, Entity to, Job job) {
        super(Kind.CARRY, x, y, PRIORITY);
        this.from = from;
        this.to = to;
        this.job = job;
    }

    /** Every trip is its own job, so several carriers can work one order at once. */
    @Override
    public boolean sameJob(CrewTask other) {
        return other == this;
    }
}
