package com.ringsofiron.uihud.variants.vfd.stations;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.SoftKeys;

/**
 * A terminal's housing (style-vfd.md 18.2), as the mockups' {@code terminal} draws it for the Station
 * link, the console panels and the Plot: a slab of smoked glass in its own bezel; the page keys down
 * its left edge, each page's full name in the glass's first column beside its key and the page up in
 * inverse, a page this place doesn't offer a blank key over blank glass (along the top edge instead
 * for the Plot, whose map takes the screen's height); the title row with the place in fourteen
 * segments, its owner dim beside it and the figures every page is bounded by in seven segments under
 * them; the response line, which says what the last press did; and the soft keys along the bottom
 * edge, the cap printing the keyboard key and the glass row over it the action, CLOSE on Esc in the
 * last slot.
 */
final class Terminal {
    /** A page key down the edge, and the glass's left beside the keys. */
    static final float SIDE_W = 44f, LEFT = SIDE_W + Glass.KEY_GAP + 4f;
    /** Room the soft keys take under the glass, and the Plot's page keys over it. */
    static final float BOT = Glass.KEY_H + Glass.KEY_GAP + 4f;
    /** The page keys down the edge: the first one's top below the glass's, and their pitch. */
    static final float PAGE_TOP = 76f, PAGE_PITCH = 40f;
    /** The longest page name, in cells ("INFIRMARY", "NAVIGATION"): the names' column. */
    static final int NAME_CELLS = 10;
    static final int MAX_PAGES = 6, MAX_FIGS = 3;
    /** The soft keys' slots: a side panel's, a wide one's, the Plot's. */
    static final int SLOTS = 4, WIDE_SLOTS = 6, PLOT_SLOTS = 5;
    /** Click ids: a page key ({@code PAGE + i}), and CLOSE. */
    static final int PAGE = 1, CLOSE = 20;
    /** The plate's pad round the housing. */
    static final float PAD = 7f;

    enum Kind {
        /** A side panel: page keys down the left edge. */
        SIDE,
        /** The screen's width under the top column (the Yard, the Shipyard), its keys as a side panel's. */
        WIDE,
        /** The Plot's: page keys along the top, the map to its left. */
        PLOT
    }

    Kind kind = Kind.SIDE;
    String title = "", owner = "";
    /** The figures under the place: a dim label, a seven-segment value in its cells, a dim unit. */
    final String[] figLabel = new String[MAX_FIGS], figValue = new String[MAX_FIGS], figUnit = new String[MAX_FIGS];
    final int[] figCells = new int[MAX_FIGS];
    int figs;
    /** The page keys: each one's name (null for a page this place doesn't offer) and cap. */
    final String[] names = new String[MAX_PAGES], caps = new String[MAX_PAGES];
    int count, active = -1;
    /** What the last press did. */
    String response = "";
    /** The page's soft keys, laid out along the bottom; CLOSE takes the last slot. */
    SoftKeys keys;
    int slots = SLOTS;
    float x, y, w, h, gx, gy, gw, gh;
    final Page.Place at = new Page.Place();
    private float namesX, ruleX;
    private final float[] box = new float[2];
    private final Glass.Key[] topStates = new Glass.Key[MAX_PAGES];
    private final String[] topCaps = new String[MAX_PAGES];

    /** Whether the page keys run down the left edge: a panel with more than one page that isn't the Plot. */
    boolean sideKeys() {
        return kind != Kind.PLOT && count > 1;
    }

    /** The soft keys' slots for its kind. */
    int slotsFor() {
        return kind == Kind.WIDE ? WIDE_SLOTS : kind == Kind.PLOT ? PLOT_SLOTS : SLOTS;
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        slots = slotsFor();
        boolean plot = kind == Kind.PLOT, side = sideKeys();
        gx = x + (side ? LEFT : 0f);
        gy = y + (plot ? BOT : 0f);
        gw = w - (side ? LEFT : 0f);
        gh = h - (plot ? BOT : 0f) - BOT;
        at.gx = gx;
        at.gy = gy;
        at.gw = gw;
        at.gh = gh;
        at.x0 = gx + 12f;
        at.x1 = gx + gw - 12f;
        at.lcd = false;
        namesX = gx + 8f;
        ruleX = namesX + Glass.width(NAME_CELLS) + 10f;
        at.left = side ? ruleX + 12f : at.x0;
        float titleY = titleY();
        at.top = plot ? titleY + 40f : titleY + 74f;
        at.split = side ? gy + PAGE_TOP + MAX_PAGES * PAGE_PITCH + 8f : at.top;
        at.bottom = footY() - 26f;
        // A page's keys fill a slot each (it defines as many slots as keys), the frame's slots wide
        if (keys != null) keys.place(gx + 8f, gy + gh + Glass.KEY_GAP + 4f, (gw - 16f) * Math.max(1, keys.count()) / slots,
                gy + gh - 7f * Glass.P - 6f);
    }

    /** The title row's top: under the page keys' names along the Plot's top, else the glass's top. */
    private float titleY() {
        return kind == Kind.PLOT ? gy + 7f * Glass.P + 20f : gy + 10f;
    }

    /** The foot of the content: the rule over the soft keys' labels. */
    float footY() {
        return gy + gh - 7f * Glass.P - 14f;
    }

    void paint(Filament f) {
        f.plate(x, y, w, h, PAD, 8f);
        f.module(gx, gy, gw, gh, f.o().filaments(Math.max(2, Math.round(gh / 120f))));
        if (kind == Kind.PLOT) paintTopKeys(f);
        else if (sideKeys()) paintSideKeys(f);
        paintTitle(f);
        float foot = footY();
        if (!response.isEmpty()) Glass.text(f, at.x0, foot - 20f, Glass.cut(response, Glass.cells(at.x1 - at.x0)), Drive.NORM);
        Glass.rule(f, gx + 8f, foot, gw - 16f);
        paintSoftKeys(f);
    }

