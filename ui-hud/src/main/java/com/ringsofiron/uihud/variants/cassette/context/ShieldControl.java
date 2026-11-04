package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * F3, shield control at fire control's left foot, above the condition strip: where the shield is thin,
 * and which way it leans. A round green tube with the cloud as a ring round the ship, drawn thicker and
 * brighter where the cloud is thicker (the game measures it in four sectors, bow, port, stern and
 * starboard, and the ring blends between them), beside a plate with the shield's state on flaps, the mass
 * against capacity on drums over a bar, the trim on flaps and its rose, and the emitters: how many hold,
 * and the hottest one's heat against the trip. When the condition unit rises beside it, the ring shrinks
 * and the emitter row goes, so the two share the zone.
 */
final class ShieldControl extends YardPart {
    static final float W = 308f, H = 184f, H_SHORT = 90f;

    private final ContextFrame f;

    ShieldControl(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Its height in {@code room}: the full unit, or the short one beside a risen condition unit; 0 if neither fits. */
    static float measure(float room) {
        if (room >= H) return H;
        return room >= H_SHORT ? H_SHORT : 0f;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        ShieldStatus s = d.shieldStatus;
        boolean tall = h >= H - 1f;
        Kit.slideRail(p, -x, true, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, true);
        // The ring on its tube
        float r = tall ? 60f : 34f, cx = 8f + r + 8f, cy = h / 2f + 1f;
        p.roundTube(cx, cy, r, 5f);
        ring(p, s, cx, cy, r - 6f);
        ship(p, cx, cy, tall ? 1f : 0.6f);
        p.glass(cx - r, cy - r, 2f * r, r);
        // The plate
        float px = cx + r + 12f, pw = w - px - 8f;
        p.face(px, 8f, pw, h - 14f, Yard.Finish.GUN, false);
        float x0 = px + 8f, y = 14f;
        Color ink = Yard.INK_LIGHT;
        String st = ArmamentPlate.shields(s);
        Color stInk = st.equals("TRIPPED") || st.equals("STUNNED") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT;
        p.legend("SHIELDS", x0, y + 12f, ink);
        p.key(px + pw - 24f, y + 1f, Kit.key(d, "SHIELDS", "9"));
        if (tall) {
            y += 18f;
            p.flaps(x0, y, st, 8, 8.4f, 17f, 12.5f, stInk);
        } else {
            p.flaps(x0 + 58f, y, st, 8, 8.4f, 17f, 12.5f, stInk);
        }
        y += 26f;
        p.legend("MASS", x0, y + 13f, ink);
        float dw = p.drums(x0 + 38f, y, YardText.count(s.cloud, 3), 11f, 18f, 15f, false);
        p.legend("KG", x0 + 38f + dw + 4f, y + 13f, ink);
        if (tall) {
            y += 22f;
            p.level(x0, y, pw - 16f, 7f, s.share(), null, null);
            String of = "OF " + Math.round(s.capacity);
            Kit.legendR(p, of, x0 + pw - 16f, y + 20f, ink);
            y += 26f;
        } else {
            y += 24f;
        }
        p.legend("TRIM", x0, y + 12f, ink);
        p.key(x0 + 32f, y + 1f, Kit.key(d, "SHIELD_TRIM", "8"));
        p.flaps(x0 + 54f, y, s.trim.toUpperCase(Locale.ROOT).replace("STARBOARD", "STBD"), 5, 8.4f, 17f, 12.5f, Yard.FLAP_TEXT);
        if (!tall) return;
        y += 28f;
        // The emitters: how many hold, the hottest one's heat against the trip
        boolean tripped = s.emittersTripped > 0;
        Kit.lens(p, x0, y, 62f, 16f, tripped ? "TRIPPED" : "HOLDING", tripped ? Yard.RED_LAMP : Yard.GREEN_LAMP, s.emitters > 0 && (tripped || s.emittersWorking > 0));
        p.legend(s.emittersWorking + "/" + s.emitters, x0 + 70f, y + 12f, ink);
        p.level(x0, y + 22f, pw - 16f, 7f, MathUtils.clamp(s.hottest, 0f, 1f), new float[][]{{0.8f, 1f}}, new Color[]{Yard.ORANGE});
    }

    /** The cloud round the hull: 96 segments, each as thick and bright as the cloud there. */
    private static void ring(YardPaint p, ShieldStatus s, float cx, float cy, float r) {
        float max = 0f;
        for (float v : s.sectors) max = Math.max(max, v);
        float fill = s.share();
        boolean dead = s.emittersTripped > 0 && s.emittersWorking == 0;
        Color c = dead ? Yard.P_RED : Yard.P_GREEN;
        for (int i = 0; i < 96; i++) {
            // Clockwise from the bow, which is up
            float at = i * 3.75f, a0 = 90f - at, a1 = a0 - 2.7f;
            float k = sector(s, at, max);
            float b = s.raised ? 0.2f + 0.8f * k * fill : 0.1f;
            float wdt = 1.5f + 4f * k * (s.raised ? fill : 0.3f);
            p.line(cx + r * MathUtils.cosDeg(a0), cy - r * MathUtils.sinDeg(a0), cx + r * MathUtils.cosDeg(a1), cy - r * MathUtils.sinDeg(a1), wdt, c, b);
        }
    }

    /** The cloud at {@code deg} clockwise from the bow, as a share of the thickest sector, blended between sector centres. */
    static float sector(ShieldStatus s, float deg, float max) {
        if (max <= 0f) return 0f;
        // The sectors clockwise from the bow: bow, starboard, stern, port
        float[] cw = {s.sectors[0], s.sectors[3], s.sectors[2], s.sectors[1]};
        float u = ((deg % 360f) + 360f) % 360f / 90f;
        int i = (int) Math.floor(u) % 4, j = (i + 1) % 4;
        float t = u - (float) Math.floor(u);
        return MathUtils.clamp((cw[i] * (1f - t) + cw[j] * t) / max, 0f, 1f);
    }

    /** The ship in the middle of the ring, bow up. */
    private static void ship(YardPaint p, float cx, float cy, float k) {
        float[] pts = {cx, cy - 22f * k, cx + 9f * k, cy - 8f * k, cx + 9f * k, cy + 18f * k, cx - 9f * k, cy + 18f * k, cx - 9f * k, cy - 8f * k};
        p.polyline(pts, true, 1.2f, Yard.P_GDIM, 0.95f);
    }

    @Override
    public String hover(float px, float py) {
        HudData d = f.data;
        ShieldStatus s = d.shieldStatus;
        float lx = localX(px);
        if (lx < 150f) return "The cloud round the hull: thicker where the line is brighter · " + Kit.key(d, "SHIELD_TRIM", "8") + " leans it";
        if (s.emittersTripped > 0) return s.emittersTripped + " emitter" + (s.emittersTripped == 1 ? "" : "s") + " tripped: back when it cools";
        if (s.scattered > 1f || s.adrift > 1f) return Math.round(s.scattered) + " kg scattered, " + Math.round(s.adrift) + " kg adrift";
        return null;
    }
}
