package com.ringsofiron.interiorgrid;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomNamesTest {

    @Test
    void theRoomWithTheHelmIsTheBridge() {
        ShipGridComponent g = TestShips.grid();
        RoomNames names = new RoomNames();
        assertEquals("BRIDGE", names.of(g, TestShips.right(g)));
        assertEquals("BRIDGE", names.at(g, 16, 5));
    }

    @Test
    void aRoomIsNamedForTheSystemThatFillsMostOfIt() {
        ShipGridComponent g = TestShips.grid();
        String left = new RoomNames().of(g, TestShips.left(g));
        // The reactor and life support stand there; whichever covers more tiles names it
        assertTrue(left.equals("REACTOR") || left.equals("LIFE SUP"), left);
    }

    @Test
    void emptyRoomsAreNamedByPlaceAndNumberedFromTheBow() {
        ChassisBlueprint bp = new ChassisBlueprint(new String[]{
                "#####################",
                "#....#....#....#....#",
                "#....#....#....#....#",
                "#####################"
        }).tileSize(1f);
        ShipGridComponent g = load(bp);
        RoomNames names = new RoomNames();
        assertEquals(4, names.rooms(g).size);
        assertEquals("FWD", names.at(g, 17, 1));
        assertEquals("MID 1", names.at(g, 12, 1));
        assertEquals("MID 2", names.at(g, 7, 1));
        assertEquals("AFT", names.at(g, 2, 1));
        assertEquals("", names.at(g, 5, 1));
        // Bow first
        assertEquals("FWD", names.of(g, names.rooms(g).first()));
    }

    @Test
    void placeSaysPortOrStarboardOffTheCentreline() {
        assertEquals("FWD PORT", RoomNames.place(9f, 4f, 0f, 10f, -5f, 5f));
        assertEquals("AFT STBD", RoomNames.place(1f, -4f, 0f, 10f, -5f, 5f));
        assertEquals("MID", RoomNames.place(5f, 0.5f, 0f, 10f, -5f, 5f));
    }

    @Test
    void namesStayPutUntilTheRoomsChange() {
        ShipGridComponent g = TestShips.grid();
        RoomNames names = new RoomNames();
        String before = names.of(g, TestShips.left(g));
        assertEquals(before, names.of(g, TestShips.left(g)));
        assertNotEquals("", before);
        assertTrue(before.length() <= RoomNames.MAX_LENGTH);
    }

    private static ShipGridComponent load(ChassisBlueprint bp) {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(bp);
        for (ChassisBlueprintLoader.SubsystemPlacement p : r.subsystems) {
            Entity e = new Entity();
            e.add(new SubsystemComponent(p.footprint, p.type));
            p.footprint.installed = e;
        }
        new GridHazardSystem(1).step(r.grid, TestShips.DT);
        return r.grid;
    }
}
