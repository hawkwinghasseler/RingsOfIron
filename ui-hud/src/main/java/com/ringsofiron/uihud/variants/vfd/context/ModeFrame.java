package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

/**
 * The frame every full-screen mode has (functional-context 10; style-vfd.md 18.2), as the mockups'
 * {@code modeFrame} draws it: one plate; its title row a strip of glass across the top, the mode's
 * title in fourteen segments and its subtitle dim; each tab a key in the bezel over the strip, its cap
 * the tab's number and its name in the strip under it, inverse while it shows; and the foot a strip
 * across the bottom with the one-line feed at its left and the keys that close the mode right-aligned
 * (its own key, or Esc). The content sits between the two strips.
 */
final class ModeFrame {
    static final float KH = Glass.KEY_H, TITLE_H = 30f, FOOT_H = 26f, GAP = 6f;
    /** Room the tab keys take over the title strip. */
    static final float TABS_H = KH + 9f;
    static final int MAX_TABS = 6;
    /** Click ids: a tab ({@code TAB + i}), and the close keys in the foot. */
    static final int TAB = 1, CLOSE = 20;

    String title = "", sub = "";
    final String[] tabs = new String[MAX_TABS];
    int tabCount, active;
    /** The tab keys show even with one tab (a mode with tabs); a mode without has none. */
    boolean tabRow;
    String feedSource = "", feedText = "";
    String closeKey = "";

    float x, y, w, h;
    private final float[] tabX = new float[MAX_TABS], tabKeyW = new float[MAX_TABS], tabSlot = new float[MAX_TABS];
    private float closeX0 = -1f;

    /** Room above and below the content: the tab row and title strip, the foot. */
    static float chrome(boolean tabRow) {
        return (tabRow ? TABS_H : 0f) + TITLE_H + GAP + GAP + FOOT_H;
    }

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        float tx = x + 12f + seg14Width(title.length()) + 14f;
        if (!sub.isEmpty()) tx += Glass.width(sub.length()) + 34f;
        for (int i = 0; i < tabCount; i++) {
            float lw = Glass.width(tabs[i].length()), kw = Math.max(44f, lw), slot = kw + 26f;
            tabX[i] = tx;
            tabKeyW[i] = kw;
            tabSlot[i] = slot;
            tx += slot;
        }
    }

    static float seg14Width(int n) {
        return n * 16f * 0.84f - 16f * 0.24f;
    }

    /** The content's top and bottom. */
    float top() {
        return y + (tabRow ? TABS_H : 0f) + TITLE_H + GAP;
    }

    float bottom() {
        return y + h - FOOT_H - GAP;
    }

    void paint(Filament f) {
        float ty = y + (tabRow ? TABS_H : 0f);
        f.plate(x, y, w, h, 7f, 8f);
        f.module(x, ty, w, TITLE_H, f.o().filaments(0).noGlare().radius(2f));
        float tx = x + 12f;
        tx += f.seg14(tx, ty + 7f, 16f, title, f.o().full()) + 14f;
        if (!sub.isEmpty()) Glass.dim(f, tx, ty + 10f, sub);
        for (int i = 0; tabRow && i < tabCount; i++) {
            float lw = Glass.width(tabs[i].length()), kw = tabKeyW[i], slot = tabSlot[i];
            f.key(tabX[i] + (slot - kw) / 2f, y, kw, KH, Integer.toString(i + 1), f.o().size(12f));
            Filament.Opt o = f.o().pitch(Glass.P).noGhost();
            if (i == active) o.full().inverse(0, tabs[i].length());
            else o.norm();
            f.dm(tabX[i] + (slot - lw) / 2f, ty + 10f, tabs[i], o);
        }
        float fy = y + h - FOOT_H;
        f.module(x, fy, w, FOOT_H, f.o().filaments(0).noGlare().radius(2f));
        float fx = x + 12f;
        if (!feedSource.isEmpty()) fx += Glass.dim(f, fx, fy + 8f, feedSource + ":") + 9f;
        float closeW = (closeKey.isEmpty() ? 0f : Glass.capWordWidth(f, new String[] {closeKey}, "OR"))
                + Glass.capWordWidth(f, ESC, "CLOSE");
        float kx = x + w - 12f - closeW + 14f;
        int room = Glass.cells(kx - 20f - fx);
        if (!feedText.isEmpty() && room > 0) Glass.text(f, fx, fy + 8f, Glass.cut(feedText, room), Drive.NORM);
        closeX0 = kx;
        if (!closeKey.isEmpty()) kx = Glass.capWord(f, kx, fy + 4f, new String[] {closeKey}, "OR", Drive.NORM);
        Glass.capWord(f, kx, fy + 4f, ESC, "CLOSE", Drive.NORM);
    }

    private static final String[] ESC = {"ESC"};

    void boxes(FilamentActor a) {
        float ty = y + (tabRow ? TABS_H : 0f);
        for (int i = 0; tabRow && i < tabCount; i++) a.box(TAB + i, tabX[i], y, tabSlot[i], ty + TITLE_H - y);
        if (closeX0 > 0f) a.box(CLOSE, closeX0, y + h - FOOT_H, x + w - closeX0, FOOT_H);
    }
}
