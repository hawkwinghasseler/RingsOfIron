package com.ringsofiron.uihud.variants.vfd.screens;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Ink;

/**
 * A terminal's soft keys (style-vfd.md 18.2): a strip of rubber keys under the glass, the glass row over
 * each saying its action, acting on the focused row. A key the keyboard has its own key for prints it
 * on its cap (ENTER, →); the others are walked with ← and →, and Enter presses the one walked to, which
 * prints ENTER on its cap so the player can see which it is. An action that can't be taken now stays in
 * its place as a dim label; a key may carry an LED (a switch's state).
 */
public final class SoftKeys {
    public static final int MAX = 8;
    /** What {@link #key} did: nothing, or moved the walk; else the index of the key pressed. */
    public static final int NONE = -1, MOVED = -2;

    private final String[] words = new String[MAX];
    /** A key's own cap ("→"), or null: walked, and pressed with Enter. */
    private final String[] caps = new String[MAX];
    private final boolean[] on = new boolean[MAX];
    /** Keys the walk never lands on by itself: one that can't be undone, walked to only by the player. */
    private final boolean[] guarded = new boolean[MAX];
    private final Filament.Led[] leds = new Filament.Led[MAX];
    private final boolean[] ledOn = new boolean[MAX];
    private final String[] shownCaps = new String[MAX];
    private final Glass.Key[] states = new Glass.Key[MAX];
    private final float[] box = new float[2];
    private int n, slots = 1, walk;
    /** Where it was last laid out: the strip's left, the keys' top, its width, the labels' row. */
    private float x, y, w, labelY;
    /** Laid out as a slate's keys under its glass ({@link #placeSlate}) rather than a terminal's strip. */
    private boolean slate;
    /** A slate key's slot and how wide its key is in it. */
    public static final float SLATE_SLOT = 112f, SLATE_KEY = 56f, SLATE_KEY_WIDE = 66f;

    /** {@code count} keys in {@code slots} places, all walked, all on, with no words yet. */
    public SoftKeys define(int count, int slots) {
        n = Math.min(MAX, count);
        this.slots = Math.max(n, slots);
        for (int i = 0; i < MAX; i++) {
            words[i] = "";
            caps[i] = null;
            on[i] = true;
            guarded[i] = false;
            leds[i] = null;
            ledOn[i] = false;
        }
        walk = 0;
        return this;
    }

    /** Key {@code i}'s action and whether it can be taken now. */
    public SoftKeys set(int i, String word, boolean can) {
        if (i < 0 || i >= n) return this;
        words[i] = word == null ? "" : word;
        on[i] = can;
        return this;
    }

    /** Key {@code i} answers to its own key, printed on its cap ("ENTER", "→"); it is not walked to. */
    public SoftKeys cap(int i, String cap) {
        if (i >= 0 && i < n) caps[i] = cap;
        return this;
    }

    /**
     * Key {@code i} does what can't be undone (give up a contract): when the key walked to can't be
     * taken, the walk doesn't move on to it by itself, so a second Enter never presses it unasked.
     */
    public SoftKeys guard(int i) {
        if (i >= 0 && i < n) guarded[i] = true;
        return this;
    }

    /** Key {@code i}'s LED. */
    public SoftKeys led(int i, Filament.Led led, boolean lit) {
        if (i >= 0 && i < n) {
            leds[i] = led;
            ledOn[i] = lit;
        }
        return this;
    }

    public int count() {
        return n;
    }

    public boolean can(int i) {
        return i >= 0 && i < n && on[i];
    }

    /** The key Enter presses: the one walked to. */
    public int walked() {
        return walk;
    }

    public void walkTo(int i) {
        if (i >= 0 && i < n && caps[i] == null) walk = i;
    }

    /** No key here is walked to: another strip in the same terminal has the walk ({@link #walkTo} gives it back). */
    public void unwalk() {
        walk = -1;
    }

    /** Whether key {@code i} is one the walk can land on now: walked, with a word, and on. */
    public boolean walkable(int i) {
        return i >= 0 && i < n && caps[i] == null && on[i] && !words[i].isEmpty();
    }

    /**
     * Walks to the first walked key that can be taken, when the one walked to can't be now; never to a
     * guarded key, so the walk stays put when only one of those can be.
     */
    public void settle() {
        if (walk >= 0 && walk < n && caps[walk] == null && on[walk] && !words[walk].isEmpty()) return;
        for (int i = 0; i < n; i++) {
            if (caps[i] == null && on[i] && !guarded[i] && !words[i].isEmpty()) {
                walk = i;
                return;
            }
        }
    }

    /** Whether any key is walked rather than keyed. */
    private boolean anyWalked() {
        for (int i = 0; i < n; i++) if (caps[i] == null) return true;
        return false;
    }

