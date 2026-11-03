package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.DamageStates;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.uihud.providers.PowerStatusProvider;

/**
 * ui-hud's {@link PowerStatusProvider}: the player's circuits from interior-grid's power routing, and
 * each system that needs power and is not working, with why and when it comes back by itself.
 */
public class PlayerPowerStatus implements PowerStatusProvider {
    private final GameContext ctx;
    private final RoomNames rooms = new RoomNames();
    private Entity roomsFor;

    public PlayerPowerStatus(GameContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void collect(PowerFrame out) {
        Entity ship = ctx.player();
        ShipGridComponent grid = ship == null ? null : ship.getComponent(ShipGridComponent.class);
        ShipSubsystemRegistryComponent registry = ship == null ? null : ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (grid == null || registry == null) return;
        if (roomsFor != ship) {
            roomsFor = ship;
            rooms.reset();
        }
        for (PowerCircuit c : grid.circuits) {
            Circuit row = out.addCircuit();
            row.stored = c.stored;
            row.capacity = c.capacity;
            row.outputWatts = c.output;
        }
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s == null) continue;
            int at = s.circuit == null ? -1 : grid.circuits.indexOf(s.circuit, true);
            Circuit circuit = at >= 0 ? out.circuits.get(at) : null;
            if (circuit != null) circuit.systems++;
            if (s.type == SubsystemType.REACTOR) {
                double rated = s.ratedOutput > 0 ? s.ratedOutput : PowerRoutingSystem.standardOutput(s.footprint.size);
                out.ratedWatts += rated;
                if (circuit != null) {
                    circuit.reactors++;
                    circuit.ratedWatts += rated;
                }
            }
            if (!s.type.needsPower()) continue;
            Fault fault = fault(s);
            if (fault == null) continue;
            SystemRow row = out.addFault();
            row.system = label(s.type);
            row.fault = fault;
            row.health = s.health;
            row.timer = fault == Fault.SHORTED ? Math.max(0f, DamageStates.AUTO_RESET - s.shortedFor)
                    : fault == Fault.STUNNED ? s.stunned : Float.NaN;
            if (s.footprint != null) {
                row.tileX = s.footprint.originX;
                row.tileY = s.footprint.originY;
                row.room = rooms.at(grid, row.tileX, row.tileY);
            }
        }
        for (Circuit c : out.circuits) out.outputWatts += c.outputWatts;
        // Worst first
        out.faults.sort((a, b) -> a.fault.ordinal() - b.fault.ordinal());
    }

    /** Why {@code s} is not working, or null when it is. */
    static Fault fault(SubsystemComponent s) {
        if (s.isDestroyed()) return Fault.DESTROYED;
        if (s.isDisabled()) return Fault.DISABLED;
        if (s.shorted) return Fault.SHORTED;
        if (s.stunned > 0f) return Fault.STUNNED;
        if (!s.switchedOn) return Fault.OFF;
        if (!s.isOperational) return Fault.NO_POWER;
        return null;
    }

    /** The HUD's name for a kind of system, at most 10 characters. */
    static String label(SubsystemType type) {
        switch (type) {
            case REACTOR: return "REACTOR";
            case SHIELD_EMITTER: return "SHIELDS";
            case TURRET_MOUNT: return "TURRET";
            case LIFE_SUPPORT: return "LIFE SUP";
            case HELM: return "HELM";
            case WEAPONS: return "WEAPONS";
            case AIRLOCK: return "AIRLOCK";
            case ENGINE: return "ENGINE";
            case DOCKING_PORT: return "DOCK PORT";
            default: return type.name();
        }
    }
}
