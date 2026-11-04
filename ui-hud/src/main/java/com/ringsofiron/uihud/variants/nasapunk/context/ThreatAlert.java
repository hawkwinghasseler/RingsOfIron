package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * P3, the threat alert (section 6): the one interrupt for incoming fire, on every seat and on deck.
 * A red-framed glass with a lit lamp carrying the missile's glyph; the count at 22 (with the
 * soonest impact where no threat list has it); then the decoy with its key cap, and point defense
 * while it fires.
 */
final class ThreatAlert extends NpInstrument {
    static final float W = 500f, H = 60f;
    private static final Color RIM = NpColors.BEZEL_SHADOW;

    String line1 = "";
    /** Line 2's parts: text, or a key cap when {@code keys} says so at the same index. */
    final List<String> parts = new ArrayList<>();
    final List<Boolean> keys = new ArrayList<>();

    ThreatAlert(NpKit kit) {
        super(kit);
        setSize(W, H);
    }

    void part(String s, boolean key) {
        parts.add(s);
        keys.add(key);
    }

    @Override
    protected void paint(NpCanvas cv) {
        cv.fillRound(-2f, -2f, W + 4f, H + 4f, 3f, RIM, 1f);
        cv.fillRound(0f, 0f, W, H, 2f, NpColors.GLASS, 1f);
        cv.stroke(1f, 1f, W - 2f, H - 2f, 2f, NpColors.RED);
        cv.fillRound(6f, 6f, 46f, H - 12f, 1.5f, NpColors.RED, 1f);
        cv.fillRound(6f, 6f, 46f, 15f, 1.5f, NpColors.WHITE, 0.14f);
        cv.fillPoly(new float[] {29f, 15f, 41f, 41f, 29f, 34f, 17f, 41f}, NpColors.INK);
        cv.text(66f, 29f, CtxDraw.fit(cv, line1, W - 76f, 22f, true, 0.5f), 22f, true, NpColors.RED, 0.5f, NpCanvas.START);
        float px = 66f;
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i);
            if (keys.get(i)) px += CtxDraw.capKey(cv, px, 37f, p, 17f) + 6f;
            else px += cv.text(px, 50f, p, CtxDraw.B, true, NpColors.WHITE, 0.4f, NpCanvas.START) + 6f;
        }
    }
}
