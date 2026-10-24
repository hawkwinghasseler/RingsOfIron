package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipSubsystemRegistryComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The engines in a ship's layout set its thrust, and lose it to damage. */
class EngineThrustTest {
    static final float FRAME = 1f / 60f;

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

    @Test
    void theCorvetteAndFreighterPushAsHardAsTheyDidBeforeEnginesSetThrust() {
        world.update(FRAME);
        assertThrust(game.player, GameScenario.PLAYER_SHIP);
        Entity hauler = world.getContent().factory.spawnShip(GameScenario.FREIGHTER_SHIP, 0f, -300f, 0f);
        world.update(FRAME);
        assertThrust(hauler, GameScenario.FREIGHTER_SHIP);
        Array<SubsystemComponent> engines = engines(hauler);
        assertEquals(2, engines.size);
        for (SubsystemComponent e : engines) {
            assertEquals("freight_drive", e.component);
            assertEquals(MountFootprint.MountSize.M, e.footprint.size);
            assertEquals(1.5e6, EngineThrust.ratedThrust(e, world.getContent().defs), 1.0, "from components.toml");
        }
    }

    @Test
    void aDamagedEngineGivesLessAndADestroyedOneNone() {
        world.update(FRAME);
        ThrusterComponent t = game.player.getComponent(ThrusterComponent.class);
        Array<SubsystemComponent> engines = engines(game.player);
        assertEquals(2, engines.size);
        for (SubsystemComponent e : engines) {
            assertEquals(MountFootprint.MountSize.S, e.footprint.size);
            assertEquals(EngineThrust.THRUST_S, EngineThrust.ratedThrust(e, world.getContent().defs), 1.0);
        }
        float retro = t.reverseThrust;
        engines.get(0).health = 0.5f;
        world.update(FRAME);
        assertEquals(1.2e6 + 0.6e6, t.forwardThrust, 1.0);
        engines.get(0).health = 0f;
        world.update(FRAME);
        assertEquals(1.2e6, t.forwardThrust, 1.0, "one engine left");
        assertEquals(retro, t.reverseThrust, 1.0, "the RCS is not the main engine");
        engines.get(1).switchedOn = false;
        world.update(FRAME);
        assertEquals(0f, t.forwardThrust, 1e-3f, "both out");
    }

    @Test
    void theStandardEnginesGrowWithTheirSize() {
        double last = 0;
        for (MountFootprint.MountSize size : MountFootprint.MountSize.values()) {
            double n = EngineThrust.standardThrust(size);
            assertTrue(n > last, size.name());
            last = n;
        }
        assertEquals(1.2e6, EngineThrust.standardThrust(MountFootprint.MountSize.S), 1.0);
    }

    @Test
    void eachMakersEnginesSpendPropellantAtTheirOwnEfficiency() {
        world.update(FRAME);
        assertEquals(1f, game.player.getComponent(ThrusterComponent.class).efficiency, 1e-6f, "Halvorsen HR-40s: the standard");
        Entity hauler = world.getContent().factory.spawnShip(GameScenario.FREIGHTER_SHIP, 0f, -300f, 0f);
        Entity gunship = world.getContent().factory.spawnShip("gunship_heavy", 0f, 300f, 0f);
        world.update(FRAME);
        assertEquals(1.15f, hauler.getComponent(ThrusterComponent.class).efficiency, 1e-5f, "HR-25F freight drives go further");
        assertEquals(0.9f, gunship.getComponent(ThrusterComponent.class).efficiency, 1e-5f, "Imbrium Ramparts are thirsty");
        // A propellant feed prices a newton-second at the exhaust velocity times that
        ThrusterComponent t = hauler.getComponent(ThrusterComponent.class);
        world.update(FRAME);
        assertEquals(1.0 / (t.exhaustVelocity * 1.15), t.fuelPerNewtonSecond, 1e-6 / t.exhaustVelocity);
    }

    @Test
    void anImbriumDriveIsCasedInMediumHull() {
        Entity gunship = world.getContent().factory.spawnShip("gunship_heavy", 0f, 300f, 0f);
        world.update(FRAME);
        ShipGridComponent grid = gunship.getComponent(ShipGridComponent.class);
        Array<SubsystemComponent> engines = engines(gunship);
        assertFalse(engines.isEmpty());
        for (SubsystemComponent e : engines) {
            assertEquals("gunship_drive", e.component);
            MountFootprint m = e.footprint;
            for (int y = m.originY; y < m.originY + m.height(); y++) {
                for (int x = m.originX; x < m.originX + m.width(); x++) {
                    assertEquals(com.ringsofiron.interiorgrid.WeightClass.MEDIUM, grid.getTile(x, y).weightClass);
                }
            }
        }
        for (SubsystemComponent e : engines(game.player)) {
            assertEquals(com.ringsofiron.interiorgrid.WeightClass.LIGHT,
                    game.player.getComponent(ShipGridComponent.class).getTile(e.footprint.originX, e.footprint.originY).weightClass,
                    "a Halvorsen's casing is light");
        }
    }

    private void assertThrust(Entity ship, String shipId) {
        ShipDef def = world.getContent().defs.get("ship", shipId, ShipDef.class);
        ThrusterComponent t = ship.getComponent(ThrusterComponent.class);
        // Within 10% of max_thrust, which it flew on before (in fact exactly)
        assertEquals(def.maxThrust, t.forwardThrust, def.maxThrust * 0.001f, shipId);
        // Its retro and strafing thrust are its RCS blocks' (RcsThrustTest)
        assertTrue(ship.getComponent(ShipGridComponent.class).rcsThrusters.size > 0, shipId);
    }

    private static Array<SubsystemComponent> engines(Entity ship) {
        Array<SubsystemComponent> out = new Array<>();
        for (Entity e : ship.getComponent(ShipSubsystemRegistryComponent.class).subsystems) {
            SubsystemComponent s = e.getComponent(SubsystemComponent.class);
            if (s.type == SubsystemComponent.SubsystemType.ENGINE) out.add(s);
        }
        return out;
    }
}
