package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;

/**
 * What a hit can do to machinery beyond wearing its health down (GDD section 3): short circuits
 * and tank bursts. Combat calls in here as it damages a system or a conduit; the crew put things
 * right with {@link #reset} and {@link #isolate}, and a repair to full health with
 * {@link #repaired}.
 *
 * <h2>Short circuits</h2>
 * A damaging hit on a powered system, or on a powered conduit tile, can short it, with
 * {@link #shortChance} (by component) scaled down for a light hit. A short trips the circuit it
 * was on (its store empties), may throw sparks that light a flammable tile beside it
 * ({@link #SHORT_FIRE_CHANCE}), and faults the part ({@link SubsystemComponent#shorted},
 * {@link Tile#shorted}): power routing leaves it out, so its consumers drop or reroute, until the
 * crew reset it or, failing that, its breaker recloses by itself after {@link #AUTO_RESET}.
 *
 * <h2>Tank bursts</h2>
 * A hit that leaves a tank below its {@link #burstThreshold} can rupture it, with
 * {@link #burstChance}. It vents everything at once: oxygen and nitrogen into the room it
 * stands in (a spike that can blow a light door), propellant over the deck around it, where it
 * is fuel for a fire, if the room is pressurised. The rupture's sparks can light it, and a large
 * one ({@link #LARGE_BURST_KG}) is a small blast as well, which combat sets off. Above the
 * threshold a damaged tank only leaks ({@link TankSupply#leak}), and the crew can isolate it.
 *
 * Every short and burst is listed on {@link ShipGridComponent#incidents} for notices.
 */
public final class DamageStates {

    /** Health a hit must take off a system for the full short chance; lighter hits scale it down. */
    public static final float SHORT_FULL_LOSS = 0.05f;
    /** Chance a hit that takes {@link #CONDUIT_FULL_LOSS} of a powered conduit tile's condition shorts it. */
    public static final float CONDUIT_SHORT_CHANCE = 0.05f;
    /** Condition a hit must take off a conduit tile for the full short chance. */
    public static final float CONDUIT_FULL_LOSS = 0.5f;
    /** Chance a short's sparks light a flammable tile at or beside it. */
    public static final float SHORT_FIRE_CHANCE = 0.3f;

    /** Standard health below which a hit can burst a tank. */
    public static final float BURST_THRESHOLD = 0.25f;
    /** Chance a burst's sparks light the propellant it spills, or the room an oxygen burst enriches. */
    public static final float BURST_FIRE_CHANCE = 0.5f;
    /** Propellant a burst leaves on each tile it reaches, kg per m² of deck; the rest boils off. */
    public static final float SPILL_FUEL_PER_AREA = 2f;
    /** Pressure relative to standard below which spilled propellant boils off instead of pooling. */
    public static final float SPILL_MIN_PRESSURE = 0.3f;
    /** A burst of at least this many kilograms is a small blast as well. */
    public static final double LARGE_BURST_KG = 500;
    /** Blast energy at the tank's tile per kilogram a large burst lets go, J. */
    public static final float BLAST_PER_KG = 500f;
    /** Most blast energy one burst gives, J: half of what kills an unprotected crew member. */
    public static final float MAX_BLAST = 1e6f;
    /** How far a large burst's blast reaches, tiles. */
    public static final int BLAST_RADIUS = 1;
    /**
     * Seconds after which a short nobody has reset clears by itself: its breaker recloses. The
     * crew reset it far sooner; this keeps a ship with nobody aboard to do it (or a short nobody
     * can reach) from staying dark for good.
     */
    public static final float AUTO_RESET = 45f;
    /** Incidents kept on a grid that nothing drains (a ship that is not the player's). */
    public static final int MAX_INCIDENTS = 16;

    private DamageStates() {
    }

    /** A short or a burst aboard, for notices. */
    public static final class Incident {
        public enum Kind { SHORT, BURST }

        public final Kind kind;
        /** The system shorted or burst; null for a shorted conduit tile. */
        public final SubsystemComponent system;
        /** The tile it happened on. */
        public final int x, y;
        /** Whether it started a fire. */
        public final boolean fire;
        /** BURST: what the tank held and how much of it went. */
        public final Fluid fluid;
        public final double kg;
        /** BURST: blast energy at its tile for combat to set off, J; 0 for no blast. */
        public final float blastEnergy;

