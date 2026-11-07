package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;

/**
 * The emergency's veil (X3): while the placard is up, everything but the placard, the caution board, the
 * threat unit, the condition and suit units and the master cue dims, so the eye goes to the way out and
 * its evidence. It is painted after the units it dims and before those it leaves lit, a dark glaze over
 * each, with a hole where a dimmed pod carries the master cue.
 */
final class Veil extends YardPart {
    static final float ALPHA = 0.55f;
    private static final Color GLAZE = new Color(0.04f, 0.035f, 0.03f, 1f);

    final Array<YardPart> under = new Array<>();
    /** The master cue's place inside a dimmed pod, in stage units; w 0 for none. */
    float holeX, holeY, holeW, holeH;

    Veil() {
        super(0f, 0f);
    }

    @Override
    protected void draw(YardPaint p) {
        for (int i = 0; i < under.size; i++) {
            YardPart u = under.get(i);
            if (!u.visible) continue;
            float x0 = u.x - 2f, y0 = u.y - 2f, x1 = u.x + u.w + 2f, y1 = u.y + u.h + 2f;
            // A side unit's slide rail runs to the screen's edge, and a top unit's rods up to the header rail
            if (u.x <= ContextLayout.MARGIN + 1f) x0 = 0f;
            if (u.x + u.w >= w - ContextLayout.MARGIN - 1f) x1 = w;
            if (Math.abs(u.y - ContextLayout.TOP_Y) < 1f) y0 = ContextLayout.RAIL_H + 2f;
            glaze(p, x0, y0, x1 - x0, y1 - y0);
        }
    }

    /** A glaze over a box, leaving the hole clear where it falls inside. */
    private void glaze(YardPaint p, float x, float y, float w, float h) {
        boolean hole = holeW > 0f && holeX >= x && holeY >= y && holeX + holeW <= x + w && holeY + holeH <= y + h;
        if (!hole) {
            p.rect(x, y, w, h, GLAZE, ALPHA);
            return;
        }
        p.rect(x, y, w, holeY - y, GLAZE, ALPHA);
        p.rect(x, holeY + holeH, w, y + h - holeY - holeH, GLAZE, ALPHA);
        p.rect(x, holeY, holeX - x, holeH, GLAZE, ALPHA);
        p.rect(holeX + holeW, holeY, x + w - holeX - holeW, holeH, GLAZE, ALPHA);
    }

    @Override
    public boolean contains(float px, float py) {
        return false;
    }
}
