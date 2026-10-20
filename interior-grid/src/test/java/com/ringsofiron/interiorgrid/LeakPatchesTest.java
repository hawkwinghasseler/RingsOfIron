package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

/** Leaks to space a crew can patch in the field, the patches themselves, and warp around them. */
class LeakPatchesTest {

    /** A hull tile on the left room's outer wall, beside floor (1, 3). */
    static final int HX = 0, HY = 3;

    private static ShipGridComponent leaking(float condition) {
        ShipGridComponent g = grid();
        g.getTile(HX, HY).setIntegrity(condition);
        new GridHazardSystem(1).step(g, DT);
        return g;
    }

    @Test
    void aHullTileBetweenAQuarterAndHalfLeaksWithoutBeingABreach() {
        ShipGridComponent g = leaking(0.4f);
        Tile hull = g.getTile(HX, HY);
        assertFalse(hull.isHullBreach());
        assertTrue(g.isVenting(), "the room vents through the damaged hull");
        Array<LeakPatches.Leak> leaks = LeakPatches.leaks(g);
        assertEquals(1, leaks.size);
        assertTrue(leaks.first().same(HX, HY, null), "the leak is the hull tile itself");
        assertTrue(leaks.first().flow > ShipGridComponent.VENTING_HAZARD_FLOW);
    }

    @Test
    void aPatchStopsTheLeakFromTheStoresButLeavesTheDamage() {
        ShipGridComponent g = leaking(0.4f);
        int kits = g.patchKits;
        assertTrue(LeakPatches.patch(g, HX, HY, null));
        Tile hull = g.getTile(HX, HY);
        assertTrue(hull.isPatched());
        assertEquals(0f, hull.leakFactor());
        assertEquals(0.4f, hull.getIntegrity(), 1e-6f, "a patch is not a repair");
        assertEquals(kits - 1, g.patchKits);
        assertFalse(LeakPatches.patch(g, HX, HY, null), "nothing left to patch there");
        assertEquals(kits - 1, g.patchKits);

        // Nothing leaves any more
        GridHazardSystem hz = new GridHazardSystem(1);
        run(hz, g, 5f);
        assertFalse(g.isVenting());
        assertEquals(0, LeakPatches.leaks(g).size);
    }

    @Test
    void aNewHitBreaksThePatchAndARepairTakesItOff() {
        ShipGridComponent g = leaking(0.4f);
        Tile hull = g.getTile(HX, HY);
        LeakPatches.patch(g, HX, HY, null);
        hull.setIntegrity(0.3f);
        assertFalse(hull.isPatched(), "hit again, the patch gives way");
        assertTrue(hull.leakFactor() > 0f);

        LeakPatches.patch(g, HX, HY, null);
        hull.setIntegrity(0.45f); // mended part of the way: still patched
        assertTrue(hull.isPatched());
        hull.setIntegrity(0.6f); // mended past where it leaks
        assertFalse(hull.isPatched());
    }

    @Test
    void noKitsNoPatch() {
        ShipGridComponent g = leaking(0.4f);
        g.patchKits = 0;
        assertFalse(LeakPatches.patch(g, HX, HY, null));
        assertTrue(LeakPatches.outOfKits(g));
    }

    @Test
    void aBreachIsNotAPatchJob() {
        ShipGridComponent g = leaking(0f);
        assertTrue(g.isVenting());
        assertEquals(0, LeakPatches.leaks(g).size, "a breach is sealed with a bulkhead, not patched");
    }

    @Test
    void aWreckedDeckPlateTakesOnePatchAndHolds() {
        // A deck plate open to space below it, shot down to nothing: no wall, so no breach to seal
        ShipGridComponent g = grid();
        Tile deck = g.getTile(1, 3);
        deck.setIntegrity(0f);
        new GridHazardSystem(1).step(g, DT);
        Array<LeakPatches.Leak> leaks = LeakPatches.leaks(g);
        assertEquals(1, leaks.size);
        assertTrue(leaks.first().same(1, 3, null));
        int kits = g.patchKits;
        assertTrue(LeakPatches.patch(g, 1, 3, null));
        assertTrue(deck.isPatched(), "the patch holds on a wrecked plate");
        assertFalse(LeakPatches.patch(g, 1, 3, null), "and needs no second one");
        assertEquals(kits - 1, g.patchKits);
        run(new GridHazardSystem(1), g, 5f);
        assertEquals(0, LeakPatches.leaks(g).size);
    }

    @Test
    void aLeakingThinWallToSpaceCanBePatched() {
        ShipGridComponent g = WallLightSaveTest.room();
        // Open the right end to space behind a thin wall, and damage the wall
        g.getTile(4, 1).wallType = Tile.WallType.NONE;
        EdgeWall wall = new EdgeWall(WeightClass.LIGHT);
        g.setEdge(3, 1, Side.RIGHT, wall);
        g.gridDirty = true;
        wall.integrity = 0.3f;
        new GridHazardSystem(1).step(g, DT);
        Array<LeakPatches.Leak> leaks = LeakPatches.leaks(g);
        assertEquals(1, leaks.size);
        assertTrue(leaks.first().same(3, 1, Side.RIGHT));
        assertTrue(LeakPatches.patch(g, 3, 1, Side.RIGHT));
        assertTrue(wall.isPatched());
        assertEquals(0f, wall.leakFactor());
    }

    @Test
    void warpNamesTheCrewsPatchingOrTheYard() {
        ShipGridComponent g = leaking(0.4f);
        InteriorHazardWarpGuard guard = new InteriorHazardWarpGuard(g);
        assertEquals("Atmosphere venting: the crew are patching the leak", guard.railsWarpBlockedReason());
        g.patchKits = 0;
        assertEquals("Atmosphere venting: no hull patches left, a yard must repair the leak", guard.railsWarpBlockedReason());

        g.patchKits = 1;
        LeakPatches.patch(g, HX, HY, null);
        run(new GridHazardSystem(1), g, 5f);
        assertNull(guard.railsWarpBlockedReason(), "a patched leak doesn't hold warp back");
    }

    @Test
    void patchesAndKitsAreSaved() {
        ShipGridComponent g = leaking(0.4f);
        LeakPatches.patch(g, HX, HY, null);
        ShipGridComponent back = WallLightSaveTest.roundTrip(g);
        assertTrue(back.getTile(HX, HY).isPatched());
        assertEquals(g.patchKits, back.patchKits);
        assertEquals(LeakPatches.DEFAULT_KITS - 1, back.patchKits);
        assertFalse(back.getTile(HX + 1, HY).isPatched());
    }
}
