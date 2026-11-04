package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpInstrument;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * P5, the objective (section 6): a strip of glass with a kapton tab. The compact face is two
 * lines, the job and then where and the time left; the full face adds the progress and how many
 * more contracts are held. The first line is bold.
 */
final class ObjectiveSlip extends NpInstrument {
    static final float W = 308f, PITCH = 19f;

    final List<String> lines = new ArrayList<>();
    final List<Color> colors = new ArrayList<>();

    ObjectiveSlip(NpKit kit) {
        super(kit);
        setSize(W, 14f + PITCH);
    }

    void line(String s, Color c) {
        lines.add(s);
        colors.add(c);
    }

    float heightFor() {
        return 14f + Math.max(1, lines.size()) * PITCH;
    }

    @Override
    protected void paint(NpCanvas cv) {
        float w = getWidth(), h = getHeight();
        cv.fill(0f, 0f, w, h, CtxDraw.SLIP);
        cv.stroke(0f, 0f, w, h, 1f, CtxDraw.SLIP_EDGE);
        cv.fill(0f, 0f, 4f, h, NpColors.KAPTON);
        for (int i = 0; i < lines.size(); i++) {
            String s = CtxDraw.fit(cv, lines.get(i), w - 22f, CtxDraw.B, i == 0, 0.3f);
            cv.text(14f, 22f + i * PITCH, s, CtxDraw.B, i == 0, colors.get(i), 0.3f, NpCanvas.START);
        }
    }
}
