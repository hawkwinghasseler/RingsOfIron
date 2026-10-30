package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;

import com.ringsofiron.coreengine.input.Action;
import java.util.function.Function;

import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;

/**
 * Which key does what. The game reads its default keys ({@link Action#defaultKey}); this keeps a
 * permutation of every key code saying which physical key now plays each of them, and
 * {@link RemappedInput} reads the game's keys through it. So rebinding needs no change to the code
 * that reads the keys.
 *
 * <p>Rebinding an action to a key that already does something swaps the two: the other action
 * takes the old key. Every key still does exactly one thing.
 */
public final class KeyBindings {
    /** Keys that are never rebound or bound to: Esc opens the pause menu and closes windows. */
    public static boolean reserved(int key) {
        return key == Input.Keys.ESCAPE || key == Input.Keys.ANY_KEY || key == Input.Keys.UNKNOWN
                || key < 0 || key > Input.Keys.MAX_KEYCODE;
    }

    /** physical[k]: the physical key that plays the game's key k. */
    private final int[] physical = new int[Input.Keys.MAX_KEYCODE + 1];
    /** logical[p]: the game's key that physical key p plays. */
    private final int[] logical = new int[Input.Keys.MAX_KEYCODE + 1];

    public KeyBindings() {
        reset();
    }

    public void reset() {
        for (int k = 0; k < physical.length; k++) {
            physical[k] = k;
            logical[k] = k;
        }
    }

    /** The physical key that plays the game's key {@code key}. */
    public int physical(int key) {
        return key < 0 || key >= physical.length ? key : physical[key];
    }

    /** The game's key that physical key {@code key} plays. */
    public int logical(int key) {
        return key < 0 || key >= logical.length ? key : logical[key];
    }

    public int keyOf(Action action) {
        return physical[action.defaultKey];
    }

    /** The action physical key {@code key} does, or null. */
    public Action actionOn(int key) {
        int k = logical(key);
        for (Action a : Action.values()) if (a.defaultKey == k) return a;
        return null;
    }

    /**
     * Binds {@code action} to physical {@code key}; whatever {@code key} did moves to the action's
     * old key. Returns the action that moved, or null. A reserved key is refused (returns null, binds nothing).
     */
    public Action bind(Action action, int key) {
        if (reserved(key)) return null;
        int game = action.defaultKey;
        int old = physical[game];
        if (old == key) return null;
        int other = logical[key];
        physical[game] = key;
        logical[key] = game;
        physical[other] = old;
        logical[old] = other;
        return actionOn(old);
    }

    public boolean isDefault() {
        for (int k = 0; k < physical.length; k++) if (physical[k] != k) return false;
        return true;
    }

    /** Action name to key name, for the settings file; actions on their default key are left out. */
    public OrderedMap<String, String> toMap() {
        OrderedMap<String, String> out = new OrderedMap<>();
        for (Action a : Action.values()) {
            if (keyOf(a) != a.defaultKey) out.put(a.name(), Input.Keys.toString(keyOf(a)));
        }
        return out;
    }

    /** Reads {@link #toMap}'s form; unknown actions or keys are skipped. */
    public void fromMap(ObjectMap<String, String> map) {
        reset();
        if (map == null) return;
        for (Action a : Action.values()) {
            String name = map.get(a.name());
            if (name == null) continue;
            int key = Input.Keys.valueOf(name);
            if (key >= 0) bind(a, key);
        }
    }

    /** A key's name for the settings window. */
    public static String keyName(int key) {
        return Action.keyName(key);
    }

    /** Each action's name for the key it is bound to now, for hints and the guide. */
    public Function<Action, String> keyNames() {
        return a -> keyName(keyOf(a));
    }
}
