package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.IntArray;

/**
 * One airtight volume of the interior: a room, a corridor segment or the gap of a door.
 * The air in it is a single well-mixed volume with one pressure and one composition.
 * Temperature is not uniform; it lives on the tiles (see {@link Tile#temperature}), and
 * the compartment's gas temperature is the mean over its tiles.
 *
 * Compartments are built by {@link CompartmentBuilder}. Openings one tile wide (doors,
 * 1-wide corridors, a single missing hull tile) do not merge two areas into one
 * compartment; they connect neighbouring compartments, and air flows through them.
 *
 * Gas amounts are moles. Atmosphere is conserved: it only leaves the ship when it is
 * vented to space through a breach, a hole in the deck or a leaking wall.
 */
public class Compartment {

    /** Universal gas constant, J/(mol K). */
    public static final double R = 8.314;
    /** Ambient temperature of the ship's structure and air, K. */
    public static final float AMBIENT_TEMPERATURE = 293.15f;
    /** Standard cabin pressure, kPa. */
    public static final double STANDARD_PRESSURE = 101.325;
    /** Oxygen share of standard cabin air. */
    public static final double STANDARD_O2_FRACTION = 0.21;
    /** Partial pressure of oxygen in standard cabin air, kPa. */
    public static final double STANDARD_O2_PRESSURE = STANDARD_PRESSURE * STANDARD_O2_FRACTION;
    /** Floor-to-ceiling height of a deck, meters. A tile's air volume is tileSize² × this. */
    public static final float DECK_HEIGHT = 2.5f;

    public final int id;
    /** Tiles in this compartment, packed as {@code y * grid.width + x}. */
    public final IntArray tiles = new IntArray();
    /** Air volume, m³. */
    public float volume;

    /** Gas amounts, mol. */
    public double oxygen, nitrogen, carbonDioxide;
    /** Suspended smoke, kg. Carried with the air when it flows. */
    public double smoke;
    /** Mean temperature of the compartment's tiles, K. Updated by {@link GridHazardSystem}. */
    public float temperature = AMBIENT_TEMPERATURE;

    public Compartment(int id) {
        this.id = id;
    }

    public double totalMoles() {
        return oxygen + nitrogen + carbonDioxide;
    }

    /** Pressure change per mole added, kPa/mol (ideal gas at the current temperature). */
    public double kPaPerMole() {
        return volume > 0f ? R * temperature / volume / 1000.0 : 0.0;
    }

    /** Total pressure, kPa. */
    public double pressure() {
        return totalMoles() * kPaPerMole();
    }

    /** Partial pressure of oxygen, kPa. */
    public double oxygenPressure() {
        return oxygen * kPaPerMole();
    }

    /** Pressure relative to standard cabin pressure (1.0 = normal, 0 = vacuum). */
    public float pressureRatio() {
        return (float) (pressure() / STANDARD_PRESSURE);
    }

    /**
     * Oxygen partial pressure relative to standard air (1.0 = normal). This is what
     * fire and breathing respond to, so it falls both when a room vents and when a fire
     * uses up the oxygen in a sealed room.
     */
    public float oxygenRatio() {
        return (float) (oxygenPressure() / STANDARD_O2_PRESSURE);
    }

    /** Share of the gas that is oxygen (0-1), or 0 in vacuum. */
    public float oxygenFraction() {
        double n = totalMoles();
        return n > 0 ? (float) (oxygen / n) : 0f;
    }

    /** Share of the gas that is carbon dioxide (0-1), or 0 in vacuum. */
    public float carbonDioxideFraction() {
        double n = totalMoles();
        return n > 0 ? (float) (carbonDioxide / n) : 0f;
    }

    /** Smoke density, kg/m³. Around 0.01 is thick smoke. */
    public float smokeDensity() {
        return volume > 0f ? (float) (smoke / volume) : 0f;
    }

    /** Fills the compartment with standard cabin air at its current temperature. */
    public void fillStandardAir() {
        double n = STANDARD_PRESSURE * 1000.0 * volume / (R * temperature);
        oxygen = n * STANDARD_O2_FRACTION;
        nitrogen = n * (1.0 - STANDARD_O2_FRACTION);
        carbonDioxide = 0.0;
        smoke = 0.0;
    }

    /** Removes the given share (0-1) of every gas and of the smoke. */
    void scaleContents(double keep) {
        oxygen *= keep;
        nitrogen *= keep;
        carbonDioxide *= keep;
        smoke *= keep;
    }
}
