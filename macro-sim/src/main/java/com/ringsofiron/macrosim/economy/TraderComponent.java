package com.ringsofiron.macrosim.economy;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;

/**
 * Credits and cargo of a ship that trades at station markets by hand (the player's ship). Cargo is
 * counted in units, like freighter capacity. Saved by core-engine's serializer like any component.
 */
public class TraderComponent implements Component {
    public double credits;
    /** Units of cargo the hold can carry, all commodities together. */
    public double cargoCapacity;
    /** Units carried by commodity id. */
    public HashMap<String, Double> cargo = new HashMap<>();

    public TraderComponent() {
    }

    public TraderComponent(double credits, double cargoCapacity) {
        this.credits = credits;
        this.cargoCapacity = cargoCapacity;
    }

    public double amount(String commodity) {
        return cargo.getOrDefault(commodity, 0.0);
    }

    public double cargoUsed() {
        double used = 0;
        for (double v : cargo.values()) used += v;
        return used;
    }

    public double freeCargo() {
        return Math.max(0, cargoCapacity - cargoUsed());
    }

    void add(String commodity, double amount) {
        EconomyNode.adjust(cargo, commodity, amount);
    }
}
