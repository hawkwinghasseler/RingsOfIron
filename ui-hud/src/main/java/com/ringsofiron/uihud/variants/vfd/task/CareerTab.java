package com.ringsofiron.uihud.variants.vfd.task;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CareerProvider.Career;
import com.ringsofiron.uihud.providers.CareerProvider.Faction;
import com.ringsofiron.uihud.providers.CareerProvider.Region;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The book's Career (functional-task 7, BOOK): the rank and sponsor; the standing with the sponsor
 * against the next rank and any time in rank still owed; the budget and the next payday; what the rank
 * may fly; and the record, contracts and bounties. Beside them, the standing with every faction, the
 * one served first, and the tension on each lawful faction's stations. It takes no orders.
 */
final class CareerTab extends Tab {
    /** A field's value from its label, a field's pitch, a faction's; the bars. */
    static final float VALUE = 100f, STEP = 20f, ROW = 18f, RANK_BAR = 90f, FACTION_BAR = 90f, TENSION_BAR = 70f;

    private Career c;
    private double now;
    private float y, bottom, right;

    CareerTab() {
        super("CAREER", ClassicWindow.CREW, "");
        keys.define(0, 0);
    }

    @Override
    void refresh(Boards b, HudData d) {
        c = b.career;
        now = d.ctx.simSeconds;
    }

    @Override
    void paint(Filament f, float time) {
        float x0 = at.lx, x1 = at.lr;
        bottom = at.lb;
        y = at.ly;
        right = x1;
        if (c == null || !c.present) {
            Slate.dim(f, x0, y, "NO SERVICE RECORD");
            return;
        }
        Slate.heading(f, x0, y + 12f, "CAREER");
        Slate.dim(f, x0 + 72f, y + 3f, Glass.cut(c.sponsor, Glass.cells(x1 - x0 - 72f)));
        y += 30f;
        field(f, x0, "RANK", upper(c.rank), false);
        boolean top = c.nextRank.isEmpty() || Float.isNaN(c.nextAt);
        if (top) {
            field(f, x0, "NEXT RANK", "NONE: THE TOP", true);
            field(f, x0, "STANDING", String.format(Locale.ROOT, "%+d", Math.round(c.standing)), false);
        } else {
            field(f, x0, "NEXT RANK", upper(c.nextRank) + " AT " + Math.round(c.nextAt), false);
            if (c.gradeLeft > 0) field(f, x0, "TIME IN RANK", Fmt.span(c.gradeLeft) + " MORE", false);
            if (fits(STEP)) {
                String v = Math.round(c.standing) + " OF " + Math.round(c.nextAt);
                Slate.label(f, x0, y + 9f, "STANDING");
                Slate.text(f, x0 + VALUE, y, v);
                float bx = x0 + VALUE + Glass.width(v.length() + 1);
                if (bx + RANK_BAR <= x1) {
                    float frac = c.nextAt <= 0f ? 1f : c.standing / c.nextAt;
                    Slate.bar(f, bx, y + 1.5f, RANK_BAR, 7.5f, 15, frac, false);
                }
                y += STEP;
            }
        }
        if (c.budget > 0) {
            field(f, x0, "BUDGET", Glass.thousands(c.budget) + " CR A WEEK", false);
            field(f, x0, "PAYDAY", c.payIn > 0 ? "DAY " + Fmt.day(now + c.payIn) + ", IN " + Fmt.span(c.payIn) : "TODAY", false);
        } else {
            field(f, x0, "BUDGET", "NONE AT THIS RANK", true);
        }
        field(f, x0, "MAY FLY", mayFly(c.mayFly), false);
        record(f, x0, x1);
        // Beside them: the factions and the tension
        x0 = at.dx;
        x1 = at.dr;
        right = x1;
        bottom = at.db;
        y = at.dy;
        factions(f, x0, x1, true);
        tension(f, x0, x1);
        right = at.lr;
    }

