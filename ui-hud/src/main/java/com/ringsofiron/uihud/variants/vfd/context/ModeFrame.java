package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

/**
 * The frame every full-screen mode has (functional-context 10; style-vfd.md 18.2), as the mockups'
 * {@code modeFrame} draws it: one plate; its title row a strip of glass across the top, the mode's
 * title in fourteen segments and its subtitle dim; each tab a key in the bezel over the strip, its cap
 * the tab's number and its name in the strip under it, inverse while it shows; and the foot a strip
 * across the bottom with the one-line feed at its left and the keys that close the mode right-aligned
 * (its own key, or Esc). The content sits between the two strips.
 *
 * <p>The captain's record is the captain's own book, so it is the slate instead ({@link #slate}; the
 * mockups' M5): the frame is the slate's rubber housing and its reflective LCD across the screen, the
 * tab keys in the housing over the glass with their names in its title row, dark while showing, under
 * the title in large dots; a rule under the title row and over the foot row, which has the feed and
 * the close keys printed dark; and the tab's own keys under the glass, printed on the housing.
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
    /** The slate's frame rather than the terminals' plate, with the silk on its housing. */
    boolean slate;
    /** Only the foot strip, on the glass across the frame's bottom: the mode draws the rest itself (the controls' card). */
    boolean footOnly;
    String brand = "SLATE", owner = "CAPTAIN";
    /** The slate's title in large dots, at this pitch. */
    static final float SLATE_TITLE_PITCH = 2.5f;

    float x, y, w, h;
    /** The slate's glass, x, y, w, h. */
    final float[] glass = new float[4];
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
        float tx;
        if (slate) {
            Slate.glass(x, y, w, h, KH - 4f, true, glass);
            tx = glass[0] + 14f + Slate.bigWidth(title.length(), SLATE_TITLE_PITCH) + 34f;
        } else {
            tx = x + 12f + seg14Width(title.length()) + 14f;
            if (!sub.isEmpty()) tx += Glass.width(sub.length()) + 34f;
        }
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
        if (slate) return glass[1] + 46f;
        return y + (tabRow ? TABS_H : 0f) + TITLE_H + GAP;
    }

    float bottom() {
        if (slate) return footY() - 8f - 6f;
        return y + h - FOOT_H - GAP;
    }

    /** The content's left and width: the whole frame, or the slate's glass less its margins. */
    float contentX() {
        return slate ? glass[0] + 14f : x;
    }

    float contentW() {
        return slate ? glass[2] - 28f : w;
    }

    /** The slate's foot row, and where its keys go under the glass: their actions' row. */
    float footY() {
        return glass[1] + glass[3] - 26f;
    }

    float slateKeysX() {
        return glass[0] + 4f;
    }

    float slateKeysY() {
        return glass[1] + glass[3] + 12f;
    }

    void paint(Filament f) {
        if (slate) {
            paintSlate(f);
            return;
        }
        if (footOnly) {
            paintFoot(f);
            return;
        }
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
        paintFoot(f);
    }

    /** The foot strip: the feed's line at its left, the keys that close the mode at its right. */
    private void paintFoot(Filament f) {
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
    private final String[] closeWords = new String[4];
    private static final boolean[] CLOSE_CAPS = {true, false, true, false};

    /** The slate: its housing and glass, the title row with the tabs over it, the rules, the foot row. */
    private void paintSlate(Filament f) {
        float gx = glass[0], gy = glass[1], gw = glass[2], gh = glass[3];
        f.slateHousing(x, y, w, h, brand, owner);
        f.lcdPanel(gx, gy, gw, gh, f.o());
        float x0 = gx + 14f, x1 = gx + gw - 14f;
        Slate.big(f, x0, gy + 9f, title, SLATE_TITLE_PITCH);
        for (int i = 0; tabRow && i < tabCount; i++) {
            float lw = Glass.width(tabs[i].length()), kw = tabKeyW[i], slot = tabSlot[i];
            f.key(tabX[i] + (slot - kw) / 2f, y + 12f, kw, KH, Integer.toString(i + 1), f.o().size(12f));
            float nx = tabX[i] + (slot - lw) / 2f;
            if (i == active) Slate.inverse(f, nx, gy + 13f, tabs[i]);
            else Slate.text(f, nx, gy + 13f, tabs[i]);
        }
        Slate.rule(f, gx + 8f, gy + 38f, gw - 16f);
        float fr = footY();
        Slate.rule(f, gx + 8f, fr - 8f, gw - 16f);
        closeWords[0] = closeKey;
        closeWords[1] = closeKey.isEmpty() ? "" : "OR";
        closeWords[2] = "ESC";
        closeWords[3] = "CLOSE";
        float cw = Slate.capWordsWidth(closeWords);
        float kx = x1 - cw;
        float fx = x0;
        if (!feedSource.isEmpty()) fx += Slate.dim(f, fx, fr, feedSource + ":") + Glass.CW;
        int room = Glass.cells(kx - 20f - fx);
        if (!feedText.isEmpty() && room > 0) Slate.text(f, fx, fr, Glass.cut(feedText, room));
        closeX0 = kx;
        Slate.capWords(f, kx, fr, closeWords, CLOSE_CAPS);
    }

    void boxes(FilamentActor a) {
        if (slate) {
            for (int i = 0; tabRow && i < tabCount; i++) a.box(TAB + i, tabX[i], y + 12f, tabSlot[i], glass[1] + 30f - y - 12f);
            if (closeX0 > 0f) a.box(CLOSE, closeX0 - 4f, footY() - 4f, glass[0] + glass[2] - closeX0, 20f);
            return;
        }
        float ty = y + (tabRow ? TABS_H : 0f);
        for (int i = 0; tabRow && !footOnly && i < tabCount; i++) a.box(TAB + i, tabX[i], y, tabSlot[i], ty + TITLE_H - y);
        if (closeX0 > 0f) a.box(CLOSE, closeX0, y + h - FOOT_H, x + w - closeX0, FOOT_H);
    }
}
