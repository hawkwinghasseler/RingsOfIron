package com.ringsofiron.interiorgrid;

import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** RCS blocks: where they can go on the hull, where their nozzles fire from, and saving them. */
class RcsThrusterTest {
    static ShipGridComponent room() {
        return ChassisBlueprintLoader.load(new ChassisBlueprint(
                "#####",
                "#...#",
                "#####")).grid;
    }

    @Test
    void aBlockGoesOnAHullEdgeThatFacesSpace() {
        ShipGridComponent g = room();
        assertNull(RcsThruster.placement(g, 2, 2, Side.TOP), "off the grid's edge is space");
        assertNull(RcsThruster.placement(g, 0, 1, Side.LEFT));
        assertNotNull(RcsThruster.placement(g, 2, 2, Side.BOTTOM), "not on the edge into the room");
        assertNotNull(RcsThruster.placement(g, 2, 1, Side.TOP), "not on a floor tile");
        assertNotNull(RcsThruster.placement(g, 9, 9, Side.TOP), "not off the grid");
    }

    @Test
    void aLinearFiresOutThroughItsEdgeAndAQuadAlongTheHullToo() {
        RcsThruster linear = new RcsThruster(2, 2, Side.TOP, RcsThruster.Kind.LINEAR);
        assertArrayEquals(new Side[]{Side.TOP}, linear.nozzles());
        assertEquals(2.5f, linear.nozzleX(Side.TOP), 1e-6f);
        assertEquals(3f + RcsThruster.STANDOFF, linear.nozzleY(Side.TOP), 1e-6f);

        RcsThruster quad = new RcsThruster(2, 2, Side.TOP, RcsThruster.Kind.QUAD);
        assertArrayEquals(new Side[]{Side.TOP, Side.LEFT, Side.RIGHT}, quad.nozzles());
        assertEquals(2.5f - RcsThruster.HALF_LENGTH, quad.nozzleX(Side.LEFT), 1e-6f);
        assertEquals(2.5f + RcsThruster.HALF_LENGTH, quad.nozzleX(Side.RIGHT), 1e-6f);
        assertTrue(quad.nozzleY(Side.LEFT) > 3f, "its side nozzles stand off the hull too");
    }

    @Test
    void aBlockFiresAtItsKindsThrustUnlessItSaysOtherwise() {
        assertEquals(RcsThruster.Kind.QUAD.standardThrust, new RcsThruster(0, 0, Side.LEFT, RcsThruster.Kind.QUAD).nozzleThrust());
        assertEquals(2e4, new RcsThruster(0, 0, Side.LEFT, RcsThruster.Kind.QUAD, 2e4).nozzleThrust());
    }

    @Test
    void itStopsWorkingWhenItsHullTileIsDestroyed() {
        ShipGridComponent g = room();
        RcsThruster r = new RcsThruster(2, 2, Side.TOP, RcsThruster.Kind.LINEAR);
        assertTrue(r.works(g));
        g.getTile(2, 2).setIntegrity(0f);
        assertFalse(r.works(g));
    }

    @Test
    void blocksAreSavedAndComeBack() {
        ShipGridComponent g = room();
        g.rcsThrusters.add(new RcsThruster(2, 2, Side.TOP, RcsThruster.Kind.QUAD, 1.5e4));
        g.rcsThrusters.add(new RcsThruster(0, 1, Side.LEFT, RcsThruster.Kind.LINEAR));
        Json json = new Json();
        json.setSerializer(ShipGridComponent.class, new ShipGridSerializer());
        ShipGridComponent back = json.fromJson(ShipGridComponent.class, json.toJson(g, ShipGridComponent.class));
        assertEquals(g.rcsThrusters, back.rcsThrusters);
    }
}
