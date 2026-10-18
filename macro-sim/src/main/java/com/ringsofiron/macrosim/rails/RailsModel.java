package com.ringsofiron.macrosim.rails;

import java.util.List;

/**
 * Everything macro-sim needs from the on-rails orbit simulation, keyed by entity UUID.
 *
 * <p>The real implementation adapts the {@code orbital-mechanics} module ({@code OrbitComponent},
 * {@code ManeuverNodeComponent}, {@code OrbitalEventSystem}). Until that lands,
 * {@link com.ringsofiron.macrosim.rails.stub.StubRailsModel} provides the same behaviour with its own
 * Kepler code, so macro-sim logic is written once against this interface.
 *
 * <p>All methods are main-thread only, except that {@link #snapshot()} returns an object that is safe
 * to read from any thread.
 */
public interface RailsModel {

    /** One impulsive burn, prograde and radial-out components in m/s. */
    final class Burn {
        public final double time;
        public final double prograde;
        public final double radial;

        public Burn(double time, double prograde, double radial) {
            this.time = time;
            this.prograde = prograde;
            this.radial = radial;
        }
    }

    /** Gravitational parameter of a massive body, m^3/s^2. */
    double muOf(String bodyUuid);

    /** True if this UUID currently moves on rails (it is not in the bubble). */
    boolean isOnRails(String uuid);

    /** Parent body UUID of a rails entity. */
    String parentOf(String uuid);

    /** Parent-relative state of a rails entity at sim second {@code t}. */
    StateVector stateAt(String uuid, double t, StateVector out);

    /** Immutable trajectory of a rails entity as it stands now (until its next event). */
    Trajectory trajectoryOf(String uuid);

    /**
     * Puts an entity on rails (or replaces its orbit) from a parent-relative state vector at {@code t}.
     * Pending burns are kept unless {@link #detach} removed them.
     */
    void attach(String uuid, String parentUuid, StateVector state, double t);

    /** Takes an entity off rails and returns its pending burns, now cancelled (they become AI goals). */
    List<Burn> detach(String uuid);

    /** Queues an impulsive burn. Burns fire in time order when {@link #processEventsUntil} passes them. */
    void scheduleBurn(String uuid, Burn burn);

    /** Cancels every pending burn of the entity. */
    void cancelBurns(String uuid);

    /** Fires every due rails event (burns, SOI changes) with time at or before {@code t}, in order. */
    void processEventsUntil(double t);

    /** Sim second of the next pending rails event, or {@code +inf}. */
    double nextEventTime();

    /** Read-only, thread-safe copy of every rails body's trajectory, for background prediction. */
    RailsSnapshot snapshot();
}
