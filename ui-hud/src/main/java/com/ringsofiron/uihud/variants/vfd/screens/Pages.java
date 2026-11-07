package com.ringsofiron.uihud.variants.vfd.screens;

import com.badlogic.gdx.Input.Keys;

/**
 * A list's focus and its pages (style-vfd.md 18.2: pages, not scrolling): the focused row, how many
 * rows its glass holds, and the page that shows the focus. ↑ and ↓ move the focus, Page Up and Page
 * Down (and the wheel) turn the page, Home and End go to the ends.
 */
public final class Pages {
    /** Rows in the list, and how many its glass shows at once. */
    private int count, size = 1;
    /** The focused row, or -1 for none (an empty list). */
    private int focus = -1;
    /** The first row of the page showing. */
    private int first;

    /** The list now has {@code count} rows and its glass holds {@code size}; the focus stays in it. */
    public void set(int count, int size) {
        this.count = Math.max(0, count);
        this.size = Math.max(1, size);
        if (this.count == 0) {
            focus = -1;
            first = 0;
            return;
        }
        if (focus < 0) focus = 0;
        if (focus >= this.count) focus = this.count - 1;
        show();
    }

    /** Turns to the page that holds the focus. */
    private void show() {
        if (focus < 0) return;
        first = focus / size * size;
    }

    public int count() {
        return count;
    }

    public int size() {
        return size;
    }

    public int focus() {
        return focus;
    }

    /** The first row on the page showing, and one past its last. */
    public int first() {
        return first;
    }

    public int end() {
        return Math.min(count, first + size);
    }

    /** The page showing, from 1, and how many there are (at least 1). */
    public int page() {
        return first / size + 1;
    }

    public int pages() {
        return Math.max(1, (count + size - 1) / size);
    }

    /** Focuses row {@code i} (clamped). */
    public void focus(int i) {
        if (count == 0) return;
        focus = Math.max(0, Math.min(count - 1, i));
        show();
    }

    /** Moves the focus by {@code by} rows; false when it was already at that end. */
    public boolean move(int by) {
        if (count == 0) return false;
        int to = Math.max(0, Math.min(count - 1, focus + by));
        if (to == focus) return false;
        focus = to;
        show();
        return true;
    }

    /** Turns {@code by} pages, the focus to the same place on the new page. */
    public boolean turn(int by) {
        if (count == 0) return false;
        int page = Math.max(0, Math.min(pages() - 1, first / size + by));
        int to = Math.min(count - 1, page * size + (focus - first));
        if (page * size == first) return false;
        focus = to;
        show();
        return true;
    }

    /**
     * A list key: ↑ ↓ the focus, Page Up and Page Down a page, Home and End the ends. True when the
     * key is one of them, whether or not the focus could move.
     */
    public boolean key(int keycode) {
        switch (keycode) {
            case Keys.UP:
                move(-1);
                return true;
            case Keys.DOWN:
                move(1);
                return true;
            case Keys.PAGE_UP:
                turn(-1);
                return true;
            case Keys.PAGE_DOWN:
                turn(1);
                return true;
            case Keys.HOME:
                focus(0);
                return true;
            case Keys.END:
                focus(count - 1);
                return true;
            default:
                return false;
        }
    }
}