    private void paintTitle(Filament f) {
        float ty = titleY(), x0 = at.x0, x1 = at.x1;
        float tx = x0 + f.seg14(x0, ty, 16f, title, f.o().full()) + 12f;
        if (!owner.isEmpty()) {
            int room = Glass.cells(x1 - tx);
            if (room > 3) Glass.dim(f, tx, ty + 4f, Glass.cut(owner, room));
        }
        if (kind != Kind.PLOT && figs > 0) {
            float fy = ty + 30f, fx = x0;
            for (int i = 0; i < figs; i++) {
                if (figLabel[i] != null && !figLabel[i].isEmpty()) fx += Glass.label(f, fx, fy + 16f, figLabel[i]) + 7f;
                fx = Glass.figure(f, fx, fy, 16f, figValue[i], figUnit[i], figCells[i], Drive.NORM) + 22f;
            }
        }
        Glass.rule(f, gx + 8f, ty + (kind == Kind.PLOT ? 26f : 56f), gw - 16f);
    }

    /** Page keys down the left edge: the key in the bezel, the name lit beside it in the glass, inverse while its page is up. */
    private void paintSideKeys(Filament f) {
        for (int i = 0; i < count; i++) {
            float ky = gy + PAGE_TOP + i * PAGE_PITCH, ty = ky + (Glass.KEY_H - 7f * Glass.P) / 2f;
            String n = names[i];
            boolean on = n != null && !n.isEmpty();
            f.key(x, ky, SIDE_W, Glass.KEY_H, on && caps[i] != null ? caps[i] : "", f.o().size(12f));
            if (on) {
                Filament.Opt o = f.o().pitch(Glass.P).noGhost();
                if (i == active) o.full().inverse(0, n.length());
                else o.norm();
                f.dm(namesX, ty, n, o);
            }
            if (i > 0) f.rectLit(namesX, ky - (PAGE_PITCH - Glass.KEY_H) / 2f, Glass.width(NAME_CELLS), 0.8f, f.o().ghostOnly().k(1.5f));
        }
        f.rectLit(ruleX, gy + PAGE_TOP - 4f, 0.8f, MAX_PAGES * PAGE_PITCH, f.o().ghostOnly().k(2.2f));
    }

    /** The Plot's page keys along its top edge, each name in the glass under its key. */
    private void paintTopKeys(Filament f) {
        for (int i = 0; i < count; i++) {
            topCaps[i] = caps[i] == null ? "" : caps[i];
            topStates[i] = names[i] == null || names[i].isEmpty() ? Glass.Key.BLANK : i == active ? Glass.Key.FOCUS : Glass.Key.ON;
        }
        Glass.keyStrip(f, gx + 8f, y, gw - 16f, count, count, gy + 6f, topCaps, names, topStates, null, null);
        // The page up is inverse, as down the side
        if (active >= 0 && active < count && names[active] != null) {
            float slot = (gw - 16f) / Math.max(1, count), lw = Glass.width(names[active].length());
            f.dm(gx + 8f + active * slot + (slot - lw) / 2f, gy + 6f, names[active],
                    f.o().pitch(Glass.P).full().inverse(0, names[active].length()).noGhost());
        }
    }

    /** The page's soft keys, blank keys in the slots it leaves, and CLOSE on Esc in the last slot. */
    private void paintSoftKeys(Filament f) {
        float kx0 = gx + 8f, kw0 = gw - 16f, ky = gy + gh + Glass.KEY_GAP + 4f, labelY = gy + gh - 7f * Glass.P - 6f;
        int n = keys == null ? 0 : Math.min(keys.count(), slots - 1);
        if (keys != null && n > 0) keys.paint(f);
        else for (int i = 1; i < slots; i++) f.rectLit(kx0 + i * kw0 / slots - 0.4f, labelY - 1f, 0.8f, 7f * Glass.P + 2f, f.o().ghostOnly().k(1.5f));
        for (int i = n; i < slots - 1; i++) {
            Glass.keyBox(kx0, kw0, slots, i, box);
            f.key(box[0], ky, box[1], Glass.KEY_H, "", f.o().size(12f));
        }
        Glass.keyBox(kx0, kw0, slots, slots - 1, box);
        f.key(box[0], ky, box[1], Glass.KEY_H, "ESC", f.o().size(12f));
        float slot = kw0 / slots, lw = Glass.width(5);
        Glass.text(f, kx0 + (slots - 1) * slot + (slot - lw) / 2f, labelY, "CLOSE", Drive.NORM);
    }

    /** The page keys' and CLOSE's boxes. */
    void boxes(FilamentActor a) {
        if (kind == Kind.PLOT) {
            float slot = (gw - 16f) / Math.max(1, count);
            for (int i = 0; i < count; i++) if (names[i] != null) a.box(PAGE + i, gx + 8f + i * slot, y, slot, gy + 7f * Glass.P + 8f - y);
        } else if (sideKeys()) {
            for (int i = 0; i < count; i++) {
                if (names[i] == null) continue;
                float ky = gy + PAGE_TOP + i * PAGE_PITCH;
                a.box(PAGE + i, x, ky - 4f, ruleX - x, Glass.KEY_H + 8f);
            }
        }
        float kx0 = gx + 8f, kw0 = gw - 16f, slot = kw0 / slots, labelY = gy + gh - 7f * Glass.P - 6f;
        a.box(CLOSE, kx0 + (slots - 1) * slot, labelY - 3f, slot, gy + gh + Glass.KEY_GAP + 4f + Glass.KEY_H - labelY + 3f);
    }
}
