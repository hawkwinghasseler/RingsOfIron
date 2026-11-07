package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardSymbols;

import java.util.Locale;

/**
 * H5, the contact scope at the bottom right: what is around the ship, and which way. A round green tube
 * in a square pod, north up like the view: contacts by allegiance, the lock's bracket and the nav mark,
 * missiles as their own darts, the ship's heading, two range rings; contacts beyond the range are ticks on
 * the rim. The range is on flaps between − and + keys: automatic by situation (2 km in an approach, the
 * smallest step that holds the lock and every inbound missile in combat, 50 km in cruise), and set by hand
 * until the situation changes. In cruise with nothing inside 50 km it folds to a SCOPE key with the count.
 */
final class Scope extends YardPart {
    static final float W = 200f, H = 200f, BUTTON = 48f;
    /** The range steps, km (RadarScope's, with a 5 km step for fights). */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    static final int APPROACH = 1, CRUISE = 4;

    private final ContextFrame f;
    /** A step set by hand, -1 for automatic; it holds until the situation changes. */
    private int manual = -1;
    private String situation = "";
    /** Opened by hand from its folded key, until the situation changes. */
    private boolean opened;
    boolean folded;
    int step = CRUISE;

    Scope(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** Picks the range for this frame, and whether the scope folds (cruise with nothing inside 50 km). */
    void update(HudData d) {
        String s = f.approach ? "approach" : f.combat ? "combat" : "cruise";
        if (!s.equals(situation)) {
            situation = s;
            manual = -1;
            opened = false;
        }
        int auto = f.approach ? APPROACH : f.combat ? autoStep(d) : CRUISE;
        step = manual >= 0 ? manual : auto;
        folded = !f.approach && !f.combat && !opened && nearest(d) > STEPS[CRUISE] * 1000f;
    }

    /** The smallest step that holds the lock and every inbound missile; else the nearest hostile. */
    static int autoStep(HudData d) {
        float need = 0f;
        boolean any = false;
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            Contact c = d.contactFrame.contacts.get(i);
            if (Float.isNaN(c.range)) continue;
            if (c.locked || c.kind == Kind.MISSILE && c.threat) {
                need = Math.max(need, c.range);
                any = true;
            }
        }
        if (!any) {
            float hostile = Float.NaN;
            for (int i = 0; i < d.contactFrame.contacts.size; i++) {
                Contact c = d.contactFrame.contacts.get(i);
                if (!Float.isNaN(c.range) && c.relation == Relation.HOSTILE && (Float.isNaN(hostile) || c.range < hostile)) hostile = c.range;
            }
            need = Float.isNaN(hostile) ? 0f : hostile;
        }
        for (int i = 0; i < STEPS.length; i++) if (need <= STEPS[i] * 1000f * 0.9f) return i;
        return STEPS.length - 1;
    }

