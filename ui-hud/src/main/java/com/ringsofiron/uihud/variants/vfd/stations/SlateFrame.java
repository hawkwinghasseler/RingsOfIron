package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * The slate's housing as a side panel (style-vfd.md 18.4; the mockups' {@code st-slate}): the captain's
 * own kit, a reflective LCD in a dark rubber housing with SLATE and CAPTAIN in its silk; the tab keys
 * down the housing's left edge with each tab's name beside its key in the glass's first column, dark
 * while it shows; the title row across the glass (the captain's rank, the ship, the credits) over a
 * printed rule; a rule between the names and the page; the response line at the page's foot; and the
 * page's action keys under the glass, each with its action printed on the housing over it, CLOSE on
 * Esc at the right.
 */
final class SlateFrame {
    /** The glass in its housing: in from the left past the tab keys, from the right, down under the silk, and up over the keys. */
    static final float LEFT = 78f, RIGHT = 14f, TOP = 26f, KEYS = 52f;
    /** The tab keys down the left: their x in, width, the first one's top below the glass's, their pitch. */
    static final float KEY_X = 14f, KEY_W = 52f, TAB_TOP = 40f, TAB_PITCH = 50f;
    /** The page's left, in from the glass's; its top under the title row. */
    static final float PAGE_LEFT = 100f, PAGE_TOP = 40f;
    static final int MAX_TABS = 9;
    /** Click ids: a tab ({@code TAB + i}), and CLOSE. */
    static final int TAB = 1, CLOSE = 20;

    String title = "", right = "";
    final String[] names = new String[MAX_TABS], caps = new String[MAX_TABS];
    int count, active = -1;
    String response = "";
    /** The page's keys, under the glass from the left; CLOSE takes the right end. */
    SoftKeys keys;
    float x, y, w, h, lx, ly, lw, lh;
    final Page.Place at = new Page.Place();
    private final SoftKeys close = new SoftKeys();

    SlateFrame() {
        close.define(1, 1).set(0, "CLOSE", true).cap(0, "ESC");
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        lx = x + LEFT;
        ly = y + TOP;
        lw = w - LEFT - RIGHT;
        lh = h - TOP - KEYS;
        at.gx = lx;
        at.gy = ly;
        at.gw = lw;
        at.gh = lh;
        at.x0 = lx + 6f;
        at.x1 = lx + lw - 8f;
        at.left = lx + PAGE_LEFT;
        at.top = ly + PAGE_TOP;
        at.split = at.top;
        at.bottom = ly + lh - 30f;
        at.lcd = true;
        float ky = ly + lh + 12f;
        if (keys != null) keys.placeSlate(x + KEY_X, ky);
        close.placeSlate(x + w - RIGHT - SoftKeys.SLATE_SLOT, ky);
    }

    void paint(Filament f) {
        f.slateHousing(x, y, w, h, "SLATE", "CAPTAIN");
        f.lcdPanel(lx, ly, lw, lh, f.o());
        for (int i = 0; i < count; i++) {
            float ky = ly + TAB_TOP + i * TAB_PITCH;
            String n = names[i];
            boolean on = n != null && !n.isEmpty();
            f.key(x + KEY_X, ky, KEY_W, Glass.KEY_H, on && caps[i] != null ? caps[i] : "", f.o().size(12f));
            if (!on) continue;
            if (i == active) Slate.inverse(f, lx + 6f, ky + 6f, n);
            else Slate.text(f, lx + 6f, ky + 6f, n);
        }
        Slate.text(f, lx + 6f, ly + 8f, Glass.cut(title, Glass.cells(lw - 12f - Glass.width(right.length()) - Glass.CW)));
        if (!right.isEmpty()) Slate.right(f, lx + lw - 6f, ly + 8f, right, false);
        Slate.rule(f, lx + 4f, ly + 26f, lw - 8f);
        Slate.vrule(f, at.left - 10f, ly + 32f, lh - 40f);
        if (!response.isEmpty()) {
            Slate.rule(f, at.left, ly + lh - 28f, at.x1 - at.left);
            Slate.text(f, at.left, ly + lh - 20f, Glass.cut(response, Glass.cells(at.x1 - at.left)));
        }
        if (keys != null) keys.paint(f);
        close.paint(f);
    }

    /** The tab keys' and CLOSE's boxes; the page's keys are its own. */
    void boxes(FilamentActor a) {
        for (int i = 0; i < count; i++) {
            if (names[i] == null) continue;
            a.box(TAB + i, x + KEY_X - 4f, ly + TAB_TOP + i * TAB_PITCH - 6f, PAGE_LEFT + LEFT - KEY_X - 6f, Glass.KEY_H + 12f);
        }
        close.boxes(a, CLOSE);
    }
}
