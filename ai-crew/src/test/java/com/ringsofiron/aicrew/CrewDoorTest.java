package com.ringsofiron.aicrew;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ChassisBlueprint;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.EdgeWall;
import com.ringsofiron.interiorgrid.FloorCovering;
import com.ringsofiron.interiorgrid.Side;
import com.ringsofiron.interiorgrid.Tile;
import com.ringsofiron.interiorgrid.WeightClass;
import com.ringsofiron.interiorgrid.crew.ExtinguishTask;
import org.junit.jupiter.api.Test;

class CrewDoorTest {

    /** Two rooms split by a thin wall at x = 4 with a shut door at y = 2. */
    static TestShips twoRooms(boolean hazards) {
        ChassisBlueprint bp = new ChassisBlueprint(
                "#########",
                "#.......#",
                "#.......#",
                "#.......#",
                "#########");
        for (int y = 1; y <= 3; y++) bp.edgeWall(4, y, Side.LEFT, WeightClass.LIGHT, y == 2);
        TestShips s = new TestShips(bp, hazards);
        s.grid.getEdge(4, 2, Side.LEFT).bulkheadSealed = true;
        return s;
    }

    static EdgeWall door(TestShips s) {
        return s.grid.getEdge(4, 2, Side.LEFT);
    }

    @Test
    void crewOpenAShutDoorOnTheirWayAndShutItBehindThem() {
        TestShips s = twoRooms(true);
        Tile t = s.grid.getTile(6, 2);
        t.setCovering(FloorCovering.CARPET, 1f);
        t.ignite();
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 2);
        boolean sawOpen = false;
        for (int i = 0; i < 30 * 20 && t.isBurning(); i++) {
            s.run(1f / 30f);
            sawOpen |= !door(s).bulkheadSealed;
        }
        assertFalse(t.isBurning(), "reached the fire behind the door");
        assertTrue(sawOpen, "opened the door");
        assertTrue(s.p(e).tileX() > 4, "went through");
        s.run(2f);
        assertTrue(door(s).bulkheadSealed, "shut it again behind them");
        assertFalse(s.crew.doors.isHeld(door(s)));
    }

    @Test
    void aDoorLeftOpenByHandStaysOpen() {
        TestShips s = twoRooms(false);
        door(s).bulkheadSealed = false;
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 2);
        s.board.post(new ExtinguishTask(6, 2));
        s.grid.getTile(6, 2).fireIntensity = 0.01f;
        s.run(8f);
        assertTrue(s.p(e).tileX() > 4);
        assertFalse(door(s).bulkheadSealed);
    }

    @Test
    void boardersCannotOpenTheShipsDoors() {
        TestShips s = twoRooms(false);
        Entity boarder = s.spawn(CrewRole.MARINE, 1, 1, 2);
        Entity crewman = s.spawn(CrewRole.CREWMAN, 0, 6, 2);
        assertFalse(s.crew.doors.mayOpen(boarder, s.ship, 3, 2, 4, 2));
        assertTrue(s.crew.doors.mayOpen(crewman, s.ship, 4, 2, 3, 2));
        s.run(6f);
        assertTrue(door(s).bulkheadSealed, "the boarder could not get at the crewman");
        assertEquals(CrewState.CONSCIOUS, s.c(crewman).state);
    }

    @Test
    void crewWithoutSuitsDoNotOpenADoorOntoVacuum() {
        TestShips s = twoRooms(false);
        Compartment far = s.grid.compartmentAt(6, 2);
        far.oxygen = far.nitrogen = far.carbonDioxide = 0;
        Entity e = s.spawn(CrewRole.CREWMAN, 0, 1, 2);
        assertFalse(s.crew.doors.mayOpen(e, s.ship, 3, 2, 4, 2), "would let the air out");
        assertTrue(s.crew.doors.mayOpen(e, s.ship, 4, 2, 3, 2), "out of vacuum into air is fine");
        s.board.post(new ExtinguishTask(6, 2));
        s.grid.getTile(6, 2).fireIntensity = 0.01f;
        s.run(5f);
        assertTrue(door(s).bulkheadSealed);
        assertTrue(s.p(e).tileX() < 4);
    }
}
