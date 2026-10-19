package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.IntSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GamepadKeysTest {

    @Test
    void sticksPastTheDeadzoneFlyAndWalk() {
        GamepadKeys.Pad p = new GamepadKeys.Pad();
        IntSet keys = new IntSet();
        p.leftY = -0.2f; // inside the deadzone
        p.leftX = 0.9f;
        GamepadKeys.keysFor(p, keys);
        assertFalse(keys.contains(Input.Keys.W));
        assertTrue(keys.contains(Input.Keys.D));

        keys.clear();
        p.leftY = -0.8f; // up is negative
        p.rightX = -0.7f;
        GamepadKeys.keysFor(p, keys);
        assertTrue(keys.contains(Input.Keys.W));
        assertTrue(keys.contains(Input.Keys.Z));
        assertFalse(keys.contains(Input.Keys.S));
    }

    @Test
    void buttonsPressTheirKeys() {
        GamepadKeys.Pad p = new GamepadKeys.Pad();
        IntSet keys = new IntSet();
        p.a = true;
        p.start = true;
        p.l2 = true;
        GamepadKeys.keysFor(p, keys);
        assertTrue(keys.contains(Input.Keys.SPACE), "A fires");
        assertTrue(keys.contains(Input.Keys.E), "A uses a console");
        assertTrue(keys.contains(Input.Keys.ESCAPE), "Start pauses");
        assertTrue(keys.contains(Input.Keys.TAB), "left trigger cycles the target");
        assertEquals(4, keys.size);
    }

    @Test
    void anIdlePadPressesNothing() {
        IntSet keys = new IntSet();
        GamepadKeys.keysFor(new GamepadKeys.Pad(), keys);
        assertEquals(0, keys.size);
        GamepadKeys pad = new GamepadKeys();
        pad.setEnabled(false);
        pad.poll();
        assertFalse(pad.any());
    }
}
