package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.IntSet;

/**
 * A gamepad (GDD section 2) read as the game's keys, through libGDX Controllers' standard mapping,
 * so it flies and walks wherever the keyboard does:
 *
 * <ul>
 *   <li>Left stick: main engine and turning outside, walking on the deck (W/A/S/D)</li>
 *   <li>Right stick left/right, or the bumpers: strafe (Z/C); right stick click: rotation assist (X)</li>
 *   <li>A or right trigger: fire, and use a console (Space, E); B: missile (R); X: decoy or force a door (F)</li>
 *   <li>Y: deck / outside view (V); left trigger: next target (Tab)</li>
 *   <li>D-pad up/down: time warp up/down; left: arm port and ask clearance (G); right: undock (U)</li>
 *   <li>Start: pause menu (Esc); Back tapped: the map (M)</li>
 * </ul>
 *
 * <p>Holding Back gives the buttons a second layer: A mission board (J), B boarding (K), X market
 * (T), Y yard (Y); the d-pad steers the planned burn (Up/Down prograde/retrograde, Left/Right radial
 * in/out), the bumpers move it earlier/later, the left trigger plans a burn (N), the right trigger
 * selects the next (B) and the right stick click deletes it; the left stick click quick-saves (F5)
 * and Start abandons ship (P, twice). Back tapped alone opens the map when it is let go.
 *
 * <p>With a menu or window open ({@link #setMenuMode}), the pad drives it instead: the d-pad and
 * left stick move between buttons (arrow keys), A presses one (Enter), B or Start backs out (Esc).
 *
 * <p>The sticks are analog where the game asks how far a key is held ({@link #amount}): the left
 * stick's push is the main engine's and the turn's throttle outside and the walking pace on deck,
 * and the right stick's the strafe's, each from nothing just past {@link #ANALOG_DEADZONE} to full
 * at the rim. As keys, for whatever reads them all or nothing (menus, just-pressed checks), sticks
 * count past {@link #DEADZONE}. The first connected pad is used.
 */
public class GamepadKeys {
    /** How far a stick goes before it presses its key, for what reads the sticks as keys. */
    public static final float DEADZONE = 0.4f;
    /**
     * How far a stick goes before it moves anything analog: past the drift of a worn stick at rest.
     * Radial, and the rest of the throw scaled to 0..1, so the push grows smoothly from the edge of it.
     */
    public static final float ANALOG_DEADZONE = 0.15f;

    /** One frame of a pad, in the standard mapping's terms. Sticks -1 to 1, y down. */
    public static final class Pad {
        public float leftX, leftY, rightX, rightY;
        public boolean a, b, x, y, l1, r1, l2, r2, start, back, up, down, left, right, rightStick, leftStick;
    }

    private IntSet down = new IntSet();
    private IntSet was = new IntSet();
    /** The keys a button holds this frame, which are all the way down whatever the sticks say. */
    private final IntSet buttons = new IntSet();
    private final Pad pad = new Pad();
    /** This frame's pad for {@link #amount}; idle while none is connected. */
    private final Pad analog = new Pad();
    private boolean enabled = true;
    private boolean failed;
    private boolean menuMode;
    /** Back is held and some other button was pressed with it, so letting go does not open the map. */
    private boolean backUsed;
    private boolean backWasDown;

