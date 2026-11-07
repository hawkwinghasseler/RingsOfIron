package com.ringsofiron.uihud.variants.vfd.task;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Pages;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * A tab that is a list with action keys on its focused row (style-vfd.md 18.2), the row's detail
 * beside it: ↑ and ↓ move the focus, the page keys and the wheel turn the list's page, ← and → walk
 * the action keys and Enter presses the one walked to; a click focuses a row or presses a key. The tab
 * says what each key does for the row in focus ({@link #keysFor}) and what a press does
 * ({@link #press}); it places the list ({@link #listX}, {@link #listY}, {@link #listCells},
 * {@link #shown}) as it lays out.
 */
abstract class ListTab extends Tab {
    static final int ROW_IDS = Windows.TAB_IDS, KEY_IDS = Windows.TAB_IDS + 80;

    final Pages pages = new Pages();
    /** The list's first row on the stage, its width in cells, the rows' pitch, and how many rows its glass shows. */
    float listX, listY, rowStep = Glass.ROW;
    int listCells = 1, shown = 1;

    ListTab(String name, ClassicWindow window, String cap) {
        super(name, window, cap);
    }

    /** Sets each key's action for the row in focus ({@code -1} with an empty list). */
    abstract void keysFor(int focus);

    /** Key {@code k} pressed, on the row in focus. */
    abstract void press(int k, int focus);

    /** The list has {@code count} rows now and its glass {@link #shown}: the focus stays in it, and the keys follow it. */
    void rows(int count) {
        pages.set(count, shown);
        focusMoved();
    }

    void focusMoved() {
        land(1);
        keysFor(pages.focus());
        keys.settle();
    }

    /** Whether row {@code i} can take the focus: not a section's head. All of them by default. */
    boolean selectable(int i) {
        return true;
    }

    /** Moves the focus off a row that can't take it, on in {@code dir}, else back. */
    void land(int dir) {
        int f = pages.focus(), n = pages.count();
        if (f < 0 || selectable(f)) return;
        int i = f;
        while (i >= 0 && i < n && !selectable(i)) i += dir;
        if (i < 0 || i >= n) {
            i = f;
            while (i >= 0 && i < n && !selectable(i)) i -= dir;
        }
        if (i >= 0 && i < n) pages.focus(i);
    }

    /** Row {@code i}'s top on the stage, on its page. */
    float rowY(int i) {
        return listY + (i - pages.first()) * rowStep;
    }

    /** How many rows of {@link #rowStep} fit from {@link #listY} to {@code bottom}. */
    int fit(float bottom) {
        return Math.max(1, (int) Math.floor((bottom - listY) / rowStep + 1e-3f));
    }

    @Override
    boolean key(int keycode) {
        if (pages.key(keycode)) {
            land(keycode == Keys.UP || keycode == Keys.END ? -1 : 1);
            focusMoved();
            return true;
        }
        int r = keys.key(keycode);
        if (r >= 0 && keys.can(r)) press(r, pages.focus());
        return r != SoftKeys.NONE;
    }

    @Override
    void scrolled(int amount) {
        if (pages.turn(amount)) focusMoved();
    }

    @Override
    void boxes(FilamentActor a) {
        float rw = Glass.width(listCells);
        for (int i = pages.first(); i < pages.end(); i++) a.box(ROW_IDS + i - pages.first(), listX, rowY(i) - 3f, rw, rowStep);
        keys.boxes(a, KEY_IDS);
    }

    @Override
    void clicked(int id) {
        if (id >= KEY_IDS && id < KEY_IDS + SoftKeys.MAX) {
            int k = id - KEY_IDS;
            keys.walkTo(k);
            if (keys.can(k)) press(k, pages.focus());
        } else if (id >= ROW_IDS && id < KEY_IDS) {
            pages.focus(pages.first() + id - ROW_IDS);
            focusMoved();
        }
    }
}
