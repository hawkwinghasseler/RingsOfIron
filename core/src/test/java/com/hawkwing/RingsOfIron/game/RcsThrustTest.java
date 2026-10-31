package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.moddingdata.defs.ShipDef;
import com.ringsofiron.physicsexterior.modding.ShipPhysicsAssembler;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.physicsexterior.components.ThrusterComponent;
import com.ringsofiron.shipsystems.RcsThrust;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The RCS blocks on a ship's hull set how hard it turns, strafes and backs up, and which of them fire. */
class RcsThrustTest {
    static final String[] SHIPS = {"corvette_patrol", "freighter_hauler", "raider_light", "gunship_heavy", "frigate_patrol",
            "picket_patrol", "tug_salvage", "corsair_pirate", "interceptor_patrol", "courier_selene", "surveyor_selene", "barge_ore",
            "tanker_propellant", "hospital_selene", "marauder_pirate", "destroyer_patrol", "bulk_carrier_trader", "cruiser_patrol",
            "liner_passenger", "reaver_pirate", "carrier_patrol"};

    GameWorld world;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        world.loadContent(assets, assets.resolve("none"));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private Entity spawn(String id) {
        return world.getContent().factory.spawnShip(id, 0, 0, 0);
    }

    private RcsThrust.Authority authority(Entity ship) {
        Body body = world.docking.bodyOf(ship);
        return RcsThrust.authority(ship.getComponent(ShipGridComponent.class),
                RcsThrust.centerOfMass(body, new Vector2()), new RcsThrust.Authority());
    }

    @Test
    void eachShipsStandardBlocksTurnStrafeAndBackItUpEveryWay() {
        for (String id : SHIPS) {
            Entity ship = spawn(id);
            for (RcsThruster r : ship.getComponent(ShipGridComponent.class).rcsThrusters) {
                assertEquals(r.kind.standardThrust, r.nozzleThrust(), id + " " + r + " gives its kind's standard thrust");
            }
            RcsThrust.Authority a = authority(ship);
            assertTrue(a.retro > 0, id + " backs up");
            assertTrue(a.port > 0 && a.starboard > 0, id + " strafes both ways");
            assertTrue(a.ccw > 0 && a.cw > 0, id + " turns both ways");
        }
    }

    @Test
    void theShipFliesOnWhatItsBlocksGiveIt() {
        Entity ship = spawn("corvette_patrol");
        RcsThrust.Authority a = authority(ship);
        world.engine.getEcsEngine().getSystem(RcsThrust.class).update(0f);
        ThrusterComponent t = ship.getComponent(ThrusterComponent.class);
        assertEquals(a.torque(), t.maxTorque, a.torque() * 1e-4);
        assertEquals(a.lateral(), t.lateralThrust, a.lateral() * 1e-4);
        assertEquals(a.retro, t.reverseThrust, a.retro * 1e-4);
    }

    @Test
    void losingTheBowBlocksTurnsItSlower() {
        Entity ship = spawn("corvette_patrol");
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        double before = authority(ship).torque();
        int bow = 0;
        for (RcsThruster r : grid.rcsThrusters) bow = Math.max(bow, r.x);
        for (RcsThruster r : grid.rcsThrusters) if (r.x >= bow - 1) grid.getTile(r.x, r.y).setIntegrity(0f);
        // About half: the stern quads turn it as hard as the bow ones did
        assertTrue(authority(ship).torque() < before * 0.6, "the stern blocks alone turn it slower");
    }

