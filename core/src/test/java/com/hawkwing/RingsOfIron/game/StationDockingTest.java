package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import com.ringsofiron.stationsdocking.DockingSystem;
import com.ringsofiron.stationsdocking.StationComponent;
import com.ringsofiron.uihud.providers.TradeProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** The player docking at Highport in the starting game: clearance, the collar, trading, saves and undocking. */
class StationDockingTest {
    static final float FRAME = 1f / 60f;
    private static final float U = PhysicsManager.UNITS_PER_METER;

    GameWorld world;
    GameScenario game;
    Entity highport;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"));
        highport = world.engine.getEntity(game.highport);
        // Long enough for the power to be routed aboard
        for (int i = 0; i < 90; i++) world.update(FRAME);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void stationsHaveInteriorsAndPortsOfEverySize() {
        for (String uuid : new String[]{game.highport, game.depot, game.lunarMine}) {
            Entity station = world.engine.getEntity(uuid);
            assertNotNull(station.getComponent(StationComponent.class));
            assertNotNull(station.getComponent(ShipGridComponent.class), "a walkable interior");
            DockingPortsComponent ports = station.getComponent(DockingPortsComponent.class);
            for (MountFootprint.MountSize size : new MountFootprint.MountSize[]{MountFootprint.MountSize.S, MountFootprint.MountSize.M, MountFootprint.MountSize.L}) {
                assertTrue(java.util.Arrays.stream(ports.ports).anyMatch(p -> p.size == size), size + " port");
            }
        }
        assertEquals(6, highport.getComponent(DockingPortsComponent.class).ports.length, "Highport is a hub");
        assertNotNull(game.player.getComponent(DockingPortsComponent.class), "the corvette has its port");
        assertNotNull(highport.getComponent(KinematicTransientComponent.class).body, "Highport is in the bubble");
    }

    @Test
    void thePlayerDocksAtHighportTradesAndUndocks() {
        TradeProvider.TradeBoard board = new TradeProvider.TradeBoard();
        MarketTrade market = new MarketTrade(world);
        int stationPort = nearestPort(MountFootprint.MountSize.S);
        parkBeside(stationPort, 1.5f);
        market.collect(board);
        assertFalse(board.isDocked(), "no trading before docking, however close");

        assertNull(world.docking.arm(game.player, 0));
        DockingSystem.Clearance clearance = world.docking.requestClearance(game.player, 0);
        assertEquals(DockingSystem.Clearance.Outcome.GRANTED, clearance.outcome);
        assertSame(highport, clearance.station);
        assertEquals(stationPort, clearance.port);
        assertEquals("docking in progress", waitForCollar());

        for (int i = 0; i < 60 * 40 && !world.docking.isRigid(game.player); i++) world.update(FRAME);
        assertTrue(world.docking.isRigid(game.player), "Highport's collar drew the ship in and latched");
        assertSame(highport, world.docking.dockedTo(game.player));
        board.clear();
        market.collect(board);
        assertEquals("Highport", board.stationName);

        // Docked through a save and load: the latch comes back.
        String save = world.engine.getSerializationService().saveGame();
        world.engine.getSerializationService().loadGame(save);
        game.player = world.getPlayer();
        highport = world.engine.getEntity(game.highport);
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertSame(highport, world.docking.dockedTo(game.player), "still docked after loading");
        assertTrue(world.docking.isRigid(game.player), "welded again");

        assertTrue(world.docking.undock(game.player));
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertNull(world.docking.dockedTo(game.player));
        board.clear();
        market.collect(board);
        assertFalse(board.isDocked(), "undocked: the market closes");
        Vector2 apart = face(body(game.player), port(game.player, 0)).sub(face(stationBody(), port(highport, stationPort)));
        assertTrue(apart.len() > 0.3f, "the springs pushed the ship off (" + apart.len() + " m)");
    }

