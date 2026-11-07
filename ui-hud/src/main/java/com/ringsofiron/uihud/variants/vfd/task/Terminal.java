package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * A terminal's housing (style-vfd.md 18.2), as the mockups' {@code terminal} draws the station
 * terminal and the panels: a slab of smoked glass in its own bezel; the tab keys along its top edge,
 * each tab's name in the glass under its key and the tab up in inverse, a tab this place doesn't offer
 * a blank key over blank glass in its fixed home; the header row with the place in fourteen segments,
 * its owner dim beside it and, from the right, the figures every tab is bounded by in seven segments;
 * under it the list on the left and the selected item's detail on the right, a ghost rule between; the
 * response line, which says what the last press did; and the action keys along the bottom edge, the
 * cap printing the keyboard key and the glass over it the action, CLOSE in the last slot.
 */
final class Terminal {
    /** Room the tab keys take over the glass, and the action keys under it. */
    static final float TOP = Glass.KEY_H + Glass.KEY_GAP + 4f, BOT = Glass.KEY_H + Glass.KEY_GAP + 4f;
    /** The plate's pad round the housing. */
    static final float PAD = 7f;
    /** The header row's height from its top to the rule under it, and the content's top under that rule. */
    static final float HEAD_RULE = 26f, HEAD_H = 40f;
    /** The tab keys' slots along the top, and the action keys' along the bottom. */
    static final int TAB_SLOTS = 8, KEY_SLOTS = 8, MAX_TABS = TAB_SLOTS, MAX_FIGS = 4;
    /** Click ids: a tab key ({@code TAB + i}), and CLOSE. */
    static final int TAB = 1, CLOSE = 20;
    /** The list's share of the content's width, when the tab has a detail beside it. */
    static final float LIST_SHARE = 0.62f;

    String title = "", owner = "";
    /** The header's figures, from the right: a dim label, a seven-segment value in its cells, a dim unit, and "OF" a limit. */
    final String[] figLabel = new String[MAX_FIGS], figValue = new String[MAX_FIGS], figUnit = new String[MAX_FIGS], figOf = new String[MAX_FIGS];
    final int[] figCells = new int[MAX_FIGS], figOfCells = new int[MAX_FIGS];
    int figs;
    /** The tab keys: each one's name (null for a tab this place doesn't offer) and cap; none for a panel. */
    final String[] names = new String[MAX_TABS], caps = new String[MAX_TABS];
    int count, active = -1;
    /** What the last press did. */
    String response = "";
    /** The tab's action keys, laid out along the bottom; CLOSE takes the last slot, on {@link #closeCap}. */
    SoftKeys keys;
    int keySlots = KEY_SLOTS;
    String closeCap = "ESC";
    /** Whether the content is a list with the detail beside it, or one block across the glass. */
    boolean split = true;
    /** With no room beside the list (a panel), the room its detail takes under it; 0 to leave the glass to the tab. */
    float under;
    /** The title's height in fourteen segments. */
    float titleH = 16f;
    float x, y, w, h, gx, gy, gw, gh;
    final Tab.Place at = new Tab.Place();
    private final float[] box = new float[2];
    private final Glass.Key[] states = new Glass.Key[MAX_TABS];
    private final String[] shownCaps = new String[MAX_TABS], shownNames = new String[MAX_TABS];
    private float headY, footY, ruleX, underY = -1f;

    /** Clears the figures. */
    void noFigures() {
        figs = 0;
    }

    /** A figure: {@code label}, {@code value} in {@code cells}, {@code unit}; "OF" {@code of} in {@code ofCells} when {@code of} isn't null. */
    void figure(String label, String value, int cells, String unit, String of, int ofCells) {
        if (figs >= MAX_FIGS) return;
        int i = figs++;
        figLabel[i] = label;
        figValue[i] = value;
        figCells[i] = cells;
        figUnit[i] = unit;
        figOf[i] = of;
        figOfCells[i] = ofCells;
    }

