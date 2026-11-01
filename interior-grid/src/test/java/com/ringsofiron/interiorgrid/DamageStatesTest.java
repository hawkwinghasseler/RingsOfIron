package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

/** Short circuits and tank bursts. */
class DamageStatesTest {

    private final Engine engine = new Engine();
    private ShipSubsystemRegistryComponent registry;

    /** Every roll comes up: the chance always passes. */
    private static final RandomXS128 ALWAYS = new RandomXS128(1) {
        @Override
        public float nextFloat() {
            return 0f;
        }
    };

    /** No roll comes up. */
    private static final RandomXS128 NEVER = new RandomXS128(1) {
        @Override
        public float nextFloat() {
            return 0.999f;
        }
    };

    private ShipGridComponent ship(ChassisBlueprint bp) {
        engine.addSystem(new PowerRoutingSystem());
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        Entity ship = new Entity();
        registry = new ShipSubsystemRegistryComponent(ship);
        ship.add(r.grid);
        ship.add(registry);
        engine.addEntity(ship);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            SubsystemComponent s = new SubsystemComponent(p.footprint, p.type);
            if (p.component != null) p.component.configure(s);
            e.add(s);
            p.footprint.installed = e;
            engine.addEntity(e);
            registry.registerSubsystem(e);
        }
        CompartmentBuilder.rebuildIfChanged(r.grid);
        run(1);
        return r.grid;
    }

    private void run(float seconds) {
        for (int i = 0; i < Math.round(seconds / PowerRoutingSystem.CHARGE_TICK); i++) engine.update(PowerRoutingSystem.CHARGE_TICK);
    }

    @Test
    void aShortedReactorTripsItsCircuitAndDropsItsConsumersUntilReset() {
        ShipGridComponent grid = ship(blueprint());
        SubsystemComponent reactor = system(grid, SubsystemComponent.SubsystemType.REACTOR);
        SubsystemComponent life = system(grid, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        assertTrue(life.isOperational);
        PowerCircuit circuit = reactor.circuit;
        assertTrue(circuit.stored > 0);

        assertTrue(DamageStates.maybeShort(grid, reactor, 0.1f, 3, 2, ALWAYS));
        assertTrue(reactor.shorted);
        assertEquals(0, circuit.stored, 1e-9, "the breaker trips");
        engine.update(0f);
        assertFalse(life.isOperational, "no power for life support");
        assertTrue(grid.circuits.isEmpty(), "no working reactor");
        assertEquals(1, grid.shortCount);
        assertEquals(DamageStates.Incident.Kind.SHORT, grid.incidents.first().kind);

        DamageStates.reset(grid, reactor);
        engine.update(0f);
        assertFalse(reactor.shorted);
        assertTrue(life.isOperational, "back once it is reset");
    }

    @Test
    void aShortNobodyResetsClearsWhenItsBreakerRecloses() {
        ShipGridComponent grid = ship(new ChassisBlueprint(
                "##########",
                "#........#",
                "##########")
                .mount("reactor", MountFootprint.MountSize.S, 1, 1)
                .mount("helm", MountFootprint.MountSize.S, 6, 1, 1)
                .conduitRun(1, 1, 6, 1).tileSize(1f));
        SubsystemComponent reactor = system(grid, SubsystemComponent.SubsystemType.REACTOR);
        DamageStates.shortOut(grid, reactor, 1, 1, NEVER);
        DamageStates.shortOut(grid, 3, 1, NEVER);
        assertTrue(DamageStates.awaitingReset(grid, registry));
        run(DamageStates.AUTO_RESET - 1f);
        assertTrue(reactor.shorted, "still waiting on the crew");
        run(2f);
        assertFalse(reactor.shorted, "reclosed by itself");
        assertFalse(grid.getTileConduit(3, 1).shorted);
        assertFalse(DamageStates.awaitingReset(grid, registry));
        assertTrue(system(grid, SubsystemComponent.SubsystemType.HELM).isOperational);
    }

    @Test
    void anUnpoweredOrUntouchedSystemDoesNotShort() {
        ShipGridComponent grid = ship(blueprint());
        SubsystemComponent life = system(grid, SubsystemComponent.SubsystemType.LIFE_SUPPORT);
        assertFalse(DamageStates.maybeShort(grid, life, 0.1f, 3, 4, NEVER), "the roll failed");
        assertFalse(DamageStates.maybeShort(grid, life, 0f, 3, 4, ALWAYS), "no damage, no short");
        life.powered = false;
        assertFalse(DamageStates.maybeShort(grid, life, 0.1f, 3, 4, ALWAYS), "nothing to short without power");
        assertFalse(life.shorted);
    }

    @Test
    void aLightHitIsLessLikelyToShort() {
        ShipGridComponent grid = ship(blueprint());
        SubsystemComponent reactor = system(grid, SubsystemComponent.SubsystemType.REACTOR);
        float full = DamageStates.shortChance(reactor);
        RandomXS128 justOver = new RandomXS128(1) {
            @Override
            public float nextFloat() {
                return full * 0.6f;
            }
        };
        assertFalse(DamageStates.maybeShort(grid, reactor, DamageStates.SHORT_FULL_LOSS / 2f, 3, 2, justOver),
                "half the full loss, half the chance");
        assertTrue(DamageStates.maybeShort(grid, reactor, DamageStates.SHORT_FULL_LOSS, 3, 2, justOver));
    }

    @Test
    void aShortedConduitCutsPowerAlongItsRun() {
        ShipGridComponent grid = ship(new ChassisBlueprint(
                "##########",
                "#........#",
                "##########")
                .mount("reactor", MountFootprint.MountSize.S, 1, 1)
                .mount("helm", MountFootprint.MountSize.S, 6, 1, 1)
                .conduitRun(1, 1, 6, 1).tileSize(1f));
        SubsystemComponent helm = system(grid, SubsystemComponent.SubsystemType.HELM);
        assertTrue(helm.isOperational);
        grid.getTileConduit(3, 1).integrity = 0.7f;
        assertTrue(DamageStates.maybeShortConduit(grid, 3, 1, 0.3f, ALWAYS));
        assertTrue(grid.getTileConduit(3, 1).shorted);
        engine.update(0f);
        assertFalse(helm.powered, "the only way to the helm is cut");
        DamageStates.reset(grid, 3, 1);
        engine.update(0f);
        assertTrue(helm.isOperational);
    }

    @Test
    void sparksCanLightTheCarpetBesideAShort() {
        ShipGridComponent grid = ship(blueprint());
        SubsystemComponent reactor = system(grid, SubsystemComponent.SubsystemType.REACTOR);
        DamageStates.shortOut(grid, reactor, 3, 2, ALWAYS);
        assertTrue(grid.hasFire(), "a carpeted tile caught");
        assertTrue(grid.incidents.first().fire);
    }

    @Test
    void aPropellantTankBurstPoolsFuelAndLightsIt() {
        ShipGridComponent grid = ship(tankRoom("tank"));
        SubsystemComponent tank = system(grid, SubsystemComponent.SubsystemType.TANK);
        double kg = tank.contents;
        assertTrue(kg > 0);
        tank.health = 0.2f;
        assertTrue(DamageStates.rollBurst(tank, 0.05f, ALWAYS), "below the threshold");
        DamageStates.Incident burst = DamageStates.burst(grid, tank, ALWAYS);
        assertEquals(0, tank.contents, 1e-9);
        assertTrue(tank.isDestroyed());
        assertTrue(grid.hasFire(), "the spill caught");
        assertTrue(burst.fire);
        assertEquals(kg, burst.kg, 1e-6);
        assertTrue(burst.blastEnergy > 0f, "a 2 t tank is a large burst");
        assertTrue(burst.blastEnergy <= DamageStates.MAX_BLAST);
        float fuel = 0f;
        for (int x = 1; x <= 6; x++) for (int y = 1; y <= 3; y++) fuel += grid.getTile(x, y).fuel;
        assertTrue(fuel > 0f, "propellant on the deck");
        assertEquals(1, grid.burstCount);
    }

    @Test
    void aTankAboveItsThresholdOnlyLeaks() {
        ShipGridComponent grid = ship(tankRoom("tank"));
        SubsystemComponent tank = system(grid, SubsystemComponent.SubsystemType.TANK);
        tank.health = 0.4f;
        assertFalse(DamageStates.rollBurst(tank, 0.1f, ALWAYS));
        tank.contents = 0;
        tank.health = 0.1f;
        assertFalse(DamageStates.rollBurst(tank, 0.1f, ALWAYS), "nothing in it to burst");
    }

    @Test
    void aSpillInVacuumBoilsOff() {
        ShipGridComponent grid = ship(tankRoom("tank"));
        SubsystemComponent tank = system(grid, SubsystemComponent.SubsystemType.TANK);
        Compartment room = grid.compartmentAt(3, 2);
        room.oxygen = room.nitrogen = room.carbonDioxide = 0;
        float carpet = grid.getTile(3, 2).fuel;
        DamageStates.burst(grid, tank, ALWAYS);
        assertFalse(grid.hasFire());
        assertEquals(carpet, grid.getTile(3, 2).fuel, 1e-6f, "no propellant pooled");
    }

    @Test
    void anOxygenTankBurstFloodsItsRoom() {
        ShipGridComponent grid = ship(tankRoom("oxygen_tank"));
        SubsystemComponent tank = system(grid, SubsystemComponent.SubsystemType.TANK);
        Compartment room = grid.compartmentAt(3, 2);
        double before = room.oxygen;
        DamageStates.burst(grid, tank, NEVER);
        assertTrue(room.oxygen > before + 1000, "300 kg of oxygen into the room");
        assertEquals(0f, DamageStates.burst(grid, tank, NEVER).blastEnergy, "small, and empty now");
    }

    @Test
    void anIsolatedTankStopsLeakingAndStopsSupplying() {
        ShipGridComponent grid = ship(tankRoom("oxygen_tank"));
        SubsystemComponent tank = system(grid, SubsystemComponent.SubsystemType.TANK);
        tank.health = 0.4f;
        assertTrue(DamageStates.needsIsolating(tank));
        double before = tank.contents;
        TankSupply.leak(grid, registry, 10);
        assertTrue(tank.contents < before, "it leaks");
        DamageStates.isolate(tank);
        assertFalse(DamageStates.needsIsolating(tank));
        assertFalse(tank.canSupply());
        double held = tank.contents;
        TankSupply.leak(grid, registry, 10);
        assertEquals(held, tank.contents, 1e-9, "the shut valve holds");
        tank.health = 1f;
        DamageStates.repaired(grid, tank);
        assertTrue(tank.canSupply(), "a repair opens it again");
    }

    @Test
    void shortsAndIsolationSurviveASave() {
        ShipGridComponent grid = ship(new ChassisBlueprint(
                "##########",
                "#........#",
                "##########")
                .mount("reactor", MountFootprint.MountSize.S, 1, 1)
                .mount("helm", MountFootprint.MountSize.S, 6, 1, 1)
                .conduitRun(1, 1, 6, 1).tileSize(1f));
        grid.getTileConduit(3, 1).shorted = true;
        grid.getTileConduit(5, 1).integrity = 0.25f;
        ShipGridComponent back = WallLightSaveTest.roundTrip(grid);
        assertTrue(back.getTileConduit(3, 1).shorted);
        assertFalse(back.getTileConduit(4, 1).shorted);
        assertEquals(0.25f, back.getTileConduit(5, 1).integrity, 1e-6f, "the conduit's own condition");
        assertEquals(1f, back.getTile(5, 1).getIntegrity(), 1e-6f, "apart from the deck's");
        assertNull(back.getTileConduit(7, 1), "no conduit where none was laid");

        SubsystemComponent helm = system(grid, SubsystemComponent.SubsystemType.HELM);
        helm.shorted = true;
        helm.isolated = true;
        helm.shortChance = 0.5f;
        Json json = new Json();
        SubsystemComponent copy = json.fromJson(SubsystemComponent.class, json.toJson(helm));
        assertTrue(copy.shorted);
        assertTrue(copy.isolated);
        assertEquals(0.5f, copy.shortChance);
    }

    @Test
    void componentsCarryTheirOwnChances() {
        ShipPartCatalog.ComponentType tank = ShipPartCatalog.builtIn().component("tank");
        assertNotNull(tank);
        com.ringsofiron.moddingdata.defs.ShipComponentDef d = ShipPartCatalog.toDef(tank);
        d.burstChance = 0.9f;
        d.burstThreshold = 0.4f;
        d.shortChance = 0.2f;
        ShipPartCatalog.ComponentType custom = ShipPartCatalog.toComponentType(d);
        SubsystemComponent s = new SubsystemComponent(new MountFootprint(MountFootprint.MountKind.AUXILIARY,
                MountFootprint.MountSize.S, 0, 0, 0), SubsystemComponent.SubsystemType.TANK);
        custom.configure(s);
        assertEquals(0.9f, DamageStates.burstChance(s));
        assertEquals(0.4f, DamageStates.burstThreshold(s));
        assertEquals(0.2f, DamageStates.shortChance(s));
        assertEquals(0.9f, ShipPartCatalog.toDef(custom).burstChance);

        d.burstChance = 1.5f;
        assertThrows(IllegalArgumentException.class, () -> ShipPartCatalog.toComponentType(d));
    }

    /** A carpeted room with a reactor and a tank of the given component, in an S slot on its side. */
    private static ChassisBlueprint tankRoom(String tank) {
        ChassisBlueprint bp = new ChassisBlueprint(
                "########",
                "#......#",
                "#......#",
                "#......#",
                "########").tileSize(1f)
                .mount("reactor", MountFootprint.MountSize.S, 1, 3)
                .mount(tank, MountFootprint.MountSize.S, 3, 2, 1);
        bp.covering(FloorCovering.CARPET, 1, 1, 6, 3);
        return bp;
    }
}
