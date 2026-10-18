package com.ringsofiron.macrosim.economy;

/**
 * Where a trader's goods physically sit: a ship's cargo bays. Volumes are cubic meters. The game
 * backs it with the ship's interior; a {@link TraderComponent} without one keeps an abstract hold.
 */
public interface CargoHold {
    /** Volume of the whole hold. */
    double capacity();

    /** Volume taken up by cargo. */
    double usedVolume();

    /** Units of {@code commodity} aboard. */
    double amount(String commodity);

    /** Units of {@code commodity} that still fit. Can be less than free volume allows, e.g. when space is partitioned. */
    double room(CommodityType commodity);

    /** Stows up to {@code units}; returns the units stowed. */
    double load(CommodityType commodity, double units);

    /** Takes up to {@code units} out; returns the units taken. */
    double unload(String commodity, double units);
}
