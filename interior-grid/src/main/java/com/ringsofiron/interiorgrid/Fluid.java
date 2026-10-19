package com.ringsofiron.interiorgrid;

/**
 * What a {@link SubsystemComponent.SubsystemType#TANK} holds. Life support draws oxygen and
 * nitrogen from the tanks piped to it, and the engines draw propellant (see {@link PipeRouting}).
 * Tank contents are in kilograms.
 */
public enum Fluid {
    /** Breathing oxygen: life support tops rooms up with it and mixes it into fresh air. */
    OXYGEN(0.032, 150f),
    /** The inert part of fresh air, which life support mixes with oxygen to refill rooms. */
    NITROGEN(0.028, 150f),
    /** Reaction mass for the main engine and the RCS. */
    PROPELLANT(0, 1000f);

    /** kg per mol, for the gases life support releases as moles; 0 for propellant. */
    public final double molarMass;
    /** What a tank holds per tile of its footprint, kg, unless its component says otherwise. */
    public final float capacityPerTile;

    Fluid(double molarMass, float capacityPerTile) {
        this.molarMass = molarMass;
        this.capacityPerTile = capacityPerTile;
    }
}
