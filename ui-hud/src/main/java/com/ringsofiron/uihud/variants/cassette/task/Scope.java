package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardSymbols;
import com.ringsofiron.uihud.variants.cassette.YardSymbols.Rel;
import com.ringsofiron.uihud.variants.cassette.YardSymbols.State;

import java.util.Locale;

/**
 * Z7's corner, the scope (SNS): a round green tube in a square pod, north up like the view. Contacts are
 * shapes by relation (green, red when they threaten), the locked target bracketed and the navigation
 * target in square brackets; contacts beyond the range sit on the rim. The range is automatic (the AUTO
 * lamp lit) until the minus or plus key sets it by hand, which shows on amber flaps until the post changes.
 */
final class Scope extends YardPart {
    static final float W = 206f, H = 206f;
    /** The range steps, km: 5 km is where fights are, the coilgun's reach. */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    private static final float CX = 1177f, CY = 630f, R = 60f;

    private final TaskFrame f;
    Yard.Finish finish = Yard.Finish.YARD;
    boolean brown;
    /** The step set by hand, or -1 for automatic. */
    private int manual = -1;
    private int step = 2;

    Scope(TaskFrame f) {
        super(1074f, 556f);
        this.f = f;
    }

    /** Back to automatic: the post changed. */
    void resetRange() {
        manual = -1;
    }

    /** Picks the step: by hand, or the smallest that holds the threats and the target with room to spare. */
    void chooseRange() {
        step = manual >= 0 ? manual : autoStep(f.data);
    }

    static int autoStep(HudData d) {
        float need = 0f;
        boolean any = false;
        for (ThreatProvider.MissileTrack m : d.threatFrame.missiles) {
            if (m.side != ThreatProvider.Side.INCOMING || Float.isNaN(m.range)) continue;
            need = Math.max(need, m.range);
            any = true;
        }
        if (d.targetStatus.present && !Float.isNaN(d.targetStatus.range)) {
            need = Math.max(need, d.targetStatus.range);
            any = true;
        }
        if (!any) {
            float hostile = Float.NaN, nearest = Float.NaN;
            for (Contact c : d.contactFrame.contacts) {
                if (Float.isNaN(c.range)) continue;
                if (c.relation == Relation.HOSTILE && (Float.isNaN(hostile) || c.range < hostile)) hostile = c.range;
                if (Float.isNaN(nearest) || c.range < nearest) nearest = c.range;
            }
            need = !Float.isNaN(hostile) ? hostile : !Float.isNaN(nearest) ? nearest : 0f;
        }
        for (int i = 0; i < STEPS.length; i++) if (need <= STEPS[i] * 1000f * 0.8f) return i;
        return STEPS.length - 1;
    }

