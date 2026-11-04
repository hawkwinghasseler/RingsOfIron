package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.FlightStatusProvider.Frame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * H1, the flight pod standing on the coaming under the ship: how fast against what, which way it is
 * pushing, how much it has left. The frame and its name on flaps, the master cue in the pod's top row
 * where the eye already is, the speed on drums, the thruster group (notched darts round a ship drawn nose
 * right, lit while firing), ASSIST OFF as a push-light, and the delta-v left on drums over the propellant
 * bar. Under warp it shrinks to the frame and the delta-v.
 */
final class FlightPod extends YardPart {
    static final float W = 400f, H = 100f, WARP_H = 64f;
    /** Where the master cue sits in the pod's top row, and its width. */
    static final float CUE_X = 206f, CUE_Y = 12f, CUE_W = 186f;

    private final ContextFrame f;
    private final Conditions c;
    private final Runnable acknowledge;
    boolean warpFace;

    FlightPod(ContextFrame f, Conditions c, Runnable acknowledge) {
        super(0f, 0f);
        this.f = f;
        this.c = c;
        this.acknowledge = acknowledge;
    }

    /** The frame tag on its flaps: LOCK, NAV, STATION, ORBIT. */
    static String frameTag(Frame fr) {
        switch (fr) {
            case TARGET: return "LOCK";
            case NAV: return "NAV";
            case STATION: return "STATION";
            case ORBIT: return "ORBIT";
            default: return "";
        }
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        FlightStatus fs = d.flightStatus;
        Kit.pod(p, 0f, 0f, w, h);
        p.face(6f, 9f, w - 12f, h - 13f, Yard.Finish.YARD, false);
        // The top row: the frame, its name, the master cue
        p.flaps(14f, 13f, frameTag(fs.frame), 7, 9f, 18f, 13f, Yard.FLAP_TEXT);
        p.flaps(14f + 7 * 9f + 6f, 13f, YardText.fit(fs.frameName, 15), 15, 8f, 18f, 13f, Yard.FLAP_TEXT);
        Kit.cue(p, CUE_X, CUE_Y, CUE_W, c.cue, "\\", f.time);
        float dvY = 46f;
        if (!warpFace) {
            // The speed in its frame, on drums ending at a fixed edge so the legend stays put
            float v = fs.speed;
            String s = Float.isNaN(v) ? YardText.blank(5, 0) : Math.abs(v) < 100f ? YardText.drums(v, 3, 1) : YardText.drums(v, 5, 0);
            p.drumsR(96f, 48f, s, 15f, 26f, 22f, 90f, true);
            p.legend("M/S", 102f, 66f, Yard.INK);
            // The thruster group
            Kit.legendC(p, "THRUST", 160f, 46f, Yard.INK);
            Kit.thrusters(p, 160f, 70f, 0.9f, false, fs.forward, fs.strafe, fs.turn, fs.mainEngine);
            p.push(196f, 50f, 46f, 30f, new String[]{"ASSIST", "OFF"}, Yard.AMBER_LAMP, !fs.rotationAssist, YardPaint.Kind.INDICATOR, false, false);
        }
        if (warpFace) deltaVRow(p, d, fs, 14f, 38f);
        else deltaV(p, d, fs, 256f, dvY);
    }

    /** The delta-v left on drums, its legend over them, and the propellant share as a bar under them. */
    private static void deltaV(YardPaint p, HudData d, FlightStatus fs, float x, float y) {
        p.legend("ΔV", x, y - 2f, Yard.INK);
        float dw = p.drums(x, y + 2f, dv(fs), 13f, 22f, 18f, true);
        p.legend("M/S", x + dw + 6f, y + 18f, Yard.INK);
        propellant(p, d, x, y + 30f, 124f);
    }

    /** The warp face's one row: the legend, the drums, and the bar beside them. */
    private static void deltaVRow(YardPaint p, HudData d, FlightStatus fs, float x, float y) {
        p.legend("ΔV", x, y + 16f, Yard.INK);
        float dw = p.drums(x + 24f, y, dv(fs), 13f, 20f, 17f, true);
        float mx = x + 24f + dw + 6f;
        p.legend("M/S", mx, y + 15f, Yard.INK);
        propellant(p, d, mx + 32f, y + 6f, 140f);
    }

    private static String dv(FlightStatus fs) {
        double left = fs.deltaVLeft;
        return Double.isNaN(left) || Double.isInfinite(left) ? YardText.blank(5, 0) : YardText.drums(left, 5, 0);
    }

    private static void propellant(YardPaint p, HudData d, float x, float y, float w) {
        float frac = Float.isNaN(d.shipStatus.propellant) ? 0f : d.shipStatus.propellant;
        Color low = frac < 0.2f ? Yard.ORANGE : Yard.MUSTARD;
        p.level(x, y, w, 8f, frac, new float[][]{{0f, 0.2f}}, new Color[]{low});
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
        if (overCue(lx, ly)) return c.cue.lit ? "Master " + (c.cue.warning ? "warning: " + Conditions.longName(c.cue.code) : "caution") + " · click or \\ to acknowledge" : null;
        if (ly < 34f && lx < CUE_X) {
            FlightStatus fs = f.data.flightStatus;
            return fs.frameName.isEmpty() ? null : "Speed against " + fs.frameName;
        }
        return null;
    }
}
