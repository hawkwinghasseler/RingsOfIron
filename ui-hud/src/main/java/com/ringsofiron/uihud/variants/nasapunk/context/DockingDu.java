package com.ringsofiron.uihud.variants.nasapunk.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpDu;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpKit;

/**
 * H3, the docking display (section 7), in H1's place under the hood. The port line (ours, theirs,
 * armed or cleared, with the G and H keys); the alignment picture (lateral offset as the bar's
 * place, angle error as its tilt, green within tolerance); the closing gauge, the approach's only
 * range and closing speed on screen, against the safe speed for the range with the safe, fast and
 * danger bands; time to contact; and along the foot H1's thrust, assist and delta-v, with the
 * collar's state or the helm's dock order.
 */
final class DockingDu extends NpDu {
    static final float W = 400f, H = 160f;
    /** The alignment picture's full scale, m, and what counts as lined up, m and degrees. */
    private static final float LAT_SCALE = 10f, LAT_OK = 1f, ANGLE_OK = 5f, LAT_FAR = 5f, ANGLE_FAR = 20f;

    String port = "", other = "", status = "", keyArm, keyPort, capture = "";
    Color statusColor = NpColors.DIM, captureColor = NpColors.GREEN;
    float range = Float.NaN, lateral = Float.NaN, angle = Float.NaN, closing = Float.NaN, safe = Float.NaN;
    String thrust = "NONE", dv = "--";
    boolean firing, assist = true;

    DockingDu(NpKit kit) {
        super(kit);
        bezel = 10f;
        setSize(W, H);
    }

    @Override
    protected void glass(NpCanvas cv, float gx, float gy, float gw, float gh) {
        float L = gx + 8f, R = gx + gw - 8f;
        portLine(cv, gx, gy, gw, L, R);
        cv.line(gx + 4f, gy + 28f, gx + gw - 4f, gy + 28f, 1f, NpColors.GRATICULE);
        alignment(cv, L, gy + 36f, 92f, 64f);
        gauge(cv, L + 108f, R, gy);
        cv.line(gx + 4f, gy + 108f, gx + gw - 4f, gy + 108f, 1f, NpColors.GRATICULE);
        foot(cv, L, R, gy + 127f);
    }

    private void portLine(NpCanvas cv, float gx, float gy, float gw, float L, float R) {
        float yy = gy + 19f;
        float sw = status.isEmpty() ? 0f : NpHardware.boxWidth(cv, status, CtxDraw.LB, 6f), sx = R - sw;
        if (sw > 0f) {
            NpHardware.BoxState st = statusColor == NpColors.GREEN ? NpHardware.BoxState.ENGAGED
                    : statusColor == NpColors.AMBER ? NpHardware.BoxState.CAUTION
                    : statusColor == NpColors.WHITE ? NpHardware.BoxState.NORMAL : NpHardware.BoxState.OFF;
            NpHardware.box(cv, sx, gy + 6f, sw, 18f, status, st, CtxDraw.LB, false);
        }
        float kx = sx - 8f;
        if (keyPort != null) kx -= CtxDraw.capW(cv, keyPort);
        if (keyPort != null) CtxDraw.capKey(cv, kx, gy + 6f, keyPort, 18f);
        if (keyArm != null) {
            kx -= CtxDraw.capW(cv, keyArm) + 4f;
            CtxDraw.capKey(cv, kx, gy + 6f, keyArm, 18f);
        }
        float x = L + cv.text(L, yy, port, CtxDraw.B, true, NpColors.WHITE, 0.3f, NpCanvas.START) + 8f;
        if (!other.isEmpty()) cv.text(x, yy, CtxDraw.fit(cv, "→ " + other, kx - 8f - x, CtxDraw.B, false, 0.3f), CtxDraw.B, false, NpColors.WHITE, 0.3f, NpCanvas.START);
    }

    /** The two-axis picture: a frame with the centreline; the port as a bar at the lateral offset, tilted by the angle error. */
    private void alignment(NpCanvas cv, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f;
        cv.stroke(x, y, w, h, 1f, NpColors.GRATICULE);
        cv.line(cx, y + 3f, cx, y + h - 3f, 1f, NpColors.GRATICULE);
        cv.line(x + 3f, cy, x + w - 3f, cy, 1f, NpColors.GRATICULE);
        float tol = LAT_OK / LAT_SCALE * (w / 2f - 6f);
        cv.stroke(cx - tol, cy - 10f, 2f * tol, 20f, 1f, NpColors.DIM);
        if (Float.isNaN(lateral) || Float.isNaN(angle)) {
            cv.text(cx, cy + 4f, "--", CtxDraw.LB, true, NpColors.DIM, 0f, NpCanvas.MIDDLE);
            return;
        }
        boolean ok = Math.abs(lateral) <= LAT_OK && Math.abs(angle) <= ANGLE_OK;
        boolean far = Math.abs(lateral) > LAT_FAR || Math.abs(angle) > ANGLE_FAR;
        Color c = ok ? NpColors.GREEN : far ? NpColors.AMBER : NpColors.WHITE;
        float px = cx + MathUtils.clamp(lateral / LAT_SCALE, -1f, 1f) * (w / 2f - 6f);
        float a = MathUtils.clamp(angle, -60f, 60f) * MathUtils.degreesToRadians, dx = MathUtils.sin(a) * 18f, dy = MathUtils.cos(a) * 18f;
        cv.line(px - dx, cy - dy, px + dx, cy + dy, 2.5f, c);
        cv.disc(px, cy, 6f, c);
    }

