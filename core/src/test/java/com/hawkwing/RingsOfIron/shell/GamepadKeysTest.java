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

    @Test
    void holdingBackGivesTheStationWindowsAndTheBurnPlanner() {
        GamepadKeys.Pad p = new GamepadKeys.Pad();
        IntSet keys = new IntSet();
        p.back = true;
        p.a = true;
        p.up = true;
        p.l2 = true;
        GamepadKeys.keysFor(p, keys);
        assertTrue(keys.contains(Input.Keys.J), "Back+A the mission board");
        assertTrue(keys.contains(Input.Keys.UP), "Back+up adds prograde");
        assertTrue(keys.contains(Input.Keys.N), "Back+LT plans a burn");
        assertFalse(keys.contains(Input.Keys.SPACE), "the layer does not fire");
        assertFalse(keys.contains(Input.Keys.PERIOD), "nor warp");
    }

    @Test
    void backTappedAloneOpensTheMapOnRelease() {
        GamepadKeys pad = new GamepadKeys();
        GamepadKeys.Pad p = new GamepadKeys.Pad();
        p.back = true;
        pad.keysNow(p);
        assertFalse(pad.pressed(Input.Keys.M), "not while held");
        p.back = false;
        pad.keysNow(p);
        assertTrue(pad.pressed(Input.Keys.M), "let go: the map");

        GamepadKeys combo = new GamepadKeys();
        GamepadKeys.Pad c = new GamepadKeys.Pad();
        c.back = true;
        c.x = true;
        combo.keysNow(c);
        c.back = false;
        c.x = false;
        combo.keysNow(c);
        assertFalse(combo.pressed(Input.Keys.M), "Back used with X is not a tap");
    }

    @Test
    void inAMenuThePadMovesAndPicks() {
        GamepadKeys.Pad p = new GamepadKeys.Pad();
        IntSet keys = new IntSet();
        p.down = true;
        p.a = true;
        GamepadKeys.menuKeysFor(p, keys);
        assertTrue(keys.contains(Input.Keys.DOWN));
        assertTrue(keys.contains(Input.Keys.ENTER));
        assertFalse(keys.contains(Input.Keys.SPACE), "nothing fires in a menu");
        keys.clear();
        p.down = false;
        p.a = false;
        p.b = true;
        GamepadKeys.menuKeysFor(p, keys);
        assertTrue(keys.contains(Input.Keys.ESCAPE), "B backs out");
    }
}
