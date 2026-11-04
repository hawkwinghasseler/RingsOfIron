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
 * H1, the flight display (section 7), under the master cue's hood. Top row: the reference frame as
 * an FMA box (green: picked automatically), the speed in that frame, rotation assist. Bottom row:
 * the thrusters firing as a pictogram and a word, delta-v left, and the propellant's share on a
 * tape. Under warp it shrinks to the frame, delta-v and propellant.
 */
final class FlightDu extends NpDu {
    static final float W = 400f, H = 100f, WARP_H = 56f;
    private static final Color JET_OFF = NpColors.rgb(0x2A333D);

    boolean warpFace;
    String frame = "ORBIT";
    boolean frameAuto = true;
    float speed = Float.NaN;
    boolean assist = true;
    boolean main, retro, turnLeft, turnRight, strafePort, strafeStbd;
    String thrust = "NONE";
    boolean firing;
    /** Delta-v left, m/s: NaN unknown, infinite for free thrust. */
    double dv = Double.NaN;
    float prop = Float.NaN;

    FlightDu(NpKit kit) {
        super(kit);
        bezel = 10f;
        setSize(W, H);
    }

    void face(boolean warp) {
        warpFace = warp;
        setSize(W, warp ? WARP_H : H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f;
        float fw = Math.min(176f, Math.max(126f, NpHardware.boxWidth(cv, frame, CtxDraw.LB, 8f)));
        String ft = CtxDraw.fit(cv, frame, fw - 12f, CtxDraw.LB, true, 0.5f);
        if (warpFace) {
            NpHardware.box(cv, L, gy + 6f, fw, 24f, ft, frameAuto ? NpHardware.BoxState.ENGAGED : NpHardware.BoxState.NORMAL, CtxDraw.LB, false);
            float x = L + fw + 16f;
            x += CtxDraw.lab(cv, x, gy + 24f, "ΔV") + 8f;
            CtxDraw.val(cv, x, gy + 24f, dvText(), Double.isInfinite(dv) || Double.isNaN(dv) ? "" : "M/S", CtxDraw.V, NpColors.WHITE);
            prop(cv, R - 110f, R, gy + 14f, gy + 30f);
            return;
        }
        NpHardware.box(cv, L, gy + 4f, fw, 24f, ft, frameAuto ? NpHardware.BoxState.ENGAGED : NpHardware.BoxState.NORMAL, CtxDraw.LB, false);
        if (!Float.isNaN(speed)) {
            String[] s = NpFormat.speed(speed);
            CtxDraw.val(cv, L + fw + 14f, gy + 23f, s[0], s[1], 20f, NpColors.WHITE);
        }
        String at = assist ? "ASSIST" : "ASSIST OFF";
        float aw = Math.max(78f, NpHardware.boxWidth(cv, at, CtxDraw.LB, 6f));
        NpHardware.box(cv, R - aw, gy + 4f, aw, 24f, at, assist ? NpHardware.BoxState.ENGAGED : NpHardware.BoxState.CAUTION, CtxDraw.LB, false);
        cv.line(gx + 4f, gy + 36f, gx + gw - 4f, gy + 36f, 1f, NpColors.GRATICULE);
        jets(cv, L + 16f, gy + 58f);
        CtxDraw.lab(cv, L + 40f, gy + 53f, "THRUST");
        cv.text(L + 40f, gy + 71f, CtxDraw.fit(cv, thrust, 86f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, firing ? NpColors.WHITE : NpColors.DIM, 0.4f, NpCanvas.START);
        CtxDraw.lab(cv, L + 132f, gy + 53f, "ΔV");
        CtxDraw.val(cv, L + 132f, gy + 72f, dvText(), Double.isInfinite(dv) || Double.isNaN(dv) ? "" : "M/S", CtxDraw.V, NpColors.WHITE);
        prop(cv, L + 236f, R, gy + 53f, gy + 70f);
    }

    private String dvText() {
        if (Double.isInfinite(dv)) return "FREE";
        if (Double.isNaN(dv)) return "--";
        return NpFormat.mps(dv);
    }

    /** PROP: its share at the right, a tape under it with red and amber bands at the bottom of the scale. */
    private void prop(NpCanvas cv, float bx, float R, float labelY, float tapeY) {
        CtxDraw.lab(cv, bx, labelY, "PROP");
        if (Float.isNaN(prop)) {
            cv.text(R, labelY, "--", CtxDraw.B, true, NpColors.DIM, 0f, NpCanvas.END);
            return;
        }
        Color c = prop < 0.1f ? NpColors.RED : prop < 0.2f ? NpColors.AMBER : NpColors.WHITE;
        cv.text(R, labelY, NpFormat.percent(prop), CtxDraw.B, true, c, 0f, NpCanvas.END);
        NpInstruments.Tape t = new NpInstruments.Tape().band(0f, 0.1f, NpColors.RED).band(0.1f, 0.2f, NpColors.AMBER);
        t.pointer = c;
        NpInstruments.tape(cv, bx, tapeY, R - bx, prop, t);
    }

    /** The thrusters in a pictogram (kit.js {@code I.jets}): a small hull, nose up, each jet lit white while it fires. */
    private void jets(NpCanvas cv, float cx, float cy) {
        float k = 0.36f;
        float[] hull = {0f, -42f, 12f, -30f, 14f, 14f, 22f, 28f, 22f, 38f, -22f, 38f, -22f, 28f, -14f, 14f, -12f, -30f};
        for (int i = 0; i < hull.length; i += 2) {
            hull[i] = cx + hull[i] * k;
            hull[i + 1] = cy + hull[i + 1] * k;
        }
        cv.poly(hull, true, 1.26f, NpColors.DIM);
        jet(cv, cx, cy, -4f, 16f, -4f, 23f, main);
        jet(cv, cx, cy, 4f, 16f, 4f, 23f, main);
        jet(cv, cx, cy, 0f, -16f, 0f, -22f, retro);
        jet(cv, cx, cy, -6f, -9f, -12f, -9f, turnLeft);
        jet(cv, cx, cy, 6f, 9f, 12f, 9f, turnLeft);
        jet(cv, cx, cy, 6f, -9f, 12f, -9f, turnRight);
        jet(cv, cx, cy, -6f, 9f, -12f, 9f, turnRight);
        jet(cv, cx, cy, 7f, 1f, 13f, 1f, strafePort);
        jet(cv, cx, cy, -7f, 1f, -13f, 1f, strafeStbd);
    }

    private static void jet(NpCanvas cv, float cx, float cy, float x1, float y1, float x2, float y2, boolean on) {
        cv.line(cx + x1, cy + y1, cx + x2, cy + y2, on ? 2.5f : 1.5f, on ? NpColors.WHITE : JET_OFF);
    }
}
