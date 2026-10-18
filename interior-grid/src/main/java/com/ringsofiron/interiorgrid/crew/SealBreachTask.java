package com.ringsofiron.interiorgrid.crew;

/** Patch a breached hull tile at (x, y) with an emergency bulkhead seal. */
public class SealBreachTask extends CrewTask {

    public static final float DEFAULT_PRIORITY = 80f;

    public SealBreachTask(int x, int y) {
        super(Kind.SEAL_BREACH, x, y, DEFAULT_PRIORITY);
    }
}
