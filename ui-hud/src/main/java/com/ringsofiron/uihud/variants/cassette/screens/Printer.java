package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * A console's till printer: a roll of paper rising from a slot with a serrated tear bar, each order's
 * result typed on it as the key is pressed, the newest at the tear bar. The roll keeps this visit's
 * lines; its first is the heading, with the place, the day and the time.
 */
final class Printer {
    static final float PAPER_H = 156f, BODY_H = 58f, HEIGHT = PAPER_H + BODY_H - 2f;
    private static final Color PAPER_TOP = Yard.hex("F3ECDA"), PAPER_FOOT = Yard.hex("E6DDC6");
    private static final Color HEAD_INK = Yard.hex("4A4036"), INK = Yard.hex("26211D");
    private static final Color BODY = Yard.hex("3A3C32"), SLOT = Yard.hex("141310");
    private final Array<String> lines = new Array<>();
    private final float[] tooth = new float[6];
    private final String label;
    private final int keep;

    Printer(String label, int keep) {
        this.label = label;
        this.keep = keep;
    }

    /** Starts a new roll with its heading. */
    void start(String heading) {
        lines.clear();
        lines.add(heading);
    }

    boolean started() {
        return lines.size > 0;
    }

    void clear() {
        lines.clear();
    }

    void print(String line) {
        lines.add(line);
        while (lines.size > keep) lines.removeIndex(0);
    }

    /** The roll and the printer under it, {@code w} wide, its paper's top at {@code y}. */
    void draw(YardPaint p, float x, float y, float w, Color labelInk) {
        float px = x + 16f, pw = w - 32f, slot = y + PAPER_H;
        p.rect(px + 1.5f, y + 2f, pw, slot - y, Yard.SHADOW, 0.5f);
        p.vgrad(px, y, pw, slot - y, PAPER_TOP, PAPER_FOOT);
        p.rect(px, y, pw, 16f, Color.BLACK, 0.1f);
        p.rect(px, y, pw, 5f, Color.BLACK, 0.1f);
        int shown = Math.min(lines.size, (int) ((PAPER_H - 30f) / 22f) + 1);
        int room = (int) ((pw - 28f) / 7.4f);
        for (int k = 0; k < shown; k++) {
            int i = lines.size - shown + k;
            float ly = slot - 14f - (shown - 1 - k) * 22f;
            String s = Ledger.cut(lines.get(i), room);
            if (i == 0) p.textC(YardFonts.Face.PAPER, 13f, s, px + pw / 2f, ly, HEAD_INK);
            else p.typed(s, px + 14f, ly, 13f, INK);
        }
        p.rrect(x + 2f, slot, w, BODY_H, 4f, Yard.SHADOW, 0.6f);
        p.rrect(x, slot - 2f, w, BODY_H, 4f, BODY);
        p.rect(x + 4f, slot - 1.5f, w - 8f, 1f, Color.WHITE, 0.08f);
        p.rrect(px - 4f, slot, pw + 8f, 4f, 1f, SLOT);
        // The tear bar's teeth
        p.rect(px - 6f, slot + 1f, pw + 12f, 5f, Yard.ALU);
        for (float tx = px - 6f; tx + 6f <= px + pw + 6.01f; tx += 6f) {
            tooth[0] = tx;
            tooth[1] = slot + 1.2f;
            tooth[2] = tx + 3f;
            tooth[3] = slot - 3f;
            tooth[4] = tx + 6f;
            tooth[5] = slot + 1.2f;
            p.fillConvex(tooth, Yard.ALU, 1f);
        }
        p.textC(YardFonts.Face.LEGEND, 12f, label, x + w / 2f, slot + 36f, labelInk);
        p.screw(x + 10f, slot + 46f);
        p.screw(x + w - 10f, slot + 46f);
    }
}