    private void gauge(NpCanvas cv, float x0, float x1, float gy) {
        float yy = gy + 48f;
        float rx = x0 + CtxDraw.lab(cv, x0, yy, "RNG") + 6f;
        if (!Float.isNaN(range)) {
            String[] r = NpFormat.range(range);
            CtxDraw.val(cv, rx, yy, r[0], r[1], CtxDraw.V, NpColors.WHITE);
        }
        boolean known = !Float.isNaN(closing) && !Float.isNaN(safe) && safe > 0f;
        Color cc = !known ? NpColors.WHITE : closing > 2f * safe ? NpColors.RED : closing > safe ? NpColors.AMBER : NpColors.WHITE;
        if (!Float.isNaN(closing)) {
            String[] s = NpFormat.speed(Math.abs(closing));
            float vx = CtxDraw.valEnd(cv, x1, yy, s[0], s[1], CtxDraw.V, cc);
            CtxDraw.labEnd(cv, vx - 6f, yy, closing >= 0f ? "CLOSING" : "OPENING");
        }
        float ty = gy + 68f, tw = x1 - x0;
        if (known) {
            float max = Math.max(2.5f * safe, Math.max(0f, closing) * 1.1f);
            NpInstruments.Tape t = new NpInstruments.Tape().band(0f, safe / max, NpColors.GREEN)
                    .band(safe / max, Math.min(1f, 2f * safe / max), NpColors.AMBER).band(Math.min(1f, 2f * safe / max), 1f, NpColors.RED);
            t.pointer = cc;
            NpInstruments.tape(cv, x0, ty, tw, Math.max(0f, closing) / max, t);
            float sx = x0 + tw * safe / max;
            cv.line(sx, ty - 8f, sx, ty + 7f, 1.5f, NpColors.GREEN);
            cv.text(sx, ty + 19f, "SAFE " + NpFormat.tenths(safe), CtxDraw.LB, true, NpColors.DIM, 0.3f, NpCanvas.MIDDLE);
        } else {
            cv.line(x0, ty, x1, ty, 1.5f, NpColors.GRATICULE);
        }
        float ly = gy + 101f;
        if (!Float.isNaN(lateral)) {
            float lx = x0 + CtxDraw.lab(cv, x0, ly, "LAT") + 5f;
            lx += CtxDraw.val(cv, lx, ly, NpFormat.tenths(Math.abs(lateral)), "M", CtxDraw.B, NpColors.WHITE) + 10f;
            if (!Float.isNaN(angle)) {
                lx += CtxDraw.lab(cv, lx, ly, "ANG") + 5f;
                CtxDraw.val(cv, lx, ly, Math.round(Math.abs(angle)) + "°", null, CtxDraw.B, NpColors.WHITE);
            }
        }
        if (known && closing > 0.05f && !Float.isNaN(range)) {
            float vx = CtxDraw.valEnd(cv, x1, ly, NpFormat.tMinus(range / closing), null, CtxDraw.B, NpColors.WHITE);
            CtxDraw.labEnd(cv, vx - 6f, ly, "CONTACT");
        }
    }

    private void foot(NpCanvas cv, float L, float R, float yy) {
        float x = L + CtxDraw.lab(cv, L, yy, "THRUST") + 6f;
        cv.text(x, yy, CtxDraw.fit(cv, thrust, 70f, CtxDraw.B, true, 0.4f), CtxDraw.B, true, firing ? NpColors.WHITE : NpColors.DIM, 0.4f, NpCanvas.START);
        // the collar's state, or the helm flying a dock order, takes assist's place: neither is the captain's to fly then
        if (!capture.isEmpty()) cv.text(L + 150f, yy, CtxDraw.fit(cv, capture, 84f, CtxDraw.LB, true, 0.4f), CtxDraw.LB, true, captureColor, 0.4f, NpCanvas.START);
        else cv.text(L + 150f, yy, assist ? "ASSIST" : "ASSIST OFF", CtxDraw.LB, true, assist ? NpColors.GREEN : NpColors.AMBER, 0.5f, NpCanvas.START);
        float dx = L + 244f;
        dx += CtxDraw.lab(cv, dx, yy, "ΔV") + 6f;
        CtxDraw.val(cv, dx, yy, dv, "--".equals(dv) || "FREE".equals(dv) ? "" : "M/S", CtxDraw.B, NpColors.WHITE);
    }
}
