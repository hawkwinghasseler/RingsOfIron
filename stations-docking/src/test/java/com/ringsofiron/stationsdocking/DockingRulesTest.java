package com.ringsofiron.stationsdocking;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.ringsofiron.coreengine.GameEngine;
import com.ringsofiron.coreengine.components.TransformComponent;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.DockingPortLayout;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.MountFootprint;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.interiorgrid.SubsystemComponent;
import com.ringsofiron.physicsexterior.CollisionCategories;
import com.ringsofiron.physicsexterior.PhysicsManager;
import com.ringsofiron.physicsexterior.components.KinematicTransientComponent;
import com.ringsofiron.physicsexterior.components.PhysicsBodyComponent;
import com.ringsofiron.physicsexterior.systems.KinematicTransientSystem;
import com.ringsofiron.physicsexterior.systems.PhysicsUpdateSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The rules around a dock: clearance, its expiry and withdrawal, and forcing a shut port door. */
class DockingRulesTest {
    private static final float[] HULL = {-5f, -2f, 5f, -2f, 5f, 2f, -5f, 2f};
    private static final float DENSITY = 500f;

    private GameEngine game;
    private PhysicsManager physics;
    private DockingSystem docking;
    private final List<DockingEvent> events = new ArrayList<>();

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    @BeforeEach
    void setUp() {
        game = new GameEngine();
        physics = new PhysicsManager();
        game.addSystem(new PhysicsUpdateSystem(physics));
        KinematicTransientSystem transients = new KinematicTransientSystem(physics);
        transients.setBodyFactory(new StationBodies(physics));
        game.addSystem(transients);
        docking = new DockingSystem(physics, game.getUuidRegistry());
        docking.requirePower = false;
        game.addSystem(docking);
        docking.events.add((signal, e) -> {
            DockingEvent copy = new DockingEvent();
            copy.kind = e.kind;
            copy.vessel = e.vessel;
            copy.other = e.other;
            copy.otherPort = e.otherPort;
            copy.permitted = e.permitted;
            copy.method = e.method;
            events.add(copy);
        });
    }

    @AfterEach
    void tearDown() {
        game.clear();
        physics.dispose();
    }

    /** A 10 x 4 m ship with a passive port of {@code size} on its belly (-y), at (x, y). */
    private Entity ship(float x, float y, MountFootprint.MountSize size, boolean active) {
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        Body body = physics.createPolygonBody(e, x, y, 0f, HULL, DENSITY,
                CollisionCategories.CATEGORY_SHIP, CollisionCategories.MASK_ALL);
        e.add(new PhysicsBodyComponent(body));
        e.add(new DockingPortsComponent(DockingPortsComponent.hullPort(size, active, 0f, -2f, 0f, -1f)));
        game.addEntity(e);
        return e;
    }

    /** A 9 x 5 station at rest at (x, 0) with one active Small port on top. */
    private Entity station(float x) {
        ChassisBlueprintLoader.LoadResult result = ChassisBlueprintLoader.load(new ChassisBlueprint(new String[]{
                "###DDD###",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########",
        }).tileSize(1f).origin(-4.5f, -2.5f));
        Entity e = game.createEntityWithUuid();
        e.add(new TransformComponent());
        e.add(result.grid);
        e.add(DockingPortsComponent.fromGrid(result.grid, List.of(new int[]{4, 4})));
        e.add(new StationComponent("Test Station", "outpost", "test"));
        e.add(new KinematicTransientComponent((t, out) -> {
            out[0] = x;
            out[1] = 0.0;
            out[2] = 0.0;
            out[3] = 0.0;
        }, 10f));
        game.addEntity(e);
        return e;
    }

    private static DockingPort port(Entity e) {
        return e.getComponent(DockingPortsComponent.class).ports[0];
    }