    /** The screenshot's case: the corvette's starboard strafe block amidships wrecked, its corner quads still firing. */
    @Test
    void losingOneSidesStrafeBlockStillStrafesTheOtherWayAndPushesWithWhatFires() {
        Entity ship = spawn("corvette_patrol");
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        RcsThruster amidships = null;
        for (RcsThruster r : grid.rcsThrusters) if (r.kind == RcsThruster.Kind.LINEAR && r.side == Side.TOP) amidships = r;
        assertNotNull(amidships);
        RcsThrust.Authority whole = authority(ship);
        grid.getTile(amidships.x, amidships.y).setIntegrity(0f);
        world.engine.getEcsEngine().getSystem(RcsThrust.class).update(0f);
        ThrusterComponent t = ship.getComponent(ThrusterComponent.class);
        RcsThrust.Authority a = authority(ship);

        // Firing up out of the top hull pushes to starboard: that way only the corner quads' 100 kN nozzles are left
        assertTrue(a.starboard > 0 && a.starboard < whole.starboard * 0.5, "the quads alone strafe it to starboard, slower");
        assertEquals(whole.port, a.port, 1.0, "to port is untouched");
        assertEquals(a.port, t.lateral(1f), a.port * 1e-4, "and the ship flies on it");
        assertEquals(a.starboard, t.lateral(-1f), a.starboard * 1e-4);
        assertEquals(a.lateral(), t.lateralThrust, a.lateral() * 1e-4, "planners still go by the weaker way");
        assertEquals(a.ccw, t.torque(1f), a.ccw * 1e-4);
        assertEquals(a.cw, t.torque(-1f), a.cw * 1e-4);
    }

    @Test
    void aJetIsDrawnAsBigAsTheThrustItGives() {
        assertEquals(1f, RcsThrust.jetSize(new RcsThruster(0, 0, Side.TOP, RcsThruster.Kind.LINEAR)), 1e-6f, "a standard linear block's 300 kN");
        assertEquals((float) Math.sqrt(1.0 / 3.0), RcsThrust.jetSize(new RcsThruster(0, 0, Side.TOP, RcsThruster.Kind.QUAD)), 1e-6f,
                "a standard quad's 100 kN: a little over half as big");
        assertEquals(0.5f, RcsThrust.jetSize(new RcsThruster(0, 0, Side.TOP, RcsThruster.Kind.QUAD, 7.5e4)), 1e-6f);
        assertEquals(RcsThrust.MAX_JET, RcsThrust.jetSize(new RcsThruster(0, 0, Side.TOP, RcsThruster.Kind.LINEAR, 1e7)), 1e-6f,
                "however hard a nozzle pushes, its jet is drawn no more than twice as big");
    }

    @Test
    void theNozzlesThatHelpFireAndTheOthersDont() {
        Entity ship = spawn("corvette_patrol");
        ShipGridComponent grid = ship.getComponent(ShipGridComponent.class);
        Vector2 center = RcsThrust.centerOfMass(world.docking.bodyOf(ship), new Vector2());
        FlightControlComponent c = new FlightControlComponent();
        c.rotationAssist = false;
        RcsThruster bowTop = null;
        for (RcsThruster r : grid.rcsThrusters) {
            if (r.kind == RcsThruster.Kind.QUAD && r.side == Side.TOP && (bowTop == null || r.x > bowTop.x)) bowTop = r;
        }
        assertNotNull(bowTop);

        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0f), "nothing asked, nothing fires");
        c.turn = -1f;
        assertEquals(1f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0f),
                "firing up from the bow pushes the nose down: a clockwise turn");
        c.turn = 1f;
        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0f));
        c.turn = 0f;
        c.strafe = -0.5f;
        assertEquals(0.5f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0f), "a strafe to starboard, at half");
        c.strafe = 0f;
        c.forward = -1f;
        assertEquals(1f, RcsThrust.firing(grid, bowTop, Side.RIGHT, center, c, null, 0f), "firing forward backs it up");
        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.LEFT, center, c, null, 0f));

        c.forward = 0f;
        c.rotationAssist = true;
        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0.5f), "no trim from flight control yet");
        c.assistTurn = 1f;
        assertEquals(1f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0.5f), "assist stops a spin to port");
        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, -0.5f));

        c.assistTurn = 0.1f;
        assertEquals(0.1f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0.5f), 1e-6f,
                "a gentle trim fires gently, as flight control trims");

        c.turn = -1f;
        grid.getTile(bowTop.x, bowTop.y).setIntegrity(0f);
        assertEquals(0f, RcsThrust.firing(grid, bowTop, Side.TOP, center, c, null, 0f), "a wrecked block fires nothing");
    }
}