    @Test
    void railsWarpWaitsForTheCollarAndTakesTheDockedShipAlong() {
        int stationPort = nearestPort(MountFootprint.MountSize.S);
        parkBeside(stationPort, 1.5f);
        world.docking.arm(game.player, 0);
        world.docking.requestClearance(game.player, 0);
        waitForCollar();
        assertFalse(world.timeWarp.step(1) && world.timeWarp.isRailsWarp(), "no rails warp while the collar draws the ship in");
        while (world.timeWarp.getWarp() > 1) world.timeWarp.step(-1);
        for (int i = 0; i < 60 * 40 && !world.docking.isRigid(game.player); i++) world.update(FRAME);
        assertTrue(world.docking.isRigid(game.player));

        for (int i = 0; i < 20 && !world.timeWarp.isRailsWarp(); i++) {
            assertTrue(world.timeWarp.step(1), String.valueOf(world.engine.getRailsWarpBlockedReason()));
            world.update(FRAME);
        }
        assertTrue(world.timeWarp.isRailsWarp());
        double before = world.engine.getSimSeconds();
        for (int i = 0; i < 30; i++) world.update(FRAME);
        assertTrue(world.engine.getSimSeconds() - before > 10, "time went by on the rails");
        while (world.timeWarp.getWarp() > 1) world.timeWarp.step(-1);
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertFalse(world.timeWarp.isRailsWarp());
        assertSame(highport, world.docking.dockedTo(game.player), "came back docked");
        assertTrue(world.docking.isRigid(game.player), "and welded");
        assertEquals(0f, face(body(game.player), port(game.player, 0))
                .dst(face(stationBody(), port(highport, stationPort))), 0.05f, "at the port");
    }

    @Test
    void theScreensDockingKeysArmAskAndUndock() {
        java.util.List<String> notices = new java.util.ArrayList<>();
        PlayerDocking keys = new PlayerDocking(world, () -> game.player, notices::add);
        int stationPort = nearestPort(MountFootprint.MountSize.S);
        parkBeside(stationPort, 1.5f);
        world.update(FRAME);
        String[] lines = keys.readout();
        assertTrue(lines[1].startsWith("To Highport #" + (stationPort + 1)), lines[1]);
        assertEquals("Highport gives clearance: port " + (stationPort + 1) + " is armed for you", keys.toggleArm());
        for (int i = 0; i < 60 * 60 && !world.docking.isRigid(game.player); i++) world.update(FRAME);
        assertTrue(notices.contains("Highport's collar has you; drawing in"), notices.toString());
        assertTrue(notices.contains("Docked at Highport"), notices.toString());
        assertTrue(keys.readout()[1].startsWith("Docked at Highport"), keys.readout()[1]);
        assertEquals("Undocked from Highport", keys.undock());
        assertEquals("Not docked", keys.undock());
        keys.dispose();
    }

    // ------------------------------------------------------------------

    private int nearestPort(MountFootprint.MountSize size) {
        DockingPortsComponent ports = highport.getComponent(DockingPortsComponent.class);
        Vector2 ship = PhysicsManager.getPosition(body(game.player), new Vector2());
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < ports.ports.length; i++) {
            if (ports.ports[i].size != size) continue;
            float d = face(stationBody(), ports.ports[i]).dst(ship);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    /** Puts the player's ship {@code gap} meters off Highport's port, facing it and keeping pace. */
    private void parkBeside(int stationPort, float gap) {
        Body station = stationBody();
        DockingPort sp = port(highport, stationPort);
        DockingPort pp = port(game.player, 0);
        Vector2 n = station.getWorldVector(new Vector2(sp.normalX, sp.normalY)).cpy();
        float angle = MathUtils.atan2(n.y, n.x) + MathUtils.PI - MathUtils.atan2(pp.normalY, pp.normalX);
        Vector2 target = face(station, sp).mulAdd(n, gap);
        float c = MathUtils.cos(angle), s = MathUtils.sin(angle);
        float x = target.x - (c * pp.faceX() - s * pp.faceY());
        float y = target.y - (s * pp.faceX() + c * pp.faceY());
        Body ship = body(game.player);
        PhysicsManager.setPosition(ship, x, y, angle);
        Vector2 v = PhysicsManager.getVelocity(station, new Vector2());
        PhysicsManager.setVelocity(ship, v.x, v.y);
        ship.setAngularVelocity(0f);
    }

    /** Steps until the station's collar has hold of the ship; returns the warp guard's reason then. */
    private String waitForCollar() {
        for (int i = 0; i < 60 * 20; i++) {
            world.update(FRAME);
            if (port(game.player, 0).state != DockingPort.State.IDLE) return world.engine.getRailsWarpBlockedReason();
        }
        fail("the collar never caught the ship");
        return null;
    }

    private Body stationBody() {
        return highport.getComponent(KinematicTransientComponent.class).body;
    }

    private static Body body(Entity e) {
        return e.getComponent(PhysicsBodyComponent.class).body;
    }

    private static DockingPort port(Entity e, int i) {
        return e.getComponent(DockingPortsComponent.class).ports[i];
    }

    private static Vector2 face(Body body, DockingPort p) {
        Vector2 w = body.getWorldPoint(new Vector2(p.faceX() * U, p.faceY() * U));
        return new Vector2(w).scl(PhysicsManager.METERS_PER_UNIT);
    }
}
