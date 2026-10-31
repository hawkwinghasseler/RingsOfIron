package com.ringsofiron.aicrew;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.crew.CrewTask;

/**
 * Work on the hull of another craft standing off nearby, with no dock between ({@link
 * Kind#STRIP_HULL}): a part to take off it and bring home. Posted on the board of the crew's own
 * craft. The one who takes it gets into an EVA suit, goes out of their own airlock and across on
 * the jetpack ({@link EvaSystem#beginCrossing}), lands on {@link #craft}'s hull and crawls along it
 * to the ring tile its {@link Job} names, works there for as long as the job says, and comes away
 * with the part's crate ({@link Job#takeOff}): back across home, in by their own airlock and the
 * crate stowed in the hold ({@link com.ringsofiron.aicrew.btree.StowCrate}), where the job hears of
 * it ({@link OutsideTask.Job#stowed}).
 *
 * <p>The job is an {@link OutsideTask.Job} so the crate it hands over can name it; of that, {@link
 * OutsideTask.Job#workTile} is on {@link #craft}'s hull, and the part-fetching methods are not
 * used.
 */
public class HullStripTask extends CrewTask {

    /** As pressing as other work outside. */
    public static final float PRIORITY = OutsideTask.PRIORITY;

    /** What the work is, and the part it gives. */
    public interface Job extends OutsideTask.Job {
        /**
         * The work is finished by {@code worker}: the crate of the part they now carry home, or null
         * if there is nothing to bring.
         */
        CrewMemberComponent.Crate takeOff(Entity worker);

        @Override
        default void done(Entity worker) {
        }
    }

    /** The craft whose hull the work is on. */
    public final Entity craft;
    public final Job job;

    /** Posted against tile (x, y) of {@code craft}'s grid: the thing worked on, for the board's dedup. */
    public HullStripTask(Entity craft, int x, int y, Job job) {
        super(Kind.STRIP_HULL, x, y, PRIORITY);
        this.craft = craft;
        this.job = job;
    }

    /** Every part is its own job. */
    @Override
    public boolean sameJob(CrewTask other) {
        return other == this;
    }
}
