package com.hawkwing.RingsOfIron.game;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.physicsexterior.components.FlightControlComponent;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.stationsdocking.DockingPort;
import com.ringsofiron.stationsdocking.ForceMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** Plays every scene of the docking demo headless, the way a player would with the autopilot. */
class DockingDemoSceneTest {
    static final float FRAME = 1f / 60f;

    DockingDemoScene demo;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        demo = new DockingDemoScene(Paths.get(System.getProperty("user.dir")).getParent().resolve("assets"));
    }

    @AfterEach
    void tearDown() {
        demo.dispose();
    }

    private boolean runUntilDocked(float seconds) {
        for (int i = 0; i < seconds * 60 && !demo.world.docking.isRigid(demo.player); i++) demo.update(FRAME);
        return demo.world.docking.isRigid(demo.player);
    }

    @Test
    void passivePortsLatchOnceBothAreArmed() {
        demo.load(DockingDemoScene.Scene.PASSIVE);
        assertEquals("Port armed", demo.docking.toggleArm());
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertSame(demo.target, demo.world.docking.dockedTo(demo.player));
    }

    @Test
    void theActiveCollarCatchesTheOtherShip() {
        demo.load(DockingDemoScene.Scene.ACTIVE);
        demo.autopilot = true;
        for (int i = 0; i < 60 * 60 && demo.autopilot; i++) demo.update(FRAME);
        assertEquals("Port armed", demo.docking.toggleArm());
        boolean captured = false;
        for (int i = 0; i < 60 * 60 && !demo.world.docking.isRigid(demo.player); i++) {
            demo.update(FRAME);
            captured |= DockingDemoScene.port(demo.player, 0).state == DockingPort.State.CAPTURED;
        }
        assertTrue(captured, "the collar had hold before the latch");
        assertTrue(demo.world.docking.isRigid(demo.player), demo.notice());
    }

    @Test
    void theStationGivesClearanceAndDrawsTheShipIn() {
        demo.load(DockingDemoScene.Scene.CLEARANCE);
        assertTrue(demo.docking.toggleArm().contains("gives clearance"));
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertEquals("Docked at Outpost Kepler", demo.notice());
        assertTrue(demo.world.docking.dockedWithClearance(demo.player));
    }

    @Test
    void aRefusingStationKeepsItsCollarInButTheShipsMagnetsStillLatch() {
        demo.load(DockingDemoScene.Scene.REFUSED);
        assertEquals("Outpost Kepler refuses docking clearance; port armed", demo.docking.toggleArm());
        for (DockingPort p : demo.target.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class).ports) {
            assertFalse(p.armed, "no station port arms");
        }
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
        assertEquals("Docked at Outpost Kepler (without clearance)", demo.notice());
        assertFalse(demo.world.docking.dockedWithClearance(demo.player));
    }

    @Test
    void pullingAwayWhileDockedTearsThePortOff() {
        demo.load(DockingDemoScene.Scene.TEAR);
        assertTrue(demo.world.docking.isRigid(demo.player), "starts docked");
        FlightControlComponent controls = demo.player.getComponent(FlightControlComponent.class);
        for (int i = 0; i < 60 * 5 && demo.world.docking.dockedTo(demo.player) != null; i++) {
            controls.forward = 1f;
            demo.update(FRAME);
        }
        assertNull(demo.world.docking.dockedTo(demo.player), "torn loose");
        assertTrue(demo.notice().startsWith("Torn loose from Outpost Kepler"), demo.notice());
        DockingPort port = DockingDemoScene.port(demo.player, 0);
        assertFalse(port.intact());
        assertEquals("Can't arm the port: the collar is torn off", demo.docking.toggleArm());
    }

    /** Gap between the two docked collar faces along the player's port normal, m: below zero they overlap. */
    private float faceGap(DockingPort mine, DockingPort theirs) {
        com.badlogic.gdx.physics.box2d.Body a = demo.world.docking.bodyOf(demo.player);
        com.badlogic.gdx.physics.box2d.Body b = demo.world.docking.bodyOf(demo.target);
        float u = com.ringsofiron.physicsexterior.PhysicsManager.UNITS_PER_METER;
        com.badlogic.gdx.math.Vector2 fa = a.getWorldPoint(new com.badlogic.gdx.math.Vector2(mine.faceX() * u, mine.faceY() * u)).cpy();
        com.badlogic.gdx.math.Vector2 fb = b.getWorldPoint(new com.badlogic.gdx.math.Vector2(theirs.faceX() * u, theirs.faceY() * u)).cpy();
        com.badlogic.gdx.math.Vector2 n = a.getWorldVector(new com.badlogic.gdx.math.Vector2(mine.normalX, mine.normalY)).cpy();
        return fb.sub(fa).dot(n) / u;
    }

    @Test
    void afterUndockingThePortsCollideAgainAtOnce() {
        demo.load(DockingDemoScene.Scene.TEAR);
        DockingPort mine = DockingDemoScene.port(demo.player, 0);
        DockingPort theirs = DockingDemoScene.port(demo.target, DockingDemoScene.smallPort(demo.target));
        assertEquals("Undocked from Outpost Kepler", demo.docking.undock());
        for (int i = 0; i < 60; i++) demo.update(FRAME);
        assertTrue(faceGap(mine, theirs) > 0.05f, "the springs pushed the craft apart: " + faceGap(mine, theirs));
        // Back in, straight at the outpost's port
        com.badlogic.gdx.physics.box2d.Body body = demo.world.docking.bodyOf(demo.player);
        float least = Float.MAX_VALUE;
        for (int i = 0; i < 60 * 4; i++) {
            com.badlogic.gdx.math.Vector2 n = body.getWorldVector(new com.badlogic.gdx.math.Vector2(mine.normalX, mine.normalY)).cpy();
            com.ringsofiron.physicsexterior.PhysicsManager.setVelocity(body, n.x * 1.5f, n.y * 1.5f);
            body.setAngularVelocity(0f);
            demo.update(FRAME);
            least = Math.min(least, faceGap(mine, theirs));
        }
        assertTrue(least > -0.05f, "the collars stopped each other, not passed through: " + least);
    }

    @Test
    void theHubHasAHaulerBerthedAndRoomForThePlayer() {
        demo.load(DockingDemoScene.Scene.HUB);
        Entity hauler = null;
        for (Entity e : demo.world.engine.getEcsEngine().getEntitiesFor(
                com.badlogic.ashley.core.Family.all(com.ringsofiron.uihud.components.HudMarkerComponent.class).get())) {
            if ("Hauler Kestrel".equals(e.getComponent(com.ringsofiron.uihud.components.HudMarkerComponent.class).label)) hauler = e;
        }
        assertNotNull(hauler);
        assertSame(demo.target, demo.world.docking.dockedTo(hauler));
        assertTrue(demo.world.docking.isRigid(hauler));
        assertTrue(demo.docking.toggleArm().contains("gives clearance"));
        demo.autopilot = true;
        assertTrue(runUntilDocked(120f), demo.notice());
    }

    @Test
    void theOutpostsHandsCarryWaterAboardAndBackACrateAtATime() {
        demo.load(DockingDemoScene.Scene.LOADING);
        String water = DockingDemoScene.WATER;
        double stocked = demo.held(demo.target, water);
        assertEquals(0, demo.held(demo.player, water), 1e-6);
        assertTrue(demo.orderWater(true).startsWith("Ordered"), demo.notice());
        boolean sawCrate = false, sawHandAboard = false;
        for (int i = 0; i < 60 * 240 && demo.held(demo.player, water) < DockingDemoScene.LOAD_UNITS - 1e-6; i++) {
            demo.update(FRAME);
            for (Entity e : demo.world.crew.crewEntities()) {
                com.ringsofiron.aicrew.CrewMemberComponent c = demo.world.crew.crew(e);
                if (c.faction != StationCrew.FACTION) continue;
                if (c.load != null) sawCrate = true;
                if (demo.world.crew.position(e).ship == demo.player) sawHandAboard = true;
            }
            double aboard = demo.held(demo.player, water);
            assertEquals(stocked, aboard + demo.held(demo.target, water), 1e-6, "nothing lost or made on the way");
        }
        assertEquals(DockingDemoScene.LOAD_UNITS, demo.held(demo.player, water), 1e-6, "all aboard");
        assertTrue(sawCrate, "carried by hand");
        assertTrue(sawHandAboard, "the hands walked aboard the corvette with it");

        demo.orderWater(false);
        for (int i = 0; i < 60 * 240 && demo.held(demo.player, water) > 1e-6; i++) {
            demo.update(FRAME);
        }
        assertEquals(0, demo.held(demo.player, water), 1e-6, "and back again");
        // Then everyone goes home
        for (int i = 0; i < 60 * 60; i++) demo.update(FRAME);
        for (Entity e : demo.world.crew.crewEntities()) {
            if (demo.world.crew.crew(e).faction != StationCrew.FACTION) continue;
            assertSame(demo.target, demo.world.crew.position(e).ship, "the hands went home");
        }
    }

    @Test
    void theShipWontUndockWithDockHandsAboard() {
        demo.load(DockingDemoScene.Scene.LOADING);
        demo.orderWater(true);
        for (int i = 0; i < 60 * 120 && demo.world.cargo.visitorsAboard(demo.player) == 0; i++) demo.update(FRAME);
        assertTrue(demo.world.cargo.visitorsAboard(demo.player) > 0, "a hand came aboard");
        assertTrue(demo.docking.undock().contains("dock hands are still aboard"));
        assertSame(demo.target, demo.world.docking.dockedTo(demo.player), "still docked");
        for (int i = 0; i < 60 * 120 && demo.world.cargo.visitorsAboard(demo.player) > 0; i++) demo.update(FRAME);
        assertEquals(0, demo.world.cargo.visitorsAboard(demo.player), "they went home");
        assertTrue(demo.docking.undock().startsWith("Undocked"));
        double total = demo.held(demo.player, DockingDemoScene.WATER) + demo.held(demo.target, DockingDemoScene.WATER);
        assertEquals(20, total, 1e-6, "a crate in hand went back, none lost");
    }

    @Test
    void hackingTheOutpostsDoorLetsYouInAndCostsStanding() {
        demo.load(DockingDemoScene.Scene.FORCE);
        DoorForcing.Target t = demo.world.forcing.target(demo.avatar, new DoorForcing.Target());
        assertSame(demo.target, t.craft, "the outpost's door is across the dock");
        assertEquals(ForceMethod.DoorState.LOCKED, t.state, "powered and locked");
        assertTrue(demo.force(ForceMethod.PRY).contains("hack it or cut it"), demo.notice());
        assertTrue(demo.force(ForceMethod.HACK).startsWith("Hacking"), demo.notice());
        for (int i = 0; i < 60 * 40; i++) demo.update(FRAME);
        assertEquals(ForceMethod.DoorState.LOCKED, demo.world.docking.doorState(demo.target, t.port), "not yet");
        for (int i = 0; i < 60 * 10; i++) demo.update(FRAME);
        assertEquals(ForceMethod.DoorState.OPEN, demo.world.docking.doorState(demo.target, t.port), "hacked open");
        float standing = demo.world.standing.standing(StationCrew.FACTION);
        assertEquals(-FactionStanding.HACK_COST, standing, 1e-3, "a hostile act");
        ShipGridComponent deck = demo.player.getComponent(ShipGridComponent.class);
        assertTrue(deck.links.first().canCross(), "the way onto the station is open");
        assertFalse(demo.world.crew.hostility.hostile(0, StationCrew.FACTION), "not enough to make them fight");

        // Saved and loaded: the door stays forced open, and the station remembers
        String station = demo.target.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id;
        String save = demo.world.engine.getSerializationService().saveGame();
        demo.world.engine.getSerializationService().loadGame(save);
        for (int i = 0; i < 120; i++) demo.world.update(FRAME);
        Entity outpost = demo.world.engine.getEntity(station);
        assertEquals(ForceMethod.DoorState.OPEN, demo.world.docking.doorState(outpost, t.port), "still forced open");
        assertEquals(-FactionStanding.HACK_COST, demo.world.standing.standing(StationCrew.FACTION), 1e-3);
    }

    @Test
    void twoCutsMakeTheStationCrewFight() {
        demo.load(DockingDemoScene.Scene.FORCE);
        demo.world.standing.change(StationCrew.FACTION, -FactionStanding.CUT_COST);
        assertFalse(demo.world.crew.hostility.hostile(StationCrew.FACTION, 0));
        demo.force(ForceMethod.CUT);
        for (int i = 0; i < 60 * 21; i++) demo.update(FRAME);
        assertEquals(-2 * FactionStanding.CUT_COST, demo.world.standing.standing(StationCrew.FACTION), 1e-3);
        assertTrue(demo.world.crew.hostility.hostile(StationCrew.FACTION, 0), "the dock hands turn on you");
        assertFalse(demo.world.crew.hostility.hostile(StationCrew.FACTION, 1), "but not on anyone else");
        assertTrue(demo.world.crew.hostility.hostile(0, 1), "two ships' crews still fight");
    }

    @Test
    void cuttingTheDoorDamagesItAndTheOutpostRefusesClearanceAfter() {
        demo.load(DockingDemoScene.Scene.FORCE);
        DoorForcing.Target t = demo.world.forcing.target(demo.avatar, new DoorForcing.Target());
        demo.force(ForceMethod.CUT);
        for (int i = 0; i < 60 * 21; i++) demo.update(FRAME);
        assertEquals(ForceMethod.DoorState.OPEN, demo.world.docking.doorState(demo.target, t.port));
        assertEquals(-FactionStanding.CUT_COST, demo.world.standing.standing(StationCrew.FACTION), 1e-3);
        ShipGridComponent station = demo.target.getComponent(ShipGridComponent.class);
        DockingPort port = demo.target.getComponent(com.ringsofiron.stationsdocking.DockingPortsComponent.class).ports[t.port];
        com.ringsofiron.interiorgrid.MountFootprint m = station.getTile(port.gridX, port.gridY).mount;
        boolean damaged = false;
        for (int y = m.originY; y < m.originY + m.height(); y++) {
            for (int x = m.originX; x < m.originX + m.width(); x++) {
                com.ringsofiron.interiorgrid.EdgeWall door = com.ringsofiron.interiorgrid.DockingPortLayout.door(station, m, x, y);
                if (door != null && door.integrity < 0.5f) damaged = true;
            }
        }
        assertTrue(damaged, "the cut door is left damaged");
        // Off again, and the outpost won't have you back
        demo.world.docking.undock(demo.player);
        for (int i = 0; i < 60 * 2; i++) demo.update(FRAME);
        demo.target.getComponent(com.ringsofiron.stationsdocking.StationComponent.class).grantsClearance = true;
        com.ringsofiron.stationsdocking.DockingSystem.Clearance c = demo.world.docking.requestClearance(demo.player, 0);
        assertEquals(com.ringsofiron.stationsdocking.DockingSystem.Clearance.Outcome.DENIED, c.outcome);
        assertEquals("you forced their doors", c.reason);
    }

    @Test
    void anUnpoweredDoorIsPried() {
        demo.load(DockingDemoScene.Scene.FORCE);
        demo.toggleStationPower();
        for (int i = 0; i < 60; i++) demo.update(FRAME);
        DoorForcing.Target t = demo.world.forcing.target(demo.avatar, new DoorForcing.Target());
        assertEquals(ForceMethod.DoorState.UNPOWERED, t.state);
        assertTrue(demo.force(ForceMethod.HACK).contains("pry it or cut it"));
        demo.force(ForceMethod.PRY);
        for (int i = 0; i < 60 * 9; i++) demo.update(FRAME);
        assertEquals(ForceMethod.DoorState.OPEN, demo.world.docking.doorState(demo.target, t.port));
        assertEquals(-FactionStanding.PRY_COST, demo.world.standing.standing(StationCrew.FACTION), 1e-3);
    }

    @Test
    void steppingAwayStopsTheWork() {
        demo.load(DockingDemoScene.Scene.FORCE);
        demo.force(ForceMethod.CUT);
        for (int i = 0; i < 60 * 5; i++) demo.update(FRAME);
        com.ringsofiron.aicrew.GridPositionComponent pos = demo.avatar.getComponent(com.ringsofiron.aicrew.GridPositionComponent.class);
        pos.setTile(pos.tileX() + 1, pos.tileY());
        int[] home = DockingDemoScene.insidePort(demo.player, 0);
        pos.setTile(home[0], home[1]);
        demo.update(FRAME);
        assertNull(demo.world.forcing.jobOf(demo.avatar));
        assertEquals("Stepped away from the door", demo.world.forcing.lastOutcome);
        assertEquals(0f, demo.world.standing.standing(StationCrew.FACTION), 1e-6, "nothing done, nothing held against you");
    }

    @Test
    void everySceneLoadsAgainCleanly() {
        for (DockingDemoScene.Scene s : DockingDemoScene.Scene.values()) {
            demo.load(s);
            demo.update(FRAME);
            assertNotNull(demo.world.docking.bodyOf(demo.player), s.title);
        }
    }
}
