package com.ringsofiron.macrosim.economy;

import com.badlogic.ashley.core.Component;

import java.util.HashMap;

/**
 * Credits and cargo of a ship that trades at station markets by hand (the player's ship). Cargo
 * takes up volume, in cubic meters, by each commodity's volume per unit.
 *
 * <p>When {@link #hold} is set the goods are physically in it (the ship's cargo bays) and the
 * fields here only cache its capacity; otherwise this keeps an abstract hold of
 * {@link #cargoCapacity} m³ in {@link #cargo} and {@link #cargoVolume}. Saved by core-engine's
 * serializer like any component; the hold is reattached after a load.
 */
public class TraderComponent implements Component {
    public double credits;
    /** Volume the hold can carry, m³, all commodities together. Mirrors {@link #hold} when set. */
    public double cargoCapacity;
    /** Abstract hold only: units carried by commodity id. */
    public HashMap<String, Double> cargo = new HashMap<>();
    /** Abstract hold only: m³ taken by {@link #cargo}. */
    public double cargoVolume;
    /** The physical hold, or null for the abstract one. Not saved. */
    public transient CargoHold hold;

    public TraderComponent() {
    }

    public TraderComponent(double credits, double cargoCapacity) {
        this.credits = credits;
        this.cargoCapacity = cargoCapacity;
    }

    /** Keeps the trader's goods in a physical hold from now on. */
    public void attach(CargoHold hold) {
        this.hold = hold;
        cargoCapacity = hold.capacity();
    }

    public double amount(String commodity) {
        return hold != null ? hold.amount(commodity) : cargo.getOrDefault(commodity, 0.0);
    }

    /** Volume taken up by cargo, m³. */
    public double cargoUsed() {
        return hold != null ? hold.usedVolume() : cargoVolume;
    }

    /** Volume left, m³. */
    public double freeCargo() {
        double capacity = hold != null ? hold.capacity() : cargoCapacity;
        return Math.max(0, capacity - cargoUsed());
    }

    /** Units of {@code commodity} that still fit; none for goods without a volume per unit. */
    public double room(CommodityType commodity) {
        if (hold != null) return hold.room(commodity);
        return commodity.unitVolume > 0 ? freeCargo() / commodity.unitVolume : 0;
    }

    /**
     * Stows up to {@code units} that change hands outside a market, such as cargo recovered from a
     * wreck; returns the units stowed.
     */
    public double stow(CommodityType commodity, double units) {
        return load(commodity, units);
    }

    /** Takes out up to {@code units} that change hands outside a market; returns the units taken. */
    public double handOver(CommodityType commodity, double units) {
        return unload(commodity, units);
    }

    /** Stows up to {@code units}; returns the units stowed. */
    double load(CommodityType commodity, double units) {
        if (hold != null) return hold.load(commodity, units);
        double qty = Math.min(units, room(commodity));
        if (qty <= 0) return 0;
        EconomyNode.adjust(cargo, commodity.id, qty);
        cargoVolume += qty * commodity.unitVolume;
        return qty;
    }

    /** Takes up to {@code units} out; returns the units taken. */
    double unload(CommodityType commodity, double units) {
        if (hold != null) return hold.unload(commodity.id, units);
        double qty = Math.min(units, amount(commodity.id));
        if (qty <= 0) return 0;
        EconomyNode.adjust(cargo, commodity.id, -qty);
        cargoVolume = Math.max(0, cargoVolume - qty * commodity.unitVolume);
        return qty;
    }
}
