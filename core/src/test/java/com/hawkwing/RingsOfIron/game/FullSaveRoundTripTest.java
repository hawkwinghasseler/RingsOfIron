package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.ringsofiron.aicrew.CrewService;
import com.ringsofiron.aicrew.GridPositionComponent;
import com.ringsofiron.coreengine.components.UuidComponent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.macrosim.fleet.FreighterComponent;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saves a whole game in one state that spans every module that saves (the player's ship with its
 * crew, docked at Highport, freighters out on the rails) and loads it into a fresh game.
 */
class FullSaveRoundTripTest {
    static final float FRAME = 1f / 60f;
    private static final float U = PhysicsManager.UNITS_PER_METER;

    Path assets;
    GameWorld world;
    GameScenario game;
    Entity highport;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
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
    void aDockedShipWithItsCrewAndFreightersOnTheRailsLoadsIntoAFreshGame(@TempDir Path dir) {
        dockAtHighport();
        warpUntilAFreighterIsUnderway();
        assertSame(highport, world.docking.dockedTo(game.player), "docked when saved");

        FileHandle file = new FileHandle(dir.resolve("full.json").toFile());
        assertNull(game.quickSave(file));
        long tick = world.engine.getTick();
        String playerId = game.player.getComponent(UuidComponent.class).id;
        int crewAboard = crewAboard(world, game.player);
        Map<String, String> fleet = fleet(world);

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertTrue(fresh.engine.getSerializationService().getLastLoadWarnings().isEmpty(),
                    String.valueOf(fresh.engine.getSerializationService().getLastLoadWarnings()));

            // Saving straight after loading writes the same game back
            assertSameSave(new JsonReader().parse(file), new JsonReader().parse(fresh.engine.getSerializationService().saveGame()), "save");

            assertEquals(tick, fresh.engine.getTick());
            assertEquals(playerId, loaded.player.getComponent(UuidComponent.class).id);
            assertEquals(crewAboard, crewAboard(fresh, loaded.player), "the crew are back aboard");
            assertEquals(fleet, fleet(fresh), "every freighter where it was, doing what it was");

            for (int i = 0; i < 120; i++) fresh.update(FRAME);
            Entity loadedHighport = fresh.engine.getEntity(game.highport);
            assertSame(loadedHighport, fresh.docking.dockedTo(loaded.player), "still docked at Highport");
            assertTrue(fresh.docking.isRigid(loaded.player), "and welded");
            ShipGridComponent deck = loaded.player.getComponent(ShipGridComponent.class);
            assertTrue(deck.links.size > 0 && deck.links.first().canCross(), "the walkway through the ports is open");
            assertTrue(fresh.engine.advanceRails(com.ringsofiron.coreengine.SimClock.secondsToTicks(3600))
                    || fresh.engine.getRailsWarpBlockedReason() != null, "the loaded universe runs on");
        } finally {
            fresh.dispose();
        }
    }

    @Test
    void warpAfterALoadWaitsForTheShipToBeWeldedAgainAndTheCrewKeepBreathing(@TempDir Path dir) {
        dockAtHighport();
        FileHandle file = new FileHandle(dir.resolve("warp.json").toFile());
        assertNull(game.quickSave(file));

        GameWorld fresh = new GameWorld();
        try {
            GameScenario loaded = GameScenario.start(fresh, assets, assets.resolve("no-mods-here"));
            loaded.quickLoad(file);
            assertNotNull(fresh.engine.getRailsWarpBlockedReason(), "no rails warp before the loaded game's first frame");
            assertFalse(fresh.timeWarp.setWarp(1_000));
            fresh.update(FRAME);
            assertNotNull(fresh.engine.getRailsWarpBlockedReason(), "nor while the latch is welded again");
            for (int i = 0; i < 60 && fresh.engine.getRailsWarpBlockedReason() != null; i++) fresh.update(FRAME);
            assertTrue(fresh.timeWarp.setWarp(1_000), String.valueOf(fresh.engine.getRailsWarpBlockedReason()));
            for (int i = 0; i < 600; i++) fresh.update(FRAME);
            fresh.timeWarp.setWarp(1);
            for (int i = 0; i < 60; i++) fresh.update(FRAME);
            Entity loadedHighport = fresh.engine.getEntity(game.highport);
            assertSame(loadedHighport, fresh.docking.dockedTo(loaded.player), "still docked after the warp");
            GridPositionComponent you = loaded.avatar.getComponent(GridPositionComponent.class);
            ShipGridComponent deck = you.ship.getComponent(ShipGridComponent.class);
            assertTrue(deck.oxygenRatioAt(you.tileX(), you.tileY()) > 0.9f, "the air is still in");
            assertTrue(loaded.avatar.getComponent(com.ringsofiron.aicrew.CrewMemberComponent.class).isAlive());
        } finally {
            fresh.dispose();
        }
    }

    // ------------------------------------------------------------------

    /**
     * Two saves hold the same game: same keys, values and entity order, numbers equal to within what
     * rebuilding Box2D bodies from them rounds away. Fast passes are left out: by design a load drops
     * them and the next forecast starts them again (see BubbleTransitionSystem.restore).
     */
    private static void assertSameSave(JsonValue expected, JsonValue actual, String path) {
        assertNotNull(actual, path + " is missing after the load");
        if (expected.isNumber() && actual.isNumber()) {
            double a = expected.asDouble(), b = actual.asDouble();
            // A body's shape and mass are Box2D floats: rebuilding the body can move them by an ulp
            double relative = path.contains(".shape.") ? 1e-6 : 1e-9;
            assertEquals(a, b, 1e-3 + Math.abs(a) * relative, path);
            return;
        }
        assertEquals(expected.type(), actual.type(), path);
        if (expected.isValue()) {
            assertEquals(expected.asString(), actual.asString(), path);
            return;
        }
        assertEquals(childNames(expected), childNames(actual), path);
        JsonValue b = actual.child;
        for (JsonValue a = expected.child; a != null; a = a.next, b = b.next) {
            if ("fastPasses".equals(a.name)) continue;
            assertSameSave(a, b, path + (a.name != null ? "." + a.name : "[]"));
        }
    }

    private static java.util.List<String> childNames(JsonValue v) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonValue c = v.child; c != null; c = c.next) if (!"fastPasses".equals(c.name)) names.add(String.valueOf(c.name));
        return names;
    }

    private void dockAtHighport() {
        int stationPort = nearestPort(MountFootprint.MountSize.S);
        parkBeside(stationPort, 1.5f);
        assertNull(world.docking.arm(game.player, 0));
        world.docking.requestClearance(game.player, 0);
        for (int i = 0; i < 60 * 60 && !world.docking.isRigid(game.player); i++) world.update(FRAME);
        assertTrue(world.docking.isRigid(game.player), "Highport's collar drew the ship in and latched");
    }

    /** Rails warp with the ship docked, until some freighter has left its station on an order. */
    private void warpUntilAFreighterIsUnderway() {
        for (int i = 0; i < 60 * 10 && !aFreighterUnderway(); i++) {
            // Warp drops out on its own (a freighter coming near, say); keep asking for it
            if (world.timeWarp.getWarp() < 100_000) {
                assertTrue(world.timeWarp.setWarp(100_000), String.valueOf(world.engine.getRailsWarpBlockedReason()));
            }
            world.update(FRAME);
        }
        assertTrue(aFreighterUnderway(), "a freighter took an order and left by " + world.engine.getSimSeconds() + " s: " + fleet(world));
        world.timeWarp.setWarp(1);
        for (int i = 0; i < 120; i++) world.update(FRAME);
        assertFalse(world.timeWarp.isRailsWarp());
    }

    private boolean aFreighterUnderway() {
        for (FreighterComponent f : world.macroSim.freighters().all().values()) {
            if (f.state == FreighterComponent.State.TO_PICKUP || f.state == FreighterComponent.State.TO_DELIVERY) return true;
        }
        return false;
    }

    /** Each freighter's state, order, cargo and next arrival, by UUID. */
    private static Map<String, String> fleet(GameWorld w) {
        Map<String, String> out = new TreeMap<>();
        w.macroSim.freighters().all().forEach((uuid, f) -> out.put(uuid, f.state + " " + f.resumeState
                + " order " + f.orderId + " at " + f.dockedAtUuid + " to " + f.destinationUuid
                + " carrying " + f.cargoQuantity + " " + f.cargoCommodity + " arriving " + f.arrivalTime
                + " credits " + f.credits + " trips " + f.tripsCompleted));
        return out;
    }

    private static int crewAboard(GameWorld w, Entity ship) {
        int aboard = 0;
        for (Entity e : w.engine.getEcsEngine().getEntitiesFor(CrewService.CREW)) {
            if (e.getComponent(GridPositionComponent.class).ship == ship) aboard++;
        }
        return aboard;
    }

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