    /**
     * A key: ← and → walk to the next walked key that can be taken, Enter presses the one walked to,
     * and a key's own key presses it. Returns the key pressed, {@link #MOVED}, or {@link #NONE}.
     */
    public int key(int keycode) {
        for (int i = 0; i < n; i++) if (caps[i] != null && matches(caps[i], keycode)) return i;
        if (!anyWalked()) return NONE;
        if (keycode == Keys.LEFT || keycode == Keys.RIGHT) {
            int dir = keycode == Keys.LEFT ? -1 : 1;
            for (int k = 1; k < n; k++) {
                int i = walk + dir * k;
                if (i < 0 || i >= n) break;
                if (caps[i] == null && on[i]) {
                    walk = i;
                    return MOVED;
                }
            }
            return MOVED;
        }
        if (keycode == Keys.ENTER || keycode == Keys.NUMPAD_ENTER) return walk >= 0 && walk < n && caps[walk] == null ? walk : NONE;
        return NONE;
    }

    /** Whether {@code keycode} is what a cap prints. */
    static boolean matches(String cap, int keycode) {
        switch (cap) {
            case "ENTER":
                return keycode == Keys.ENTER || keycode == Keys.NUMPAD_ENTER;
            case "→":
                return keycode == Keys.RIGHT;
            case "←":
                return keycode == Keys.LEFT;
            case "PG UP":
                return keycode == Keys.PAGE_UP;
            case "PG DN":
                return keycode == Keys.PAGE_DOWN;
            case "DEL":
                return keycode == Keys.FORWARD_DEL || keycode == Keys.DEL;
            default:
                return cap.length() == 1 && Keys.valueOf(cap) == keycode;
        }
    }

    /** Lays the strip out: its left {@code x}, its keys' top {@code y}, width {@code w}, its labels' row {@code labelY}. */
    public void place(float x, float y, float w, float labelY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.labelY = labelY;
        slate = false;
    }

    /**
     * Lays the keys out under a slate's glass, from {@code x}: each its action printed on the housing at
     * {@code y} and its rubber key under it, a slot apart (the mockups' {@code slateKeys}).
     */
    public void placeSlate(float x, float y) {
        this.x = x;
        this.y = y + 6f;
        this.labelY = y;
        this.w = n * SLATE_SLOT;
        slate = true;
    }

    public void paint(Filament f) {
        if (n == 0) return;
        if (slate) {
            paintSlate(f);
            return;
        }
        boolean walking = anyWalked();
        for (int i = 0; i < n; i++) {
            boolean walkedHere = walking && caps[i] == null && i == walk;
            shownCaps[i] = caps[i] != null ? caps[i] : walkedHere && on[i] ? "ENTER" : "";
            states[i] = words[i].isEmpty() ? Glass.Key.BLANK : !on[i] ? Glass.Key.DIM : walkedHere ? Glass.Key.FOCUS : Glass.Key.ON;
        }
        // Only the keys defined: the rest of the slots stay bare bezel
        Glass.keyStrip(f, x, y, w, slots, n, labelY, shownCaps, words, states, leds, ledOn);
    }

    /** A slate's keys: the action in silk over each, dim when it does nothing now, the cap ENTER on the one walked to. */
    private void paintSlate(Filament f) {
        boolean walking = anyWalked();
        for (int i = 0; i < n; i++) {
            if (words[i].isEmpty()) continue;
            boolean walkedHere = walking && caps[i] == null && i == walk;
            String cap = caps[i] != null ? caps[i] : walkedHere && on[i] ? "ENTER" : "";
            float kx = x + i * SLATE_SLOT, kw = slateKeyWidth(caps[i]);
            Filament.Opt silk = f.o().size(12f).centre().ink(on[i] ? Ink.SLATE_KEY_SILK : Ink.SLATE_KEY_SILK_DIM);
            f.silk(kx + SLATE_SLOT / 2f, labelY, words[i], silk);
            Filament.Opt key = f.o().size(12f);
            if (!on[i]) key.ink(Ink.SLATE_KEY_DIM);
            if (leds[i] != null) key.led(leds[i], ledOn[i]);
            f.key(kx + (SLATE_SLOT - kw) / 2f, y, kw, Glass.KEY_H, cap, key);
        }
    }

    /** How wide a slate key with {@code cap} is: wider for a word than for one key. */
    static float slateKeyWidth(String cap) {
        return cap == null || cap.length() <= 1 ? SLATE_KEY : SLATE_KEY_WIDE;
    }

    /** Each key's box, as {@code base + i}; the label over it too. */
    public void boxes(FilamentActor a, int base) {
        if (slate) {
            for (int i = 0; i < n; i++) if (!words[i].isEmpty()) a.box(base + i, x + i * SLATE_SLOT, labelY - 12f, SLATE_SLOT, y + Glass.KEY_H - labelY + 12f);
            return;
        }
        for (int i = 0; i < n; i++) {
            if (words[i].isEmpty()) continue;
            Glass.keyBox(x, w, slots, i, box);
            float slot = w / Math.max(1, slots);
            a.box(base + i, x + i * slot, labelY - 3f, slot, y + Glass.KEY_H - labelY + 3f);
        }
    }
}