    /** A station and a ship latched onto its port in place, the station's door shut against it. */
    private Entity[] dockedWithDoorShut() {
        Entity s = station(0f);
        Entity a = ship(0f, 7f, MountFootprint.MountSize.S, false);
        docking.dockInPlace(a, 0, s, 0);
        game.stepTicks(60);
        assertEquals(DockingPort.State.DOCKED, port(s).state);
        // As if it latched with the ship's own magnets: the station never agreed
        port(s).consented = false;
        ShipGridComponent grid = s.getComponent(ShipGridComponent.class);
        DockLinks.setDoors(grid, DockLinks.mount(grid, port(s)), false);
        return new Entity[]{s, a};
    }

    private List<EdgeWall> doors(Entity station) {
        ShipGridComponent grid = station.getComponent(ShipGridComponent.class);
        MountFootprint m = DockLinks.mount(grid, port(station));
        List<EdgeWall> out = new ArrayList<>();
        for (int[] t : DockLinks.doorTiles(m)) out.add(DockingPortLayout.door(grid, m, t[0], t[1]));
        return out;
    }

    // ------------------------------------------------------------------
    // Clearance
    // ------------------------------------------------------------------

    @Test
    void noStationInRangeMeansNoOneToAsk() {
        station(DockingSystem.CLEARANCE_RANGE + 1000f);
        Entity a = ship(0f, 0f, MountFootprint.MountSize.S, false);
        game.stepTicks(1);
        DockingSystem.Clearance c = docking.requestClearance(a, 0);
        assertEquals(DockingSystem.Clearance.Outcome.NO_STATION, c.outcome);
        assertNull(c.station);
    }

    @Test
    void aStationWithNoPortOfTheRightSizeHasNoFreePort() {
        Entity s = station(0f);
        Entity a = ship(0f, 30f, MountFootprint.MountSize.M, false);
        game.stepTicks(1);
        DockingSystem.Clearance c = docking.requestClearance(a, 0);
        assertEquals(DockingSystem.Clearance.Outcome.NO_FREE_PORT, c.outcome);
        assertEquals(s, c.station);
        assertFalse(port(s).armed);
    }

    @Test
    void aPortClearedForOneCraftIsNotGivenToAnother() {
        Entity s = station(0f);
        Entity a = ship(0f, 30f, MountFootprint.MountSize.S, false);
        Entity b = ship(30f, 30f, MountFootprint.MountSize.S, false);
        game.stepTicks(1);
        assertEquals(DockingSystem.Clearance.Outcome.GRANTED, docking.requestClearance(a, 0).outcome);
        assertEquals(DockingSystem.Clearance.Outcome.NO_FREE_PORT, docking.requestClearance(b, 0).outcome);
        // Asking again for the same craft is fine: the port is already its own.
        assertEquals(DockingSystem.Clearance.Outcome.GRANTED, docking.requestClearance(a, 0).outcome);
        assertEquals(a.getComponent(com.ringsofiron.coreengine.components.UuidComponent.class).id, port(s).armedFor);
    }

    @Test
    void theStationKeepsItsCollarInWhenTheAskersOwnCollarDoesTheReaching() {
        Entity s = station(0f);
        Entity passive = ship(0f, 30f, MountFootprint.MountSize.S, false);
        game.stepTicks(1);
        docking.requestClearance(passive, 0);
        assertFalse(port(s).holdCollar, "a passive asker is reached for");
        docking.disarm(passive, 0);
        Entity active = ship(30f, 30f, MountFootprint.MountSize.S, true);
        game.stepTicks(1);
        docking.requestClearance(active, 0);
        assertTrue(port(s).holdCollar, "two collars reaching would only push each other off");
    }

    @Test
    void clearanceRunsOutAndThePortDisarms() {
        Entity s = station(0f);
        Entity a = ship(0f, 40f, MountFootprint.MountSize.S, false);
        game.stepTicks(1);
        docking.requestClearance(a, 0);
        assertTrue(port(s).armed);
        assertEquals(physics.getSimSeconds() + DockingSystem.CLEARANCE_SECONDS, port(s).clearanceUntil, 1e-6);
        game.stepTicks((int) (DockingSystem.CLEARANCE_SECONDS * 60) - 120);
        assertTrue(port(s).armed, "still waiting for it");
        game.stepTicks(240);
        assertFalse(port(s).armed, "clearance lapsed");
        assertNull(port(s).armedFor);
        assertTrue(Double.isNaN(port(s).clearanceUntil));
    }

