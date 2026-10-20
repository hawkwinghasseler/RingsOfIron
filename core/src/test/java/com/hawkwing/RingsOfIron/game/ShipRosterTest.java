package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.combatsys.components.WeaponMountComponent;
import com.ringsofiron.combatsys.components.WeaponsComponent;
import com.ringsofiron.combatsys.defs.WeaponDef;
import com.ringsofiron.interiorgrid.CargoHoldComponent;
import com.ringsofiron.interiorgrid.Fluid;
import com.ringsofiron.interiorgrid.PipeNetwork;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent.SubsystemType;
import com.ringsofiron.interiorgrid.TankSupply;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The ships hostile traffic flies, spawned from the base data the way the game spawns them. */
class ShipRosterTest {
    GameWorld world;
    GameScenario game;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Entity spawn(String id) {
        Entity ship = game.content.factory.spawnShip(id, 3_000f, 3_000f, 0f);
        // Two fixed ticks: power and pipes route, tanks fill the thrusters
        for (int i = 0; i < 3; i++) world.update(1f / 60f);
        return ship;
    }

    /** Forward acceleration at full thrust, m/s^2. */
    private static float acceleration(Entity ship) {
        return ship.getComponent(ThrusterComponent.class).forwardThrust / mass(ship);
    }

    private static float mass(Entity ship) {
        return PhysicsManager.getMass(ship.getComponent(PhysicsBodyComponent.class).body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"raider_light", "gunship_heavy"})
    void spawnsAsAWorkingShip(String id) {
        assertFalse(game.content.report.hasErrors(), game.content.report.toString());
        Entity ship = spawn(id);
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        assertNotNull(grid, "an interior from its chassis");
        ShipSubsystemRegistryComponent registry = ship.getComponent(ShipSubsystemRegistryComponent.class);

        int reactors = 0, helms = 0, consoles = 0, engines = 0;
        for (Entity e : registry.subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemType.REACTOR) reactors++;
            if (s.type == SubsystemType.HELM) helms++;
            if (s.type == SubsystemType.WEAPONS) consoles++;
            if (s.type == SubsystemType.ENGINE) {
                engines++;
                assertTrue(s.circuit != null, "engine powered");
            }
            if (s.type == SubsystemType.TURRET_MOUNT || s.type == SubsystemType.LIFE_SUPPORT) {
                assertTrue(s.circuit != null, s.type + " powered");
            }
        }
        assertEquals(1, reactors);
        assertEquals(1, helms);
        assertEquals(1, consoles);
        assertEquals(2, engines);

        // One pipe run joins both engines, every tank and life support
        assertEquals(1, grid.pipeNetworks.size, "one pipe network");
        PipeNetwork pipes = grid.pipeNetworks.first();
        int propellantTanks = TankSupply.tanks(registry, Fluid.PROPELLANT).size;
        assertTrue(propellantTanks >= 1);
        assertEquals(propellantTanks + 2, pipes.tanks.size, "every propellant tank, oxygen and nitrogen on the pipes");
        int onPipes = 0;
        for (SubsystemComponent s : pipes.systems) if (s.type == SubsystemType.ENGINE || s.type == SubsystemType.LIFE_SUPPORT) onPipes++;
        assertEquals(3, onPipes, "both engines and life support draw from the pipes");
        assertTrue(pipes.available(Fluid.PROPELLANT) > 0);
        assertTrue(pipes.available(Fluid.OXYGEN) > 0);
        assertTrue(pipes.available(Fluid.NITROGEN) > 0);
        assertFalse(ship.getComponent(ThrusterComponent.class).isOutOfFuel(), "the engines have propellant");

        // Every hardpoint is wired to a turret mount under it
        ShipDef def = game.content.defs.get("ship", id, ShipDef.class);
        WeaponsComponent weapons = ship.getComponent(WeaponsComponent.class);
        assertEquals(def.hardpoints.size(), weapons.mounts.size);
        for (WeaponMountComponent m : weapons.mounts) {
            assertNotNull(m.subsystem, m.def.id + " wired to a system");
            SubsystemComponent s = m.subsystem.getComponent(SubsystemComponent.class);
            assertEquals(SubsystemType.TURRET_MOUNT, s.type);
            assertTrue(s.circuit != null);
        }
        assertTrue(ship.getComponent(CargoHoldComponent.class) != null
                && ship.getComponent(CargoHoldComponent.class).bayTileCount() > 0, "a hold for loot");
        assertNotNull(ship.getComponent(DockingPortsComponent.class), "a port to be boarded through");
        assertTrue(GameScenario.floorTiles(grid).size() >= def.crew, "room for its crew");
    }

    @Test
    void theRaiderIsLighterAndQuickerThanTheCorvetteWithOneGunAndNoMissiles() {
        Entity raider = spawn("raider_light");
        Entity corvette = spawn(GameScenario.PLAYER_SHIP);
        float raiderMass = mass(raider), corvetteMass = mass(corvette);
        assertTrue(raiderMass < corvetteMass, raiderMass + " kg against " + corvetteMass);
        assertTrue(acceleration(raider) > 1.2f * acceleration(corvette),
                acceleration(raider) + " m/s2 against " + acceleration(corvette));
        WeaponsComponent weapons = raider.getComponent(WeaponsComponent.class);
        assertEquals(1, weapons.mounts.size);
        WeaponDef gun = weapons.mounts.first().def;
        assertEquals(WeaponDef.MountType.TURRET, gun.mount);
        assertFalse(gun.pointDefense, "it shoots at its target, not at missiles");
    }

    @Test
    void theGunshipIsHeavierThanTheCorvetteWithATurretAndMissiles() {
        Entity gunship = spawn("gunship_heavy");
        Entity corvette = spawn(GameScenario.PLAYER_SHIP);
        float gunshipMass = mass(gunship), corvetteMass = mass(corvette);
        assertTrue(gunshipMass > 1.3f * corvetteMass, gunshipMass + " kg against " + corvetteMass);
        assertTrue(acceleration(gunship) < acceleration(corvette), "slower to get going: " + gunshipMass + " kg, "
                + acceleration(gunship) + " m/s2 against " + acceleration(corvette));
        int launchers = 0, turrets = 0;
        for (WeaponMountComponent m : gunship.getComponent(WeaponsComponent.class).mounts) {
            if (m.def.mount == WeaponDef.MountType.LAUNCHER) launchers++;
            if (m.def.mount == WeaponDef.MountType.TURRET && !m.def.pointDefense) turrets++;
        }
        assertEquals(1, turrets, "the railgun");
        assertEquals(2, launchers);
    }
}
