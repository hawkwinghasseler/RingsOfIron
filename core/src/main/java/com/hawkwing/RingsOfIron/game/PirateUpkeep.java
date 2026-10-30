package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.aicrew.pilot.NpcCombatComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.macrosim.hostile.HostileComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;

import java.util.Map;
import com.ringsofiron.coreengine.GameEngine;

/**
 * Pirates' upkeep on the rails (round 8). Round 7 made a hostile with dry tanks give up in a fight,
 * and pirates burn propellant coming alongside freighters, but only freighters refuelled: aces met
 * the player with empty tanks and gave up in the first second of their bounty fight (seed 2026's
 * Cold Teodor met the gunship with 0 of 4,000 kg). Now a pirate off on the rails refills its
 * propellant tanks, full in {@link #RAILS_REFUEL_DAYS}, and a robbery tops it up first from its
 * prey's own reaction mass ({@link #robbed}). In the bubble it burns as before; it takes on nothing
 * while it fights or stalks, and refills at the rails' rate only while it lies {@link #idle}.
 *
 * <p>Holds no state of its own beyond the clock it last ran at, which {@link HostileTraffic} saves:
 * what the tanks hold is saved with the ship's grid.
 */
public final class PirateUpkeep {
    /** Days on the rails for a pirate to fill its propellant tanks from empty. Placeholder number. */
    public static final double RAILS_REFUEL_DAYS = 2;
    static final double DAY = 24 * 3600.0;

    /**
     * Refuelling comes in whole steps of this many rails seconds, each on a multiple of it: however a
     * frame or a warp cuts the time up, the tanks take the same sums in the same order, so a career
     * replays to the bit, across a save too (round 8: refuelling by each frame's time drifted).
     */
    public static final double STEP = 60;

    private final GameEngine engine;
    /** The last step boundary refuelled, as rails time; NaN until it first runs (saved by {@link HostileTraffic}). */
    double last = Double.NaN;

    PirateUpkeep(GameEngine engine) {
        this.engine = engine;
    }

    /** Refuels every pirate off on the rails for each whole step passed since the last call. */
    void update(Map<String, HostileComponent> hostiles, double now) {
        double boundary = Math.floor(now / STEP) * STEP;
        if (Double.isNaN(last)) last = boundary;
        if (boundary <= last) return;
        int steps = (int) Math.round((boundary - last) / STEP);
        last = boundary;
        for (Map.Entry<String, HostileComponent> e : hostiles.entrySet()) {
            HostileComponent h = e.getValue();
            if (!h.isPirate() || h.moored()) continue;
            Entity ship = engine.getEntity(e.getKey());
            if (ship == null || !idle(ship)) continue;
            for (int i = 0; i < steps; i++) if (refuel(ship, STEP) <= 0) break;
        }
    }

    /**
     * True for a pirate that refuels now: one out on the rails, or one in the bubble that is neither
     * fighting nor stalking (no combat pilot) and not beaten. A pirate that ran its tanks dry in the
     * bubble can't fly out past its edge, so it waits there for its reserve to come back (round 8:
     * seeds 7 and 12 found aces drifting dry 15 km from the player's station for weeks).
     */
    static boolean idle(Entity ship) {
        if (ship.getComponent(PhysicsBodyComponent.class) == null) return true;
        return ship.getComponent(NpcCombatComponent.class) == null && !ShipDisablement.isDisabled(ship);
    }

    /** Puts {@code seconds} of rails refuelling into {@code ship}'s propellant tanks. Returns the kg taken on. */
    static double refuel(Entity ship, double seconds) {
        Array<SubsystemComponent> tanks = propellantTanks(ship);
        if (tanks == null) return 0;
        double share = seconds / (RAILS_REFUEL_DAYS * DAY), added = 0;
        for (SubsystemComponent t : tanks) {
            double more = Math.min(t.capacity - t.contents, t.capacity * share);
            if (more <= 0) continue;
            t.contents += more;
            added += more;
        }
        return added;
    }

    /**
     * Share of a pirate's empty tank space a robbery out on the rails fills, where its prey's tanks
     * are not modelled (a freighter has a grid only in the bubble). Placeholder number.
     */
    public static final double RAILS_ROBBERY_SHARE = 1;

    /**
     * {@code pirate} robbed {@code prey}: it tops up from the prey's tanks when the prey has them (in
     * the bubble), and by {@link #RAILS_ROBBERY_SHARE} of what it lacks when it has none (on the rails).
     * Returns the kg taken on.
     */
    static double robbed(Entity pirate, Entity prey) {
        if (propellantTanks(prey) != null) return topUpFrom(pirate, prey);
        Array<SubsystemComponent> ours = propellantTanks(pirate);
        if (ours == null) return 0;
        double added = 0;
        for (SubsystemComponent t : ours) {
            double more = Math.max(0, t.capacity - t.contents) * RAILS_ROBBERY_SHARE;
            t.contents += more;
            added += more;
        }
        return added;
    }

    /**
     * A pirate that robbed {@code prey} tops its tanks up from the prey's reaction mass first, as far
     * as the prey has it. Returns the kg moved.
     */
    static double topUpFrom(Entity pirate, Entity prey) {
        Array<SubsystemComponent> ours = propellantTanks(pirate), theirs = propellantTanks(prey);
        if (ours == null || theirs == null) return 0;
        double want = 0;
        for (SubsystemComponent t : ours) want += Math.max(0, t.capacity - t.contents);
        double moved = TankSupply.draw(theirs, Fluid.PROPELLANT, want);
        double left = moved;
        for (SubsystemComponent t : ours) {
            double more = Math.min(left, Math.max(0, t.capacity - t.contents));
            t.contents += more;
            left -= more;
        }
        return moved;
    }

    /** What share of its propellant tanks' capacity {@code ship} holds, 1 when it has none. */
    static double fuelShare(Entity ship) {
        Array<SubsystemComponent> tanks = propellantTanks(ship);
        if (tanks == null) return 1;
        double held = 0, capacity = 0;
        for (SubsystemComponent t : tanks) {
            held += t.contents;
            capacity += t.capacity;
        }
        return capacity <= 0 ? 1 : held / capacity;
    }

    private static Array<SubsystemComponent> propellantTanks(Entity ship) {
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry == null) return null;
        Array<SubsystemComponent> tanks = TankSupply.tanks(registry, Fluid.PROPELLANT);
        return tanks.size == 0 ? null : tanks;
    }
}
