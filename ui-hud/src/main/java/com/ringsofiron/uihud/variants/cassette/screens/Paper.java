package com.ringsofiron.uihud.variants.cassette.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts;
import com.ringsofiron.uihud.variants.cassette.YardPaint;

/**
 * The paper stocks records are typed on (style guide 9.8): fanfold with green bars and sprocket holes
 * for a contract, and the words wrapped to a sheet's width in Special Elite.
 */
final class Paper {
    private Paper() {}

    static final float LINE = 18f, SIZE = 13f;
    static final Color INK = Yard.hex("26211D"), FADED = Yard.hex("6A5F52"), STAMP = Yard.hex("9E2A23");
    private static final Color STOCK_TOP = Yard.hex("F3EEDF"), STOCK_FOOT = Yard.hex("E8E1CC"), BAR = Yard.hex("CFE2C2");
    private static final Color CLIP = Yard.hex("B9B6AE"), CLIP_DARK = Yard.hex("6F6C66");

    /**
     * A fanfold sheet torn off and clipped: green bars two lines deep, the sprocket margins with their
     * holes and perforations. {@code ground} is what shows through the holes.
     */
    static void fanfold(YardPaint p, float x, float y, float w, float h, Color ground) {
        p.rect(x + 2f, y + 3f, w, h, Yard.SHADOW, 0.55f);
        p.vgrad(x, y, w, h, STOCK_TOP, STOCK_FOOT);
        float m = 16f;
        for (float by = y + 30f; by + LINE <= y + h - 4f; by += 2f * LINE) p.rect(x + m, by, w - 2f * m, LINE, BAR, 0.55f);
        for (float hy = y + 9f; hy < y + h - 4f; hy += 18f) {
            p.circle(x + m / 2f, hy, 2.6f, ground);
            p.circle(x + w - m / 2f, hy, 2.6f, ground);
        }
        for (float dy = y + 2f; dy < y + h - 2f; dy += 6f) {
            p.rect(x + m - 0.4f, dy, 0.8f, 3f, FADED, 0.5f);
            p.rect(x + w - m - 0.4f, dy, 0.8f, 3f, FADED, 0.5f);
        }
        // The tear along the top, and the clip that holds it
        for (float tx = x; tx < x + w; tx += 4f) p.rect(tx, y - 1f, 2f, 1.2f, STOCK_TOP);
        p.rrect(x + w / 2f - 34f, y - 9f, 68f, 16f, 3f, CLIP_DARK);
        p.rrect(x + w / 2f - 33f, y - 9f, 66f, 13f, 3f, CLIP);
        p.rect(x + w / 2f - 26f, y - 5f, 52f, 2f, CLIP_DARK, 0.6f);
    }

    /** {@code text} wrapped to {@code w} units of typing at {@code size}, into {@code out} (cleared). */
    static void wrap(YardPaint p, String text, float w, float size, Array<String> out) {
        out.clear();
        if (text == null || text.isEmpty()) return;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && p.width(YardFonts.Face.PAPER, size, next) > w) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) out.add(line.toString());
    }

    /** A rubber stamp: a word in a red frame, tilted a little by its ink. */
    static void stamp(YardPaint p, String word, float x, float y) {
        float w = p.width(YardFonts.Face.LEGEND, 14f, word) + 16f;
        p.frame(x, y, w, 22f, 2f, STAMP, 0.8f);
        p.text(YardFonts.Face.LEGEND, 14f, word, x + 8f, y + 16f, STAMP, 0.85f);
    }
}
