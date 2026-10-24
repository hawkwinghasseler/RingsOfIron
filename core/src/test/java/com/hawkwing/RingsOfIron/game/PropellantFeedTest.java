package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.PowerRoutingSystem;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import org.junit.jupiter.api.Test;

/** Thrust and planned burns paid for out of the propellant tanks piped to the engines. */
class PropellantFeedTest {

    private final Engine engine = new Engine();
    private ShipGridComponent grid;
    private ThrusterComponent thruster;
    private Entity ship;

    /** An engine on the stern at (0, 1)-(1, 1), its flange against the hull, and two propellant tanks at x = 7 and 9, with a reactor. */
    private static ChassisBlueprint layout() {
        return new ChassisBlueprint(
                "  #########",
                "  #R......#",
                "  #....P.P#",
                "EE#....P.P#",
                "  #########");
    }

    private static ChassisBlueprint piped(ChassisBlueprint bp) {
        for (int x = 3; x <= 9; x++) bp.pipe(x, 1);
        return bp;
    }

    private void build(ChassisBlueprint bp) {
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new PropellantFeed());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        thruster = new ThrusterComponent(1e5f, 4e4f, 2e4f, 1e5f);
        thruster.exhaustVelocity = 1e4f;
        ship.add(r.grid);
        ship.add(registry);
        ship.add(thruster);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent sub = new SubsystemComponent(p.footprint, p.type);
            p.component.configure(sub);
            e.add(sub);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        grid = r.grid;
        grid.gridDirty = true;
        engine.update(0f);
    }

    private double tanks() {
        double kg = 0;
        for (SubsystemComponent t : com.ringsofiron.interiorgrid.TankSupply.tanks(
                ship.getComponent(ShipSubsystemRegistryComponent.class), Fluid.PROPELLANT)) kg += t.contents;
        return kg;
    }

    @Test
    void theThrustersGetWhatThePipedTanksHold() {
        build(piped(layout()));
        assertEquals(4000, thruster.fuel, 1e-3, "two S tanks of 2,000 kg");
        assertEquals(4000, thruster.fuelCapacity, 1e-3);
        assertEquals(1e-4f, thruster.fuelPerNewtonSecond, 1e-9f, "force over exhaust velocity");
        assertFalse(thruster.isOutOfFuel());
    }

    @Test
    void whatFlightControlBurnsComesOutOfTheTanks() {
        build(piped(layout()));
        thruster.fuel -= 250;
        engine.update(0.1f);
        assertEquals(3750, tanks(), 1e-3);
        assertEquals(3750, thruster.fuel, 1e-3);
    }

    @Test
    void unpipedTanksLeaveTheShipWithNoThrust() {
        build(layout());
        assertTrue(thruster.isOutOfFuel());
    }

    @Test
    void anEngineWithoutPowerFeedsNothing() {
        build(piped(layout()));
        for (com.badlogic.ashley.core.Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.REACTOR) s.switchedOn = false;
        }
        grid.gridDirty = true;
        engine.update(0.1f);
        assertTrue(thruster.isOutOfFuel());
    }

    @Test
    void aShipWithoutAnEngineKeepsFreeThrust() {
        build(new ChassisBlueprint(
                "#########",
                "#R......#",
                "#....P.P#",
                "#....P.P#",
                "#########"));
        assertEquals(0f, thruster.fuelPerNewtonSecond);
        assertFalse(thruster.isOutOfFuel());
        assertEquals(500, PropellantFeed.payForBurn(ship, 1e5, 500), 1e-9);
    }

    @Test
    void aPlannedBurnPaysByTheRocketEquation() {
        build(piped(layout()));
        double mass = 1e5, dv = 100;
        assertEquals(dv, PropellantFeed.payForBurn(ship, mass, dv), 1e-9);
        assertEquals(4000 - mass * (1 - Math.exp(-dv / 1e4)), tanks(), 1e-6);
    }

    @Test
    void aBurnTheTanksCannotCoverIsCutShort() {
        build(piped(layout()));
        double paid = PropellantFeed.payForBurn(ship, 1e5, 5000);
        assertEquals(1e4 * Math.log(1e5 / (1e5 - 4000)), paid, 1e-6);
        assertEquals(0, tanks(), 1e-9);
        assertEquals(0, PropellantFeed.payForBurn(ship, 1e5, 10), 1e-9);
    }
}
