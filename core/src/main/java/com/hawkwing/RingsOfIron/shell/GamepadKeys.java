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
 *   <li>Start: pause menu (Esc); Back: traffic map (M)</li>
 * </ul>
 *
 * Sticks count past {@link #DEADZONE}. The first connected pad is used.
 */
public class GamepadKeys {
    public static final float DEADZONE = 0.4f;

    /** One frame of a pad, in the standard mapping's terms. Sticks -1 to 1, y down. */
    public static final class Pad {
        public float leftX, leftY, rightX, rightY;
        public boolean a, b, x, y, l1, r1, l2, r2, start, back, up, down, left, right, rightStick;
    }

    private IntSet down = new IntSet();
    private IntSet was = new IntSet();
    private final Pad pad = new Pad();
    private boolean enabled = true;
    private boolean failed;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Reads the pad; call once a frame, before the game reads keys. */
    public void poll() {
        IntSet t = was;
        was = down;
        down = t;
        down.clear();
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
        keysFor(pad, down);
    }

    public boolean pressed(int key) {
        return down.contains(key);
    }

    public boolean justPressed(int key) {
        return down.contains(key) && !was.contains(key);
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
    }

    private static float axis(Controller c, int axis) {
        return axis == ControllerMapping.UNDEFINED ? 0f : c.getAxis(axis);
    }

    private static boolean button(Controller c, int button) {
        return button != ControllerMapping.UNDEFINED && c.getButton(button);
    }

    /** The game's keys the pad holds down. */
    public static void keysFor(Pad p, IntSet out) {
        if (p.leftY < -DEADZONE) out.add(Input.Keys.W);
        if (p.leftY > DEADZONE) out.add(Input.Keys.S);
        if (p.leftX < -DEADZONE) out.add(Input.Keys.A);
        if (p.leftX > DEADZONE) out.add(Input.Keys.D);
        if (p.rightX < -DEADZONE || p.l1) out.add(Input.Keys.Z);
        if (p.rightX > DEADZONE || p.r1) out.add(Input.Keys.C);
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
        if (p.back) out.add(Input.Keys.M);
    }
}
