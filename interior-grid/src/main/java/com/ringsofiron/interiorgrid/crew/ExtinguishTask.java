package com.ringsofiron.interiorgrid.crew;

/** Put out the fire on tile (x, y). */
public class ExtinguishTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 60f;

    public ExtinguishTask(int x, int y) {
        super(Kind.EXTINGUISH, x, y, DEFAULT_PRIORITY);
    }
}
