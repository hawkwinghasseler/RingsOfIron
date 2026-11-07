package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * H3, the docking pod: it takes the flight pod's place in an approach, to fly into a port without
 * reading numbers. The port line on flaps (our port and its size, theirs, ARMED, the clearance); the
 * alignment dial (the ship's offset from the corridor and its turn); the closing gauge, its scale across
 * the top half, with an avocado bug at the safe speed for the distance and a red one at twice it, a FAST
 * or DANGER flag dropping into the face and the distance on a counter under the needle; the master cue,
 * the time to contact, the capture lamps and the helm's dock order; and a foot strip with the thruster
 * group, assist and the delta-v.
 */
final class DockingPod extends YardPart {
    static final float W = 400f, H = 196f;
    static final float CUE_X = 232f, CUE_Y = 44f, CUE_W = 160f;
    /** The closing gauge's full scale, m/s, and the alignment dial's, m. */
    private static final float SCALE = 12f, LATERAL = 10f;

    private final ContextFrame f;
    private final Conditions c;
    private final Runnable acknowledge;

    DockingPod(ContextFrame f, Conditions c, Runnable acknowledge) {
        super(0f, 0f);
        this.f = f;
        this.c = c;
        this.acknowledge = acknowledge;
    }

    static String clearance(DockingApproach k) {
        switch (k.clearance) {
            case GRANTED: return "GRANTED";
            case DENIED: return "REFUSED";
            case NO_PORT: return "NO PORT";
            case YIELDS: return "YIELDS";
            default: return k.armed ? "ASKED" : "NOT ASKED";
        }
    }

    /** Fast is over the safe speed for the distance, danger over twice it (the corridor's three states). */
    static int closingState(DockingApproach k) {
        if (Float.isNaN(k.closing) || Float.isNaN(k.safeSpeed) || k.safeSpeed <= 0f || k.closing <= k.safeSpeed) return 0;
        return k.closing > 2f * k.safeSpeed ? 2 : 1;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        DockingApproach k = d.docking;
        Kit.pod(p, 0f, 0f, w, h);
        p.face(6f, 9f, w - 12f, h - 13f, Yard.Finish.YARD, false);

        // The port line
        float ly = 14f;
        p.legend("PORT", 14f, ly + 13f, Yard.INK);
        String ours = k.ports > 1 ? k.port + "/" + k.ports + " " + k.portSize : k.portSize;
        p.flaps(48f, ly, YardText.fit(ours, 5), 5, 8f, 18f, 13f, Yard.FLAP_TEXT);
        p.legend("TO", 94f, ly + 13f, Yard.INK);
        p.flaps(114f, ly, YardText.fit(k.other, 9), 9, 8f, 18f, 13f, Yard.FLAP_TEXT);
        p.legend("#", 192f, ly + 13f, Yard.INK);
        p.drums(202f, ly, k.otherPort > 0 ? String.valueOf(k.otherPort) : " ", 11f, 18f, 15f, false);
        Kit.lens(p, 222f, ly + 1f, 50f, 16f, "ARMED", Yard.WHITE_LAMP, k.armed);
        p.legend("CLR", 280f, ly + 13f, Yard.INK);
        Color clrInk = k.clearance == Clearance.DENIED || k.clearance == Clearance.NO_PORT ? Yard.FLAP_AMBER : Yard.FLAP_TEXT;
        p.flaps(308f, ly, clearance(k), 10, 8.2f, 18f, 13f, clrInk);

        align(p, k, 62f, 98f, 44f);
        closing(p, k, 168f, 98f, 46f);

        // The right column: the cue, contact, capture, the helm's order
        Kit.cue(p, CUE_X, CUE_Y, CUE_W, c.cue, "\\", f.time);
        float ry = 76f;
        p.legend("CONTACT", 234f, ry + 14f, Yard.INK);
        boolean coming = !Float.isNaN(k.range) && !Float.isNaN(k.closing) && k.closing > 0.05f;
        String t = coming ? YardText.clock(k.range / k.closing) : " : ";
        p.legend("T-", 296f, ry + 14f, Yard.INK);
        p.drums(314f, ry, t, 11f, 20f, 15f, false);
        float cy = 116f;
        lampRow(p, 252f, cy, "HOLD", k.capture == Capture.CAPTURED || k.capture == Capture.DOCKED);
        lampRow(p, 308f, cy, "DRAW IN", k.capture == Capture.CAPTURED);
        lampRow(p, 364f, cy, "DOCKED", k.capture == Capture.DOCKED);
        boolean helmDocks = d.orderState.helm == HelmOrder.DOCK;
        Kit.lens(p, 236f, 140f, 152f, 16f, "HELM DOCKING", Yard.WHITE_LAMP, helmDocks);

        // The foot strip: the flight pod's thrusters, assist and delta-v
        FlightStatus fs = d.flightStatus;
        float sy = 164f;
        p.rrect(10f, sy, w - 20f, 26f, 3f, Yard.hex("2B2622"));
        Kit.thrusters(p, 54f, sy + 13f, 0.62f, true, fs.forward, fs.strafe, fs.turn, fs.mainEngine);
        Kit.lens(p, 96f, sy + 5f, 76f, 16f, "ASSIST OFF", Yard.AMBER_LAMP, !fs.rotationAssist);
        p.legend("ΔV", 232f, sy + 18f, Yard.INK_LIGHT);
        double left = fs.deltaVLeft;
        String dv = Double.isNaN(left) || Double.isInfinite(left) ? YardText.blank(5, 0) : YardText.drums(left, 5, 0);
        float dw = p.drums(254f, sy + 4f, dv, 11f, 18f, 15f, true);
        p.legend("M/S", 254f + dw + 6f, sy + 18f, Yard.INK_LIGHT);
    }

    private static void lampRow(YardPaint p, float cx, float cy, String name, boolean lit) {
        p.lamp(cx, cy, 5f, Yard.GREEN_LAMP, lit);
        Kit.legendC(p, name, cx, cy + 19f, Yard.INK);
    }

    /**
     * The alignment dial: the corridor a band down the middle, the ship a dart offset by its lateral error
     * (L and R as the pilot sees them) and turned by its angle; inside the band is lined up.
     */
    private static void align(YardPaint p, DockingApproach k, float cx, float cy, float r) {
        dialFace(p, cx, cy, r);
        p.rect(cx - 6f, cy - r + 6f, 12f, 2f * r - 12f, Yard.hex("B9C4A0"));
        p.rect(cx - 0.6f, cy - r + 4f, 1.2f, 2f * r - 8f, Yard.INK, 0.5f);
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            float tx = cx + i * (r - 8f) / 4f;
            p.rect(tx - 0.5f, cy - 3f, 1f, i % 2 == 0 ? 7f : 4f, Yard.INK);
        }
        p.text(Face.LEGEND, 12f, "L", cx - r + 8f, cy + 18f, Yard.INK);
        p.textR(Face.LEGEND, 12f, "R", cx + r - 8f, cy + 18f, Yard.INK);
        float lat = Float.isNaN(k.lateral) ? 0f : MathUtils.clamp(k.lateral / LATERAL, -1f, 1f);
        float ang = Float.isNaN(k.angleDeg) ? 0f : MathUtils.clamp(k.angleDeg, -45f, 45f);
        float sx = cx + lat * (r - 12f), sy = cy;
        // The ship: a dart pointing up the corridor, turned by its error (clockwise on screen for positive)
        float c = MathUtils.cosDeg(ang), s = MathUtils.sinDeg(ang);
        float[] pts = {0f, -12f, 6f, 8f, 0f, 4f, -6f, 8f};
        float[] out = new float[pts.length];
        for (int i = 0; i < pts.length; i += 2) {
            out[i] = sx + pts[i] * c - pts[i + 1] * s;
            out[i + 1] = sy + pts[i] * s + pts[i + 1] * c;
        }
        boolean lined = Math.abs(lat) < 0.15f && Math.abs(ang) < 3f;
        Color hull = lined ? Yard.AVOCADO : Yard.ORANGE;
        p.fillConvex(new float[]{out[0], out[1], out[2], out[3], out[4], out[5]}, hull, 1f);
        p.fillConvex(new float[]{out[0], out[1], out[4], out[5], out[6], out[7]}, hull, 1f);
        p.glass(cx - r, cy - r, 2f * r, r);
        Kit.legendC(p, "ALIGN", cx, cy + r + 15f, Yard.INK);
    }

    /**
     * The closing gauge: 0 to 12 m/s over the top half, the needle at the closing speed, the safe speed's
     * avocado bug and twice it in red; under the hub the flag (FAST, DANGER) and the distance counter.
     */
    private static void closing(YardPaint p, DockingApproach k, float cx, float cy, float r) {
        dialFace(p, cx, cy, r);
        for (int i = 0; i <= 12; i++) {
            float a = 180f - i * 15f;
            float c = MathUtils.cosDeg(a), s = -MathUtils.sinDeg(a);
            float in = i % 6 == 0 ? r - 11f : i % 2 == 0 ? r - 8f : r - 5f;
            p.line(cx + c * in, cy + s * in, cx + c * (r - 2f), cy + s * (r - 2f), i % 6 == 0 ? 1.6f : 1f, Yard.INK);
        }
        p.text(Face.LEGEND, 12f, "0", cx - r + 12f, cy + 4f, Yard.INK);
        p.textC(Face.LEGEND, 12f, "6", cx, cy - r + 24f, Yard.INK);
        p.textR(Face.LEGEND, 12f, "12", cx + r - 11f, cy + 4f, Yard.INK);
        if (!Float.isNaN(k.safeSpeed) && k.safeSpeed > 0f) {
            bug(p, cx, cy, r, k.safeSpeed, Yard.AVOCADO);
            bug(p, cx, cy, r, 2f * k.safeSpeed, Yard.RED);
        }
        int state = closingState(k);
        if (state > 0) {
            float fx = cx - 22f, fy = cy + 6f;
            p.rect(fx, fy, 44f, 14f, state == 2 ? Yard.RED : Yard.MUSTARD);
            p.hazard(fx, fy, 6f, 14f);
            p.hazard(fx + 38f, fy, 6f, 14f);
            p.textC(Face.LEGEND, 12f, state == 2 ? "DANGER" : "FAST", cx, fy + 11f, state == 2 ? Yard.INK_LIGHT : Yard.INK);
        }
        String range = Float.isNaN(k.range) ? "   " : YardText.count(Math.min(9999f, k.range), Math.min(9999f, k.range) >= 1000f ? 4 : 3);
        float dw = YardPaint.drumWidth(range, 10f);
        p.drums(cx - dw / 2f - 4f, cy + 23f, range, 10f, 16f, 14f, false);
        p.text(Face.LEGEND, 12f, "M", cx + dw / 2f, cy + 36f, Yard.INK);
        float v = Float.isNaN(k.closing) ? 0f : MathUtils.clamp(k.closing, 0f, SCALE);
        float a = 180f - v / SCALE * 180f;
        float c = MathUtils.cosDeg(a), s = -MathUtils.sinDeg(a);
        p.line(cx, cy, cx + c * (r - 6f), cy + s * (r - 6f), 1.8f, Yard.ORANGE);
        p.circle(cx, cy, 3.4f, Yard.INK);
        p.glass(cx - r, cy - r, 2f * r, r);
        Kit.legendC(p, "CLOSING M/S", cx, cy + r + 15f, Yard.INK);
    }

    private static void bug(YardPaint p, float cx, float cy, float r, float v, Color c) {
        if (v > SCALE) return;
        float a = 180f - v / SCALE * 180f;
        float co = MathUtils.cosDeg(a), s = -MathUtils.sinDeg(a);
        float bx = cx + co * (r + 1f), by = cy + s * (r + 1f);
        p.circle(bx, by, 4.2f, Yard.BAKELITE);
        p.circle(bx, by, 3.2f, c);
    }

    private static void dialFace(YardPaint p, float cx, float cy, float r) {
        p.circle(cx, cy, r + 4f, Yard.BAKELITE);
        p.circle(cx, cy, r + 1.5f, Yard.ALU);
        p.circle(cx, cy, r, Kit.SCALE);
    }

    @Override
    public boolean pressable(float px, float py) {
        return c.cue.lit && overCue(localX(px), localY(py));
    }

    @Override
    public boolean press(float px, float py) {
        if (!pressable(px, py)) return false;
        acknowledge.run();
        return true;
    }

    private static boolean overCue(float lx, float ly) {
        return lx >= CUE_X && lx <= CUE_X + CUE_W && ly >= CUE_Y && ly <= CUE_Y + 24f;
    }

    @Override
    public String hover(float px, float py) {
        float lx = localX(px), ly = localY(py);
        DockingApproach k = f.data.docking;
        if (overCue(lx, ly)) return c.cue.lit ? "Master " + (c.cue.warning ? "warning: " + Conditions.longName(c.cue.code) : "caution") + " · click or \\ to acknowledge" : null;
        if (ly < 36f && lx > 276f && !k.refusal.isEmpty()) return k.refusal;
        if (ly > 40f && ly < 160f && lx > 118f && lx < 218f && !Float.isNaN(k.safeSpeed)) {
            return "Closing " + Kit.speed(k.closing) + " · safe " + Kit.speed(k.safeSpeed) + " at " + Kit.dist(k.range);
        }
        return null;
    }
}
