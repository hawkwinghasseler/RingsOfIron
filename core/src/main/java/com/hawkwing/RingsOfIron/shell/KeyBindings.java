package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;

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
    /** Every rebindable action, with the key the game reads for it. Shared keys do both things, as in the game. */
    public enum Action {
        THRUST("Main engine forward / walk up", Input.Keys.W),
        REVERSE("Main engine back / walk down", Input.Keys.S),
        TURN_LEFT("Turn left / walk left", Input.Keys.A),
        TURN_RIGHT("Turn right / walk right", Input.Keys.D),
        STRAFE_LEFT("Strafe left", Input.Keys.Z),
        STRAFE_RIGHT("Strafe right", Input.Keys.C),
        ROTATION_ASSIST("Rotation assist", Input.Keys.X),
        FIRE("Fire guns", Input.Keys.SPACE),
        MISSILE("Launch missile", Input.Keys.R),
        DECOY("Decoy / force door", Input.Keys.F),
        CUT("Cut door / next burn", Input.Keys.B),
        NEXT_TARGET("Next target", Input.Keys.TAB),
        WARP_UP("Time warp up", Input.Keys.PERIOD),
        WARP_DOWN("Time warp down", Input.Keys.COMMA),
        VIEW("Deck / outside view", Input.Keys.V),
        USE("Use console", Input.Keys.E),
        LIGHTS("Deck lights", Input.Keys.O),
        MAP("Traffic map", Input.Keys.M),
        TRADE("Station market", Input.Keys.T),
        YARD("Station yard", Input.Keys.Y),
        ARM_PORT("Arm port / ask clearance", Input.Keys.G),
        NEXT_PORT("Next docking port", Input.Keys.H),
        UNDOCK("Undock", Input.Keys.U),
        PLAN_BURN("Plan a burn", Input.Keys.N),
        BURN_PROGRADE("Burn: more prograde", Input.Keys.UP),
        BURN_RETROGRADE("Burn: more retrograde", Input.Keys.DOWN),
        BURN_RADIAL_OUT("Burn: radial out", Input.Keys.RIGHT),
        BURN_RADIAL_IN("Burn: radial in", Input.Keys.LEFT),
        BURN_LATER("Burn: move later", Input.Keys.RIGHT_BRACKET),
        BURN_EARLIER("Burn: move earlier", Input.Keys.LEFT_BRACKET),
        BURN_DELETE("Burn: delete", Input.Keys.FORWARD_DEL),
        MISSIONS("Mission board", Input.Keys.J),
        BOARD("Boarding window", Input.Keys.K),
        ABANDON("Abandon ship (press twice)", Input.Keys.P),
        LOG("Notice log", Input.Keys.L),
        HELP("Controls", Input.Keys.F1),
        QUICK_SAVE("Quick save", Input.Keys.F5),
        QUICK_LOAD("Quick load", Input.Keys.F9);

        public final String label;
        public final int defaultKey;

        Action(String label, int defaultKey) {
            this.label = label;
            this.defaultKey = defaultKey;
        }
    }

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
        String s = Input.Keys.toString(key);
        return s == null ? "?" : s;
    }
}
