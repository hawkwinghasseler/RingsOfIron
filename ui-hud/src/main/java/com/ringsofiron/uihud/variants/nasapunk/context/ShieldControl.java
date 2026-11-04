package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * F3, shield control (section 7), at fire control on a ship with an emitter: a ring of sectors
 * around the ship's outline, each as wide as the cloud there (a dead emitter's gap shows), and a
 * cyan arc outside the side the trim leans to. Beside it the cloud's mass against capacity,
 * RAISED or LOWERED, the TRIM, the emitters and the hottest one's heat against its trip.
 */
final class ShieldControl extends NpDu {
    static final float W = 308f, H = 186f;
    /** The sectors' bearings, by {@code ShieldStatusProvider.Sector}: bow, port, stern, starboard. */
    private static final float[] BEARING = {0f, 270f, 180f, 90f};

    final float[] sectors = new float[4];
    float cloud, capacity, heat;
    String raised = "RAISED", trim = "even", emitters = "";
    NpHardware.BoxState raisedBox = NpHardware.BoxState.ENGAGED;
    Color emitterColor = NpColors.WHITE;

    ShieldControl(NpKit kit) {
        super(kit);
        bezel = 6f;
        setSize(W, H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float cx = gx + 70f, cy = gy + gh / 2f, r = 50f;
        for (int i = 0; i < 4; i++) {
            // a sector's density against the cloud's mean: 1 is even, 0 bare
            float t = Math.max(0f, Math.min(1f, sectors[i] / 1.6f));
            float w = 2f + t * 8f;
            Color c = sectors[i] <= 0.05f ? NpColors.RED : sectors[i] < 0.5f ? NpColors.AMBER : NpColors.WHITE;
            cv.arc(cx, cy, r + w / 2f, BEARING[i] - 42f, BEARING[i] + 42f, w, c, 1f);
        }
        int lean = lean();
        if (lean >= 0) cv.arc(cx, cy, r + 17f, BEARING[lean] - 24f, BEARING[lean] + 24f, 2.5f, NpColors.CYAN, 1f);
        else cv.ring(cx, cy, 3f, 1.5f, NpColors.CYAN);
        // the ship, nose up, in outline
        cv.poly(new float[] {cx, cy - 30f, cx + 11f, cy - 16f, cx + 11f, cy + 24f, cx - 11f, cy + 24f, cx - 11f, cy - 16f}, true, 1.2f, NpColors.DIM);
        float X = gx + 152f, R = gx + gw - 8f;
        CtxDraw.lab(cv, X, gy + 20f, "CLOUD");
        CtxDraw.val(cv, X, gy + 44f, NpFormat.thousands(Math.round(cloud)), "OF " + NpFormat.thousands(Math.round(capacity)) + " KG", 20f, NpColors.WHITE);
        NpHardware.box(cv, X, gy + 56f, R - X, 24f, raised, raisedBox, CtxDraw.B, false);
        NpHardware.box(cv, X, gy + 86f, R - X, 24f, "TRIM  " + trimWord(), NpHardware.BoxState.SELECTED, CtxDraw.B, false);
        cv.line(X, gy + 122f, R, gy + 122f, 1f, NpColors.GRATICULE);
        CtxDraw.lab(cv, X, gy + 140f, "EMITTERS");
        cv.text(R, gy + 140f, emitters, CtxDraw.B, true, emitterColor, 0f, NpCanvas.END);
        CtxDraw.lab(cv, X, gy + 158f, "HEAT");
        Color hc = heat >= 0.85f ? NpColors.RED : heat >= 0.6f ? NpColors.AMBER : NpColors.WHITE;
        CtxDraw.valEnd(cv, R, gy + 158f, NpFormat.percent(heat), null, CtxDraw.V, hc);
        NpInstruments.Tape t = new NpInstruments.Tape();
        t.notch = 1f;
        t.notchColor = NpColors.RED;
        t.pointer = hc;
        NpInstruments.tape(cv, X + 44f, gy + 158f, R - X - 84f, heat, t);
    }

    private int lean() {
        switch (trim) {
            case "bow": return 0;
            case "port": return 1;
            case "stern": return 2;
            case "starboard": return 3;
            default: return -1;
        }
    }

    private String trimWord() {
        return "starboard".equals(trim) ? "STBD" : trim.toUpperCase(java.util.Locale.ROOT);
    }
}
