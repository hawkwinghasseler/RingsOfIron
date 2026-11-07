package com.ringsofiron.uihud.variants.vfd.screens;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A terminal's soft keys: the walk, and a key that can't be undone, which the walk never lands on unasked. */
class SoftKeysTest {
    @Test
    void theWalkMovesOnFromAKeyThatCantBeTakenNow() {
        SoftKeys k = new SoftKeys().define(3, 3);
        k.set(0, "BUY", false).set(1, "SELL", true).set(2, "DUMP", true);
        k.settle();
        assertEquals(1, k.walked());
        assertEquals(1, k.key(Keys.ENTER));
        assertEquals(SoftKeys.MOVED, k.key(Keys.RIGHT));
        assertEquals(2, k.walked());
    }

    @Test
    void aGuardedKeyIsWalkedToByThePlayerAloneSoASecondEnterNeverPressesIt() {
        SoftKeys k = new SoftKeys().define(2, 2).guard(1);
        k.set(0, "SET AS NAV", true).set(1, "GIVE UP", true);
        assertEquals(0, k.key(Keys.ENTER));
        // Set as nav done: it can't be taken again, and the walk stays on it
        k.set(0, "SET AS NAV", false);
        k.settle();
        assertEquals(0, k.walked());
        assertFalse(k.can(k.key(Keys.ENTER)), "Enter presses nothing that can be taken");
        // Walked to by the player, it presses as any key does
        assertEquals(SoftKeys.MOVED, k.key(Keys.RIGHT));
        assertEquals(1, k.key(Keys.ENTER));
    }
}
