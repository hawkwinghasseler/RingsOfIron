package com.hawkwing.RingsOfIron.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.IntArray;
import com.ringsofiron.interiorgrid.ChassisBlueprintLoader;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.CompartmentBuilder;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import org.junit.jupiter.api.Test;

/**
 * A room's hull breaches are the breached hull tiles beside it: a breach is open to space, so it is
 * never one of the room's own air tiles.
 */
class GameAlertsBreachTest {
    // Text row r is grid row 3 - r
    static final String[] SHIP = {
            "########",
            "#...#..#",
            "#..##..#",
            "########",
    };

    @Test
    void breachesBesideTheRoomAreCountedOnceEach() {
        ShipGridComponent g = ChassisBlueprintLoader.load(SHIP, 1f).grid;
        CompartmentBuilder.rebuild(g);
        Compartment left = g.compartmentAt(1, 1), right = g.compartmentAt(5, 1);
        IntArray at = new IntArray();
        assertEquals(0, GameAlerts.breaches(g, left, at), "a sound hull has no breaches");

        // The inside corner touches two of the left room's tiles: still one breach
        g.getTile(3, 1).setIntegrity(0f);
        // The roof over the left room
        g.getTile(1, 3).setIntegrity(0f);
        // The outer corner touches no room at all
        g.getTile(0, 0).setIntegrity(0f);

        assertEquals(2, GameAlerts.breaches(g, left, at));
        assertEquals(1 * g.width + 3, at.first(), "the alert points at a breach beside the room");
        assertEquals(0, GameAlerts.breaches(g, right, at), "neither breach opens onto the right room");
    }
}
