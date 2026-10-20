package com.ringsofiron.interiorgrid.crew;

import com.ringsofiron.interiorgrid.LeakPatches;
import com.ringsofiron.interiorgrid.Side;

/**
 * Patch a leak to space from the ship's stores: the wall or deck plate at (x, y), or with a
 * {@link #side} the thin wall on that side of tile (x, y). See {@link LeakPatches}.
 */
public class PatchLeakTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 70f;

    /** The thin wall's side of (x, y), or null for the tile itself. */
    public final Side side;

    public PatchLeakTask(int x, int y, Side side) {
        super(Kind.PATCH_LEAK, x, y, DEFAULT_PRIORITY);
        this.side = side;
    }

    public PatchLeakTask(LeakPatches.Leak leak) {
        this(leak.x, leak.y, leak.side);
    }

    @Override
    public boolean sameJob(CrewTask other) {
        return super.sameJob(other) && ((PatchLeakTask) other).side == side;
    }
}
