package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CareerProvider.Career;
import com.ringsofiron.uihud.providers.CareerProvider.Faction;
import com.ringsofiron.uihud.providers.CareerProvider.Region;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The record's Standing (functional-context 10.5; mockup st-slate): the standing with every faction,
 * the one served first, each its figure and a bar either side of nothing, and what it means now (the
 * pirates at war with everyone, a faction whose ships fire on the player's, one whose stations refuse
 * them); and under it the strain on each lawful faction's stations, its tension, what its yards charge
 * over their prices for it and the combat jobs its boards post. It only tells, so it has no keys.
 */
final class StandingTab extends ModeTab {
    static final float STEP = 20f, BAR_W = 200f, TENSION_W = 140f;
    static final int NAME_CELLS = 26, VALUE_CELLS = 4;

    private Career c;
    private final StringBuilder sb = new StringBuilder();

    StandingTab() {
        super("STANDING", ClassicWindow.LOG, "");
    }

    @Override
    void refresh(Boards b, HudData d) {
        c = b.career;
    }

    @Override
    void paint(Filament f, float time) {
        if (c == null || !c.present) {
            Slate.dim(f, x, y + 8f, "NO SERVICE RECORD");
            return;
        }
        float x0 = x, fy = y + 8f;
        float vx = x0 + NAME_CELLS * Glass.CW, bx = vx + (VALUE_CELLS + 2) * Glass.CW, nx = bx + BAR_W + 3f * Glass.CW;
        Slate.heading(f, x0, fy + 12f, "STANDING");
        fy += 30f;
        Slate.label(f, x0, fy + 9f, "WITH EACH FACTION");
        fy += 22f;
        for (int i = 0; i < c.factions.size; i++) {
            Faction r = c.factions.get(i);
            Slate.text(f, x0, fy, Glass.cut(r.name, NAME_CELLS - 2));
            if (r.outlaw) {
                Slate.text(f, vx, fy, "AT WAR WITH EVERYONE");
            } else {
                String v = String.format(Locale.ROOT, "%+d", Math.round(r.standing));
                Slate.right(f, vx + Glass.width(VALUE_CELLS), fy, v, false);
                Slate.signedBar(f, bx, fy + 1.5f, BAR_W, 7.5f, 10, r.standing / Career.MAX);
                String note = r.sponsor ? "YOU SERVE THEM" : r.hostile ? "HOSTILE: THEIR SHIPS FIRE ON YOURS"
                        : r.refuses ? "THEIR STATIONS REFUSE YOU" : "";
                if (!note.isEmpty()) Slate.text(f, nx, fy, note);
            }
            fy += STEP;
        }
        Slate.dim(f, x0, fy + 4f, String.format(Locale.ROOT, "STATIONS REFUSE YOU BELOW %+d; SHIPS TURN HOSTILE AT %+d",
                Math.round(c.refuseBelow), Math.round(c.hostileAt)));
        fy += STEP + 14f;
        Slate.rule(f, x0, fy, w);
        fy += 10f;
        Slate.label(f, x0, fy + 9f, "TENSION ON EACH FACTION'S STATIONS");
        fy += 22f;
        float tx = vx + (VALUE_CELLS + 2) * Glass.CW, tn = tx + TENSION_W + 3f * Glass.CW;
        if (c.regions.size == 0) {
            Slate.dim(f, x0, fy, "NONE TO TELL");
            fy += STEP;
        }
        for (int i = 0; i < c.regions.size; i++) {
            Region r = c.regions.get(i);
            Slate.text(f, x0, fy, Glass.cut(r.name, NAME_CELLS - 2));
            Slate.right(f, vx + Glass.width(VALUE_CELLS), fy, Integer.toString(Math.round(r.tension)), false);
            Slate.bar(f, tx, fy + 1.5f, TENSION_W, 7.5f, 10, r.tension / 100f, false);
            String note = regionNote(r);
            if (!note.isEmpty()) Slate.text(f, tn, fy, note);
            fy += STEP;
        }
        Slate.dim(f, x0, fy + 4f, String.format(Locale.ROOT, "HIGH FROM %d: MORE PATROLS, AND THEIR STATIONS CLEAR ONLY FRIENDS; "
                + "BOARDS POST MORE COMBAT JOBS FROM %d", Math.round(c.highAt), Math.round(c.combatJobsAt)));
    }

    /** "HIGH, YARDS +12%, 2 MORE COMBAT JOBS", or "". */
    String regionNote(Region r) {
        sb.setLength(0);
        if (r.high) sb.append("HIGH");
        int rise = Math.round(r.yardRise * 100f);
        if (rise > 0) sep().append("YARDS +").append(rise).append('%');
        if (r.combatJobs > 0) sep().append(r.combatJobs).append(r.combatJobs == 1 ? " MORE COMBAT JOB" : " MORE COMBAT JOBS");
        return sb.toString();
    }

    private StringBuilder sep() {
        if (sb.length() > 0) sb.append(", ");
        return sb;
    }
}
