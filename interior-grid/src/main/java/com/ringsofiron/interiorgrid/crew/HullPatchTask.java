package com.ringsofiron.interiorgrid.crew;

import com.ringsofiron.interiorgrid.HullPlating;

/**
 * Frame wrecked hull tile (x, y) back up from outside and plate it over ({@link HullPlating}): a crew
 * member gets into an EVA suit, cycles out through a docking port's airlock, crawls along the hull to
 * the tile and works on it there. It waits for the fight to be over.
 */
public class HullPatchTask extends CrewTask {

    /** Below an RCS block: the ship can manoeuvre with a holed hull. */
    public static final float DEFAULT_PRIORITY = 30f;

    public HullPatchTask(int x, int y) {
        super(Kind.PATCH_HULL, x, y, DEFAULT_PRIORITY);
    }
}
