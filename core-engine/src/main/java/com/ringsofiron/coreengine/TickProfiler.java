package com.ringsofiron.coreengine;

/**
 * Told how long each part of the frame loop took, for benchmarks and profiling. Installed with
 * {@link GameEngine#setProfiler} (and physics-exterior's {@code PhysicsManager.setProfiler} for the
 * step listeners); with none installed nothing is timed and the loop runs exactly as before.
 *
 * <p>Sections are named {@code "tick/<System>"} for a fixed system's tick, {@code "frame/<System>"}
 * for a system's part of the frame pass, {@code "catch-up/<System>"} for its share of a rails-warp
 * jump, and {@code "step/..."} for the parts of a physics step.
 * The step sections run inside {@code tick/PhysicsUpdateSystem}, so they are already counted there.
 */
public interface TickProfiler {
    /** {@code section} took {@code nanos} nanoseconds once. Called on the thread that ran it. */
    void record(String section, long nanos);
}
