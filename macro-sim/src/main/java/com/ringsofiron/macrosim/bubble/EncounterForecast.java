package com.ringsofiron.macrosim.bubble;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** Result of one {@link EncounterPredictionTask}: who may reach the bubble, and when, within its horizon. */
public final class EncounterForecast {
    /** Sim second the prediction was made from. */
    public final double from;
    /** Sim second the prediction covers up to. */
    public final double until;
    /** Body UUID to the earliest sim second it may come within the entry radius. */
    public final Map<String, Double> entries;
    /** Bodies of {@link #entries} that were already within the radius at {@link #from}. */
    public final Set<String> alreadyInside;
    /**
     * Earliest of {@link #entries} that is an outside-to-inside crossing, or {@code +inf}: where rails
     * warp must drop out. Bodies already inside do not count, or nothing near the bubble could warp.
     */
    public final double earliestEntry;

    public EncounterForecast(double from, double until, Map<String, Double> entries) {
        this(from, until, entries, Set.of());
    }

    public EncounterForecast(double from, double until, Map<String, Double> entries, Set<String> alreadyInside) {
        this.from = from;
        this.until = until;
        this.entries = Collections.unmodifiableMap(entries);
        this.alreadyInside = Collections.unmodifiableSet(alreadyInside);
        double earliest = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Double> e : entries.entrySet()) {
            if (!alreadyInside.contains(e.getKey())) earliest = Math.min(earliest, e.getValue());
        }
        this.earliestEntry = earliest;
    }
}
