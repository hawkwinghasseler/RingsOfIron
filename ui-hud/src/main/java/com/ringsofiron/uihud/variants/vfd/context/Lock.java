package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.State;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.TargetStatusProvider.TargetStatus;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * The lock card (functional-context 7, H6 and F1), at the top of the right column's middle. H6 at the
 * helm: the Lock's name; its state in one word (HOSTILE behind red) with FIRING ON YOU as a flag of
 * its own; the range with closing or opening and the rate; the guns' time of flight and the best hit
 * chance with its word. F1 at fire control and a gun drops the hit chance (F2 has it per mount) and
 * adds the speed relative to the ship with its course against ours, and what is known of its
 * condition.
 */
final class Lock {
    static final float H6_H = 4 * 24f + 6f, F1_H = 2 * 24f + 4 * Geo.ROW + 8f;
    /** The hit chance's words (HitChance.OPEN_FIRE is fair). */
    static final float GOOD = 0.75f, FAIR = 0.5f;
    static final String[] WORDS = {"GOOD", "FAIR", "POOR"}, MOTION = {"CLOSING", "OPENING"};

    boolean on, full;
    String name = "", state = "", range = "", unit = "KM", rate = "", shot = "", hit = "", rel = "", course = "", known = "";
    boolean hostile, firing, closing, opening, solution;
    int word = -1;
    private final float[] lit3 = new float[3], lit2 = new float[2];

    void update(HudData d, Situations s) {
        TargetStatus t = d.targetStatus;
        on = false;
        if (!t.present || s.fullScreen) return;
        Contact c = d.contactFrame.locked();
        // Latched to the Lock for boarding, X2 names it (and a range of 40 m tells nothing)
        boolean latchedToIt = s.latched && !d.docking.other.isEmpty() && Fmt.name(d.docking.other, 16).equals(Fmt.name(t.label, 16));
        switch (s.seat) {
            case HELM:
                on = !s.warp && !(s.approach && !s.combat) && !latchedToIt && !s.docked;
                full = false;
                break;
            case FIRE_CONTROL:
            case GUN:
                on = !latchedToIt && !s.docked;
                full = true;
                break;
            default:
                return;
        }
        if (!on) return;
        name = Fmt.name(t.label, 16);
        state = state(t, c);
        hostile = state.equals("HOSTILE");
        firing = t.firingOnUs;
        range = Fmt.range(t.range, 3);
        unit = Fmt.km(t.range) ? "KM" : "M";
        closing = !Float.isNaN(t.closing) && t.closing > 0.5f;
        opening = !Float.isNaN(t.closing) && t.closing < -0.5f;
        rate = Float.isNaN(t.closing) ? "" : Fmt.fit(Math.round(Math.abs(t.closing)), 3, 0);
        float tof = d.targeting == null ? Float.NaN : d.targeting.getTimeToImpact();
        solution = !Float.isNaN(tof) && tof > 0f && !Float.isInfinite(tof);
        shot = solution ? Fmt.fit(tof, 2, 1) : "";
        float best = Float.NaN;
        for (Mount m : d.weaponFrame.mounts) {
            if (m.kind == Kind.POINT_DEFENSE || Float.isNaN(m.hitChance) || !m.working()) continue;
            if (Float.isNaN(best) || m.hitChance > best) best = m.hitChance;
        }
        hit = Float.isNaN(best) ? "" : Integer.toString(Math.min(99, Math.round(best * 100f)));
        word = Float.isNaN(best) ? -1 : best >= GOOD ? 0 : best >= FAIR ? 1 : 2;
        rel = Float.isNaN(t.relativeSpeed) ? "" : Fmt.fit(Math.round(t.relativeSpeed), 4, 0);
        course = Float.isNaN(t.aspectDeg) ? "" : Integer.toString(Math.floorMod(Math.round(t.aspectDeg), 360));
        known = known(c);
    }

    /** The Lock's state in one word (HostileLabel's, then its allegiance). */
    static String state(TargetStatus t, Contact c) {
        if (c != null && c.state == State.WRECKED) return "WRECK";
        String s = t.state == null ? "" : t.state.toLowerCase(Locale.ROOT);
        switch (s) {
            case "struck": return "SURRENDERED";
            case "running": return "FLEEING";
            case "silenced": return "DISABLED";
            case "":
            case "fit":
                break;
            default:
                // A DisabledComponent's reason: NO_POWER, NO_CREW, HULL and the rest
                return "DISABLED";
        }
        switch (t.relation) {
            case HOSTILE: return "HOSTILE";
            case FRIENDLY: return "FRIENDLY";
            case MISSILE: return "MISSILE";
            case NEUTRAL: return "NEUTRAL";
            default: return "UNKNOWN";
        }
    }

