package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardShellParts;

/**
 * A page whose heart is a list on a ledger tube (style guide 9.5): the selected row in reverse video,
 * moved with the cursor keys and set with a click; the list scrolling under a position meter; and
 * the row's actions as soft keys under the tube, their labels on its bottom row, a key that can't
 * act struck through with its reason at the left. An order that can't be undone has a red guard on
 * its key: the first press lifts it and lights its lamp, the second gives the order, and the guard
 * drops again after {@link #GUARD_UP} seconds or when the row changes.
 *
 * <p>Keys: Up and Down the row, Page Up and Page Down a tubeful, Home and End; Left and Right the
 * soft key; Enter presses it.
 */
abstract class ListPage extends Page {
    /** Hit codes: a list row from {@code ROW}, a soft key from {@code KEY}; a page's own from {@code OWN}. */
    static final int ROW = 0, KEY = 10000, OWN = 20000;
    static final float KEY_W = 96f, KEY_GAP = 10f, GUARD_UP = 4f;
    static final Color AMBER = Ledger.AMBER, DIM = Ledger.DIM;
    private static final int MAX_KEYS = 5;

    final Ledger L = new Ledger();
    /** The selected row, -1 for none; the first row in view; the soft key the keyboard is on, -1 for none. */
    int sel = -1, top, focusKey = -1;
    private int shownRows = 1;
    /** Whether the view follows the selection: off once the wheel moves it, on again with a key. */
    private boolean follow = true;
    // The selected row's soft keys, as keys() says them each frame
    private final String[] keyLabel = new String[MAX_KEYS], keyRefusal = new String[MAX_KEYS], keyAsk = new String[MAX_KEYS];
    private final boolean[] keyGuarded = new boolean[MAX_KEYS];
    private int keyCount;
    private int guardKey = -1;
    private float guardAt, now;
    /** Where the soft keys start, and their width, as last drawn. */
    private float keysX = -1f, keyW = KEY_W;

    ListPage(String label, String tag, ClassicWindow window) {
        super(label, tag, window);
    }

    /** Rows in the list. */
    abstract int count();

    /** Whether row {@code i} can be selected; a section's heading can't. */
    boolean selectable(int i) {
        return true;
    }

    /** Draws row {@code i} on the tube's row {@code r}; when {@code selected} its bar is drawn already. */
    abstract void row(YardPaint p, int i, int r, boolean selected);

    /** Says the soft keys for the selected row with {@link #softKey} (or none). */
    abstract void keys();

    /** Soft key {@code k} was pressed, and can act. */
    abstract void press(int k);

    /** What the tube says on its first row while the list is empty. */
    String empty() {
        return "NOTHING TO SHOW";
    }

    /** A soft key for the selected row: {@code refusal} is why it can't act now, or null when it can. */
    final void softKey(String label, String refusal) {
        softKey(label, refusal, false);
    }

    /** A soft key whose order can't be undone: it sits under a red guard. */
    final void guardedKey(String label, String refusal) {
        guardedKey(label, refusal, null);
    }

    /**
     * A guarded soft key whose first press says {@code ask} in the feed: what the second press does
     * ("Let Cole go? Press again: Cole leaves the ship for good"). Null says it can't be undone.
     */
    final void guardedKey(String label, String refusal, String ask) {
        if (softKey(label, refusal, true)) keyAsk[keyCount - 1] = ask;
    }

    private boolean softKey(String label, String refusal, boolean guarded) {
        if (keyCount >= MAX_KEYS) return false;
        keyLabel[keyCount] = label;
        keyRefusal[keyCount] = refusal;
        keyGuarded[keyCount] = guarded;
        keyAsk[keyCount] = null;
        keyCount++;
        return true;
    }

    private void sayKeys() {
        keyCount = 0;
        keys();
        if (focusKey >= keyCount) focusKey = keyCount - 1;
    }

    /** After a refresh: keeps the selection on a row that is still there. */
    void keepSelection() {
        int n = count();
        if (n == 0) {
            sel = -1;
            return;
        }
        if (sel >= n) sel = n - 1;
        if (sel < 0 || !selectable(sel)) {
            int s = next(sel < 0 ? -1 : sel, 1);
            if (s < 0) s = next(n, -1);
            sel = s;
        }
    }