        Incident(Kind kind, SubsystemComponent system, int x, int y, boolean fire, Fluid fluid, double kg, float blastEnergy) {
            this.kind = kind;
            this.system = system;
            this.x = x;
            this.y = y;
            this.fire = fire;
            this.fluid = fluid;
            this.kg = kg;
            this.blastEnergy = blastEnergy;
        }
    }

    // ------------------------------------------------------------------
    // Tuning by component
    // ------------------------------------------------------------------

    /** Chance a damaging hit shorts {@code s} at full strength: its component's, else the standard for its type. */
    public static float shortChance(SubsystemComponent s) {
        if (s.shortChance >= 0f) return s.shortChance;
        return standardShortChance(s.type);
    }

    /** The standard short chance for a type: reactors most, passive systems never. */
    public static float standardShortChance(SubsystemComponent.SubsystemType type) {
        switch (type) {
            case REACTOR: return 0.08f;
            case SHIELD_EMITTER: return 0.06f;
            case TANK:
            case CARGO:
            case ESCAPE_POD: return 0f;
            default: return 0.04f;
        }
    }

    /** Health below which a hit can burst tank {@code t}. */
    public static float burstThreshold(SubsystemComponent t) {
        return t.burstThreshold >= 0f ? t.burstThreshold : BURST_THRESHOLD;
    }

    /** Chance each hit below its threshold bursts tank {@code t}: its component's, else the standard for its fluid. */
    public static float burstChance(SubsystemComponent t) {
        if (t.burstChance >= 0f) return t.burstChance;
        return standardBurstChance(t.fluid);
    }

    /** The standard burst chance for a fluid: propellant tanks are the touchiest. */
    public static float standardBurstChance(Fluid fluid) {
        if (fluid == null) return 0f;
        switch (fluid) {
            case PROPELLANT: return 0.35f;
            case OXYGEN: return 0.3f;
            default: return 0.2f;
        }
    }

    // ------------------------------------------------------------------
    // Short circuits
    // ------------------------------------------------------------------

    /**
     * After a hit took {@code healthLoss} off system {@code s} on tile (x, y): rolls for a short and
     * shorts it out if it comes up. Only a powered system that still stands can short.
     *
     * @return true if it shorted
     */
    public static boolean maybeShort(ShipGridComponent grid, SubsystemComponent s, float healthLoss, int x, int y,
                                     RandomXS128 rng) {
        if (s.shorted || !s.powered || s.isDestroyed() || healthLoss <= 0f || rng == null) return false;
        float chance = shortChance(s) * Math.min(1f, healthLoss / SHORT_FULL_LOSS);
        if (chance <= 0f || rng.nextFloat() >= chance) return false;
        shortOut(grid, s, x, y, rng);
        return true;
    }

