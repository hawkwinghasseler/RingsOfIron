package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The guide card (spec 7.6), Z5: the tutorial is out of fiction and looks it, one flat card in plain type
 * with no hardware, the guide's heading and the objective now. It never shares the ticker. A finished
 * guide's last word stays a while, then the card goes.
 */
final class GuideCard extends YardPart {
    static final float X = 16f, Y = 16f, W = StationsLayout.GUIDE_W;
    /** How long a finished guide's last word stays up. */
    static final float LAST_WORD = 20f;
    private static final Color CARD = Yard.hex("F7F5F0"), HEAD = Yard.hex("6B6B6B"), BODY = Yard.hex("3A3A3C");
    private static final float PAD = 14f, HEAD_SIZE = 12f, BODY_SIZE = 13f, LEADING = 17f;

    private final StationsFrame f;
    private final Array<String> lines = new Array<>();
    private String wrapped = "";
    private float wrapWidth;
    private String doneText;
    private float doneAt;

    GuideCard(StationsFrame f) {
        super(X, Y);
        this.f = f;
    }

    /** The card's height this frame, 0 with no guide running. Wraps the text when it changed. */
    float measure(YardPaint p) {
        HudData d = f.data;
        String text = d.guideText;
        if (text == null || text.isEmpty()) return 0f;
        if (d.guideDone) {
            if (!text.equals(doneText)) {
                doneText = text;
                doneAt = f.time;
            }
            if (f.time - doneAt > LAST_WORD) return 0f;
        } else doneText = null;
        float maxW = W - 2 * PAD;
        if (!text.equals(wrapped) || maxW != wrapWidth) {
            wrap(p, text, maxW);
            wrapped = text;
            wrapWidth = maxW;
        }
        return PAD + 14f + lines.size * LEADING + PAD - 4f;
    }

    private void wrap(YardPaint p, String text, float maxW) {
        lines.clear();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && p.width(Face.PLAIN, BODY_SIZE, next) > maxW) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        float h = this.h;
        p.rrect(X + 1f, Y + 2f, W, h, 6f, Color.BLACK, 0.25f);
        p.rrect(X, Y, W, h, 6f, CARD);
        p.text(Face.PLAIN, HEAD_SIZE, d.guideTitle == null ? "" : d.guideTitle, X + PAD, Y + PAD + 8f, HEAD);
        for (int i = 0; i < lines.size; i++) p.text(Face.PLAIN, BODY_SIZE, lines.get(i), X + PAD, Y + PAD + 28f + i * LEADING, BODY);
    }
}
