package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** A docking port cycled as an airlock: the hatch shuts, the chamber is pumped down, the door opens, and back. */
class AirlockTest {
    static final float DT = GridHazardSystem.HAZARD_TICK;
    final GridHazardSystem hazards = new GridHazardSystem(1);
    final Object user = new Object();

    /** One room, a Medium port facing up through its top hull row: opening x 4 to 6, rows 3 and 4. */
    ShipGridComponent grid() {
        ChassisBlueprint bp = new ChassisBlueprint(
                "##########",
                "#........#",
                "#........#",
                "#........#",
                "##########").tileSize(1f);
        ShipGridComponent g = ChassisBlueprintLoader.load(bp).grid;
        g.placeMount(new MountFootprint(MountFootprint.MountKind.PORT, MountFootprint.MountSize.M, 3, 3, 0));
        hazards.step(g, DT);
        return g;
    }

    MountFootprint port(ShipGridComponent g) {
        return g.getTile(5, 4).mount;
    }

    @Test
    void aPortIsBuiltWithAnOpenInnerHatchBehindItsDoor() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        for (int x = 4; x <= 6; x++) {
            EdgeWall hatch = DockingPortLayout.hatch(g, port, x, 3);
            assertNotNull(hatch, "a hatch across the opening's inner edge at " + x);
            assertSame(hatch, g.getEdge(x, 3, Side.BOTTOM));
            assertFalse(hatch.bulkheadSealed, "open");
            assertTrue(DockingPortLayout.door(g, port, x, 4).bulkheadSealed, "the port door shut");
        }
        assertSame(g.compartmentAt(5, 4), g.compartmentAt(5, 2), "the opening is part of the room while the hatch is open");
        assertArrayEquals(new int[]{5, 4}, DockingPortLayout.chamberTile(port, new int[2]));
        assertArrayEquals(new int[]{5, 3}, DockingPortLayout.hatchTile(port, new int[2]));
        assertTrue(Airlock.works(g, port));
    }

    @Test
    void aPortFromBeforeHatchesGetsThemFitted() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        for (int x = 4; x <= 6; x++) g.setEdge(x, 3, Side.BOTTOM, null);
        assertFalse(Airlock.works(g, port), "no hatch, no airlock");
        assertTrue(DockingPortLayout.fitHatches(g, port));
        assertFalse(DockingPortLayout.fitHatches(g, port), "fitted once");
        assertTrue(Airlock.works(g, port));
    }

    @Test
    void cyclingOutPumpsTheChambersAirIntoTheCabinBeforeTheDoorOpens() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        Airlock lock = Airlock.of(g, port);
        assertTrue(lock.isIn());
        double before = TestShips.totalMoles(g);
        assertTrue(lock.claim(user, true));
        assertFalse(lock.claim(new Object(), false), "one user at a time");
        run(g, lock, 1f);
        assertEquals(Airlock.State.SEALING_HATCH, lock.state());
        EdgeWall hatch = DockingPortLayout.hatch(g, port, 5, 3);
        assertTrue(hatch.bulkheadSealed);
        assertTrue(hatch.locked, "no one opens it under the cycle");
        assertNotSame(g.compartmentAt(5, 4), g.compartmentAt(5, 2), "the chamber is a room of its own");
        assertTrue(runUntil(g, lock, 60f, lock::isOut), "out: " + lock.state());
        assertFalse(DockingPortLayout.door(g, port, 5, 4).bulkheadSealed, "the door stands open");
        assertTrue(hatch.bulkheadSealed && hatch.locked, "the hatch holds the cabin's air");
        run(g, lock, 2f);
        double lost = before - TestShips.totalMoles(g);
        Compartment cabin = g.compartmentAt(5, 2);
        // The chamber's 7.5 m3 at standard pressure, but for the last few percent, went into the cabin
        double chamberAir = Compartment.STANDARD_PRESSURE * 1000.0 * 6 * Compartment.DECK_HEIGHT / (Compartment.R * Compartment.AMBIENT_TEMPERATURE);
        assertTrue(lost < chamberAir * 0.05, "lost " + lost + " of the chamber's " + chamberAir);
        assertTrue(cabin.pressureRatio() > 1.0f, "the cabin holds it: " + cabin.pressureRatio());
        assertEquals(0f, lock.chamberPressure(g), 0.01f);
    }

    @Test
    void cyclingInFillsTheChamberFromTheCabinBeforeTheHatchOpens() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        Airlock lock = Airlock.of(g, port);
        lock.claim(user, true);
        assertTrue(runUntil(g, lock, 60f, lock::isOut));
        run(g, lock, 3f);
        lock.claim(user, false);
        run(g, lock, 1f);
        assertTrue(DockingPortLayout.door(g, port, 5, 4).bulkheadSealed, "the door shuts first");
        assertTrue(runUntil(g, lock, 60f, lock::isIn), "in: " + lock.state());
        EdgeWall hatch = DockingPortLayout.hatch(g, port, 5, 3);
        assertFalse(hatch.bulkheadSealed || hatch.locked, "the hatch open again");
        run(g, lock, 1f);
        assertSame(g.compartmentAt(5, 4), g.compartmentAt(5, 2));
        assertTrue(g.compartmentAt(5, 2).pressureRatio() > 0.95f, "nearly all the air kept: " + g.compartmentAt(5, 2).pressureRatio());
        lock.release(user);
        assertNull(lock.user());
    }

    @Test
    void noCycleStartsOnSomeoneElseInTheChamber() {
        ShipGridComponent g = grid();
        Airlock lock = Airlock.of(g, port(g));
        lock.crowded = true;
        lock.claim(user, true);
        run(g, lock, 5f);
        assertTrue(lock.isIn(), "waits for them to step out");
        lock.crowded = false;
        run(g, lock, 1f);
        assertEquals(Airlock.State.SEALING_HATCH, lock.state());
    }

    @Test
    void withNoPowerThereIsNoPumpAndTheChambersAirVents() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        Entity system = new Entity();
        SubsystemComponent s = new SubsystemComponent(port, SubsystemComponent.SubsystemType.DOCKING_PORT);
        s.powered = false;
        system.add(s);
        port.installed = system;
        Airlock lock = Airlock.of(g, port);
        double before = TestShips.totalMoles(g);
        lock.claim(user, true);
        assertTrue(runUntil(g, lock, 10f, lock::isOut), "no pumping: " + lock.state());
        run(g, lock, 30f);
        double lost = before - TestShips.totalMoles(g);
        assertTrue(lost > 400, "the chamber's air is let out: " + lost);
    }

    @Test
    void withNoOneToCycleForItGoesBackToRestUnlessSomeoneIsOutOnItsTether() {
        ShipGridComponent g = grid();
        Airlock lock = Airlock.of(g, port(g));
        lock.claim(user, true);
        assertTrue(runUntil(g, lock, 60f, lock::isOut));
        lock.release(user);
        lock.tethered = 1;
        run(g, lock, 30f);
        assertTrue(lock.isOut(), "stands open for whoever is out");
        lock.tethered = 0;
        assertTrue(runUntil(g, lock, 60f, lock::isIn), "back to rest: " + lock.state());
    }

    @Test
    void aLoadedPortPartWayThroughACycleFinishesAsACycleIn() {
        ShipGridComponent g = grid();
        MountFootprint port = port(g);
        for (int x = 4; x <= 6; x++) DockingPortLayout.hatch(g, port, x, 3).bulkheadSealed = true;
        Airlock lock = Airlock.of(g, port);
        assertEquals(Airlock.State.FILLING, lock.state());
        assertTrue(runUntil(g, lock, 60f, lock::isIn));
    }

    private void run(ShipGridComponent g, Airlock lock, float seconds) {
        for (float t = 0f; t < seconds; t += DT) {
            lock.update(g, DT);
            hazards.step(g, DT);
        }
    }

    private boolean runUntil(ShipGridComponent g, Airlock lock, float seconds, java.util.function.BooleanSupplier done) {
        for (float t = 0f; t < seconds; t += DT) {
            if (done.getAsBoolean()) return true;
            lock.update(g, DT);
            hazards.step(g, DT);
        }
        return done.getAsBoolean();
    }
}
