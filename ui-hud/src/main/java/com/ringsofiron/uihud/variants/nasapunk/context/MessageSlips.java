package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * P4, the message feed (section 6): up to three comm-loop slips, newest on top, each a source tag
 * in its box and then the message in sentence case (speech and events are prose; instruments are
 * capitals), with a repeat's count at the end. A notable message's tag is white, a routine one's dim.
 */
final class MessageSlips extends NpInstrument {
    static final float W = 308f, PITCH = 24f, SLIP_H = 21f;

    static final class Slip {
        String tag = "", msg = "", count;
        boolean notable;
    }

    final List<Slip> slips = new ArrayList<>();

    MessageSlips(NpKit kit) {
        super(kit);
        setSize(W, PITCH);
    }

    float heightFor() {
        return Math.max(1, slips.size()) * PITCH;
    }

    @Override
    protected void paint(NpCanvas cv) {
        for (int i = 0; i < slips.size(); i++) slip(cv, slips.get(i), i * PITCH);
    }

    private void slip(NpCanvas cv, Slip s, float y) {
        float w = getWidth();
        cv.fill(0f, y, w, SLIP_H, CtxDraw.SLIP);
        Color tagC = s.notable ? NpColors.WHITE : NpColors.DIM;
        float tagW = cv.width(s.tag, CtxDraw.LB, true, 0.6f) + 10f, th = 16f;
        cv.stroke(4f, y + SLIP_H / 2f - th / 2f, tagW, th, 1f, tagC);
        cv.text(4f + tagW / 2f, y + SLIP_H / 2f + 4.4f, s.tag, CtxDraw.LB, true, tagC, 0.6f, NpCanvas.MIDDLE);
        float mx = tagW + 12f, countW = s.count == null ? 0f : cv.width(s.count, CtxDraw.LB, true, 0f) + 10f;
        String msg = CtxDraw.fit(cv, s.msg, w - mx - 6f - countW, CtxDraw.B, false, 0f);
        cv.text(mx, y + SLIP_H / 2f + 4.9f, msg, CtxDraw.B, false, NpColors.WHITE, 0f, NpCanvas.START);
        if (s.count != null) cv.text(w - 6f, y + SLIP_H / 2f + 4.4f, s.count, CtxDraw.LB, true, NpColors.DIM, 0f, NpCanvas.END);
    }
}
