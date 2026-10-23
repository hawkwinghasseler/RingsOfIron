package com.ringsofiron.interiorgrid;

import static com.ringsofiron.interiorgrid.TestShips.*;
import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import org.junit.jupiter.api.Test;

/**
 * Furnishings set down anywhere on a deck, at an offset within their tile and any angle: what they
 * fit around, that a fire burns them, and that a save keeps them. TestShips' right room (x 10 to 18,
 * y 1 to 6) is bare steel, with a system at (15, 3) and a thin wall along x = 10's left edge.
 */
class FurnishingTest {
    static final FurnishingType COUCH = new FurnishingType("couch", "Couch", "decor/couch", 1.5f, 0.75f, 6f, 900,
            FurnishingType.Use.LOUNGE, 3, 0.04f, null);
    static final FurnishingType POOL = new FurnishingType("pool_table", "Pool table", null, 2f, 1f, 8f, 2500,
            FurnishingType.Use.GAME, 2, 0.05f, "shooting pool");
    static final FurnishingType TABLE = new FurnishingType("coffee_table", "Coffee table", null, 0.75f, 0.5f, 2f, 200,
            FurnishingType.Use.DECOR, 1, 0f, null);
    static final FurnishingType RUG = new FurnishingType("rug", "Rug", null, 1.5f, 1f, 2f, 300,
            FurnishingType.Use.DECOR, 1, 0f, null);

    static {
        RUG.flat = true;
    }

    private final GridHazardSystem hz = new GridHazardSystem(1);

    @Test
    void aFurnishingSitsAtAnExactSpotWithinItsTileAndAnyAngle() {
        ShipGridComponent g = grid();
        Furnishing chair = Furnishing.at(COUCH, 12.8f, 4.3f, 45f);
        assertNull(Furnishings.place(g, chair));
        assertEquals(12, chair.tileX);
        assertEquals(4, chair.tileY);
        assertEquals(0.3f, chair.offsetX, 1e-5f);
        assertEquals(-0.2f, chair.offsetY, 1e-5f);
        assertEquals(45f, chair.rotation, 1e-5f);
        assertTrue(g.getTile(12, 4).furnishings.contains(chair, true), "it belongs to the tile its middle is over");
        assertEquals(1, Furnishings.all(g).size);
        assertSame(chair, Furnishings.at(g, 12.8f, 4.3f));
        assertNull(Furnishings.at(g, 12.8f, 5.3f), "nothing a tile away");
        // A turn of -90 is the same as 270
        assertEquals(270f, new Furnishing(COUCH, 1, 1, 0f, 0f, -90f).rotation, 1e-5f);
    }

    @Test
    void itMustStandOnOpenFloorClearOfWallsAndSystems() {
        ShipGridComponent g = grid();
        assertNotNull(Furnishings.place(g, Furnishing.at(COUCH, 18.5f, 1.5f, 0f)), "reaches into the hull");
        assertNotNull(Furnishings.place(g, Furnishing.at(COUCH, 10.3f, 1.5f, 0f)), "straddles the thin wall");
        assertNotNull(Furnishings.place(g, Furnishing.at(COUCH, 15.5f, 3.5f, 0f)), "on top of a system");
        assertNotNull(Furnishings.place(g, Furnishing.at(POOL, 13.5f, 1.6f, 90f)), "turned on end it reaches the hull");
        assertNull(Furnishings.place(g, Furnishing.at(POOL, 13.5f, 1.6f, 0f)), "lying along the room it fits");
        assertNull(Furnishings.place(g, Furnishing.at(COUCH, 10.8f, 4.5f, 90f)), "turned, it fits against the thin wall");
        assertEquals(2, Furnishings.all(g).size);
    }

    @Test
    void furnishingsDontOverlapExceptOnARug() {
        ShipGridComponent g = grid();
        assertNull(Furnishings.place(g, Furnishing.at(RUG, 13.5f, 4.5f, 10f)));
        assertNull(Furnishings.place(g, Furnishing.at(TABLE, 13.5f, 4.5f, 0f)), "the table stands on the rug");
        assertNull(Furnishings.place(g, Furnishing.at(COUCH, 13.5f, 5.5f, 0f)), "and the couch behind it");
        String no = Furnishings.place(g, Furnishing.at(COUCH, 13.9f, 5.6f, 30f));
        assertNotNull(no);
        assertTrue(no.contains("couch"), no);
        Array<Furnishing> pending = new Array<>();
        pending.add(Furnishing.at(TABLE, 16.5f, 1.5f, 0f));
        assertNotNull(Furnishings.refusal(g, Furnishing.at(TABLE, 16.7f, 1.6f, 0f), pending), "one on its way holds its spot");
        assertTrue(Furnishings.remove(g, Furnishings.at(g, 13.5f, 5.5f)));
        assertNull(Furnishings.place(g, Furnishing.at(COUCH, 13.9f, 5.6f, 30f)), "with the first couch gone the second fits");
    }

