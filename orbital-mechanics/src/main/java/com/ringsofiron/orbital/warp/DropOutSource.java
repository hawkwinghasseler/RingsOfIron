package com.ringsofiron.orbital.warp;

/**
 * Another module's reason to stop rails warp at a known time, e.g. macro-sim's predicted bubble
 * encounters. Core wiring adapts {@code MacroSimulation.nextDropOutTime()} to this.
 */
public interface DropOutSource {
    /** Sim seconds of the next time rails warp must stop, or NaN / +infinity if none is known. */
    double nextDropOutTime();
}
