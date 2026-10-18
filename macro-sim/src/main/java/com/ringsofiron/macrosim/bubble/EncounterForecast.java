package com.ringsofiron.macrosim.bubble;

import java.util.Collections;
import java.util.Map;

/** Result of one {@link EncounterPredictionTask}: who may reach the bubble, and when, within its horizon. */
public final class EncounterForecast {
    /** Sim second the prediction was made from. */
    public final double from;
    /** Sim second the prediction covers up to. */
    public final double until;
    /** Body UUID to the earliest sim second it may come within the entry radius. */
    public final Map<String, Double> entries;
    /** Earliest of {@link #entries}, or {@code +inf}: where rails warp must drop out. */
    public final double earliestEntry;

    public EncounterForecast(double from, double until, Map<String, Double> entries) {
        this.from = from;
        this.until = until;
        this.entries = Collections.unmodifiableMap(entries);
        double earliest = Double.POSITIVE_INFINITY;
        for (double t : entries.values()) earliest = Math.min(earliest, t);
        this.earliestEntry = earliest;
    }
}
