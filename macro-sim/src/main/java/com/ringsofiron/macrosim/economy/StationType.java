package com.ringsofiron.macrosim.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a kind of station makes and uses. One production cycle turns {@code inputs} into {@code outputs};
 * {@code upkeep} is consumed every day regardless (population needs food and water), all in units.
 */
public final class StationType {
    public final String id;
    public final Map<String, Double> inputsPerCycle;
    public final Map<String, Double> outputsPerCycle;
    public final Map<String, Double> upkeepPerDay;
    public final double cyclesPerDay;
    /** Days of throughput a station wants in stock of each good it touches; sets its price curve. */
    public final double targetStockDays;

    public StationType(String id, Map<String, Double> inputsPerCycle, Map<String, Double> outputsPerCycle,
                       Map<String, Double> upkeepPerDay, double cyclesPerDay, double targetStockDays) {
        this.id = id;
        this.inputsPerCycle = Collections.unmodifiableMap(new LinkedHashMap<>(inputsPerCycle));
        this.outputsPerCycle = Collections.unmodifiableMap(new LinkedHashMap<>(outputsPerCycle));
        this.upkeepPerDay = Collections.unmodifiableMap(new LinkedHashMap<>(upkeepPerDay));
        this.cyclesPerDay = cyclesPerDay;
        this.targetStockDays = targetStockDays;
    }

    /** Units per day this station moves of {@code commodity} (in or out), at full production. */
    public double dailyThroughput(String commodity) {
        double perCycle = inputsPerCycle.getOrDefault(commodity, 0.0) + outputsPerCycle.getOrDefault(commodity, 0.0);
        return perCycle * cyclesPerDay + upkeepPerDay.getOrDefault(commodity, 0.0);
    }

    public boolean produces(String commodity) {
        return outputsPerCycle.containsKey(commodity);
    }

    public boolean consumes(String commodity) {
        return inputsPerCycle.containsKey(commodity) || upkeepPerDay.containsKey(commodity);
    }
}
