package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Ink;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * The controls reference (functional-task 7, Controls (F1); mockup card-controls), in place of the
 * game's controls panel: a printed quick-reference card laid over the screen, which is out of the
 * fiction (style-vfd.md 7.12), under the band and CHR, which stay on the glass since a card can't show
 * what is live. Its sides are index tabs printed along its top edge, the one showing in front: the
 * Keyboard ({@link ControlsTab}) and the Gamepad. F1 opens it on the Keyboard and closes it, as Esc
 * does; it opens over any screen and closes back to it. Tab turns it over, as a click on a tab does,
 * and Pg Up, Pg Dn and the wheel turn a side's pages.
 */
final class ControlsCard extends Screen {
    /** The card on the stage: at most this wide, in from the sides, its foot in from the stage's. */
    static final float MAX_W = 1000f, SIDE = 40f, UNDER = 16f;
    /** The index tabs: their size, and in from the card's left. */
    static final float TAB_W = 158f, TAB_H = 30f, TAB_IN = 22f;
    /** Click ids: a tab ({@code TAB + i}). */
    static final int TAB = 1;

    final ControlsTab keyboard = new ControlsTab("Keyboard", false);
    final ControlsTab gamepad = new ControlsTab("Gamepad", true);
    /** The screen it opened over, which shows again as it closes. */
    Screen under;

    @Override
    Tab[] tabs() {
        return new Tab[] {keyboard, gamepad};
    }

    @Override
    void title(Boards b, HudData d) {
    }

    @Override
    void layout(float stageW, float stageH) {
        w = Math.min(MAX_W, stageW - 2f * SIDE);
        x = (stageW - w) / 2f;
        // Under the band and the tabs over the card's edge
        y = Geo.SCREEN_Y + TAB_H;
        h = stageH - UNDER - y;
        keyboard.face(x, y, w, h);
        gamepad.face(x, y, w, h);
    }

    @Override
    void paint(Filament f, float time) {
        float t1 = x + TAB_IN, t2 = t1 + TAB_W - 4f;
        boolean pad = tab == gamepad;
        float front = pad ? t2 : t1, back = pad ? t1 : t2;
        // The tab behind, then the card and the tab in front of it as one sheet
        f.cardShadow(back, y - TAB_H + 3f, TAB_W, TAB_H + 4f, 6f, 0.5f, 10f);
        f.cardSheet(back, y - TAB_H + 3f, TAB_W, TAB_H + 4f, 6f, Ink.CARD_BACK);
        f.cardShadow(front, y - TAB_H, TAB_W, TAB_H, 6f, 0.6f, 16f);
        f.card(x, y, w, h);
        f.cardSheet(front, y - TAB_H, TAB_W, TAB_H + 8f, 6f, Ink.CARD);
        tabLabel(f, t1, "TAB", keyboard.word, !pad);
        tabLabel(f, t2 + 4f, "TAB", gamepad.word, pad);
        if (tab != null) tab.paint(f, time);
        // F1 closes it, at the head's right
        String closes = "closes this card";
        float cw = f.printWidth(closes, 13f, 0.02f), kx = x + w - 30f - cw;
        f.print(kx, y + 44f, closes, f.o().size(13f));
        f.printedKey(kx - 8f - f.printedKeyWidth("F1", 18f, 12f), y + 30f, "F1", 18f, 12f);
    }

    /** A tab's label: the key that turns to it (Tab, for the side behind), and its word. */
    private void tabLabel(Filament f, float tx, String key, String word, boolean front) {
        float kx = tx + 16f;
        if (!front) kx += f.printedKey(kx, y - TAB_H + 7f, key, 18f, 12f) + 9f;
        f.print(kx, y - TAB_H + 21f, word, f.o().size(13f).ink(front ? Ink.CARD_INK : Ink.CARD_SOFT));
    }

    @Override
    void boxes(FilamentActor a) {
        a.box(Windows.PLATE, x, y - TAB_H, w, h + TAB_H);
        float t1 = x + TAB_IN, t2 = t1 + TAB_W - 4f;
        a.box(TAB, t1, y - TAB_H, TAB_W - 4f, TAB_H);
        a.box(TAB + 1, t2, y - TAB_H, TAB_W, TAB_H);
    }

    @Override
    void clicked(int id) {
        if (id == TAB) turn(keyboard);
        else if (id == TAB + 1) turn(gamepad);
    }
}
