package com.ringsofiron.uihud.data;

import com.ringsofiron.uihud.data.AlertRegistry.Category;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRegistryTest {

    @Test
    void anAlertNotRaisedAgainHasCleared() {
        AlertRegistry r = new AlertRegistry();
        r.begin(1);
        r.raise("fire:HOLD", Level.WARNING, Category.LIFE_SUPPORT).set("FIRE", "Fire in HOLD");
        r.end();
        assertEquals(1, r.size());
        r.begin(2);
        r.end();
        assertEquals(0, r.size());
        assertNull(r.get("fire:HOLD"));
    }

    @Test
    void aHeldAlertKeepsWhenItStartedAndIsFreshOnlyOnce() {
        AlertRegistry r = new AlertRegistry();
        r.begin(10);
        AlertRegistry.Alert a = r.raise("missile", Level.WARNING, Category.TACTICAL);
        r.end();
        assertTrue(a.fresh);
        r.begin(11);
        AlertRegistry.Alert again = r.raise("missile", Level.WARNING, Category.TACTICAL);
        r.end();
        assertSame(a, again);
        assertFalse(again.fresh);
        assertEquals(10, again.since);
    }

    @Test
    void acknowledgingPutsTheMasterLampOutUntilSomethingWorse() {
        AlertRegistry r = new AlertRegistry();
        r.begin(0);
        r.raise("hull", Level.CAUTION, Category.ENGINEERING);
        r.raise("eva", Level.ADVISORY, Category.CREW);
        r.end();
        assertEquals(Level.CAUTION, r.master());
        r.acknowledgeAll();
        assertNull(r.master());
        r.begin(1);
        r.raise("hull", Level.WARNING, Category.ENGINEERING);
        r.raise("eva", Level.ADVISORY, Category.CREW);
        r.end();
        assertEquals(Level.WARNING, r.master());
        assertEquals(Level.WARNING, r.worst());
    }

    @Test
    void allIsWorstFirstThenOldest() {
        AlertRegistry r = new AlertRegistry();
        r.begin(0);
        r.raise("a", Level.CAUTION, Category.SHIP);
        r.end();
        r.begin(1);
        r.raise("a", Level.CAUTION, Category.SHIP);
        r.raise("b", Level.WARNING, Category.SHIP);
        r.raise("c", Level.CAUTION, Category.SHIP);
        r.end();
        assertEquals("b", r.all().get(0).id);
        assertEquals("a", r.all().get(1).id);
        assertEquals("c", r.all().get(2).id);
    }

    @Test
    void twoRaisesInAFrameKeepTheWorseLevel() {
        AlertRegistry r = new AlertRegistry();
        r.begin(0);
        r.raise("x", Level.WARNING, Category.SHIP);
        r.raise("x", Level.CAUTION, Category.SHIP);
        r.end();
        assertEquals(Level.WARNING, r.get("x").level);
    }
}
