package com.ringsofiron.interiorgrid;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

/** Two small craft joined at their Small docking ports: air across the link, never out to space. */
class GridLinkTest {
    private static final float DT = 0.25f;
    private final GridHazardSystem hz = new GridHazardSystem(1);

    /** A 3 x 2 room with a Small port on top whose opening is tile (2, 3). */
    private static ShipGridComponent craft() {
        ChassisBlueprintLoader.LoadResult r = ChassisBlueprintLoader.load(new ChassisBlueprint(new String[]{
                "#####",
                "#...#",
                "#...#",
                "#####",
        }).tileSize(1f).mount("docking_port", MountFootprint.MountSize.S, 1, 3));
        new GridHazardSystem(1).step(r.grid, DT);
        return r.grid;
    }

    private static EdgeWall door(ShipGridComponent g) {
        return g.getEdge(2, 3, Side.TOP);
    }

    private static double moles(ShipGridComponent g) {
        double n = 0;
        for (Compartment c : g.compartments) n += c.totalMoles();
        return n;
    }

    private void run(ShipGridComponent a, ShipGridComponent b, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            hz.step(a, DT);
            hz.step(b, DT);
            hz.exchangeAcrossLinks(a, DT);
            hz.exchangeAcrossLinks(b, DT);
        }
    }

    @Test
    void anOpenPortDoorVentsToSpaceUntilTheCraftAreLinked() {
        ShipGridComponent a = craft();
        assertTrue(door(a).blocks(), "the port door starts shut");
        door(a).bulkheadSealed = false;
        hz.step(a, DT);
        assertTrue(a.isVenting(), "an open port with nothing docked is open to space");
    }

    @Test
    void airFlowsAcrossALinkWithBothDoorsOpenAndStaysAboard() {
        ShipGridComponent a = craft(), b = craft();
        for (Compartment c : b.compartments) c.scaleContents(0.2);
        GridLink link = GridLink.join(new Entity(), a, 2, 3, Side.TOP, new Entity(), b, 2, 3, Side.TOP);
        assertSame(link, a.linkAt(2, 3, Side.TOP));
        assertSame(link.mate, b.linkAt(2, 3, Side.TOP));
        door(a).bulkheadSealed = false;
        run(a, b, 5f);
        assertFalse(a.isVenting(), "a linked port does not vent");
        double pa = a.compartmentAt(2, 1).pressure(), pb = b.compartmentAt(2, 1).pressure();
        assertTrue(pa > 95 && pb < 25, "the far door is still shut: " + pa + " / " + pb);
        assertFalse(link.canCross());

        double total = moles(a) + moles(b);
        door(b).bulkheadSealed = false;
        assertTrue(link.canCross());
        assertEquals(1f, link.flowWidth());
        run(a, b, 30f);
        assertEquals(a.compartmentAt(2, 1).pressure(), b.compartmentAt(2, 1).pressure(), 1.0, "pressures even out");
        assertEquals(total, moles(a) + moles(b), total * 1e-6,
                "nothing lost to space");

        link.unjoin();
        assertEquals(0, a.links.size);
        assertEquals(0, b.links.size);
        hz.step(a, DT);
        assertTrue(a.isVenting(), "undocked with the door open: it vents again");
    }

    @Test
    void aSpotOnTheWalkwayIsTheSameSpotSeenFromTheOtherCraft() {
        ShipGridComponent a = craft(), b = craft();
        GridLink link = GridLink.join(new Entity(), a, 2, 3, Side.TOP, new Entity(), b, 2, 3, Side.TOP);
        link.setGap(1f);
        assertEquals(1f, link.mate.gap);
        float[] out = new float[2];
        // A little right of the middle, 0.6 of the way across from the top edge of a's tile (2, 3)
        link.carryAcross(2.6f, 4.6f, out);
        // b faces the other way: right on a is left on b, and 0.4 out past b's top edge
        assertEquals(2.4f, out[0], 1e-4f);
        assertEquals(4.4f, out[1], 1e-4f);
        assertEquals(0.4f, link.mate.past(out[0], out[1]), 1e-4f);
        link.mate.carryAcross(out[0], out[1], out);
        assertEquals(2.6f, out[0], 1e-4f);
        assertEquals(4.6f, out[1], 1e-4f);
    }

    /** Runs {@code seconds}; true once tile (2, 3) of {@code b} catches. */
    private boolean spreads(ShipGridComponent a, ShipGridComponent b, float seconds) {
        Tile src = a.getTile(2, 3), dst = b.getTile(2, 3);
        dst.setCovering(FloorCovering.CARPET, 1f);
        src.setCovering(FloorCovering.CARPET, 1f);
        src.ignite();
        for (int i = 0; i < Math.round(seconds / DT) && !dst.isBurning(); i++) {
            src.fireIntensity = 1f; // keep the source going
            src.fuel = 100f;
            run(a, b, DT);
        }
        return dst.isBurning();
    }

    @Test
    void fireJumpsAcrossAnOpenDockButNotThroughAShutDoor() {
        ShipGridComponent a = craft(), b = craft();
        GridLink.join(new Entity(), a, 2, 3, Side.TOP, new Entity(), b, 2, 3, Side.TOP).setGap(1f);
        door(a).bulkheadSealed = false;
        assertFalse(spreads(a, b, 120f), "b's door is shut");

        ShipGridComponent c = craft(), d = craft();
        GridLink.join(new Entity(), c, 2, 3, Side.TOP, new Entity(), d, 2, 3, Side.TOP).setGap(1f);
        door(c).bulkheadSealed = false;
        door(d).bulkheadSealed = false;
        assertTrue(spreads(c, d, 120f), "both doors open: it jumps");
    }

    @Test
    void heatCrossesAnOpenDock() {
        ShipGridComponent a = craft(), b = craft();
        GridLink.join(new Entity(), a, 2, 3, Side.TOP, new Entity(), b, 2, 3, Side.TOP).setGap(1f);
        door(a).bulkheadSealed = false;
        door(b).bulkheadSealed = false;
        a.getTile(2, 3).temperature = 800f;
        float before = b.getTile(2, 3).temperature;
        hz.exchangeAcrossLinks(a, DT);
        assertTrue(b.getTile(2, 3).temperature > before + 1f);
        assertTrue(b.getTile(2, 3).temperature < a.getTile(2, 3).temperature);
    }
}
