package com.ringsofiron.uihud.minimap;

import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;

/** Summary of what is wrong aboard a ship, counted from its grid for the alert list. */
public class ShipAlerts {
    /** Compartment pressure ratio below which it counts as venting / depressurized. */
    public static final float LOW_PRESSURE = 0.7f;
    public static final float VACUUM_PRESSURE = 0.1f;
    /** Mean hull integrity below which the hull is flagged as damaged. */
    public static final float HULL_DAMAGED = 0.75f;

    public int burningTiles;
    public int breaches;
    public int lowPressureCompartments;
    public int vacuumCompartments;
    public int smokyCompartments;
    public float hullIntegrity = 1f;
    public float minPressure = 1f;

    public boolean any() {
        return burningTiles > 0 || breaches > 0 || lowPressureCompartments > 0 || smokyCompartments > 0
                || hullIntegrity < HULL_DAMAGED;
    }

    public ShipAlerts scan(ShipGridComponent grid) {
        burningTiles = breaches = lowPressureCompartments = vacuumCompartments = smokyCompartments = 0;
        hullIntegrity = 1f;
        minPressure = 1f;
        if (grid == null) return this;

        float hullSum = 0f;
        int hullCount = 0;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.getTile(x, y);
                if (t.isBurning()) burningTiles++;
                if (t.isHullBreach()) breaches++;
                if (t.wallType == Tile.WallType.HULL) {
                    hullSum += t.getIntegrity();
                    hullCount++;
                }
            }
        }
        if (hullCount > 0) hullIntegrity = hullSum / hullCount;

        for (Compartment c : grid.compartments) {
            float p = c.pressureRatio();
            minPressure = Math.min(minPressure, p);
            if (p < VACUUM_PRESSURE) vacuumCompartments++;
            else if (p < LOW_PRESSURE) lowPressureCompartments++;
            if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) smokyCompartments++;
        }
        if (vacuumCompartments > 0) lowPressureCompartments += vacuumCompartments;
        return this;
    }
}
