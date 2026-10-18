package com.ringsofiron.macrosim.bubble;

import com.ringsofiron.macrosim.rails.RailsSnapshot;
import com.ringsofiron.macrosim.rails.StateVector;
import com.ringsofiron.macrosim.rails.Trajectory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Background search for rails bodies that may come within {@code radius} of the bubble anchor before
 * {@code from + horizon}. Reads only immutable snapshots, so it is safe on any thread.
 *
 * <p>Each body is first culled by radius band (its periapsis-apoapsis ring must overlap the anchor's,
 * widened by the radius). Survivors are searched by conservative advancement: from a distance
 * {@code d}, nothing can close faster than both bodies' periapsis speeds combined, so the next sample
 * can safely skip {@code (d - radius) / maxClosingSpeed} seconds. Far bodies cost a few samples; no
 * encounter can be stepped over.
 */
public class EncounterPredictionTask implements Callable<EncounterForecast> {
    /** Smallest sample step in seconds, so grazing bodies cannot stall the search. */
    public static final double MIN_STEP = 0.25;

    private final RailsSnapshot snapshot;
    private final Trajectory anchor;
    private final String anchorParent;
    private final Set<String> excluded;
    private final double from;
    private final double horizon;
    private final double radius;

    /**
     * @param excluded UUIDs never reported (massive bodies, the player, bodies already in the bubble);
     *                 the set must not be changed while the task runs
     */
    public EncounterPredictionTask(RailsSnapshot snapshot, Trajectory anchor, String anchorParent,
                                   Set<String> excluded, double from, double horizon, double radius) {
        this.snapshot = snapshot;
        this.anchor = anchor;
        this.anchorParent = anchorParent;
        this.excluded = excluded;
        this.from = from;
        this.horizon = horizon;
        this.radius = radius;
    }

    @Override
    public EncounterForecast call() {
        Map<String, Double> entries = new LinkedHashMap<>();
        StateVector s = new StateVector();
        StateVector an = new StateVector();
        double until = from + horizon;
        for (RailsSnapshot.Entry e : snapshot.entries) {
            if (excluded.contains(e.uuid) || !anchorParent.equals(e.parentUuid)) continue;
            Trajectory body = e.trajectory;
            if (body.minRadius() > anchor.maxRadius() + radius || body.maxRadius() < anchor.minRadius() - radius) continue;
            double closing = body.maxSpeed() + anchor.maxSpeed();
            double t = from;
            double previous = from;
            while (t <= until) {
                body.stateAt(t, s);
                anchor.stateAt(t, an);
                double d = Math.hypot(s.x - an.x, s.y - an.y);
                if (d <= radius) {
                    // The last sample known to be outside: never later than the true crossing.
                    entries.put(e.uuid, previous);
                    break;
                }
                previous = t;
                t += Math.max(MIN_STEP, (d - radius) / closing);
            }
            if (Thread.currentThread().isInterrupted()) break;
        }
        return new EncounterForecast(from, until, entries);
    }
}