    boolean hasTabs() {
        return count > 0;
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        float top = hasTabs() ? TOP : 0f;
        gx = x;
        gy = y + top;
        gw = w;
        gh = h - top - BOT;
        headY = gy + (hasTabs() ? 7f * Glass.P + 14f : 8f) + 8f;
        footY = gy + gh - 7f * Glass.P - 14f;
        float x0 = gx + 14f, x1 = gx + gw - 14f;
        at.gx = gx;
        at.gy = gy;
        at.gw = gw;
        at.gh = gh;
        at.lcd = false;
        float top0 = headY + HEAD_H, bottom = footY - 24f;
        at.lx = x0;
        at.ly = top0;
        at.lb = bottom;
        at.dy = top0;
        at.db = bottom;
        if (split) {
            int listCells = Glass.cells((x1 - x0) * LIST_SHARE);
            at.lr = x0 + Glass.width(listCells);
            ruleX = at.lr + Glass.CW;
            at.dx = ruleX + 14f;
        } else {
            at.lr = x1;
            ruleX = -1f;
            at.dx = x0;
        }
        underY = -1f;
        if (!split && under > 0f && bottom - under > top0 + 3f * Glass.ROW) {
            at.lb = bottom - under;
            underY = at.lb + 4f;
            at.dy = underY + 10f;
        }
        at.dr = x1;
        if (keys != null) {
            float kx0 = gx + 8f, kw0 = gw - 16f;
            keys.place(kx0, gy + gh + Glass.KEY_GAP + 4f, kw0 * Math.max(1, keys.count()) / keySlots, gy + gh - 7f * Glass.P - 6f);
        }
    }

    /** The header row's top. */
    float headY() {
        return headY;
    }

    void paint(Filament f) {
        f.plate(x, y, w, h, PAD, 8f);
        f.module(gx, gy, gw, gh, f.o().filaments(Math.max(2, Math.round(gh / 120f))));
        if (hasTabs()) paintTabs(f);
        paintHeader(f);
        if (ruleX > 0f) f.rectLit(ruleX, at.ly - 4f, 0.8f, footY - at.ly - 6f, f.o().ghostOnly().k(2.2f));
        if (underY > 0f) Glass.rule(f, at.lx, underY, at.dr - at.lx);
        if (!response.isEmpty()) Glass.text(f, at.lx, footY - 20f, Glass.cut(response, Glass.cells(at.dr - at.lx)), Drive.NORM);
        Glass.rule(f, gx + 8f, footY, gw - 16f);
        paintKeys(f);
    }

    private void paintHeader(Filament f) {
        float x0 = gx + 14f, x1 = gx + gw - 14f, ty = headY;
        // The figures from the right, so the place's name has the rest
        float fx = x1;
        for (int i = 0; i < figs; i++) {
            if (figUnit[i] != null && !figUnit[i].isEmpty()) {
                fx -= f.legendWidth(figUnit[i], f.o().size(13f));
                f.legend(fx, ty + 16f, figUnit[i], f.o().size(13f).dim().noGhost());
                fx -= 5f;
            }
            if (figOf[i] != null) {
                fx -= f.seg7Width(16f, figOfCells[i], f.o());
                f.seg7(fx, ty, 16f, figOf[i], f.o().cells(figOfCells[i]).right().norm());
                fx -= 5f + f.legendWidth("OF", f.o().size(13f));
                f.legend(fx, ty + 16f, "OF", f.o().size(13f).dim().noGhost());
                fx -= 5f;
            }
            fx -= f.seg7Width(16f, figCells[i], f.o());
            f.seg7(fx, ty, 16f, figValue[i], f.o().cells(figCells[i]).right().norm());
            fx -= 6f;
            if (figLabel[i] != null && !figLabel[i].isEmpty()) {
                fx -= f.legendWidth(figLabel[i], f.o().size(13f));
                f.legend(fx, ty + 16f, figLabel[i], f.o().size(13f).dim().noGhost());
            }
            fx -= 22f;
        }
        float room = fx - x0;
        int fits = Math.max(1, (int) Math.floor(room / f.seg14Width(titleH, 1, f.o())));
        String t = title.length() > fits ? title.substring(0, fits) : title;
        float tx = x0 + f.seg14(x0, ty, titleH, t, f.o().full()) + 14f;
        if (!owner.isEmpty()) {
            int cells = Glass.cells(fx - tx);
            if (cells > 3) Glass.dim(f, tx, ty + 3f, Glass.cut(owner, cells));
        }
        f.rectLit(gx + 8f, ty + HEAD_RULE, gw - 16f, 0.8f, f.o().ghostOnly().k(2.2f));
    }

