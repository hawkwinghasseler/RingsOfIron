package com.ringsofiron.macrosim.economy;

/** A tradeable good. Identified by {@code id}; definitions come from a {@link CommodityCatalog}. */
public final class CommodityType {
    public final String id;
    public final String displayName;
    /** Price in credits per unit when supply exactly meets the station's target stock. */
    public final double basePrice;
    /** Cargo volume of one unit, in cubic meters. */
    public final double unitVolume;
    /** Mass of one unit, in kilograms. */
    public final double unitMass;

    public CommodityType(String id, String displayName, double basePrice, double unitVolume, double unitMass) {
        this.id = id;
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.unitVolume = unitVolume;
        this.unitMass = unitMass;
    }

    @Override
    public String toString() {
        return id;
    }
}
