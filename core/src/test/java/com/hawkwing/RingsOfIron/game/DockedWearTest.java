package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.career.NewGame;
import com.ringsofiron.combatsys.events.HullHitEvent;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.DockingPortsComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Round 7's docking: collars worn by hits, kept through a save, and no air lost at the berth after a load. */
class DockedWearTest {
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
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(2026, "patrol"));
        game.beginCareer();
        run(120);
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    @Test
    void aHitBesideAPortWearsItsCollarByTheShareItTook() {
        DockingPort port = port();
        hitBeside(port, 0.3f);
        assertEquals(0.7f, port.collarIntegrity, 1e-5f, "worn by the hit's share");
        assertTrue(port.intact());
        // The yard has a collar to refit, and charges for it
        assertTrue(world.yard.damage(game.player).stream().anyMatch(d -> d.kind == StationYard.Damage.Kind.COLLAR));
    }

    @Test
    void aHitFarFromAnyPortLeavesTheCollarsAlone() {
        DockingPort port = port();
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        MountFootprint m = grid.getTile(port.gridX, port.gridY).mount;
        int far = m.originX > grid.width / 2 ? 0 : grid.width - 1;
        hit(far, m.originY + m.height() + 3 < grid.height ? m.originY + m.height() + 3 : 0, 0.5f);
        assertEquals(1f, port.collarIntegrity, 1e-6f);
    }

    @Test
    void noSingleHitTearsACollar() {
        DockingPort port = port();
        hitBeside(port, 1f);
        assertTrue(port.intact(), "worn, not torn");
        assertEquals(CollarWear.FLOOR, port.collarIntegrity, 1e-6f);
        for (int i = 0; i < 5; i++) hitBeside(port, 1f);
        assertEquals(CollarWear.FLOOR, port.collarIntegrity, 1e-6f, "hits never take it off");
    }

    @Test
    void aWornCollarLatchedInPlaceHoldsAsAWholeOne(@TempDir Path dir) {
        // A shot-up prize boarded, or a worn ship towed in: the clamps take the load the collar can't
        DockingPort port = port();
        int index = java.util.Arrays.asList(game.player.getComponent(DockingPortsComponent.class).ports).indexOf(port);
        Entity station = world.docking.dockedTo(game.player);
        int stationPort = port.partnerPort;
        for (int i = 0; i < 5; i++) hitBeside(port, 1f);
        assertEquals(CollarWear.FLOOR, port.collarIntegrity, 1e-6f);
        java.util.List<String> torn = new java.util.ArrayList<>();
        world.docking.events.add((sig, e) -> {
            if (e.kind == com.ringsofiron.stationsdocking.DockingEvent.Kind.TORN) torn.add("torn");
        });
        world.docking.undock(game.player);
        run(30);
        world.docking.dockInPlace(game.player, index, station, stationPort);
        run(60);
        assertSame(station, world.docking.dockedTo(game.player), "latched again");
        assertEquals(java.util.List.of(), torn, "torn on the latch");
        com.badlogic.gdx.physics.box2d.Body body = game.player.getComponent(
                com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body;
        // Pulled straight off the port through the ship's centre of mass, so the latch takes a load
        // and no turn: a third of what a whole collar holds, three times what a tenth of one does
        com.badlogic.gdx.math.Vector2 off = body.getWorldVector(new com.badlogic.gdx.math.Vector2(port.normalX, port.normalY)).nor().scl(-1f);
        float push = port.tuning().breakForce / 3f;
        for (int i = 0; i < 5 * 60; i++) {
            com.ringsofiron.physicsexterior.PhysicsManager.applyForce(body, off.x * push, off.y * push);
            world.update(FRAME);
        }
        assertEquals(java.util.List.of(), torn);
        assertSame(station, world.docking.dockedTo(game.player), "still latched");
        assertEquals(CollarWear.FLOOR, port.collarIntegrity, 1e-6f, "the collar itself is still worn, for the yard");
        // And after a load, the clamps still hold it
        FileHandle file = new FileHandle(dir.resolve("clamped.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        run(60);
        DockingPort loaded = port();
        assertTrue(loaded.clamped);
        body = game.player.getComponent(com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body;
        off = body.getWorldVector(new com.badlogic.gdx.math.Vector2(loaded.normalX, loaded.normalY)).nor().scl(-1f);
        for (int i = 0; i < 5 * 60; i++) {
            com.ringsofiron.physicsexterior.PhysicsManager.applyForce(body, off.x * push, off.y * push);
            world.update(FRAME);
        }
        assertNotNull(world.docking.dockedTo(game.player), "still latched after a load");
    }

    @Test
    void anIonBoltWearsNothing() {
        DockingPort port = port();
        HullHitEvent e = event(port.gridX, port.gridY, 0.5f);
        e.ion = true;
        world.combatEvents.hullHits.dispatch(e);
        assertEquals(1f, port.collarIntegrity, 1e-6f);
    }

    @Test
    void aSaveKeepsTheWear(@TempDir Path dir) {
        hitBeside(port(), 0.4f);
        FileHandle file = new FileHandle(dir.resolve("worn.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        run(2);
        assertEquals(0.6f, port().collarIntegrity, 1e-5f);
    }

    @Test
    void noAirIsLostAtTheBerthAfterALoad(@TempDir Path dir) {
        // The real-window playtest: for about 15 frames after a load the docked port's opening let
        // air out to space, until its link to the station was made again
        assertNotNull(world.docking.dockedTo(game.player), "docked at Highport");
        run(5);
        FileHandle file = new FileHandle(dir.resolve("berth.json").toFile());
        assertNull(game.quickSave(file));
        // Loaded over the same game, and over a new one somewhere else, as the main menu loads it
        assertNoVentingAfter(() -> game.quickLoad(file), "over the same game");
        world.docking.undock(game.player);
        run(60);
        assertNoVentingAfter(() -> game.quickLoad(file), "just undocked");
        // Loaded a little off the latched pose, as a save's float positions far out come back: the
        // latch seats the ship for a moment before it welds
        assertNoVentingAfter(() -> {
            game.quickLoad(file);
            com.badlogic.gdx.physics.box2d.Body body = game.player.getComponent(
                    com.ringsofiron.physicsexterior.components.PhysicsBodyComponent.class).body;
            com.badlogic.gdx.math.Vector2 at = com.ringsofiron.physicsexterior.PhysicsManager.getPosition(body, new com.badlogic.gdx.math.Vector2());
            com.ringsofiron.physicsexterior.PhysicsManager.setPosition(body, at.x + 0.05f, at.y, body.getAngle() + 0.02f);
        }, "a little off the latch");
        world.dispose();
        Path assets = Paths.get(System.getProperty("user.dir")).getParent().resolve("assets");
        world = new GameWorld();
        game = GameScenario.start(world, assets, assets.resolve("no-mods-here"), NewGame.seeded(7, "patrol"));
        run(2);
        assertNoVentingAfter(() -> game.quickLoad(file), "over a new game");
    }

    private void assertNoVentingAfter(Runnable load, String how) {
        load.run();
        float worst = 0f;
        int frame = -1;
        StringBuilder rates = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            world.update(FRAME);
            ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
            if (i < 30) rates.append(String.format(" %.3f", grid.ventingRate()));
            if (grid.ventingRate() > worst) {
                worst = grid.ventingRate();
                frame = i;
            }
        }
        assertEquals(0f, worst, 1e-6f, how + ": air vented to space at frame " + frame + ":" + rates);
        assertNotNull(world.docking.dockedTo(game.player), how + ": still docked");
    }

    private DockingPort port() {
        for (DockingPort p : game.player.getComponent(DockingPortsComponent.class).ports) if (p.gridX >= 0) return p;
        fail("no port on the grid");
        return null;
    }

    private void hitBeside(DockingPort port, float share) {
        ShipGridComponent grid = game.player.getComponent(ShipGridComponent.class);
        MountFootprint m = grid.getTile(port.gridX, port.gridY).mount;
        hit(m.originX + m.width(), m.originY, share);
    }

    private void hit(int x, int y, float share) {
        world.combatEvents.hullHits.dispatch(event(x, y, share));
    }

    private HullHitEvent event(int x, int y, float share) {
        HullHitEvent e = new HullHitEvent();
        e.ship = game.player;
        e.tileX = x;
        e.tileY = y;
        e.severity = share;
        e.outcome = HullHitEvent.Outcome.STOPPED;
        return e;
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }
}
