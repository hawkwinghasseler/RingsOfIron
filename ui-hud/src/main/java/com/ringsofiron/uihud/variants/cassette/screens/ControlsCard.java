package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpFrame;
import com.ringsofiron.uihud.providers.KeyHintProvider.HelpSection;
import com.ringsofiron.uihud.providers.KeyHintProvider.KeyHint;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

import java.util.Locale;

/**
 * One of the controls cards (the classic F1 panel; Context's M6): yellow ring-bound cards, a card for
 * each place keys apply (style guide 9.8), flipped with the tabs or Left and Right. Each card lists
 * its keys as the player bound them, a keycap and what it does. The cards are read from
 * {@code KeyHintProvider.collectAll} when the controls come up; a section no card names goes on the
 * last card but one, Anywhere.
 */
final class ControlsCard extends Page {
    private static final Color CARD_TOP = Yard.hex("F2DA7E"), CARD_FOOT = Yard.hex("E6C964"), RULE = Yard.hex("C7A646");
    private static final Color HEAD_RULE = Yard.hex("B8352B"), RING = Yard.hex("B9B6AE"), RING_DARK = Yard.hex("6F6C66");
    private static final Color HOLE = Yard.hex("3C332B");
    private static final float ROW = 26f;
    /** The help frame's section titles each card shows. */
    private final String[] titles;
    /** Typed at the card's head. */
    private final String heading;
    /** True for the card that takes every section no card names. */
    private final boolean rest;
    private final HelpFrame frame;
    private final ControlsCard[] all;

    ControlsCard(String label, String heading, HelpFrame frame, ControlsCard[] all, boolean rest, String... titles) {
        super(label, "KEYS", ClassicWindow.HELP);
        this.heading = heading;
        this.titles = titles;
        this.rest = rest;
        this.frame = frame;
        this.all = all;
    }

    /** Whether the card lists {@code s}: by its title, or as the catch-all for sections no card names. */
    private boolean holds(HelpSection s) {
        for (String t : titles) if (t.equalsIgnoreCase(s.title)) return true;
        if (!rest) return false;
        for (ControlsCard c : all) {
            if (c == null || c == this) continue;
            for (String t : c.titles) if (t.equalsIgnoreCase(s.title)) return false;
        }
        return true;
    }

    @Override
    boolean offered() {
        for (int i = 0; i < frame.sections.size; i++) {
            HelpSection s = frame.sections.get(i);
            if (s.hints.size > 0 && holds(s)) return true;
        }
        return false;
    }

    @Override
    void refresh(Boards b) {
    }

    @Override
    boolean key(int keycode) {
        if (keycode == Input.Keys.LEFT || keycode == Input.Keys.UP) return console.flip(-1);
        if (keycode == Input.Keys.RIGHT || keycode == Input.Keys.DOWN) return console.flip(1);
        return false;
    }

    @Override
    void draw(YardPaint p, Hits hits, float x, float y, float w, float h) {
        float bx = x + 8f, by = y + 6f, bw = w - 16f, bh = h - 12f;
        BigConsole.bay(p, bx, by, bw, bh, "CONTROLS", false);
        float cw = Math.min(1040f, bw - 80f), cx = bx + (bw - cw) / 2f, cy = by + 46f, ch = bh - 66f;
        // The cards behind, edges showing under this one
        for (int k = 2; k >= 1; k--) {
            p.rect(cx + k * 4f, cy + k * 5f, cw, ch, Yard.SHADOW, 0.35f);
            p.vgrad(cx + k * 3f, cy + k * 4f, cw, ch, CARD_FOOT, Yard.hex("D7B955"));
        }
        p.rect(cx + 2f, cy + 3f, cw, ch, Yard.SHADOW, 0.5f);
        p.vgrad(cx, cy, cw, ch, CARD_TOP, CARD_FOOT);
        // The rings through the card's punched holes
        float[] rings = {cx + cw * 0.2f, cx + cw * 0.5f, cx + cw * 0.8f};
        for (float rx : rings) {
            p.circle(rx, cy + 14f, 6f, HOLE);
            p.rrect(rx - 4f, cy - 16f, 8f, 34f, 4f, RING_DARK);
            p.rrect(rx - 3f, cy - 16f, 6f, 32f, 3f, RING);
            p.rect(rx - 1.5f, cy - 14f, 1.5f, 26f, Color.WHITE, 0.45f);
        }
        float tx = cx + 34f;
        p.text(YardFonts.Face.PAPER, 20f, heading, tx, cy + 54f, Paper.INK);
        p.rect(cx + 18f, cy + 66f, cw - 36f, 1.5f, HEAD_RULE, 0.8f);
        // Two columns of keys
        float colW = (cw - 68f) / 2f, top = cy + 96f, bottom = cy + ch - 18f;
        for (float ly = top + 6f; ly < bottom; ly += ROW) p.rect(cx + 18f, ly, cw - 36f, 0.7f, RULE, 0.7f);
        // The keycaps make a column as wide as the widest of them, the labels after it
        float capW = 15f;
        for (int i = 0; i < frame.sections.size; i++) {
            HelpSection s = frame.sections.get(i);
            if (s.hints.size == 0 || !holds(s)) continue;
            for (int j = 0; j < s.hints.size; j++) {
                String k = s.hints.get(j).key;
                capW = Math.max(capW, p.keyWidth(k.isEmpty() ? "--" : k));
            }
        }
        capW = Math.min(capW, colW * 0.4f);
        int chars = (int) ((colW - capW - 18f) / 7.6f);
        int col = 0;
        float ly = top;
        for (int i = 0; i < frame.sections.size; i++) {
            HelpSection s = frame.sections.get(i);
            if (s.hints.size == 0 || !holds(s)) continue;
            // A second section on one card: its own heading
            if (titles.length > 1 || rest) {
                if (ly + ROW > bottom) {
                    if (++col > 1) break;
                    ly = top;
                }
                p.text(YardFonts.Face.LEGEND, 12f, s.title.toUpperCase(Locale.ROOT), tx + col * (colW + 20f), ly, Paper.FADED);
                ly += ROW;
            }
            for (int j = 0; j < s.hints.size; j++) {
                if (ly + 6f > bottom) {
                    if (++col > 1) break;
                    ly = top;
                }
                if (col > 1) break;
                KeyHint kh = s.hints.get(j);
                float kx = tx + col * (colW + 20f);
                p.key(kx, ly - 12f, kh.key.isEmpty() ? "--" : kh.key);
                p.typed(kh.label.length() > chars ? Ledger.cut(kh.label, chars) : kh.label, kx + capW + 14f, ly, 14f, Paper.INK);
                ly += ROW;
            }
            if (col > 1) break;
        }
    }
}