    /** What is known of its condition: why it is disabled, or that it has been hit. */
    static String known(Contact c) {
        if (c == null) return "";
        if (!c.disabledReason.isEmpty()) return Fmt.cut(c.disabledReason, 25);
        switch (c.state) {
            case STRUCK: return "STRUCK ITS COLOURS";
            case SILENCED: return "GUNS SILENCED";
            case RUNNING: return "BREAKING OFF";
            default: return "";
        }
    }

    float height() {
        return on ? (full ? F1_H : H6_H) : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid from the right

    void paint(Filament f, float y) {
        if (!on) return;
        float x = Geo.RX, h = height();
        f.slab(x, y, Geo.CW, h, 5f, 1, true);
        float b0 = y + 3f + 17f, b1 = y + 3f + 24f + 17f;
        f.legend(x + 6f, b0, "LOCK", f.o().dim());
        f.dm(x + 52f, b0 - 14f, name, f.o().pitch(2f).cells(16).norm());
        // The state in one word (hostile behind red), and FIRING ON YOU as a flag of its own
        f.dm(x + 52f, b1 - 10.5f, state, f.o().pitch(Geo.P).cells(11).level(hostile ? Drive.FULL : Drive.NORM).color(hostile ? Phosphor.RED : Phosphor.ZINC));
        float fw = f.legendWidth("FIRING ON YOU", f.o()) + 12f, fx = x + Geo.CW - 6f - fw;
        if (firing) {
            f.rectLit(fx, b1 - 14f, fw, 18f, f.o().red().norm().r(1.2f));
            f.knockout(fx + 6f, b1, "FIRING ON YOU", f.o());
        } else {
            f.frameLit(fx, b1 - 14f, fw, 18f, 1f, f.o().ghostOnly().red());
            f.legend(fx + 6f, b1, "FIRING ON YOU", f.o().red().lit(false));
        }
        if (full) paintFull(f, x, y + 2f * 24f + 5f);
        else paintCompact(f, x, y);
    }

    private void paintCompact(Filament f, float x, float y) {
        float b2 = y + 3f + 48f + 17f, b3 = y + 3f + 72f + 17f;
        float cx = motion(f, x + 52f, b2, 15f);
        Kit.fits(cx, x + Geo.CW - 4f, "H6 range row");
        cx = x + 52f;
        if (solution) {
            cx = Rows.lab(f, cx, b3, "SHOT");
            cx = Rows.num(f, cx, b3, shot, 2) - 1f;
            cx = Rows.lab(f, cx, b3, "S") + 8f;
        } else {
            cx = Rows.txt(f, cx, b3, "NO SOLUTION", 11);
        }
        cx = Rows.lab(f, cx, b3, "HIT");
        cx = Rows.num(f, cx, b3, hit, 2) - 1f;
        cx = Rows.lab(f, cx, b3, "%") + 6f;
        for (int i = 0; i < 3; i++) lit3[i] = i == word ? Drive.NORM : Float.NaN;
        Kit.fits(Kit.states(f, cx, b3, WORDS, lit3, 13f, 6f) - 6f, x + Geo.CW - 4f, "H6 shot row");
    }

    private void paintFull(Filament f, float x, float top) {
        float ry = top;
        motion(f, x + 52f, Geo.base(ry) + 2f, 15f);
        ry += Geo.ROW + 2f;
        float b = Geo.base(ry), cx = x + 52f;
        cx = Rows.num(f, cx, b, rel, 4) - 2f;
        cx = Rows.lab(f, cx, b, "M/S REL") + 6f;
        cx = Rows.lab(f, cx, b, "COURSE");
        cx = Rows.num(f, cx, b, course, 3) - 2f;
        Kit.fits(Rows.lab(f, cx, b, "°"), x + Geo.CW - 4f, "F1 relative row");
        ry += Geo.ROW;
        b = Geo.base(ry);
        cx = x + 52f;
        if (solution) {
            cx = Rows.lab(f, cx, b, "SHOT");
            cx = Rows.num(f, cx, b, shot, 2) - 1f;
            Rows.lab(f, cx, b, "S");
        } else {
            Rows.txt(f, cx, b, "NO SOLUTION", 11);
        }
        ry += Geo.ROW;
        Rows.txt(f, x + 52f, Geo.base(ry), known, 25);
    }

    /** Range, closing or opening, the rate: returns the end x. */
    private float motion(Filament f, float x, float b, float size) {
        float cx = x;
        cx += f.seg7(cx, b - size, size, range, f.o().cells(3).right().norm()) + 6f;
        cx += f.legend(cx, b, unit, f.o().dim()) + 10f;
        lit2[0] = closing ? Drive.NORM : Float.NaN;
        lit2[1] = opening ? Drive.NORM : Float.NaN;
        cx = Kit.states(f, cx, b, MOTION, lit2, 13f, 6f);
        cx += f.seg7(cx, b - 12f, 12f, rate, f.o().cells(3).right().norm()) + 5f;
        return cx + f.legend(cx, b, "M/S", f.o().dim());
    }
}
