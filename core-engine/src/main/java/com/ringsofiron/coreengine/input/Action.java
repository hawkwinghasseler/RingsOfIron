package com.ringsofiron.coreengine.input;

import com.badlogic.gdx.Input;

/**
 * Every rebindable action, with the key the game reads for it. Shared keys do both things, as in
 * the game. The shell's KeyBindings says which physical key plays each one now; game code names
 * keys through a {@code Function<Action, String>} the shell hands it.
 */
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
    SHIELDS("Raise / lower shields", Input.Keys.NUM_9),
    SHIELD_TRIM("Shield trim: even, bow, port, stern, starboard", Input.Keys.NUM_8),
    CUT("Cut door / next burn", Input.Keys.B),
    NEXT_TARGET("Next target", Input.Keys.TAB),
    WARP_UP("Time warp up", Input.Keys.PERIOD),
    WARP_DOWN("Time warp down", Input.Keys.COMMA),
    VIEW("Deck / outside view", Input.Keys.V),
    USE("Use console", Input.Keys.E),
    LIGHTS("Lighting", Input.Keys.O),
    MAP("Map: orbits, burns and traffic", Input.Keys.M),
    TRADE("Station market", Input.Keys.T),
    YARD("Station yard", Input.Keys.Y),
    CREW("Crew roster", Input.Keys.I),
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
    NAV_TARGET("Next navigation target", Input.Keys.Q),
    PLAN_TO_TARGET("Plan transfer to target", Input.Keys.SEMICOLON),
    ACCEPT_PLAN("Accept transfer plan", Input.Keys.ENTER),
    DISCARD_PLAN("Discard transfer plan", Input.Keys.BACKSPACE),
    WARP_TO_BURN("Warp to next burn", Input.Keys.SLASH),
    HELM_FLY_BURNS("Helm: fly planned burns", Input.Keys.NUM_1),
    HELM_MATCH_VELOCITY("Helm: match target velocity", Input.Keys.NUM_2),
    HELM_HOLD("Helm: hold position", Input.Keys.NUM_3),
    HELM_APPROACH("Helm: approach target", Input.Keys.NUM_4),
    HELM_DOCK("Helm: dock at target", Input.Keys.NUM_5),
    AUTOPILOT("Autopilot: fly the transfer", Input.Keys.NUM_6),
    HELM_CANCEL("Helm: cancel order", Input.Keys.NUM_0),
    QUICK_SAVE("Quick save", Input.Keys.F5),
    QUICK_LOAD("Quick load", Input.Keys.F9);

    public final String label;
    public final int defaultKey;

    Action(String label, int defaultKey) {
        this.label = label;
        this.defaultKey = defaultKey;
    }

    /** The name of the action's default key, before any rebinding. */
    public String defaultKeyName() {
        return keyName(defaultKey);
    }

    /** A key's name as libGDX spells it, or "?" for a code with no name. */
    public static String keyName(int key) {
        String s = Input.Keys.toString(key);
        return s == null ? "?" : s;
    }
}
