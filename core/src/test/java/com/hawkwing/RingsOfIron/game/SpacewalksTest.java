package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.aicrew.CrewMemberComponent;
import com.ringsofiron.aicrew.CrewState;
import com.ringsofiron.aicrew.EngineExhaust;
import com.ringsofiron.aicrew.EvaComponent;
import com.ringsofiron.aicrew.EvaSystem;
import com.ringsofiron.interiorgrid.Airlock;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.HullSurface;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.RcsThruster;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The patrol career's corvette, cast off from Highport with the RCS block nearest its docking port
 * wrecked: an engineer goes out through the port to mend it, rails warp waits while they are out,
 * the notices and the status line say so, and a save made while they are on the hull loads with
 * them still out there. Out at the bow block instead, a full burn ahead parts their line, while the
 * swing a long burn astern starts them on doesn't; with their line parted and their pack empty they
 * are adrift, marked on the HUD for the ship to come back for, until their air runs out. One on the
 * hull behind an engine's nozzle is warned of, and a burn kills them.
 */
class SpacewalksTest {
    static final float FRAME = 1f / 60f;

    GameWorld world;
    GameScenario game;
    ShipGridComponent grid;
    MountFootprint port;
    RcsThruster block;

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
        assertTrue(world.docking.undock(game.player), "cast off: a docked port is no way out");
        run(60 * 5);
        grid = game.player.getComponent(ShipGridComponent.class);
        port = null;
        for (MountFootprint m : grid.mounts) if (m.kind == MountFootprint.MountKind.PORT) port = m;
        assertNotNull(port, "the corvette's docking port");
        int[] chamber = DockingPortLayout.chamberTile(port, new int[2]);
        for (RcsThruster r : grid.rcsThrusters) {
            if (block == null || dist(r, chamber) < dist(block, chamber)) block = r;
        }
        grid.getTile(block.x, block.y).setIntegrity(0f);
        grid.gridDirty = true;
        assertTrue(RcsThruster.fieldRepairable(grid, block.x, block.y));
    }

    @AfterEach
    void tearDown() {
        world.dispose();
    }

    private static int dist(RcsThruster r, int[] t) {
        return Math.abs(r.x - t[0]) + Math.abs(r.y - t[1]);
    }

    private void run(int frames) {
        for (int i = 0; i < frames; i++) world.update(FRAME);
    }

    /** The first of the player's crew out of the airlock, or null. */
    private Entity outside() {
        for (Entity e : world.spacewalks.onEva()) if (EvaSystem.outside(e)) return e;
        return null;
    }

    private Entity goOut() {
        Entity out = null;
        for (int i = 0; i < 60 * 10 && out == null; i++) {
            run(60);
            out = outside();
        }
        assertNotNull(out, "an engineer went out");
        return out;
    }

    private boolean said(String text) {
        return world.notices.history().stream().anyMatch(n -> n.text.equals(text));
    }

    @Test
    void anEngineerGoesOutToMendTheBlockAndRailsWarpWaitsForThem() {
        Entity e = goOut();
        String name = world.crew.crew(e).name;
        assertTrue(said(name + " goes out on EVA"), "the notice");
        assertEquals(Spacewalks.REFUSAL + ": " + name + " is outside", world.spacewalks.railsWarpBlockedReason());
        String status = world.spacewalks.statusLine();
        assertTrue(status.startsWith("EVA: " + name + " o"), status);
        assertTrue(status.endsWith("A hard burn can throw them off the hull"), status);
        for (int i = 0; i < 60 * 20 && !(block.works(grid) && world.spacewalks.onEva().isEmpty()); i++) run(60);
        assertTrue(block.works(grid), "the block fires again");
        assertTrue(world.spacewalks.onEva().isEmpty(), "everyone back in");
        assertTrue(said(name + " is back in from EVA"));
        assertNull(world.spacewalks.railsWarpBlockedReason(), "warp is clear once they are in");
        assertNull(world.spacewalks.statusLine());
        assertTrue(Airlock.of(grid, port).isIn(), "the airlock back at rest");
    }

    /**
     * The starboard bow block instead of the one by the docking port, and whoever goes out to it once
     * they are at work there, round the bow from the docking port with their line wrapped round it.
     */
    private Entity outAtTheBowBlock() {
        grid.getTile(block.x, block.y).setIntegrity(1f);
        block = null;
        for (RcsThruster r : grid.rcsThrusters) if (r.side == Side.BOTTOM && (block == null || r.x > block.x)) block = r;
        grid.getTile(block.x, block.y).setIntegrity(0f);
        grid.gridDirty = true;
        Entity e = goOut();
        for (int i = 0; i < 60 * 120 && !"repairing an RCS thruster on EVA".equals(world.crew.crew(e).activity); i++) run(1);
        assertEquals("repairing an RCS thruster on EVA", world.crew.crew(e).activity, "out at the block");
        return e;
    }

    @Test
    void aFullBurnAheadPartsTheLineOfOneAtTheBow() {
        // A burn ahead drags the hull back past them, and the line round the bow takes their weight in it
        Entity e = outAtTheBowBlock();
        String name = world.crew.crew(e).name;
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 3 && !said(name + "'s tether has parted"); i++) {
            controls.forward = 1f;
            run(1);
        }
        controls.forward = 0f;
        assertTrue(said(name + " is thrown off the hull"));
        assertTrue(said(name + "'s tether has parted"), "the corvette's full burn is more than the line holds");
    }

    @Test
    void aLongBurnAsternSwingsOneAtTheBowRoundItOnTheLineAndItHolds() {
        // A burn astern throws them off ahead of the bow, and they swing back and forth round it on
        // the line, its pull peaking at the bottom of each swing
        Entity e = outAtTheBowBlock();
        String name = world.crew.crew(e).name;
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 4; i++) {
            controls.forward = -1f;
            run(1);
        }
        controls.forward = 0f;
        assertTrue(said(name + " is thrown off the hull"));
        assertFalse(said(name + "'s tether has parted"), "the line held through the swing");
        for (int i = 0; i < 60 * 30 && !said(name + " has caught hold of the hull again"); i++) run(1);
        assertTrue(said(name + " has caught hold of the hull again"), "back on the hull once the burn ends");
        assertTrue(EvaSystem.of(e).tethered, "still on their line");
    }

    @Test
    void oneAdriftWithNoWayBackIsMarkedForTheShipToComeBackForTillTheirAirRunsOut() {
        Entity e = outAtTheBowBlock();
        CrewMemberComponent c = world.crew.crew(e);
        String name = c.name;
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 3 && !said(name + "'s tether has parted"); i++) {
            controls.forward = 1f;
            run(1);
        }
        controls.forward = 0f;
        assertTrue(said(name + "'s tether has parted"));
        // An empty pack: no way back alone
        c.suitJet = 0f;
        java.util.function.BooleanSupplier warned = () -> world.notices.history().stream().anyMatch(n -> n.text.startsWith(name + " is adrift ")
                && n.text.endsWith(" off with no way back alone: bring the ship to them"));
        for (int i = 0; i < 60 * 5 && !warned.getAsBoolean(); i++) run(1);
        assertTrue(warned.getAsBoolean(), "the warning");
        assertNotNull(world.spacewalks.marker(e), "a marker on the HUD over them");
        assertEquals(Spacewalks.REFUSAL + ": " + name + " is adrift", world.spacewalks.railsWarpBlockedReason());
        String status = world.spacewalks.statusLine();
        assertTrue(status.startsWith("EVA: " + name + " adrift "), status);
        assertTrue(status.contains(" min of air: waiting to be picked up"), status);
        // Their air runs out, and they die out there: warp doesn't wait for a body
        c.suitAir = 5f;
        for (int i = 0; i < 60 * 60 && c.state != CrewState.DEAD; i++) run(1);
        assertTrue(said(name + "'s suit is out of air"));
        assertEquals(CrewState.DEAD, c.state);
        run(2);
        assertNull(world.spacewalks.marker(e), "no marker over the body");
        assertNull(world.spacewalks.railsWarpBlockedReason());
        assertNull(world.spacewalks.statusLine());
    }

    @Test
    void oneOnTheHullBehindAnEngineIsWarnedOfAndABurnKillsThem() {
        Entity e = goOut();
        CrewMemberComponent c = world.crew.crew(e);
        String name = c.name;
        // The tile of hull a full burn pushes hardest on: right behind a nozzle
        HullSurface hull = world.crew.eva.hull(grid);
        EngineExhaust exhaust = new EngineExhaust();
        assertTrue(exhaust.read(game.player, grid, 1f), "the corvette's engines");
        Vector2 v = new Vector2();
        int bx = 0, by = 0;
        float best = 0f;
        for (int y = -1; y <= grid.height; y++) {
            for (int x = -1; x <= grid.width; x++) {
                if (!hull.isRing(x, y)) continue;
                hull.hug(x, y, v);
                float push = exhaust.push(v.x, v.y, null);
                if (push > best) {
                    best = push;
                    bx = x;
                    by = y;
                }
            }
        }
        assertTrue(best > EngineExhaust.BURN_FROM, "hull behind a nozzle: " + best);
        // There, their line cast off
        EvaComponent eva = EvaSystem.of(e);
        hull.hug(bx, by, v);
        eva.phase = EvaComponent.Phase.ON_HULL;
        eva.ringX = bx;
        eva.ringY = by;
        eva.rx = v.x;
        eva.ry = v.y;
        eva.rvx = eva.rvy = 0f;
        eva.path.clear();
        eva.stepDone = 0f;
        eva.slideX = eva.slideY = 0f;
        eva.tethered = false;
        eva.rope = null;
        eva.worldValid = false;
        eva.hullVValid = false;
        String status = world.spacewalks.statusLine();
        assertTrue(status.endsWith(name + " is in the way of the main engines' exhaust"), status);
        FlightControlComponent controls = game.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 10; i++) {
            controls.forward = 1f;
            run(1);
        }
        controls.forward = 0f;
        assertTrue(said(name + " is caught in the main engines' exhaust"));
        assertEquals(CrewState.DEAD, c.state, "right behind the nozzle of an engine at full burn");
    }

    @Test
    void aSaveMadeWithThemOnTheHullLoadsWithThemStillOut(@TempDir Path dir) {
        Entity e = goOut();
        String name = world.crew.crew(e).name;
        run(60 * 10);
        EvaComponent eva = EvaSystem.of(e);
        assertNotNull(eva);
        float rx = eva.rx, ry = eva.ry;
        FileHandle file = new FileHandle(dir.resolve("eva.json").toFile());
        assertNull(game.quickSave(file));
        game.quickLoad(file);
        grid = game.player.getComponent(ShipGridComponent.class);
        Entity back = null;
        for (Entity c : world.crew.crewEntities()) if (name.equals(world.crew.crew(c).name)) back = c;
        assertNotNull(back);
        EvaComponent loaded = EvaSystem.of(back);
        assertNotNull(loaded, "still on their EVA");
        assertTrue(loaded.outside(), "still outside");
        assertEquals(rx, loaded.rx, 0.01f, "where they were on the hull");
        assertEquals(ry, loaded.ry, 0.01f);
        assertNotNull(world.spacewalks.railsWarpBlockedReason(), "warp still waits for them");
        port = null;
        for (MountFootprint m : grid.mounts) if (m.kind == MountFootprint.MountKind.PORT) port = m;
        for (int i = 0; i < 60 * 20 && !world.spacewalks.onEva().isEmpty(); i++) run(60);
        assertTrue(world.spacewalks.onEva().isEmpty(), "back in after the load");
        assertTrue(Airlock.of(grid, port).isIn(), "the airlock back at rest");
    }
}
