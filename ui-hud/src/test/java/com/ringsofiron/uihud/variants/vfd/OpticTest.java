package com.ringsofiron.uihud.variants.vfd;

import com.badlogic.ashley.core.Entity;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.data.Insets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where FILAMENT puts the parts of a magnified inset, and the room it asks the game for. */
class OpticTest {
    private static final float H = 800f;
    private final Optic.Layout l = new Optic.Layout();
    private final Optic.Look plain = new Optic.Look(), lock = new Optic.Look();

    OpticTest() {
        lock.lock = true;
    }

    /** An inset of the game's size at ({@code x}, {@code y}) on the stage, y down, onto a craft at ({@code craftX}, {@code craftY}). */
    private static Insets.Inset inset(float x, float y, float craftX, float craftY) {
        Insets insets = new Insets();
        Insets.Inset in = insets.add();
        in.x = x;
        in.y = H - y;
        in.radius = 56f;
        in.craftX = craftX;
        in.craftY = H - craftY;
        in.onScreen = craftX >= 0f && craftX <= 1280f && craftY >= 0f && craftY <= H;
        in.reach = 2f;
        return in;
    }

    @Test
    void overItsCraftTheRingHasNothingToPointAtAndItsCaptionSitsJustUnder() {
        Insets.Inset in = inset(640f, 400f, 640f, 400f);
        in.centered = true;
        Optic.layout(in, H, plain, l);
        assertEquals(640f, l.cx);
        assertEquals(400f, l.cy);
        assertEquals(59f, l.ring);
        assertFalse(l.chain || l.chevron);
        assertEquals(400f + 59f + Optic.CAPTION_GAP, l.captionTop);
        // A lock round the ring puts the caption under its ticks
        Optic.layout(in, H, lock, l);
        assertTrue(Optic.markOut(lock) >= Optic.LOCK_OUT + Optic.TICKS);
        assertEquals(400f + 59f + Optic.markOut(lock) + Optic.CAPTION_GAP, l.captionTop);
    }

    @Test
    void besideItsCraftADotChainRunsFromPastTheRingToJustShortOfIt() {
        Insets.Inset in = inset(500f, 300f, 700f, 300f);
        Optic.layout(in, H, plain, l);
        assertTrue(l.chain);
        assertFalse(l.chevron);
        assertEquals(500f + 59f + 4f, l.x0, 1e-3f);
        assertEquals(700f - 5f, l.x1, 1e-3f, "short of the craft by its reach and a margin");
        assertEquals(300f, l.y0, 1e-3f);
        assertEquals(300f, l.y1, 1e-3f);
        // With a lock it starts past the brackets' ticks
        Optic.layout(in, H, lock, l);
        assertEquals(500f + 59f + Optic.markOut(lock) + 4f, l.x0, 1e-3f);
        // Too close to need one
        Optic.layout(inset(500f, 300f, 560f, 300f), H, plain, l);
        assertFalse(l.chain);
    }

    @Test
    void outOfViewAChevronPastTheRingAndItsMarkPointsAtTheCraftAndTheCaptionKeepsUnderIt() {
        Insets.Inset in = inset(1100f, 300f, 1600f, 300f);
        Optic.layout(in, H, plain, l);
        assertTrue(l.chevron);
        assertFalse(l.chain);
        assertEquals(1f, l.dx, 1e-3f);
        assertEquals(0f, l.dy, 1e-3f);
        assertEquals(59f + Optic.CHEVRON_OUT, l.tip);
        assertEquals(300f + 59f + Optic.CAPTION_GAP, l.captionTop);
        // Its arms, three dots back, stand clear of a lock's ticks
        Optic.layout(in, H, lock, l);
        assertTrue(l.tip - 6f > 59f + Optic.markOut(lock) + 2f, "tip " + l.tip);
        // Pointing down, the caption goes under the chevron
        Optic.layout(inset(640f, 600f, 640f, 1200f), H, lock, l);
        assertEquals(1f, l.dy, 1e-3f);
        assertTrue(l.captionTop > 600f + l.tip + 2f, "caption " + l.captionTop + ", tip " + l.tip);
    }

