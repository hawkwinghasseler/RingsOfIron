package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * The ship's book's housing (style-vfd.md 18.4; the mockups' {@code tk-book}): the captain's own
 * records, a reflective LCD in a dark rubber housing with SHIP'S BOOK and the ship in its silk; the tab
 * keys down the housing's left edge with each tab's name beside its key in the glass's first column,
 * dark while it shows; a rule between the names and the tab, which has a list and the selected item's
 * detail beside it, a rule between them; the response line at the foot; and the tab's action keys
 * under the glass from the list's left, each with its action printed on the housing over it, CLOSE
 * on Esc at the right.
 */
final class BookFrame {
    /** The glass in its housing: in from the left past the tab keys, from the right, down under the silk, and up over the keys. */
    static final float LEFT = 78f, RIGHT = 14f, TOP = 26f, KEYS = 52f;
    /** The tab keys down the left: their x in, width, the first one's top below the glass's, their pitch. */
    static final float KEY_X = 14f, KEY_W = 52f, TAB_TOP = 8f, TAB_PITCH = 44f;
    /** The tab's left, in from the glass's, and its top. */
    static final float TAB_LEFT = 104f, TOP_IN = 12f;
    /** The list's share of the tab's width; the detail has the rest. */
    static final float LIST_SHARE = 0.56f;
    static final int MAX_TABS = 8;
    /** Click ids: a tab ({@code TAB + i}), and CLOSE. */
    static final int TAB = 1, CLOSE = 20;

    String brand = "SHIP'S BOOK", owner = "";
    final String[] names = new String[MAX_TABS], caps = new String[MAX_TABS];
    int count, active = -1;
    String response = "";
    /** The tab's keys, under the glass from the list's left; CLOSE takes the right end. */
    SoftKeys keys;
    /** Whether the tab is a list with its detail beside it. */
    boolean split = true;
    float x, y, w, h, lx, ly, lw, lh;
    private float ruleX = -1f;
    final Tab.Place at = new Tab.Place();
    private final SoftKeys close = new SoftKeys();

    BookFrame() {
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
        at.lcd = true;
        float x0 = lx + TAB_LEFT, x1 = lx + lw - 12f, top = ly + TOP_IN, bottom = ly + lh - 32f;
        at.lx = x0;
        at.ly = top;
        at.lb = bottom;
        at.dy = top;
        at.db = bottom;
        if (split) {
            int cells = Glass.cells((x1 - x0) * LIST_SHARE);
            at.lr = x0 + Glass.width(cells);
            ruleX = at.lr + 16f;
            at.dx = ruleX + 16f;
        } else {
            at.lr = x1;
            ruleX = -1f;
            at.dx = x0;
        }
        at.dr = x1;
        float ky = ly + lh + 12f;
        if (keys != null) keys.placeSlate(x0, ky);
        close.placeSlate(x + w - RIGHT - SoftKeys.SLATE_SLOT, ky);
    }

    void paint(Filament f) {
        f.slateHousing(x, y, w, h, brand, owner);
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
        Slate.vrule(f, at.lx - 10f, ly + 8f, lh - 16f);
        if (ruleX > 0f) Slate.vrule(f, ruleX, ly + 8f, lh - 16f);
        if (!response.isEmpty()) {
            Slate.rule(f, at.lx, ly + lh - 28f, at.dr - at.lx);
            Slate.text(f, at.lx, ly + lh - 20f, Glass.cut(response, Glass.cells(at.dr - at.lx)));
        }
        if (keys != null) keys.paint(f);
        close.paint(f);
    }

    /** The tab keys' and CLOSE's boxes; the tab's keys are its own. */
    void boxes(FilamentActor a) {
        for (int i = 0; i < count; i++) {
            if (names[i] == null) continue;
            a.box(TAB + i, x + KEY_X - 4f, ly + TAB_TOP + i * TAB_PITCH - 6f, TAB_LEFT + LEFT - KEY_X - 6f, Glass.KEY_H + 12f);
        }
        close.boxes(a, CLOSE);
    }
}