    /** The nearest contact's range, metres; infinite with none. */
    static float nearest(HudData d) {
        float best = Float.POSITIVE_INFINITY;
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            Contact c = d.contactFrame.contacts.get(i);
            if (c.kind == Kind.WAYPOINT || Float.isNaN(c.range)) continue;
            best = Math.min(best, c.range);
        }
        return best;
    }

    static int count(HudData d) {
        int n = 0;
        for (int i = 0; i < d.contactFrame.contacts.size; i++) if (d.contactFrame.contacts.get(i).kind != Kind.WAYPOINT) n++;
        return n;
    }

    static String label(int step) {
        float km = STEPS[step];
        return (km < 1f ? String.format(Locale.ROOT, "%.1f", km) : String.valueOf((int) km)) + " KM";
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        if (folded) {
            Kit.pod(p, 0f, 0f, w, h);
            p.textC(Face.LEGEND, 12f, "SCOPE", w / 2f, 22f, Yard.INK_LIGHT);
            p.drums(w / 2f - 6f, 26f, String.valueOf(Math.min(9, count(d))), 12f, 16f, 14f, false);
            return;
        }
        Kit.pod(p, 0f, 0f, w, h);
        float cx = w / 2f, cy = 92f, r = 76f;
        p.roundTube(cx, cy, r, 6f);
        float range = STEPS[step] * 1000f;
        Color g = Yard.P_GREEN, gd = Yard.P_GDIM;
        ring(p, cx, cy, r - 1f, gd, 0.9f);
        ring(p, cx, cy, r / 2f, gd, 0.7f);
        for (int i = 0; i < 12; i++) {
            float a = i * 30f, c = MathUtils.cosDeg(a), s = MathUtils.sinDeg(a);
            p.line(cx + c * (r - 6f), cy - s * (r - 6f), cx + c * (r - 1f), cy - s * (r - 1f), 1f, gd, 0.8f);
        }
        float hd = d.flightStatus.present && !Float.isNaN(d.flightStatus.heading) ? d.flightStatus.heading : 90f;
        // The ship's heading: a line out from the centre
        p.line(cx, cy, cx + MathUtils.cosDeg(hd) * r * 0.35f, cy - MathUtils.sinDeg(hd) * r * 0.35f, 1.2f, g, 0.7f);
        YardSymbols.rel(p, YardSymbols.Rel.OWN, cx, cy, 4f, g, 90f - hd, false, 1.2f, YardSymbols.State.ACTIVE, false);
        for (int i = 0; i < d.contactFrame.contacts.size; i++) {
            Contact c = d.contactFrame.contacts.get(i);
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg) || c.kind == Kind.WAYPOINT) continue;
            float abs = hd + c.bearingDeg, co = MathUtils.cosDeg(abs), sn = MathUtils.sinDeg(abs);
            Color col = c.threat ? Yard.P_RED : c.kind == Kind.STATION || c.kind == Kind.DERELICT ? gd : g;
            if (c.range > range) {
                // Beyond the range: a tick on the rim where it lies
                p.line(cx + co * (r - 7f), cy - sn * (r - 7f), cx + co * (r + 1f), cy - sn * (r + 1f), 2f, col, 0.95f);
                continue;
            }
            float k = Math.min(c.range / range, 0.97f);
            float px = cx + k * r * co, py = cy - k * r * sn;
            if (c.kind == Kind.MISSILE) YardSymbols.missile(p, px, py, c.threat ? 180f - abs : -abs, col, 0.7f);
            else if (c.kind == Kind.STATION) YardSymbols.station(p, px, py, 3f, col, 1.2f);
            else if (c.kind == Kind.EVA) p.circle(px, py, 1.6f, g);
            else YardSymbols.rel(p, rel(c), px, py, 3.4f, col, 0f, c.relation == Relation.HOSTILE, 1.2f, state(c), false);
            if (c.locked) YardSymbols.bracket(p, px, py, 8f, g, 1.1f);
            if (c.nav) YardSymbols.navMark(p, px, py, g, 1.1f);
        }
        p.glass(cx - r, cy - r, 2f * r, r);
        // The range between its keys
        p.keyButton(10f, h - 30f, 24f, 24f, "−");
        p.keyButton(w - 34f, h - 30f, 24f, 24f, "+");
        p.flaps(w / 2f - 30f, h - 27f, label(step), 7, 8.6f, 18f, 13f, Yard.FLAP_TEXT);
    }

    private static void ring(YardPaint p, float cx, float cy, float r, Color c, float a) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            float a0 = i * 360f / n, a1 = (i + 1) * 360f / n;
            p.line(cx + r * MathUtils.cosDeg(a0), cy + r * MathUtils.sinDeg(a0), cx + r * MathUtils.cosDeg(a1), cy + r * MathUtils.sinDeg(a1), 0.9f, c, a);
        }
    }

    private static YardSymbols.Rel rel(Contact c) {
        if (c.kind == Kind.DERELICT) return YardSymbols.Rel.INERT;
        switch (c.relation) {
            case HOSTILE: return YardSymbols.Rel.HOSTILE;
            case FRIENDLY: return YardSymbols.Rel.FRIENDLY;
            default: return YardSymbols.Rel.NEUTRAL;
        }
    }

    private static YardSymbols.State state(Contact c) {
        switch (c.state) {
            case DISABLED: return YardSymbols.State.DISABLED;
            case STRUCK:
            case SILENCED: return YardSymbols.State.STRUCK;
            case WRECKED: return YardSymbols.State.WRECKED;
            default: return YardSymbols.State.ACTIVE;
        }
    }

    @Override
    public boolean pressable(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (folded) return true;
        return ly >= h - 32f && (lx <= 38f || lx >= w - 38f);
    }

    @Override
    public boolean press(float px, float py) {
        float lx = localX(px), ly = localY(py);
        if (folded) {
            opened = true;
            return true;
        }
        if (ly < h - 32f) return false;
        if (lx <= 38f) manual = Math.max(0, step - 1);
        else if (lx >= w - 38f) manual = Math.min(STEPS.length - 1, step + 1);
        else return false;
        return true;
    }

    @Override
    public String hover(float px, float py) {
        if (folded) return count(f.data) + " contacts, none within 50 km · click to open the scope";
        float ly = localY(py);
        if (ly >= h - 32f) return manual >= 0 ? "Range set by hand until the situation changes" : "Range chosen for the situation · − and + set it";
        return null;
    }
}