    boolean auto() {
        return manual < 0;
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        p.unit(1074f, 556f, W, H, finish, "rb");
        p.roundTube(CX, CY, R, 6f);
        float a = brown ? 0.5f : 1f;
        ring(p, R * 0.5f, a);
        ring(p, R - 2f, a);
        for (int i = 0; i < 12; i++) {
            float an = i * 30f, s = MathUtils.sinDeg(an), c = MathUtils.cosDeg(an), in = i % 3 == 0 ? 10f : 6f;
            p.line(CX + (R - 2f) * s, CY - (R - 2f) * c, CX + (R - in) * s, CY - (R - in) * c, 1f, Yard.P_GDIM, a);
        }
        // Heading: the nose's line, the own-ship cross at the centre
        float hd = d.flightStatus.present && !Float.isNaN(d.flightStatus.heading) ? d.flightStatus.heading : 90f;
        float hs = MathUtils.cosDeg(hd), hc = MathUtils.sinDeg(hd);
        p.dashed(CX, CY, CX + (R - 4f) * hs, CY - (R - 4f) * hc, 1f, 3f, 3f, Yard.P_GREEN, a);
        YardSymbols.rel(p, Rel.OWN, CX, CY, 4f, Yard.P_GREEN, 90f - hd, false, 1.3f, State.ACTIVE, false);

        float range = STEPS[step] * 1000f;
        int hostiles = 0;
        for (Contact c : d.contactFrame.contacts) {
            if (c.kind == Kind.SHIP && c.relation == Relation.HOSTILE && c.state != com.ringsofiron.uihud.providers.ContactProvider.State.WRECKED) hostiles++;
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg)) continue;
            float dd = Math.min(c.range / range, 0.97f);
            // Bearing from the nose, port +, turned into the north-up view by the heading
            float abs = hd + c.bearingDeg;
            float px = CX + dd * (R - 2f) * MathUtils.cosDeg(abs), py = CY - dd * (R - 2f) * MathUtils.sinDeg(abs);
            Color col = c.threat ? Yard.P_RED : Yard.P_GREEN;
            if (c.kind == Kind.MISSILE) {
                // Pointing in at the ship when it threatens, out when it is ours
                float dir = c.threat ? 180f - abs : -abs;
                YardSymbols.missile(p, px, py, dir, col, 0.85f);
            } else if (c.kind == Kind.STATION) {
                YardSymbols.station(p, px, py, 4f, Yard.P_GREEN, 1.3f);
            } else if (c.kind == Kind.EVA) {
                // One of the crew outside: a small dot, theirs to look after
                p.circle(px, py, 1.9f, Yard.P_GREEN, a);
            } else if (c.kind == Kind.WAYPOINT) {
                p.polyline(new float[]{px - 3f, py, px, py - 3f, px + 3f, py, px, py + 3f}, true, 1.2f, Yard.P_GREEN, a);
            } else {
                YardSymbols.rel(p, rel(c), px, py, 3.6f, col, 0f, c.relation == Relation.HOSTILE, 1.3f, state(c), false);
            }
            if (c.locked) YardSymbols.bracket(p, px, py, 9f, Yard.P_GREEN, 1.2f);
            if (c.nav) YardSymbols.navMark(p, px, py, Yard.P_GREEN, 1.2f);
        }
        p.tubeOver(CX - R, CY - R, 2 * R, 2 * R);

        Color ink = finish.ink;
        if (!brown) {
            p.lamp(1092f, 712f, 4f, Yard.GREEN_LAMP, auto());
            p.legend("AUTO", 1101f, 716.5f, ink);
        }
        if (hostiles > 0) {
            p.legend("HOSTILE", 1168f, 716.5f, ink);
            p.drums(1222f, 703f, String.valueOf(Math.min(9, hostiles)), 13f, 17f, 15f, false);
        }
        if (brown) {
            // On the battery: the panel lights go out, and the BATT lamp takes the AUTO lamp's place (the
            // range no longer follows by itself), clear of the hostile count
            p.rect(1079f, 700f, 198f, 59f, Yard.hex("140D06"), 0.38f);
            p.lamp(1092f, 712f, 4f, Yard.AMBER_LAMP, true);
            p.legend("BATT", 1101f, 716.5f, Yard.hex("EFE2C4"));
        }
        float ky = 728f;
        p.keyButton(1086f, ky, 26f, 24f, "−");
        p.flaps(1118f, ky + 4f, label(step), 6, 8.4f, 16f, 13f, auto() ? Yard.FLAP_TEXT : Yard.FLAP_AMBER);
        p.keyButton(1174f, ky, 26f, 24f, "+");
    }

    private static void ring(YardPaint p, float r, float a) {
        int n = 48;
        float[] pts = new float[2 * n];
        for (int i = 0; i < n; i++) {
            float an = MathUtils.PI2 * i / n;
            pts[2 * i] = CX + r * MathUtils.cos(an);
            pts[2 * i + 1] = CY + r * MathUtils.sin(an);
        }
        p.polyline(pts, true, 0.9f, Yard.P_GDIM, a);
    }

    static String label(int step) {
        float km = STEPS[step];
        return (km < 1f ? String.format(Locale.ROOT, "%.1f", km) : String.valueOf((int) km)) + " KM";
    }

    static Rel rel(Contact c) {
        if (c.kind == Kind.DERELICT) return Rel.INERT;
        switch (c.relation) {
            case HOSTILE: return Rel.HOSTILE;
            case FRIENDLY: return Rel.FRIENDLY;
            default: return Rel.NEUTRAL;
        }
    }

    static State state(Contact c) {
        switch (c.state) {
            case DISABLED: return State.DISABLED;
            case STRUCK: case SILENCED: return State.STRUCK;
            case WRECKED: return State.WRECKED;
            default: return State.ACTIVE;
        }
    }

    @Override
    public boolean pressable(float px, float py) {
        float lx = localX(px), ly = localY(py);
        return ly >= 728f && ly <= 752f && ((lx >= 1086f && lx <= 1112f) || (lx >= 1174f && lx <= 1200f));
    }

    /** The minus and plus keys set the range by hand, until the post changes. */
    @Override
    public boolean press(float px, float py) {
        float lx = localX(px);
        int s = manual >= 0 ? manual : step;
        if (lx <= 1112f) s = Math.max(0, s - 1);
        else s = Math.min(STEPS.length - 1, s + 1);
        manual = s;
        step = s;
        return true;
    }

    @Override
    public String hover(float px, float py) {
        if (pressable(px, py)) return localX(px) <= 1112f ? "Shorter range" : "Longer range";
        float lx = localX(px), ly = localY(py);
        if (ly >= 700f && ly < 726f && lx < 1160f) return auto() ? "Range automatic: it holds the threats and the target" : "Range set by hand: it no longer follows the threats";
        return null;
    }
}