    /** The standing with every faction, the one served first. */
    private void factions(Filament f, float x0, float x1, boolean first) {
        if (first) {
            Slate.label(f, x0, y + 9f, "STANDING BY FACTION");
            y += 22f;
        } else if (!section(f, x0, x1, "STANDING BY FACTION")) {
            return;
        }
        float vx = x0 + 15f * Glass.CW, bx = x0 + 19f * Glass.CW;
        for (int i = 0; i < c.factions.size && fits(ROW); i++) {
            Faction r = c.factions.get(i);
            Slate.text(f, x0, y, Glass.cut(r.name, 14));
            if (r.outlaw) {
                Slate.text(f, vx, y, "AT WAR");
            } else {
                Slate.right(f, vx + Glass.width(3), y, String.format(Locale.ROOT, "%+d", Math.round(r.standing)), false);
                if (bx + FACTION_BAR <= x1) Slate.signedBar(f, bx, y + 1.5f, FACTION_BAR, 7.5f, 10, r.standing / Career.MAX);
            }
            y += ROW;
        }
    }

    /** The record behind the rank. */
    private void record(Filament f, float x0, float x1) {
        if (!section(f, x0, x1, "RECORD")) return;
        if (c.recordOf == 0) {
            field(f, x0, "CONTRACTS", "NONE ENDED YET", true);
        } else {
            field(f, x0, "CONTRACTS", c.done + " DONE, " + (c.failed == 0 ? "NONE FAILED" : c.failed + " FAILED")
                    + (c.abandoned > 0 ? ", " + c.abandoned + " GIVEN UP" : ""), false);
        }
        if (c.bounties == 0) field(f, x0, "BOUNTIES", "NONE YET", true);
        else field(f, x0, "BOUNTIES", c.bounties + " FOR " + Glass.thousands(c.bountyPay) + " CR", false);
    }

    /** The tension on each lawful faction's stations. */
    private void tension(Filament f, float x0, float x1) {
        if (c.regions.size == 0 || !section(f, x0, x1, "TENSION")) return;
        float vx = x0 + 18f * Glass.CW, bx = x0 + 22f * Glass.CW;
        for (int i = 0; i < c.regions.size && fits(ROW); i++) {
            Region r = c.regions.get(i);
            Slate.text(f, x0, y, Glass.cut(r.name, 17));
            Slate.right(f, vx + Glass.width(3), y, Integer.toString(Math.round(r.tension)), false);
            if (bx + TENSION_BAR <= x1) Slate.bar(f, bx, y + 1.5f, TENSION_BAR, 7.5f, 10, r.tension / 100f, false);
            y += ROW;
        }
        if (fits(ROW)) Slate.dim(f, x0, y, Glass.cut("COMBAT JOBS FROM " + Math.round(c.combatJobsAt), Glass.cells(x1 - x0)));
        y += ROW;
    }

    /** A rule and a section's legend; false when there is no room for it and a line under it. */
    private boolean section(Filament f, float x0, float x1, String legend) {
        if (!fits(4f + 10f + 22f + ROW)) return false;
        y += 4f;
        Slate.rule(f, x0, y, x1 - x0);
        y += 10f;
        Slate.label(f, x0, y + 9f, legend);
        y += 22f;
        return true;
    }

    private boolean fits(float h) {
        return y + h <= bottom + 4f;
    }

    /** A label and its value, while there is room. */
    private void field(Filament f, float x0, String label, String value, boolean dim) {
        if (!fits(STEP)) return;
        Slate.label(f, x0, y + 9f, label);
        Slate.text(f, x0 + VALUE, y, Glass.cut(value, Glass.cells((right > 0f ? right : at.lr) - x0 - VALUE)), dim);
        y += STEP;
    }

    /** "corvette" as "UP TO A CORVETTE"; any ship with no limit. */
    static String mayFly(String chassis) {
        if (chassis == null || chassis.isEmpty()) return "ANY SHIP";
        String s = chassis.replace('_', ' ').toUpperCase(Locale.ROOT);
        return "UP TO " + ("AEIOU".indexOf(s.charAt(0)) >= 0 ? "AN " : "A ") + s;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
