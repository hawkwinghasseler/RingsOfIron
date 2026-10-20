package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

class PowerRoutingSystemTest {

    private final Engine engine = new Engine();
    private ShipGridComponent grid;

    private ShipGridComponent ship() {
        engine.addSystem(new PowerRoutingSystem());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(blueprint());
        Entity ship = new Entity();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            e.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        grid = r.grid;
        run(1);
        return grid;
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / PowerRoutingSystem.CHARGE_TICK); i++) engine.update(PowerRoutingSystem.CHARGE_TICK);
    }

    private SubsystemComponent reactor() {
        return system(grid, SubsystemComponent.SubsystemType.REACTOR);
    }

    private SubsystemComponent lifeSupport() {
        return system(grid, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
    }

    @Test
    void aStunnedSystemIsOutUntilItsStunRunsDown() {
        ship();
        SubsystemComponent life = lifeSupport();
        life.stunned = 2f;
        grid.gridDirty = true;
        engine.update(0f);
        assertFalse(life.isOperational, "stunned");
        assertTrue(life.powered, "though power still reaches it");
        run(1);
        assertFalse(life.isOperational);
        run(1.25f);
        assertEquals(0f, life.stunned);
        assertTrue(life.isOperational, "back by itself, nothing to repair");
        assertEquals(1f, life.health);
    }

    @Test
    void aStunnedReactorFeedsNothingUntilItComesBack() {
        ship();
        reactor().stunned = 1f;
        grid.gridDirty = true;
        engine.update(0f);
        assertEquals(0, grid.circuits.size, "no circuit while the only reactor is stunned");
        assertFalse(lifeSupport().powered);
        run(1.25f);
        assertEquals(1, grid.circuits.size, "rerouted when the stun ran out");
        assertTrue(lifeSupport().isOperational);
    }

    @Test
    void oneReactorFeedsOneCircuitWithAFullStore() {
        ship();
        assertEquals(1, grid.circuits.size);
        PowerCircuit c = grid.circuits.first();
        assertEquals(PowerRoutingSystem.reactorOutput(reactor()), c.output, 1e-6);
        assertEquals(c.output * PowerRoutingSystem.STORE_SECONDS, c.capacity, 1e-6);
        assertEquals(1f, c.charge(), 1e-6f);
        assertSame(c, lifeSupport().circuit);
    }

    @Test
    void drawsEmptyTheStoreAndItRecharges() {
        ship();
        PowerCircuit c = grid.circuits.first();
        assertTrue(lifeSupport().tryDrawPower(c.capacity * 0.75));
        assertFalse(lifeSupport().tryDrawPower(c.capacity * 0.5), "not enough left");
        assertEquals(0.25f, c.charge(), 1e-6f);
        run(1);
        assertEquals(0.75f, c.charge(), 1e-3f, "one second of output refills half the store");
        run(5);
        assertEquals(1f, c.charge(), 1e-6f);
    }

    @Test
    void storedEnergySurvivesARerouteButNotTheReactor() {
        ship();
        PowerCircuit c = grid.circuits.first();
        c.draw(c.capacity);
        grid.gridDirty = true;
        engine.update(0f);
        assertSame(c, grid.circuits.first(), "the same circuit while the layout is unchanged");
        assertEquals(0f, c.charge(), 1e-6f, "the empty store stays empty");

        reactor().switchedOn = false;
        grid.gridDirty = true;
        engine.update(0f);
        assertEquals(0, grid.circuits.size);
        assertNull(lifeSupport().circuit);
        assertFalse(lifeSupport().tryDrawPower(1));
    }

    @Test
    void aDamagedReactorMakesLessPowerSoTheStoreFillsMoreSlowly() {
        ship();
        reactor().health = 0.5f;
        grid.gridDirty = true;
        run(PowerRoutingSystem.CHARGE_TICK);
        PowerCircuit c = grid.circuits.first();
        assertEquals(PowerRoutingSystem.REACTOR_OUTPUT_S * 0.5, c.output, 1e-6, "the test ship's reactor is small");
        c.draw(c.capacity);
        run(1);
        assertEquals(c.output, c.stored, 1e-3);
    }

    @Test
    void aCutGridLeavesSystemsBeyondTheCutWithoutPower() {
        ship();
        SubsystemComponent helm = system(grid, SubsystemComponent.SubsystemType.HELM);
        assertTrue(helm.isOperational);
        // The helm is in the right room, reached only through the door and the conduit through the wall
        door(grid).bulkheadSealed = true;
        grid.getTile(9, 5).setIntegrity(0f);
        grid.getTile(10, 5).setIntegrity(0f);
        grid.gridDirty = true;
        engine.update(0f);
        assertFalse(helm.powered);
        assertNull(helm.circuit);
        assertTrue(lifeSupport().powered, "the reactor's own room still has power");
    }
}
