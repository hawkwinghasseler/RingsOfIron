package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.career.RankLadder;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.macrosim.economy.TraderComponent;
import com.ringsofiron.moddingdata.components.DefRefComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;

/**
 * How hard the game pushes back (GDD section 5): rises with the player's rank and their net worth,
 * credits plus the value of their ship. 1 for a new ensign in the starter corvette; encounters
 * (round 2 category 2) size themselves by it. Round 5 has it pick what the pirates fly too
 * ({@link HostileTraffic}): how many raiders and gunships, how many fly in each wing, and whether an
 * ace takes a gunship (once the player flies one).
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

    /** From this difficulty raiders fly in pairs: a Lieutenant, or an ensign grown rich. */
    public static final double PAIRS = 1.25;
    /** From this difficulty raiders fly in threes. */
    public static final double THREES = 2.0;

    private Difficulty() {
    }

    /** Raiders the pirate population keeps at difficulty {@code d}. */
    public static int raiders(double d) {
        return 1 + (int) Math.round(Math.max(0, d));
    }

    /** Pirate gunships the population keeps at difficulty {@code d}. */
    public static int gunships(double d) {
        return (int) Math.floor(Math.max(0, d));
    }

    /** How many raiders fly in a wing, the leader included, at difficulty {@code d}: lone ones at first. */
    public static int wingSize(double d) {
        return d >= THREES ? 3 : d >= PAIRS ? 2 : 1;
    }

    /**
     * True once the player flies a ship at least the size of {@code shipId}'s: from then on an ace
     * flies a pirate gunship ({@link PirateAces#gunshipAce}).
     */
    public static boolean fliesAtLeast(GameWorld world, String shipId) {
        Entity player = world.getPlayer();
        DefRefComponent ref = player == null ? null : player.getComponent(DefRefComponent.class);
        var content = world.getContent();
        if (ref == null || content == null) return false;
        ShipDef mine = content.defs.get("ship", ref.defId, ShipDef.class);
        ShipDef theirs = content.defs.get("ship", shipId, ShipDef.class);
        if (mine == null || theirs == null) return false;
        RankLadder ranks = world.standing.ranks();
        int a = ranks.chassisSize(mine.chassis), b = ranks.chassisSize(theirs.chassis);
        return a >= 0 && b >= 0 && a >= b;
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
