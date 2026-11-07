package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.Kit;
import com.ringsofiron.uihud.variants.vfd.Phosphor;

import java.util.Locale;

/**
 * P5, the objective (functional-context 6, P5): a slab at the top of the left column's middle, the
 * tracked contract (the one with the soonest deadline) in two lines: the job, then where it sends the
 * ship and the time left in seven segments with its unit. Under warp a third line counts the other
 * contracts held. In a fight it shows only when the contract is itself a fight, with its progress.
 */
final class Goal {
    static final float H2 = 48f, H3 = 68f;
    /** The contract kinds that are fights (4.6, note 1). */
    static final String[] FIGHTS = {"escort", "patrol", "bounty", "defence", "defense", "convoy", "hunt"};

    boolean on, more3;
    String line1 = "", line2 = "", left = "", unit = "", others = "";
    boolean late;

    void update(HudData d, Situations s) {
        on = false;
        if (!visible(s)) return;
        ObjectiveFrame frame = d.objectiveFrame;
        Objective o = frame.first();
        if (o == null) {
            if (frame.operation.isEmpty()) return;
            on = true;
            line1 = Fmt.cut(frame.operation, 33);
            line2 = frame.operationSteps > 0 ? "STEP " + frame.operationStep + " OF " + frame.operationSteps : "";
            left = unit = others = "";
            more3 = false;
            return;
        }
        if (s.combat && !fight(o)) return;
        on = true;
        String kind = o.kind.isEmpty() ? "" : o.kind.toUpperCase(Locale.ROOT) + ": ";
        String progress = o.of > 0 ? " · " + Math.round(o.progress) + " OF " + Math.round(o.of) : "";
        line1 = Fmt.cut(kind + title(o), 33 - progress.length()) + progress;
        String where = o.state == State.RETURN ? "BACK TO " + o.station : o.next;
        line2 = where.isEmpty() ? "" : "→ " + Fmt.name(where, 19);
        late = o.late || (!Double.isNaN(o.timeLeft) && o.timeLeft < 0);
        if (late) {
            left = "";
            unit = "LATE";
        } else {
            timeLeft(o.timeLeft);
        }
        int n = frame.objectives.size;
        more3 = s.warp && n > 1;
        others = more3 ? "+" + (n - 1) + (n == 2 ? " MORE CONTRACT" : " MORE CONTRACTS") : "";
    }

    /** P5's column in the visibility matrix (4.6). */
    static boolean visible(Situations s) {
        if (s.fullScreen || s.approach || s.emergency) return false;
        if (s.seat == Situations.Seat.NONE) return false;
        // On deck in a fight the captain has other things to read
        return !(s.seat == Situations.Seat.DECK && s.combat);
    }

    /** A contract's title without the kind the line already leads with: the game's bounty is "Bounty: Raider 'Vex'". */
    static String title(Objective o) {
        String t = o.title, lead = o.kind + ": ";
        return !o.kind.isEmpty() && t.regionMatches(true, 0, lead, 0, lead.length()) ? t.substring(lead.length()) : t;
    }

    static boolean fight(Objective o) {
        String k = (o.kind + " " + o.title).toLowerCase(Locale.ROOT);
        for (String f : FIGHTS) if (k.contains(f)) return true;
        return false;
    }

    /** The time left in three cells and its unit: days or hours with a tenth, minutes, seconds. */
    private void timeLeft(double t) {
        if (Double.isNaN(t)) {
            left = unit = "";
            return;
        }
        if (t >= 86_400) {
            left = Fmt.fit(t / 86_400.0, 3, 1);
            unit = "D LEFT";
        } else if (t >= 3600) {
            left = Fmt.fit(t / 3600.0, 3, 1);
            unit = "H LEFT";
        } else if (t >= 60) {
            left = Long.toString((long) Math.ceil(t / 60.0));
            unit = "M LEFT";
        } else {
            left = Fmt.secs(t);
            unit = "S LEFT";
        }
    }

    float height() {
        return on ? (more3 ? H3 : H2) : 0f;
    }

    // ------------------------------------------------------------------ painting, on the grid

    void paint(Filament f, float y) {
        if (!on) return;
        float x = Geo.LX;
        f.slab(x, y, Geo.CW, height(), 5f, 1, true);
        f.dm(x + 6f, y + 6f, line1, f.o().pitch(Geo.P).cells(33).norm());
        float b = y + 40f;
        boolean timed = !left.isEmpty() || late;
        f.dm(x + 6f, b - 10.5f, line2, f.o().pitch(Geo.P).cells(timed ? 21 : 33).dim());
        if (timed) {
            float tx = x + 6f + 21 * Geo.CELL + 4f;
            float w = late ? 0f : f.seg7(tx, b - 13f, 13f, left, f.o().cells(3).right().norm()) + 6f;
            Kit.fits(tx + w + f.legend(tx + w, b, unit, late ? f.o().amber().full() : f.o().dim()), x + Geo.CW - 4f, "P5 time left");
        }
        if (more3) f.dm(x + 6f, y + 46f, others, f.o().pitch(Geo.P).cells(33).level(Drive.DIM).color(Phosphor.ZINC));
    }
}