    /**
     * After a hit took {@code integrityLoss} off the conduit tile (x, y): rolls for a short. Only a
     * powered conduit that still stands can short.
     */
    public static boolean maybeShortConduit(ShipGridComponent grid, int x, int y, float integrityLoss, RandomXS128 rng) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.isPowerConduit() || t.shorted || !t.isPowered || t.getIntegrity() <= 0f
                || integrityLoss <= 0f || rng == null) return false;
        float chance = CONDUIT_SHORT_CHANCE * Math.min(1f, integrityLoss / CONDUIT_FULL_LOSS);
        if (rng.nextFloat() >= chance) return false;
        shortOut(grid, x, y, rng);
        return true;
    }

    /** Shorts system {@code s} out: trips its circuit, may light a fire at (x, y), and faults it. */
    public static void shortOut(ShipGridComponent grid, SubsystemComponent s, int x, int y, RandomXS128 rng) {
        if (s.circuit != null) s.circuit.stored = 0; // the breaker trips
        s.shorted = true;
        s.isOperational = false;
        grid.gridDirty = true;
        grid.shortCount++;
        boolean fire = rng != null && rng.nextFloat() < SHORT_FIRE_CHANCE && sparks(grid, x, y, rng);
        report(grid, new Incident(Incident.Kind.SHORT, s, x, y, fire, null, 0, 0f));
    }

    /** Shorts the conduit tile (x, y) out: it stops carrying power, and may light a fire. */
    public static void shortOut(ShipGridComponent grid, int x, int y, RandomXS128 rng) {
        Tile t = grid.getTile(x, y);
        if (t == null) return;
        t.shorted = true;
        grid.gridDirty = true;
        grid.shortCount++;
        boolean fire = rng != null && rng.nextFloat() < SHORT_FIRE_CHANCE && sparks(grid, x, y, rng);
        report(grid, new Incident(Incident.Kind.SHORT, null, x, y, fire, null, 0, 0f));
    }

    /** The crew reset a shorted system: it rejoins its circuit on the next reroute. */
    public static void reset(ShipGridComponent grid, SubsystemComponent s) {
        if (!s.shorted) return;
        s.shorted = false;
        s.shortedFor = 0f;
        grid.gridDirty = true;
    }

    /** The crew reset a shorted conduit tile. */
    public static void reset(ShipGridComponent grid, int x, int y) {
        Tile t = grid.getTile(x, y);
        if (t == null || !t.shorted) return;
        t.shorted = false;
        t.shortedFor = 0f;
        grid.gridDirty = true;
    }

    /**
     * True while anything aboard is shorted: like an ion stun, a fault that clears in moments (the
     * crew reset it, or its breaker recloses after {@link #AUTO_RESET}), so a ship that has lost
     * power or guns to it is not beaten yet.
     */
    public static boolean awaitingReset(ShipGridComponent grid, ShipSubsystemRegistryComponent registry) {
        return grid != null && anyShorted(grid, registry);
    }

    /**
     * Counts {@code seconds} on every shorted system and conduit of the ship and clears the ones
     * that have waited {@link #AUTO_RESET}. True if one cleared, and the ship must reroute.
     */
    public static boolean runDownShorts(ShipGridComponent grid, ShipSubsystemRegistryComponent registry, float seconds) {
        boolean cleared = false;
        if (registry != null) {
            for (com.badlogic.ashley.core.Entity e : registry.subsystems) {
                SubsystemComponent s = e == null ? null : e.getComponent(SubsystemComponent.class);
                if (s == null) continue;
                if (!s.shorted) {
                    s.shortedFor = 0f;
                    continue;
                }
                s.shortedFor += seconds;
                if (s.shortedFor >= AUTO_RESET) {
                    s.shorted = false;
                    s.shortedFor = 0f;
                    cleared = true;
                }
            }
        }
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                Tile t = grid.tiles[y][x];
                if (!t.shorted) continue;
                t.shortedFor += seconds;
                if (t.shortedFor >= AUTO_RESET) {
                    t.shorted = false;
                    t.shortedFor = 0f;
                    cleared = true;
                }
            }
        }
        return cleared;
    }

    /** True if any system or conduit aboard is shorted. */
    public static boolean anyShorted(ShipGridComponent grid, ShipSubsystemRegistryComponent registry) {
        if (registry != null) {
            for (com.badlogic.ashley.core.Entity e : registry.subsystems) {
                SubsystemComponent s = e.getComponent(SubsystemComponent.class);
                if (s != null && s.shorted) return true;
            }
        }
        for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) if (grid.tiles[y][x].shorted) return true;
        return false;
    }

    /**
     * Sparks at (x, y): light that tile if it can burn, else a flammable tile beside it that air
     * reaches. True if a fire started.
     */
    static boolean sparks(ShipGridComponent grid, int x, int y, RandomXS128 rng) {
        Tile here = grid.getTile(x, y);
        if (here != null && here.ignite()) return true;
        int start = rng.nextInt(4);
        Side[] sides = Side.values();
        for (int i = 0; i < sides.length; i++) {
            Side s = sides[(start + i) % sides.length];
            int nx = x + s.dx, ny = y + s.dy;
            Tile n = grid.getTile(nx, ny);
            if (n == null || here == null || !here.holdsAir() || !grid.canFlow(x, y, nx, ny)) continue;
            if (n.ignite()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Tanks
    // ------------------------------------------------------------------

    /**
     * After a hit took {@code healthLoss} off tank {@code t}: true if it bursts. Only a tank with
     * something in it, left below its burst threshold, can burst; each such hit rolls once.
     */
    public static boolean rollBurst(SubsystemComponent t, float healthLoss, RandomXS128 rng) {
        if (t.type != SubsystemComponent.SubsystemType.TANK || t.contents <= 0 || healthLoss <= 0f || rng == null) return false;
        if (t.health >= burstThreshold(t)) return false;
        return rng.nextFloat() < burstChance(t);
    }

    /**
     * Ruptures tank {@code t}: it is destroyed and lets everything go at once. Gas goes into the
     * room its footprint stands in; propellant pools on the deck of a pressurised room, where the
     * rupture may light it. Returns the incident, whose {@link Incident#blastEnergy} combat sets
     * off for a large burst.
     */
    public static Incident burst(ShipGridComponent grid, SubsystemComponent t, RandomXS128 rng) {
        double kg = Math.max(0, t.contents);
        t.contents = 0;
        t.health = 0f;
        t.isOperational = false;
        grid.gridDirty = true;
        grid.burstCount++;
        MountFootprint f = t.footprint;
        int cx = f.originX + (f.width() - 1) / 2, cy = f.originY + (f.height() - 1) / 2;
        Compartment room = roomOf(grid, f);
        boolean fire = false;
        if (room != null && t.fluid != null) {
            switch (t.fluid) {
                case PROPELLANT:
                    if (room.pressureRatio() >= SPILL_MIN_PRESSURE) {
                        Array<int[]> wet = spill(grid, f, room, kg);
                        if (wet.size > 0 && rng != null && rng.nextFloat() < BURST_FIRE_CHANCE) {
                            int[] at = wet.get(rng.nextInt(wet.size));
                            fire = grid.getTile(at[0], at[1]).ignite();
                        }
                    }
                    break;
                case OXYGEN:
                    room.oxygen += kg / t.fluid.molarMass;
                    // Sparks in air suddenly rich in oxygen light anything that will burn
                    fire = rng != null && rng.nextFloat() < BURST_FIRE_CHANCE && sparks(grid, cx, cy, rng);
                    break;
                default:
                    room.nitrogen += kg / t.fluid.molarMass;
                    break;
            }
        }
        float blast = kg >= LARGE_BURST_KG && room != null ? (float) Math.min(MAX_BLAST, kg * BLAST_PER_KG) : 0f;
        Incident incident = new Incident(Incident.Kind.BURST, t, cx, cy, fire, t.fluid, kg, blast);
        report(grid, incident);
        return incident;
    }

    /**
     * Pools up to {@code kg} of propellant over the footprint and the tiles of {@code room} beside
     * it, {@link #SPILL_FUEL_PER_AREA} a tile, as fuel. Returns the tiles it wet.
     */
    static Array<int[]> spill(ShipGridComponent grid, MountFootprint f, Compartment room, double kg) {
        Array<int[]> wet = new Array<>();
        float perTile = SPILL_FUEL_PER_AREA * grid.tileSize * grid.tileSize;
        double left = kg;
        for (int y = f.originY - 1; y <= f.originY + f.height() && left > 0; y++) {
            for (int x = f.originX - 1; x <= f.originX + f.width() && left > 0; x++) {
                if (!grid.inBounds(x, y) || grid.compartmentAt(x, y) != room) continue;
                Tile tile = grid.getTile(x, y);
                float add = (float) Math.min(left, perTile);
                tile.fuel += add;
                left -= add;
                wet.add(new int[]{x, y});
            }
        }
        return wet;
    }

    /** The first room with air among the footprint's tiles, or null. */
    static Compartment roomOf(ShipGridComponent grid, MountFootprint f) {
        for (int y = f.originY; y < f.originY + f.height(); y++) {
            for (int x = f.originX; x < f.originX + f.width(); x++) {
                if (!grid.inBounds(x, y)) continue;
                Compartment c = grid.compartmentAt(x, y);
                if (c != null) return c;
            }
        }
        return null;
    }

    /**
     * True if tank {@code t} wants isolating: it leaks ({@link TankSupply#leakRate}), has something
     * left, still stands and is not isolated yet.
     */
    public static boolean needsIsolating(SubsystemComponent t) {
        return t.type == SubsystemComponent.SubsystemType.TANK && !t.isolated && t.contents > 0
                && t.health > 0f && TankSupply.leakRate(t.health) > 0;
    }

    /** The crew shut a leaking tank's valve: it stops leaking, and supplies nothing until repaired. */
    public static void isolate(SubsystemComponent t) {
        t.isolated = true;
    }

    /** A system repaired to full health: a short is cleared and an isolated tank opened again. */
    public static void repaired(ShipGridComponent grid, SubsystemComponent s) {
        if (s.shorted || s.isolated) grid.gridDirty = true;
        s.shorted = false;
        s.shortedFor = 0f;
        s.isolated = false;
    }

    private static void report(ShipGridComponent grid, Incident incident) {
        grid.incidents.add(incident);
        while (grid.incidents.size > MAX_INCIDENTS) grid.incidents.removeIndex(0);
    }
}
