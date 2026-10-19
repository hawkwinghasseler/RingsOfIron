package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.PowerCircuit;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.providers.ShipStatusProvider;

import java.util.function.Supplier;

/**
 * ui-hud's ship gauges from interior-grid: propellant and oxygen from the ship's tanks (#84),
 * structure as the hull's mean integrity (as the minimap's alert list counts it), and power from
 * the charge of its power circuits' stores and what their reactors produce.
 */
public class PlayerShipStatus implements ShipStatusProvider {
    private static final ComponentMapper<SubsystemComponent> SUBSYSTEMS = ComponentMapper.getFor(SubsystemComponent.class);

    private final Supplier<Entity> player;
    private final ShipStatus status = new ShipStatus();
    private final ShipAlerts alerts = new ShipAlerts();

    public PlayerShipStatus(Supplier<Entity> player) {
        this.player = player;
    }

    @Override
    public ShipStatus getShipStatus() {
        Entity ship = player.get();
        return ship == null ? null : read(ship, status, alerts);
    }

    /** Fills {@code out} with {@code ship}'s gauges. */
    static ShipStatus read(Entity ship, ShipStatus out, ShipAlerts alerts) {
        out.clear();
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        if (grid != null) {
            alerts.scan(grid);
            out.structure = alerts.hullIntegrity;
            out.cabinPressure = alerts.minPressure;
            double stored = 0, capacity = 0, output = 0;
            for (PowerCircuit c : grid.circuits) {
                stored += c.stored;
                capacity += c.capacity;
                output += c.output;
            }
            out.power = capacity > 0 ? (float) (stored / capacity) : 0f;
            out.powerOutputWatts = output;
        }
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        if (registry != null) {
            double propellant = 0, propellantCap = 0, oxygen = 0, oxygenCap = 0;
            for (Entity e : registry.subsystems) {
                SubsystemComponent t = SUBSYSTEMS.get(e);
                if (t == null || t.type != SubsystemComponent.SubsystemType.TANK) continue;
                if (t.fluid == Fluid.PROPELLANT) {
                    propellant += Math.max(0, t.contents);
                    propellantCap += t.capacity;
                } else if (t.fluid == Fluid.OXYGEN) {
                    oxygen += Math.max(0, t.contents);
                    oxygenCap += t.capacity;
                }
            }
            if (propellantCap > 0) {
                out.propellant = (float) (propellant / propellantCap);
                out.propellantKg = propellant;
            }
            if (oxygenCap > 0) out.oxygen = (float) (oxygen / oxygenCap);
        }
        return out;
    }
}
