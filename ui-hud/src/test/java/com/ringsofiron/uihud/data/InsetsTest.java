package com.ringsofiron.uihud.data;

import com.badlogic.ashley.core.Entity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frame of the game's magnified insets a HUD variant reads. */
class InsetsTest {
    private final Insets insets = new Insets();

    @Test
    void theGameFillsAtMostThreeAndAHudFindsEachByItsCraft() {
        assertEquals(0, new HudData().insets.count());
        Entity ship = new Entity(), raider = new Entity();
        Insets.Inset own = insets.add();
        own.entity = ship;
        own.own = true;
        Insets.Inset lock = insets.add();
        lock.entity = raider;
        lock.locked = lock.nav = true;
        lock.label = "Raider 6 (surrendered)";
        assertEquals(2, insets.count());
        assertSame(lock, insets.of(raider));
        assertSame(own, insets.get(0));
        assertNull(insets.of(new Entity()));
        assertNull(insets.of(null));
        insets.add();
        assertNull(insets.add(), "three at most: the player's ship and two targets");
        assertEquals(Insets.MAX, insets.count());
        assertThrows(IndexOutOfBoundsException.class, () -> insets.get(Insets.MAX));
    }

    @Test
    void aFrameStartsEmptyAndEachInsetStartsBlank() {
        Insets.Inset in = insets.add();
        in.entity = new Entity();
        in.locked = in.onScreen = in.centered = true;
        in.label = "Freighter";
        in.distance = 3_000f;
        insets.clear();
        assertEquals(0, insets.count());
        assertThrows(IndexOutOfBoundsException.class, () -> insets.get(0));
        Insets.Inset again = insets.add();
        assertSame(in, again);
        assertNull(again.entity);
        assertFalse(again.locked || again.onScreen || again.centered);
        assertEquals("", again.label);
        assertTrue(Float.isNaN(again.distance));
    }
}
