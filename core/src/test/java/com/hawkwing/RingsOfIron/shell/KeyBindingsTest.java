package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.ObjectMap;
import com.hawkwing.RingsOfIron.shell.KeyBindings.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyBindingsTest {

    @Test
    void byDefaultEveryKeyIsItself() {
        KeyBindings b = new KeyBindings();
        assertTrue(b.isDefault());
        assertEquals(Input.Keys.W, b.keyOf(Action.THRUST));
        assertEquals(Input.Keys.Q, b.physical(Input.Keys.Q));
        assertEquals(Action.FIRE, b.actionOn(Input.Keys.SPACE));
        assertNull(b.actionOn(Input.Keys.Q));
    }

    @Test
    void bindingToAFreeKeyMovesTheGamesKeyThere() {
        KeyBindings b = new KeyBindings();
        assertNull(b.bind(Action.THRUST, Input.Keys.Q));
        assertEquals(Input.Keys.Q, b.keyOf(Action.THRUST));
        // Pressing Q is the game's W; the old W key now plays the game's Q, which nothing reads
        assertEquals(Input.Keys.W, b.logical(Input.Keys.Q));
        assertEquals(Input.Keys.Q, b.logical(Input.Keys.W));
        assertNull(b.actionOn(Input.Keys.W));
    }

    @Test
    void bindingToATakenKeySwapsTheTwoActions() {
        KeyBindings b = new KeyBindings();
        assertEquals(Action.FIRE, b.bind(Action.MISSILE, Input.Keys.SPACE));
        assertEquals(Input.Keys.SPACE, b.keyOf(Action.MISSILE));
        assertEquals(Input.Keys.R, b.keyOf(Action.FIRE));
        assertEquals(Action.MISSILE, b.actionOn(Input.Keys.SPACE));
    }

    @Test
    void physicalAndLogicalStayInversesAfterManyBinds() {
        KeyBindings b = new KeyBindings();
        b.bind(Action.THRUST, Input.Keys.UP);
        b.bind(Action.BURN_PROGRADE, Input.Keys.I);
        b.bind(Action.FIRE, Input.Keys.W);
        b.bind(Action.TURN_LEFT, Input.Keys.LEFT);
        for (int k = 0; k <= Input.Keys.MAX_KEYCODE; k++) assertEquals(k, b.logical(b.physical(k)), "key " + k);
        // Each action is on its own key
        java.util.Set<Integer> keys = new java.util.HashSet<>();
        for (Action a : Action.values()) assertTrue(keys.add(b.keyOf(a)), a + " shares a key");
    }

    @Test
    void escapeCannotBeBound() {
        KeyBindings b = new KeyBindings();
        assertNull(b.bind(Action.FIRE, Input.Keys.ESCAPE));
        assertEquals(Input.Keys.SPACE, b.keyOf(Action.FIRE));
        assertTrue(b.isDefault());
    }

    @Test
    void savedBindingsReadBackTheSame() {
        KeyBindings b = new KeyBindings();
        b.bind(Action.THRUST, Input.Keys.S);
        b.bind(Action.FIRE, Input.Keys.J);
        b.bind(Action.VIEW, Input.Keys.F1);
        ObjectMap<String, String> saved = new ObjectMap<>(b.toMap());
        assertFalse(saved.containsKey(Action.MISSILE.name()), "actions on their default key are left out");

        KeyBindings read = new KeyBindings();
        read.fromMap(saved);
        for (Action a : Action.values()) assertEquals(b.keyOf(a), read.keyOf(a), a.name());
    }

    @Test
    void unknownNamesInTheFileAreSkipped() {
        ObjectMap<String, String> map = new ObjectMap<>();
        map.put("NO_SUCH_ACTION", "Q");
        map.put(Action.FIRE.name(), "not a key");
        KeyBindings b = new KeyBindings();
        b.fromMap(map);
        assertTrue(b.isDefault());
    }
}