    @Test
    void disarmingWithdrawsTheClearanceTheStationGave() {
        Entity s = station(0f);
        Entity a = ship(0f, 40f, MountFootprint.MountSize.S, false);
        game.stepTicks(1);
        docking.requestClearance(a, 0);
        assertTrue(port(s).armed);
        docking.disarm(a, 0);
        assertFalse(port(s).armed);
        assertNull(port(s).armedFor);
    }

    @Test
    void aRefusalCarriesTheStationsReason() {
        Entity s = station(0f);
        Entity a = ship(0f, 30f, MountFootprint.MountSize.S, false);
        docking.clearance = (station, vessel) -> station == s ? "you forced their doors" : null;
        game.stepTicks(1);
        DockingSystem.Clearance c = docking.requestClearance(a, 0);
        assertEquals(DockingSystem.Clearance.Outcome.DENIED, c.outcome);
        assertEquals("you forced their doors", c.reason);
        assertFalse(port(s).armed);
    }

    @Test
    void anUnpoweredPortCannotArm() {
        docking.requirePower = true;
        Entity s = station(0f);
        ShipGridComponent grid = s.getComponent(ShipGridComponent.class);
        DockingPort p = port(s);
        MountFootprint m = grid.getTile(p.gridX, p.gridY).mount;
        Entity system = game.createEntity();
        SubsystemComponent sub = new SubsystemComponent(m, SubsystemComponent.SubsystemType.DOCKING_PORT);
        sub.powered = false;
        system.add(sub);
        m.installed = system;
        assertEquals("the port has no power", docking.arm(s, 0));
        sub.powered = true;
        assertNull(docking.arm(s, 0));
        // Losing power while armed drops the magnets.
        sub.powered = false;
        game.stepTicks(2);
        assertFalse(p.armed);
    }

    @Test
    void armingANonexistentPortSaysSo() {
        Entity a = ship(0f, 0f, MountFootprint.MountSize.S, false);
        assertEquals("no docking port", docking.arm(a, 3));
        assertFalse(docking.undock(a), "not docked");
        assertNull(docking.dockedTo(a));
    }

    // ------------------------------------------------------------------
    // Forcing a port door
    // ------------------------------------------------------------------

    @Test
    void forceMethodsWorkOnTheDoorsTheyAreFor() {
        assertTrue(ForceMethod.PRY.works(ForceMethod.DoorState.UNPOWERED));
        assertFalse(ForceMethod.PRY.works(ForceMethod.DoorState.LOCKED));
        assertTrue(ForceMethod.HACK.works(ForceMethod.DoorState.LOCKED));
        assertFalse(ForceMethod.HACK.works(ForceMethod.DoorState.UNPOWERED));
        assertTrue(ForceMethod.CUT.works(ForceMethod.DoorState.LOCKED));
        assertTrue(ForceMethod.CUT.works(ForceMethod.DoorState.UNPOWERED));
        for (ForceMethod m : ForceMethod.values()) {
            assertFalse(m.works(ForceMethod.DoorState.OPEN), m + " on an open door");
            assertFalse(m.works(ForceMethod.DoorState.NONE), m + " with no door");
        }
        assertTrue(ForceMethod.CUT.seconds < ForceMethod.HACK.seconds, "cutting is quicker than hacking");
        assertTrue(ForceMethod.CUT.damages && !ForceMethod.PRY.damages && !ForceMethod.HACK.damages);
    }

    @Test
    void aDoorOnlyHasAStateWhileItsPortIsLatched() {
        Entity s = station(0f);
        game.stepTicks(1);
        assertEquals(ForceMethod.DoorState.NONE, docking.doorState(s, 0));
        Entity a = ship(0f, 0f, MountFootprint.MountSize.S, false);
        assertEquals(ForceMethod.DoorState.NONE, docking.doorState(a, 0), "a port with no grid has no door");
        assertEquals("no docked port door there", docking.forceDoor(a, s, 0, ForceMethod.CUT));
        assertTrue(events.isEmpty());
    }

