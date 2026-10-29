package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * An order to go aboard another craft as one of a boarding party: cross the dock onto
 * {@link #ship} and walk to tile (x, y) of it, a spot in one of its rooms, fighting whoever stands
 * in the way (a hostile in sight always comes first). The task is done on arrival; the poster
 * posts the next room to sweep while the boarding goes on. Posted on the board of the boarders' craft,
 * one order for each member of the party ({@link #member}), so only the party goes, and all of it.
 *
 * <p>With no dock to walk across, the boarders go over on EVA ({@link EvaSystem#beginCrossing}): into
 * EVA suits, out of their own airlock, across on the jetpack, and in by one of the craft's airlocks,
 * whose door they force from outside first ({@link Job#breach}).
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

        /**
         * {@code crew}, outside {@link #ship} at the outer door of its port {@code port}, works for
         * {@code dt} seconds on getting in by it: {@link Breach#WORKING} while at it, {@link Breach#OPEN}
         * once the door stands open (they go straight in), {@link Breach#CYCLED} once the airlock is
         * theirs to cycle (hacked, or the craft's own crew no longer hold it).
         */
        default Breach breach(Entity crew, MountFootprint port, float dt) {
            return Breach.CYCLED;
        }

        /** What getting in by {@code port} takes, for the activity line, e.g. "cutting the airlock door of the Raider". */
        default String breaching(MountFootprint port) {
            return "forcing the airlock door";
        }
    }

    /** How work on a craft's airlock door from outside stands. */
    public enum Breach { WORKING, OPEN, CYCLED }

    /** The craft being boarded. */
    public final Entity ship;
    public final Job job;
    /** The one crew member this order is for, or null for anyone who boards. */
    public final Entity member;

    public BoardTask(Entity ship, int x, int y, Job job) {
        this(ship, x, y, job, null);
    }

    public BoardTask(Entity ship, int x, int y, Job job, Entity member) {
        super(Kind.BOARD, x, y, PRIORITY);
        this.ship = ship;
        this.job = job;
        this.member = member;
    }

    /** True if {@code crew} may take this order: it is theirs, or anyone's. */
    public boolean isFor(Entity crew) {
        return member == null || member == crew;
    }

    /** Orders for different members to the same spot are different jobs: two boarders may sweep one room. */
    @Override
    public boolean sameJob(CrewTask other) {
        return super.sameJob(other) && other instanceof BoardTask && ((BoardTask) other).member == member;
    }
}
