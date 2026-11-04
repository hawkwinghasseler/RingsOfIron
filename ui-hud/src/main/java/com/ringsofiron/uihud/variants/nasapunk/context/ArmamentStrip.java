package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;
import java.util.ArrayList;
import java.util.List;

/**
 * H7, the armament strip (section 7), under the lock card at the helm in combat: can I shoot, and
 * with what. GUNS and who fires them, MSL and what is left, and on a ship with an emitter SHIELDS
 * raised or lowered and the trim: at the helm this is where the shield settings live.
 */
final class ArmamentStrip extends NpDu {
    static final float W = 308f, P = 17.5f;

    /** Each row: its label, then its words with each word's colour. */
    final List<String> labels = new ArrayList<>();
    final List<String[]> words = new ArrayList<>();
    final List<Color[]> colors = new ArrayList<>();

    ArmamentStrip(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, 12f + 3f * P + 6f);
    }

    void row(String label, String[] w, Color[] c) {
        labels.add(label);
        words.add(w);
        colors.add(c);
    }

    float heightFor() {
        return 12f + 18f + (Math.max(1, labels.size()) - 1) * P + 7f;
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f, M = L + 52f;
        for (int i = 0; i < labels.size(); i++) {
            float yy = gy + 18f + i * P, x = M;
            CtxDraw.lab(cv, L, yy, labels.get(i));
            String[] w = words.get(i);
            Color[] c = colors.get(i);
            // the words run to the glass's right edge and no further: the last one that doesn't fit is cut
            for (int k = 0; k < w.length && x < R; k++) {
                if (k > 0) x += cv.text(x, yy, "·", CtxDraw.B, true, NpColors.DIM, 0f, NpCanvas.START) + 6f;
                String t = CtxDraw.fit(cv, w[k], R - x, CtxDraw.B, true, 0.3f);
                x += cv.text(x, yy, t, CtxDraw.B, true, c[k], 0.3f, NpCanvas.START) + 6f;
                if (!t.equals(w[k])) break;
            }
        }
    }
}
