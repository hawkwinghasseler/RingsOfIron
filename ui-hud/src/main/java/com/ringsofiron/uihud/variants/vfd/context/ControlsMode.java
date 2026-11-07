package com.ringsofiron.uihud.variants.vfd.context;

import com.badlogic.gdx.Input.Keys;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Ink;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;

/**
 * M6, the controls (functional-context 10.6; mockup cx-controls): the F1 card as the Context design's
 * mode, a printed quick-reference card laid over the game, which is out of the fiction (style-vfd.md
 * 7.12). Its tabs are index tabs printed along its top edge, the one showing in front, each with its
 * number: the Keyboard, and the Gamepad. The top rack above it and the mode's foot strip under it stay
 * on the glass, since a card can't show what is live. It stands for the game's controls panel: F1
 * opens and closes it, as Esc closes it.
 */
final class ControlsMode extends Mode {
    /** The card on a 1280 x 800 stage: in from the sides, its top under the rack and its tabs, and room under it for the foot. */
    static final float SIDE = 28f, TOP = 74f, UNDER = 54f;
    /** The index tabs: their size, and in from the card's left. */
    static final float TAB_W = 158f, TAB_H = 30f, TAB_IN = 22f;
    /** The foot strip's frame: the stage less the rack and a margin. */
    static final float MARGIN = 12f, Y = 40f;

    final ControlsTab keyboard = new ControlsTab("Keyboard", false);
    final ControlsTab gamepad = new ControlsTab("Gamepad", true);
    /** The card. */
    float cx, cy, cw, ch;

    ControlsMode() {
        super(false);
        frame.footOnly = true;
    }

    @Override
    ModeTab[] tabs() {
        return new ModeTab[] {keyboard, gamepad};
    }

    @Override
    ModeTab tabFor(ClassicWindow window) {
        return window == ClassicWindow.HELP ? keyboard : null;
    }

    @Override
    boolean showing(ClassicWindow window) {
        return tab != null && window == ClassicWindow.HELP;
    }

    @Override
    void title(Boards b, HudData d) {
        frame.title = "CONTROLS";
        frame.sub = "";
    }

    @Override
    String closeKey() {
        return "F1";
    }

    @Override
    void place(float w, float h) {
        frame.place(MARGIN, Y, w - 2f * MARGIN, h - Y - MARGIN);
        cx = SIDE;
        cy = TOP;
        cw = w - 2f * SIDE;
        ch = h - TOP - UNDER;
    }

    @Override
    void layout(float stageW, float stageH) {
        super.layout(stageW, stageH);
        if (tab != null) tab.place(cx, cy, cw, ch);
    }

    /** The digits are the card's tabs; the rest go to the tab. */
    @Override
    boolean key(int keycode) {
        if (keycode == Keys.NUM_1 || keycode == Keys.NUM_2) {
            int i = keycode - Keys.NUM_1;
            if (i < count) turn(offered[i]);
            return true;
        }
        return tab != null && tab.key(keycode);
    }

    @Override
    void paint(Filament f, float time) {
        float t1 = cx + TAB_IN, t2 = t1 + TAB_W - 4f;
        boolean pad = tab == gamepad;
        float front = pad ? t2 : t1, back = pad ? t1 : t2;
        // The tab behind, then the card and the tab in front of it as one sheet
        f.cardShadow(back, cy - TAB_H + 3f, TAB_W, TAB_H + 4f, 6f, 0.5f, 10f);
        f.cardSheet(back, cy - TAB_H + 3f, TAB_W, TAB_H + 4f, 6f, Ink.CARD_BACK);
        f.cardShadow(front, cy - TAB_H, TAB_W, TAB_H, 6f, 0.6f, 16f);
        f.card(cx, cy, cw, ch);
        f.cardSheet(front, cy - TAB_H, TAB_W, TAB_H + 8f, 6f, Ink.CARD);
        tabLabel(f, t1, "1", keyboard.word, !pad);
        tabLabel(f, t2 + 4f, "2", gamepad.word, pad);
        if (tab != null) tab.paint(f, time);
        frame.paint(f);
    }

    private void tabLabel(Filament f, float tx, String key, String word, boolean front) {
        float kw = f.printedKey(tx + 16f, cy - TAB_H + 7f, key, 18f, 12f);
        f.print(tx + 16f + kw + 9f, cy - TAB_H + 21f, word, f.o().size(13f).ink(front ? Ink.CARD_INK : Ink.CARD_SOFT));
    }

    @Override
    void boxes(FilamentActor a) {
        a.box(Modes.PLATE, frame.x, frame.y, frame.w, frame.h);
        float t1 = cx + TAB_IN, t2 = t1 + TAB_W - 4f;
        a.box(ModeFrame.TAB, t1, cy - TAB_H, TAB_W - 4f, TAB_H);
        a.box(ModeFrame.TAB + 1, t2, cy - TAB_H, TAB_W, TAB_H);
        frame.boxes(a);
        if (tab != null) tab.boxes(a);
    }
}
