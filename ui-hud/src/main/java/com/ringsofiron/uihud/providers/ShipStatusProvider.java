package com.ringsofiron.uihud.providers;

/**
 * The player ship's gauges: propellant, structure, power and oxygen. Filled from interior-grid's
 * tanks, hull and power circuits by the game wiring layer; null leaves the gauges reading "--".
 */
public interface ShipStatusProvider {
    /** The ship's state this frame, or null with no ship. The HUD only reads it during the call. */
    ShipStatus getShipStatus();

    /** Plain data for one frame's gauges. A NaN share means the ship has nothing of that kind to measure. */
    final class ShipStatus {
        /** Share of the propellant tanks' capacity they hold, 0 to 1; NaN for a ship with no propellant tanks. */
        public float propellant = Float.NaN;
        public double propellantKg;
        /** Mean integrity of the hull, 0 to 1. */
        public float structure = 1f;
        /** Share of the power store that is charged, 0 to 1; 0 with no working reactor. */
        public float power = Float.NaN;
        /** Watts the working reactors produce. */
        public double powerOutputWatts;
        /** Share of the oxygen tanks' capacity they hold, 0 to 1; NaN for a ship with no oxygen tanks. */
        public float oxygen = Float.NaN;
        /** Pressure of the worst room aboard, as a share of normal, 0 to 1. */
        public float cabinPressure = 1f;

        public void clear() {
            propellant = Float.NaN;
            propellantKg = 0;
            structure = 1f;
            power = Float.NaN;
            powerOutputWatts = 0;
            oxygen = Float.NaN;
            cabinPressure = 1f;
        }
    }
}