    /** The next selectable row from {@code from} in direction {@code d}, or -1. */
    private int next(int from, int d) {
        int n = count();
        for (int i = from + d; i >= 0 && i < n; i += d) if (selectable(i)) return i;
        return -1;
    }

    void select(int i) {
        if (i < 0 || i >= count() || !selectable(i)) return;
        if (i != sel) guardKey = -1;
        sel = i;
        follow = true;
    }

    private void move(int d, int steps) {
        int s = sel;
        for (int k = 0; k < steps; k++) {
            int n = next(s, d);
            if (n < 0) break;
            s = n;
        }
        if (s < 0) s = next(d > 0 ? -1 : count(), d);
        select(s);
    }

    @Override
    void shown() {
        guardKey = -1;
        focusKey = -1;
        follow = true;
    }

    @Override
    boolean key(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
                move(-1, 1);
                return true;
            case Input.Keys.DOWN:
                move(1, 1);
                return true;
            case Input.Keys.PAGE_UP:
                move(-1, Math.max(1, shownRows - 1));
                return true;
            case Input.Keys.PAGE_DOWN:
                move(1, Math.max(1, shownRows - 1));
                return true;
            case Input.Keys.HOME:
                select(next(-1, 1));
                return true;
            case Input.Keys.END:
                select(next(count(), -1));
                return true;
            case Input.Keys.LEFT:
                sayKeys();
                if (keyCount > 0) focusKey = focusKey <= 0 ? keyCount - 1 : focusKey - 1;
                return true;
            case Input.Keys.RIGHT:
                sayKeys();
                if (keyCount > 0) focusKey = focusKey < 0 || focusKey >= keyCount - 1 ? 0 : focusKey + 1;
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
                sayKeys();
                if (focusKey >= 0) pressKey(focusKey);
                else for (int k = 0; k < keyCount; k++) {
                    if (keyRefusal[k] == null) {
                        pressKey(k);
                        break;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    void scroll(float amount) {
        follow = false;
        top += Math.round(amount * 3f);
        int max = Math.max(0, count() - shownRows);
        if (top < 0) top = 0;
        if (top > max) top = max;
    }

    /** Presses soft key {@code k}: nothing if it can't act, the guard first if it has one. */
    void pressKey(int k) {
        sayKeys();
        if (k < 0 || k >= keyCount) return;
        focusKey = k;
        if (keyRefusal[k] != null) {
            say(keyRefusal[k]);
            return;
        }
        if (keyGuarded[k] && (guardKey != k || now - guardAt > GUARD_UP)) {
            guardKey = k;
            guardAt = now;
            say(keyAsk[k] != null ? keyAsk[k] : "Press " + keyLabel[k] + " again: it can't be undone");
            return;
        }
        guardKey = -1;
        press(k);
        if (console != null) console.refreshNow();
    }

    @Override
    public void hit(int code) {
        if (code >= OWN) return;
        if (code >= KEY) pressKey(code - KEY);
        else select(code - ROW);
    }

    // --- Drawing -----------------------------------------------------------------------------

    /** The title row in reverse video: {@code left} at the left, {@code right} ending at the right. */
    void title(YardPaint p, String left, String right) {
        L.bar(p, 0);
        L.rev(p, 1, 0, left);
        if (right != null) L.revRight(p, L.cols - 1, 0, right);
    }

    /**
     * The list on rows {@code r0} to {@code r1}, scrolled to keep the selection in view, with the
     * position meter beside it while it doesn't all fit.
     */
    void drawList(YardPaint p, Hits hits, int r0, int r1) {
        now = p.time();
        if (guardKey >= 0 && now - guardAt > GUARD_UP) guardKey = -1;
        int n = count();
        shownRows = Math.max(1, r1 - r0 + 1);
        if (n == 0) {
            L.at(p, 1, r0, empty(), DIM);
            return;
        }
        if (follow && sel >= 0) {
            if (sel < top) top = sel;
            if (sel >= top + shownRows) top = sel - shownRows + 1;
            // A heading just above the selection comes into view with it
            if (sel == top && top > 0 && !selectable(top - 1)) top--;
        }
        top = Math.max(0, Math.min(top, n - shownRows));
        for (int r = 0; r < shownRows && top + r < n; r++) {
            int i = top + r, rr = r0 + r;
            boolean s = i == sel;
            if (s) L.bar(p, rr);
            row(p, i, rr, s);
            if (selectable(i)) hits.add(p, L.x, L.rowTop(rr), L.w, Ledger.ROW, this, ROW + i);
        }
        L.meter(p, r0, r1, top, shownRows, n);
    }

    /**
     * The soft keys: their labels on the tube's last row over the rule, the reason a struck one can't
     * act at the row's left, and the keys themselves under the tube. Draw before {@link Ledger#over}.
     */
    void drawSoftRow(YardPaint p, Hits hits) {
        sayKeys();
        int r = L.rows - 1;
        L.rule(p, r - 1);
        if (keyCount == 0) {
            keysX = -1f;
            return;
        }
        // The keys share one width, wide enough for the longest label (MOUNT ON EVA)
        float kw = KEY_W;
        for (int k = 0; k < keyCount; k++) kw = Math.max(kw, keyLabel[k].length() * 7.8f + 18f);
        keyW = kw;
        float x0 = L.cx(L.cols - 1) - (keyCount * kw + (keyCount - 1) * KEY_GAP);
        keysX = x0;
        float yy = L.base(r);
        String reason = focusKey >= 0 && focusKey < keyCount ? keyRefusal[focusKey] : null;
        for (int k = 0; k < keyCount && reason == null; k++) reason = keyRefusal[k];
        if (reason != null) {
            int room = (int) ((x0 - L.cx(0)) / Ledger.COL) - 2;
            L.at(p, 0, r, Ledger.cut(" " + reason, room), DIM);
        }
        for (int k = 0; k < keyCount; k++) {
            float kx = x0 + k * (kw + KEY_GAP);
            boolean struck = keyRefusal[k] != null, lifted = k == guardKey;
            Color ink = lifted ? Yard.P_RED : struck ? DIM : AMBER;
            if (k == focusKey) p.rect(kx, yy - 13.5f, kw, 18f, AMBER, 0.2f);
            if (lifted) p.rect(kx, yy - 13.5f, kw, 18f, Yard.P_RED, 0.22f);
            p.frame(kx, yy - 13.5f, kw, 18f, 1f, lifted ? Yard.P_RED : AMBER, struck ? 0.45f : 0.9f);
            p.textC(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.TUBE, 13f, keyLabel[k], kx + kw / 2f, yy, ink);
            if (struck) p.rect(kx + 6f, yy - 5.2f, kw - 12f, 1.4f, AMBER);
            hits.add(p, kx, yy - 13.5f, kw, 18f, this, KEY + k);
        }
    }

    /** The soft keys themselves, under the tube: draw after {@link Ledger#over}. */
    void drawSoftKeys(YardPaint p, Hits hits) {
        if (keysX < 0f) return;
        float ky = L.y + L.h + 10f;
        for (int k = 0; k < keyCount; k++) {
            float kx = keysX + k * (keyW + KEY_GAP);
            boolean lifted = k == guardKey;
            p.rrect(kx + 1f, ky + 2f, keyW, 11f, 2f, Yard.SHADOW, 0.7f);
            p.rrect(kx, ky, keyW, 11f, 2f, Yard.BAKELITE);
            p.rrect(kx + 1.5f, ky + 1f, keyW - 3f, 6f, 1.5f, Yard.hex("3D3732"));
            if (k == focusKey) p.rect(kx + 1.5f, ky + 8f, keyW - 3f, 2f, Yard.ORANGE);
            if (keyGuarded[k]) {
                if (lifted) {
                    // The guard flipped down under its key, and its lamp lit
                    p.rrect(kx - 2f, ky + 11f, keyW + 4f, 5f, 1.5f, Yard.RED);
                    p.lamp(kx + keyW + 5f, ky + 5.5f, 2.2f, Yard.RED_LAMP, true);
                } else {
                    p.rrect(kx - 2f, ky - 2f, keyW + 4f, 15f, 2.5f, Yard.RED, 0.95f);
                    p.rect(kx - 2f, ky - 2f, keyW + 4f, 3f, Yard.hex("7A221B"));
                    p.glass(kx - 2f, ky - 2f, keyW + 4f, 15f);
                }
            }
            if (k == focusKey) YardShellParts.focusTab(p, kx - 1.5f, ky + 5.5f);
            hits.add(p, kx, ky - 2f, keyW, 17f, this, KEY + k);
        }
    }

    /** Whether the guard on soft key {@code k} is up now. */
    boolean guardUp(int k) {
        return guardKey == k && now - guardAt <= GUARD_UP;
    }
}