    /** The tab keys along the top edge, each name in the glass under its key, the tab up inverse. */
    private void paintTabs(Filament f) {
        for (int i = 0; i < count; i++) {
            boolean on = names[i] != null && !names[i].isEmpty();
            shownCaps[i] = caps[i] == null ? "" : caps[i];
            shownNames[i] = on ? names[i] : "";
            states[i] = !on ? Glass.Key.BLANK : i == active ? Glass.Key.FOCUS : Glass.Key.ON;
        }
        float labelY = gy + 6f;
        Glass.keyStrip(f, gx + 8f, y, gw - 16f, TAB_SLOTS, count, labelY, shownCaps, shownNames, states, null, null);
        if (active >= 0 && active < count && names[active] != null) {
            float slot = (gw - 16f) / TAB_SLOTS, lw = Glass.width(names[active].length());
            f.dm(gx + 8f + active * slot + (slot - lw) / 2f, labelY, names[active], f.o().pitch(Glass.P).full().inverse(0, names[active].length()).noGhost());
        }
    }

    /** The tab's action keys, blank keys in the slots it leaves, and CLOSE in the last slot. */
    private void paintKeys(Filament f) {
        float kx0 = gx + 8f, kw0 = gw - 16f, ky = gy + gh + Glass.KEY_GAP + 4f, labelY = gy + gh - 7f * Glass.P - 6f;
        int n = keys == null ? 0 : Math.min(keys.count(), keySlots - 1);
        if (keys != null && n > 0) keys.paint(f);
        float slot = kw0 / keySlots;
        for (int i = Math.max(1, n); i < keySlots; i++) f.rectLit(kx0 + i * slot - 0.4f, labelY - 1f, 0.8f, 7f * Glass.P + 2f, f.o().ghostOnly().k(1.5f));
        for (int i = n; i < keySlots - 1; i++) {
            Glass.keyBox(kx0, kw0, keySlots, i, box);
            f.key(box[0], ky, box[1], Glass.KEY_H, "", f.o().size(12f));
        }
        Glass.keyBox(kx0, kw0, keySlots, keySlots - 1, box);
        f.key(box[0], ky, box[1], Glass.KEY_H, closeCap, f.o().size(12f));
        float lw = Glass.width(5);
        Glass.text(f, kx0 + (keySlots - 1) * slot + (slot - lw) / 2f, labelY, "CLOSE", Drive.NORM);
    }

    /** The tab keys' and CLOSE's boxes. */
    void boxes(FilamentActor a) {
        if (hasTabs()) {
            float slot = (gw - 16f) / TAB_SLOTS;
            for (int i = 0; i < count; i++) if (names[i] != null) a.box(TAB + i, gx + 8f + i * slot, y, slot, gy + 7f * Glass.P + 8f - y);
        }
        float kx0 = gx + 8f, kw0 = gw - 16f, slot = kw0 / keySlots, labelY = gy + gh - 7f * Glass.P - 6f;
        a.box(CLOSE, kx0 + (keySlots - 1) * slot, labelY - 3f, slot, gy + gh + Glass.KEY_GAP + 4f + Glass.KEY_H - labelY + 3f);
    }
}