    @Test
    void theCaptionCountsTheMarksItHides() {
        Optic.Look look = new Optic.Look();
        assertEquals("", Optic.sub(look));
        look.others = 3;
        assertEquals("+3", Optic.sub(look));
        look.sub = "12.4 KM";
        look.others = 2;
        assertEquals("12.4 KM +2", Optic.sub(look));
        look.mark = look.text = Phosphor.ZINC;
        look.lock = true;
        look.clear();
        assertEquals("", look.name + look.sub);
        assertEquals(0, look.others);
        assertFalse(look.lock);
        assertNull(look.mark);
        assertNull(look.text);
    }

    @Test
    void aMarkUnderThePictureOrTheCaptionIsCovered() {
        Insets.Inset in = inset(640f, 400f, 640f, 400f);
        assertTrue(Optic.covers(in, H, 630f, 390f, 20f, 20f), "on the picture");
        assertTrue(Optic.covers(in, H, 690f, 395f, 30f, 10f), "reaching over the ring");
        assertTrue(Optic.covers(in, H, 600f, 400f + 56f + 20f, 60f, 14f), "where the caption goes");
        assertFalse(Optic.covers(in, H, 760f, 400f, 30f, 14f), "beside it");
        assertFalse(Optic.covers(in, H, 640f - 120f, 400f + 56f + 20f, 30f, 14f), "beside the caption");
        assertFalse(Optic.covers(in, H, 600f, 300f - 70f, 60f, 14f), "above it");
    }

    @Test
    void aMarkOnALocksBracketsOrANavsDiamondIsCoveredToo() {
        Insets.Inset in = inset(640f, 400f, 640f, 400f);
        // Where the lock's top tick goes, 70 units up; at its brackets' top right corner; at the nav's right point
        float[] tick = {632f, 322f, 16f, 8f}, corner = {696f, 334f, 6f, 6f}, point = {703f, 404f, 6f, 6f};
        for (float[] b : new float[][] {tick, corner, point}) {
            assertFalse(Optic.covers(in, H, b[0], b[1], b[2], b[3]), "clear of a plain ring at " + b[0] + ", " + b[1]);
        }
        in.locked = true;
        assertTrue(Optic.covers(in, H, tick[0], tick[1], tick[2], tick[3]), "on the lock's tick");
        assertTrue(Optic.covers(in, H, corner[0], corner[1], corner[2], corner[3]), "on the lock's bracket");
        assertFalse(Optic.covers(in, H, point[0], point[1], point[2], point[3]), "beside the lock's side tick");
        in.locked = false;
        in.nav = true;
        assertTrue(Optic.covers(in, H, point[0], point[1], point[2], point[3]), "on the nav's point");
        assertFalse(Optic.covers(in, H, tick[0], tick[1], tick[2], tick[3]), "past the nav's top point");
        assertFalse(Optic.covers(in, H, corner[0], corner[1], corner[2], corner[3]), "between the nav's points");
    }

    @Test
    void theShipsReachIsHalfItsDeckPlansDiagonal() {
        Entity ship = new Entity();
        assertEquals(0f, Optic.hullReach(ship, 1f), "no deck plan, no reach");
        assertEquals(0f, Optic.hullReach(null, 1f));
        // 20 x 8 tiles of 2 m: a 40 x 16 m hull, 21.5 m from its middle to a corner
        ship.add(new ShipGridComponent(20, 8, 2f));
        assertEquals(21.54f, Optic.hullReach(ship, 1f), 0.01f);
        assertEquals(2.154f, Optic.hullReach(ship, 10f), 0.001f);
    }

    @Test
    void theRoomAskedOfTheGameHoldsTheMarksAndATwoLineCaption() {
        float picture = 56f, ring = picture + Optic.RING_GAP, mark = Optic.markOut(lock);
        assertTrue(ring + mark <= picture + Optic.ABOVE, "the lock's ticks above: " + (ring + mark));
        assertTrue(ring + mark <= picture + Optic.SIDE, "the lock's ticks either side: " + (ring + mark));
        Optic.Look nav = new Optic.Look();
        nav.nav = true;
        assertTrue(ring + Optic.markOut(nav) <= picture + Optic.ABOVE, "the nav diamond's points");
        assertTrue(Optic.width("RAIDER 6 +12") / 2f <= picture + Optic.SIDE, "a 12-cell line");
        float caption = ring + mark + Optic.CAPTION_GAP + Optic.LINE + 7f * Optic.PITCH;
        assertTrue(caption <= picture + Optic.BELOW, "two lines under a lock: " + caption);
    }
}
