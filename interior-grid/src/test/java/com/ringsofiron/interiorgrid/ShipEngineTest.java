package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.ringsofiron.coreengine.GameEngine;
import org.junit.jupiter.api.Test;

/** The interior systems running together under a {@link GameEngine}: power, warp and saving. */
class ShipEngineTest {

    private static final int TICKS_PER_SECOND = 60;

    private final GameEngine engine = new GameEngine();

    private ShipGridComponent addShip(GameEngine engine) {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(blueprint());
        Entity ship = engine.createEntityWithUuid();
        ShipSubsystemRegistryComponent registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = engine.createEntityWithUuid();
            e.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        return r.grid;
    }

    private static void addSystems(GameEngine engine) {
        engine.addSystem(new GridHazardSystem(1));
        engine.addSystem(new PowerRoutingSystem());
        engine.addSystem(new LifeSupportSystem());
    }

    @Test
    void theReactorPowersSystemsUntilItIsSwitchedOffOrWrecked() {
        addSystems(engine);
        ShipGridComponent g = addShip(engine);
        SubsystemComponent lifeSupport = system(g, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        SubsystemComponent reactor = system(g, SubsystemComponent.SubsystemType.REACTOR);
        engine.stepTicks(2 * TICKS_PER_SECOND);
        assertTrue(lifeSupport.isOperational);

        reactor.switchedOn = false;
        g.gridDirty = true;
        engine.stepTicks(2 * TICKS_PER_SECOND);
        assertFalse(lifeSupport.isOperational);

        reactor.switchedOn = true;
        reactor.health = SubsystemComponent.DISABLE_THRESHOLD / 2;
        g.gridDirty = true;
        engine.stepTicks(2 * TICKS_PER_SECOND);
        assertFalse(lifeSupport.isOperational, "a reactor below the damage threshold gives no power");
    }

    @Test
    void railsWarpIsRefusedDuringAFireOrABreach() {
        addSystems(engine);
        ShipGridComponent g = addShip(engine);
        engine.addRailsWarpGuard(new InteriorHazardWarpGuard(g));
        engine.stepTicks(TICKS_PER_SECOND);
        assertNull(engine.getRailsWarpBlockedReason());

        g.getTile(5, 1).ignite();
        engine.stepTicks(TICKS_PER_SECOND);
        assertEquals("Fire aboard", engine.getRailsWarpBlockedReason());
        assertFalse(engine.advanceRails(3600L * TICKS_PER_SECOND));

        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) g.getTile(x, y).extinguish();
        g.getTile(0, 3).setIntegrity(0f);
        engine.stepTicks(TICKS_PER_SECOND);
        assertEquals("Atmosphere venting", engine.getRailsWarpBlockedReason());
    }

    @Test
    void anHourOfRailsWarpLetsLifeSupportCleanTheAir() {
        addSystems(engine);
        ShipGridComponent g = addShip(engine);
        engine.stepTicks(TICKS_PER_SECOND);
        right(g).nitrogen -= 200;
        right(g).carbonDioxide += 200;
        for (int y = 0; y < g.height; y++) for (int x = 0; x < g.width; x++) g.getTile(x, y).temperature = 500f;
        double before = totalMoles(g);

        assertTrue(engine.advanceRails(3600L * TICKS_PER_SECOND));
        assertTrue(right(g).carbonDioxideFraction() < 0.001f, "CO2 " + right(g).carbonDioxideFraction());
        assertEquals(before, totalMoles(g), before * 0.01, "no air appears or vanishes in a sealed ship");
        assertEquals(Compartment.AMBIENT_TEMPERATURE, g.getTile(5, 1).temperature, 1f, "the heat has faded");
        assertFalse(g.hasFire());
    }

    @Test
    void aSaveRestoresTheInteriorAndItsSystems() {
        engine.getSerializationService().addParticipant(new InteriorGridSaveParticipant(engine));
        addSystems(engine);
        ShipGridComponent g = addShip(engine);
        engine.stepTicks(TICKS_PER_SECOND);
        door(g).bulkheadSealed = true;
        g.getTile(19, 1).setIntegrity(0.4f);
        g.getTile(5, 1).ignite();
        system(g, SubsystemComponent.SubsystemType.REACTOR).health = 0.6f;
        engine.stepTicks(10 * TICKS_PER_SECOND);
        String json = engine.getSerializationService().saveGame();

        GameEngine loaded = new GameEngine();
        loaded.getSerializationService().addParticipant(new InteriorGridSaveParticipant(loaded));
        loaded.getSerializationService().loadGame(json);
        assertTrue(loaded.getSerializationService().getLastLoadWarnings().isEmpty(),
                () -> "warnings " + loaded.getSerializationService().getLastLoadWarnings());

        Entity ship = loaded.getEcsEngine().getEntitiesFor(Family.all(ShipGridComponent.class).get()).first();
        ShipGridComponent copy = ship.getComponent(ShipGridComponent.class);
        assertEquals(g.compartments.size, copy.compartments.size);
        assertEquals(totalMoles(g), totalMoles(copy), 1e-6);
        assertEquals(left(g).carbonDioxide, left(copy).carbonDioxide, 1e-6);
        assertTrue(copy.getTile(5, 1).isBurning());
        assertEquals(g.getTile(5, 1).temperature, copy.getTile(5, 1).temperature, 1e-3f);
        assertEquals(0.4f, copy.getTile(19, 1).getIntegrity(), 1e-6f);
        assertTrue(door(copy).bulkheadSealed);

        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);
        assertSame(ship, registry.ownerEntity);
        assertEquals(g.mounts.size, registry.subsystems.size);
        assertEquals(0.6f, system(copy, SubsystemComponent.SubsystemType.REACTOR).health, 1e-6f,
                "mounts are linked back to their system entities");
    }
}
