package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;

/**
 * How hard the game pushes back (GDD section 5): rises with the player's rank and their net worth,
 * credits plus the value of their ship. 1 for a new ensign in the starter corvette; encounters
 * (round 2 category 2) size themselves by it.
 */
public final class Difficulty {
    /** Difficulty each rank above the lowest adds. */
    public static final double PER_RANK = 0.25;
    /** Difficulty each doubling of net worth over {@link #BASE_WORTH} adds. */
    public static final double PER_DOUBLING = 0.5;
    /** Net worth that adds nothing: about the starter corvette and its starting credits. Placeholder. */
    public static final double BASE_WORTH = 300_000;
    /** What a ship's hull is worth per tile of its layout, besides its weapons. Placeholder. */
    public static final double HULL_PER_TILE = 1_000;

    private Difficulty() {
    }

    /** Difficulty from a rank (0 the lowest) and a net worth in credits; never below 1. */
    public static double of(int rank, double netWorth) {
        double worth = netWorth > BASE_WORTH ? Math.log(netWorth / BASE_WORTH) / Math.log(2) : 0;
        return 1 + PER_RANK * Math.max(0, rank) + PER_DOUBLING * worth;
    }

    /** The player's difficulty now. */
    public static double of(GameWorld world) {
        return of(world.standing.record().rank, netWorth(world.getPlayer()));
    }

    /** Credits aboard {@code ship} plus what the ship is worth. */
    public static double netWorth(Entity ship) {
        if (ship == null) return 0;
        TraderComponent trader = ship.getComponent(TraderComponent.class);
        return (trader == null ? 0 : trader.credits) + shipValue(ship);
    }

    /** A ship's value: its hull by the tile, and the new price of each weapon mounted. */
    public static double shipValue(Entity ship) {
        double value = 0;
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid != null) {
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    Tile t = grid.getTile(x, y);
                    if (t != null && !t.isVoid()) value += HULL_PER_TILE;
                }
            }
        }
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        if (weapons != null) for (WeaponMountComponent m : weapons.mounts) value += m.def == null ? 0 : m.def.price;
        WeaponMountComponent single = ship.getComponent(WeaponMountComponent.class);
        if (single != null && single.def != null) value += single.def.price;
        return value;
    }
}