    @Test
    void hackingALockedDoorOpensItUndamagedAndReportsTheHostileAct() {
        Entity[] pair = dockedWithDoorShut();
        Entity s = pair[0], a = pair[1];
        assertEquals(ForceMethod.DoorState.LOCKED, docking.doorState(s, 0));
        assertEquals("the door is powered: hack it or cut it", docking.forceDoor(a, s, 0, ForceMethod.PRY));
        assertTrue(events.stream().noneMatch(e -> e.kind == DockingEvent.Kind.DOOR_FORCED));

        assertNull(docking.forceDoor(a, s, 0, ForceMethod.HACK));
        assertEquals(ForceMethod.DoorState.OPEN, docking.doorState(s, 0));
        assertTrue(port(s).forced);
        for (EdgeWall door : doors(s)) assertEquals(1f, door.integrity, 1e-6f, "hacking leaves no mark");
        DockingEvent forced = events.get(events.size() - 1);
        assertEquals(DockingEvent.Kind.DOOR_FORCED, forced.kind);
        assertEquals(a, forced.vessel);
        assertEquals(s, forced.other);
        assertEquals(0, forced.otherPort);
        assertEquals(ForceMethod.HACK, forced.method);
        assertEquals("the door is open", docking.forceDoor(a, s, 0, ForceMethod.CUT));
    }

    @Test
    void cuttingLeavesTheDoorDamagedSoItLeaksOnceShut() {
        Entity[] pair = dockedWithDoorShut();
        Entity s = pair[0], a = pair[1];
        assertNull(docking.forceDoor(a, s, 0, ForceMethod.CUT));
        for (EdgeWall door : doors(s)) {
            assertEquals(DockingSystem.CUT_DOOR_INTEGRITY, door.integrity, 1e-6f);
            assertTrue(door.integrity < 0.5f, "below half: it leaks");
        }
    }

    @Test
    void anUnpoweredDoorIsPriedButCannotBeHacked() {
        Entity[] pair = dockedWithDoorShut();
        Entity s = pair[0], a = pair[1];
        docking.requirePower = true;
        ShipGridComponent grid = s.getComponent(ShipGridComponent.class);
        MountFootprint m = grid.getTile(port(s).gridX, port(s).gridY).mount;
        Entity system = game.createEntity();
        SubsystemComponent sub = new SubsystemComponent(m, SubsystemComponent.SubsystemType.DOCKING_PORT);
        sub.powered = false;
        system.add(sub);
        m.installed = system;
        assertEquals(ForceMethod.DoorState.UNPOWERED, docking.doorState(s, 0));
        assertEquals("the door has no power to hack: pry it or cut it", docking.forceDoor(a, s, 0, ForceMethod.HACK));
        assertNull(docking.forceDoor(a, s, 0, ForceMethod.PRY));
        assertEquals(ForceMethod.DoorState.OPEN, docking.doorState(s, 0));
    }

    @Test
    void undockingShutsAForcedDoorAndForgetsIt() {
        Entity[] pair = dockedWithDoorShut();
        Entity s = pair[0], a = pair[1];
        assertNull(docking.forceDoor(a, s, 0, ForceMethod.HACK));
        assertTrue(docking.undock(a));
        assertFalse(port(s).forced);
        assertEquals(DockingPort.State.IDLE, port(s).state);
        for (EdgeWall door : doors(s)) assertTrue(door.bulkheadSealed, "shut again");
        assertEquals(ForceMethod.DoorState.NONE, docking.doorState(s, 0));
    }

    @ParameterizedTest
    @EnumSource(ForceMethod.class)
    void everyMethodTakesSomeTime(ForceMethod m) {
        assertTrue(m.seconds > 0);
        assertFalse(m.verb.isEmpty());
        assertFalse(m.doing.isEmpty());
    }
}