    /** True while a menu or window has the pad: it navigates rather than flies. */
    public void setMenuMode(boolean menuMode) {
        this.menuMode = menuMode;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Reads the pad; call once a frame, before the game reads keys. */
    public void poll() {
        IntSet t = was;
        was = down;
        down = t;
        down.clear();
        buttons.clear();
        clear(analog);
        if (!enabled || failed) return;
        Controller c;
        try {
            c = Controllers.getCurrent();
        } catch (RuntimeException | LinkageError e) {
            // No controller backend on this machine: carry on with the keyboard
            failed = true;
            if (Gdx.app != null) Gdx.app.log("RingsOfIron", "Gamepads unavailable: " + e);
            return;
        }
        if (c == null || !c.isConnected()) return;
        read(c, pad);
        keysNow(pad);
    }

    /** The keys for this frame's pad, with Back's tap-or-layer worked out. */
    void keysNow(Pad p) {
        if (menuMode) {
            menuKeysFor(p, down);
            backWasDown = p.back;
            backUsed = false;
            return;
        }
        copy(p, analog);
        buttons.clear();
        buttonKeysFor(p, buttons);
        down.addAll(buttons);
        stickKeysFor(p, down);
        if (p.back && (p.a || p.b || p.x || p.y || p.l1 || p.r1 || p.l2 || p.r2 || p.up || p.down || p.left
                || p.right || p.rightStick || p.leftStick || p.start)) backUsed = true;
        // Back let go after a tap alone: the map, for this one frame
        if (!p.back && backWasDown && !backUsed) down.add(Input.Keys.M);
        if (!p.back) backUsed = false;
        backWasDown = p.back;
    }

    public boolean pressed(int key) {
        return down.contains(key);
    }

    public boolean justPressed(int key) {
        return down.contains(key) && !was.contains(key);
    }

    /**
     * How far the pad holds the game's key, 0 to 1: a button all the way, a stick as far as it is
     * pushed toward the key past {@link #ANALOG_DEADZONE}. Nothing in a menu, which the pad navigates.
     */
    public float amount(int key) {
        if (menuMode) return 0f;
        if (buttons.contains(key)) return 1f;
        return stickAmount(analog, key);
    }

    /** How far this pad's sticks push toward the game's key, 0 to 1; 0 for a key no stick plays. */
    public static float stickAmount(Pad p, int key) {
        switch (key) {
            case Input.Keys.W: return Math.max(0f, -radial(p.leftY, p.leftX));
            case Input.Keys.S: return Math.max(0f, radial(p.leftY, p.leftX));
            case Input.Keys.A: return Math.max(0f, -radial(p.leftX, p.leftY));
            case Input.Keys.D: return Math.max(0f, radial(p.leftX, p.leftY));
            case Input.Keys.Z: return Math.max(0f, -radial(p.rightX, 0f));
            case Input.Keys.C: return Math.max(0f, radial(p.rightX, 0f));
            default: return 0f;
        }
    }

    /**
     * The stick's {@code along} axis past a radial deadzone, scaled so the push runs 0 to 1 from the
     * deadzone's edge to the rim; {@code across} is the stick's other axis. Radial, so a diagonal is
     * not cut to one axis near the middle; a push past the rim (square gates) is held to 1.
     */
    static float radial(float along, float across) {
        float length = (float) Math.sqrt(along * along + across * across);
        if (length <= ANALOG_DEADZONE) return 0f;
        float scaled = Math.min(1f, (length - ANALOG_DEADZONE) / (1f - ANALOG_DEADZONE));
        return along / length * scaled;
    }

    public boolean any() {
        return down.size > 0;
    }

    /** Keys pressed this frame and not last frame, into {@code out}. */
    public void pressedNow(IntSet out) {
        out.clear();
        IntSet.IntSetIterator it = down.iterator();
        while (it.hasNext) {
            int k = it.next();
            if (!was.contains(k)) out.add(k);
        }
    }

    /** Keys released this frame, into {@code out}. */
    public void releasedNow(IntSet out) {
        out.clear();
        IntSet.IntSetIterator it = was.iterator();
        while (it.hasNext) {
            int k = it.next();
            if (!down.contains(k)) out.add(k);
        }
    }

    private static void read(Controller c, Pad p) {
        ControllerMapping m = c.getMapping();
        p.leftX = axis(c, m.axisLeftX);
        p.leftY = axis(c, m.axisLeftY);
        p.rightX = axis(c, m.axisRightX);
        p.rightY = axis(c, m.axisRightY);
        p.a = button(c, m.buttonA);
        p.b = button(c, m.buttonB);
        p.x = button(c, m.buttonX);
        p.y = button(c, m.buttonY);
        p.l1 = button(c, m.buttonL1);
        p.r1 = button(c, m.buttonR1);
        p.l2 = button(c, m.buttonL2);
        p.r2 = button(c, m.buttonR2);
        p.start = button(c, m.buttonStart);
        p.back = button(c, m.buttonBack);
        p.up = button(c, m.buttonDpadUp);
        p.down = button(c, m.buttonDpadDown);
        p.left = button(c, m.buttonDpadLeft);
        p.right = button(c, m.buttonDpadRight);
        p.rightStick = button(c, m.buttonRightStick);
        p.leftStick = button(c, m.buttonLeftStick);
    }

    private static void copy(Pad from, Pad to) {
        to.leftX = from.leftX;
        to.leftY = from.leftY;
        to.rightX = from.rightX;
        to.rightY = from.rightY;
    }

    private static void clear(Pad p) {
        p.leftX = p.leftY = p.rightX = p.rightY = 0f;
    }

    private static float axis(Controller c, int axis) {
        return axis == ControllerMapping.UNDEFINED ? 0f : c.getAxis(axis);
    }

    private static boolean button(Controller c, int button) {
        return button != ControllerMapping.UNDEFINED && c.getButton(button);
    }

    /** The game's keys the pad holds down; Back's tap (the map) is {@link #keysNow}'s. */
    public static void keysFor(Pad p, IntSet out) {
        stickKeysFor(p, out);
        buttonKeysFor(p, out);
    }

    /** The keys the sticks hold down, pushed past {@link #DEADZONE}. */
    static void stickKeysFor(Pad p, IntSet out) {
        if (p.leftY < -DEADZONE) out.add(Input.Keys.W);
        if (p.leftY > DEADZONE) out.add(Input.Keys.S);
        if (p.leftX < -DEADZONE) out.add(Input.Keys.A);
        if (p.leftX > DEADZONE) out.add(Input.Keys.D);
        if (p.rightX < -DEADZONE) out.add(Input.Keys.Z);
        if (p.rightX > DEADZONE) out.add(Input.Keys.C);
    }

    /** The keys the buttons hold down, with Back's second layer. */
    static void buttonKeysFor(Pad p, IntSet out) {
        if (p.back) {
            // The second layer
            if (p.a) out.add(Input.Keys.J);
            if (p.b) out.add(Input.Keys.K);
            if (p.x) out.add(Input.Keys.T);
            if (p.y) out.add(Input.Keys.Y);
            if (p.up) out.add(Input.Keys.UP);
            if (p.down) out.add(Input.Keys.DOWN);
            if (p.left) out.add(Input.Keys.LEFT);
            if (p.right) out.add(Input.Keys.RIGHT);
            if (p.l1) out.add(Input.Keys.LEFT_BRACKET);
            if (p.r1) out.add(Input.Keys.RIGHT_BRACKET);
            if (p.l2) out.add(Input.Keys.N);
            if (p.r2) out.add(Input.Keys.B);
            if (p.rightStick) out.add(Input.Keys.FORWARD_DEL);
            if (p.leftStick) out.add(Input.Keys.F5);
            if (p.start) out.add(Input.Keys.P);
            return;
        }
        if (p.l1) out.add(Input.Keys.Z);
        if (p.r1) out.add(Input.Keys.C);
        if (p.rightStick) out.add(Input.Keys.X);
        if (p.a || p.r2) out.add(Input.Keys.SPACE);
        if (p.a) out.add(Input.Keys.E);
        if (p.b) out.add(Input.Keys.R);
        if (p.x) out.add(Input.Keys.F);
        if (p.y) out.add(Input.Keys.V);
        if (p.l2) out.add(Input.Keys.TAB);
        if (p.up) out.add(Input.Keys.PERIOD);
        if (p.down) out.add(Input.Keys.COMMA);
        if (p.left) out.add(Input.Keys.G);
        if (p.right) out.add(Input.Keys.U);
        if (p.start) out.add(Input.Keys.ESCAPE);
    }

    /** The keys the pad presses in a menu or window: arrows to move, Enter to press, Esc to back out. */
    public static void menuKeysFor(Pad p, IntSet out) {
        if (p.up || p.leftY < -DEADZONE) out.add(Input.Keys.UP);
        if (p.down || p.leftY > DEADZONE) out.add(Input.Keys.DOWN);
        if (p.left || p.leftX < -DEADZONE) out.add(Input.Keys.LEFT);
        if (p.right || p.leftX > DEADZONE) out.add(Input.Keys.RIGHT);
        if (p.a) out.add(Input.Keys.ENTER);
        if (p.b || p.start || p.back) out.add(Input.Keys.ESCAPE);
    }
}