    @Test
    void aFurnishingOnBareDeckBurnsAndIsGone() {
        ShipGridComponent g = grid();
        Tile bare = g.getTile(13, 4), other = g.getTile(16, 1);
        assertFalse(bare.isFlammable(), "bare steel doesn't burn");
        Furnishing couch = Furnishing.at(COUCH, 13.5f, 4.5f, 0f);
        assertNull(Furnishings.place(g, couch));
        assertTrue(bare.isFlammable(), "the couch does");
        assertTrue(bare.ignite());
        assertFalse(other.ignite());
        run(hz, g, 60);
        assertTrue(couch.condition() < 1f && couch.condition() > Furnishing.BURNT, "a minute in it is burning: " + couch.condition());
        run(hz, g, 1200);
        assertFalse(bare.isBurning());
        assertEquals(0, bare.furnishings.size, "burned away");
        assertTrue(g.burntFurnishings.contains(couch, true), "and reported");
        assertTrue(couch.isBurnt());
    }

    @Test
    void aFireCaughtEarlyLeavesItScorched() {
        ShipGridComponent g = grid();
        Furnishing couch = Furnishing.at(COUCH, 13.5f, 4.5f, 0f);
        Furnishings.place(g, couch);
        g.getTile(13, 4).ignite();
        run(hz, g, 120);
        g.getTile(13, 4).extinguish();
        run(hz, g, 5);
        assertTrue(couch.condition() < 1f, "scorched: " + couch.condition());
        assertTrue(g.getTile(13, 4).furnishings.contains(couch, true), "but still there");
    }

    @Test
    void aCarpetFireBurnsTheFurnishingsOnItToo() {
        ShipGridComponent g = grid();
        Furnishing couch = Furnishing.at(COUCH, 5.5f, 1.5f, 0f);
        assertNull(Furnishings.place(g, couch));
        Tile t = g.getTile(5, 1);
        float carpet = t.fuel;
        t.ignite();
        run(hz, g, 30);
        float carpetShare = (carpet - t.fuel) / carpet, couchShare = 1f - couch.condition();
        assertTrue(carpetShare > 0f);
        assertEquals(carpetShare, couchShare, 1e-3f, "both burn their share");
    }

    @Test
    void aSaveKeepsWhereEachOneIsAndWhatIsLeftOfIt() {
        ShipGridComponent g = grid();
        Furnishing couch = Furnishing.at(COUCH, 12.8f, 4.3f, 37.5f);
        Furnishings.place(g, couch);
        couch.fuel = 4f;
        Furnishings.place(g, Furnishing.at(RUG, 15.5f, 1.5f, 0f));
        Json json = new Json();
        json.setSerializer(ShipGridComponent.class, new ShipGridSerializer());
        ShipGridComponent back = json.fromJson(ShipGridComponent.class, json.toJson(g, ShipGridComponent.class));
        Array<Furnishing> all = Furnishings.all(back);
        assertEquals(2, all.size);
        Furnishing c = back.getTile(12, 4).furnishings.first();
        assertEquals("couch", c.type.id);
        assertEquals("Couch", c.type.name);
        assertEquals(FurnishingType.Use.LOUNGE, c.type.use);
        assertEquals(3, c.type.seats);
        assertEquals(12.8f, c.x(), 1e-5f);
        assertEquals(4.3f, c.y(), 1e-5f);
        assertEquals(37.5f, c.rotation, 1e-5f);
        assertEquals(4f, c.fuel, 1e-5f);
        assertEquals(6f, c.type.fuel, 1e-5f);
        assertTrue(back.getTile(15, 1).furnishings.first().type.flat);
    }

    @Test
    void crewSettleOnItWithoutLeavingItsTile() {
        Furnishing couch = Furnishing.at(COUCH, 12.5f, 4.5f, 90f);
        Vector2 seat = new Vector2();
        for (int i = 0; i < COUCH.seats; i++) {
            couch.seat(i, seat);
            assertEquals(12, (int) Math.floor(seat.x));
            assertEquals(4, (int) Math.floor(seat.y));
        }
        // Turned 90 degrees, the seats run along y
        assertTrue(couch.seat(0, new Vector2()).y < couch.seat(2, new Vector2()).y);
    }
}
