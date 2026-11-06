package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CareerProvider.Career;
import com.ringsofiron.uihud.providers.CareerProvider.Promotion;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.Fmt;
import com.ringsofiron.uihud.variants.vfd.screens.Boards;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;
import com.ringsofiron.uihud.variants.vfd.screens.Slate;

import java.util.Locale;

/**
 * The record's Career (functional-context 10.5; mockup st-slate): the faction served and the rank
 * held, the next rank and the standing that earns it, how long the rank must still be held, the
 * standing toward it, the budget and its payday, the largest ship the rank may fly; the record behind
 * it, the contracts done, failed and given up and the bounties among them; and each rank given, and
 * when. It only tells, so it has no keys.
 */
final class CareerTab extends ModeTab {
    /** A field's value from its label, a field's pitch, and the standing's bar. */
    static final float VALUE = 130f, STEP = 20f, BAR_W = 150f;
    static final int BAR_SEGMENTS = 15, RANK_CELLS = 22;

    private Career c;
    private double now;

    CareerTab() {
        super("CAREER", ClassicWindow.LOG, "");
    }

    @Override
    void refresh(Boards b, HudData d) {
        c = b.career;
        now = d.ctx.simSeconds;
    }

    @Override
    void paint(Filament f, float time) {
        if (c == null || !c.present) {
            Slate.dim(f, x, y + 8f, "NO SERVICE RECORD");
            return;
        }
        float colW = (w - 60f) / 2f;
        career(f, x, y + 8f, colW);
        promotions(f, x + colW + 60f, y + 8f);
    }

    /** The left column: the career, then the record. */
    private void career(Filament f, float x0, float fy, float colW) {
        Slate.heading(f, x0, fy + 12f, "CAREER");
        Slate.dim(f, x0 + 80f, fy + 3f, upper(c.sponsor));
        fy += 30f;
        fy = field(f, x0, fy, "RANK", upper(c.rank), false);
        boolean top = c.nextRank.isEmpty() || Float.isNaN(c.nextAt);
        if (top) {
            fy = field(f, x0, fy, "NEXT RANK", "NONE: THE TOP OF THE LADDER", true);
            fy = field(f, x0, fy, "STANDING", String.format(Locale.ROOT, "%+d", Math.round(c.standing)), false);
        } else {
            fy = field(f, x0, fy, "NEXT RANK", upper(c.nextRank) + " AT " + Math.round(c.nextAt), false);
            if (c.gradeLeft > 0) fy = field(f, x0, fy, "TIME IN RANK", Fmt.span(c.gradeLeft) + " MORE BEFORE IT", false);
            String v = Math.round(c.standing) + " OF " + Math.round(c.nextAt);
            Slate.label(f, x0, fy + 9f, "STANDING");
            Slate.text(f, x0 + VALUE, fy, v);
            float frac = c.nextAt <= 0f ? 1f : c.standing / c.nextAt;
            Slate.bar(f, x0 + VALUE + Glass.width(12) + 10f, fy + 1.5f, BAR_W, 7.5f, BAR_SEGMENTS, frac, false);
            fy += STEP;
        }
        if (c.budget > 0) {
            fy = field(f, x0, fy, "BUDGET", Glass.thousands(c.budget) + " CR A WEEK", false);
            String pay = c.payIn > 0 ? "DAY " + Fmt.day(now + c.payIn) + ", IN " + Fmt.span(c.payIn) : "TODAY";
            fy = field(f, x0, fy, "PAYDAY", pay, false);
        } else {
            fy = field(f, x0, fy, "BUDGET", "NONE AT THIS RANK", true);
        }
        fy = field(f, x0, fy, "PAID IN ALL", c.budgetPaid > 0 ? Glass.thousands(c.budgetPaid) + " CR" : "NOTHING YET", c.budgetPaid <= 0);
        fy = field(f, x0, fy, "MAY FLY", mayFly(c.mayFly), false);
        fy += 4f;
        Slate.rule(f, x0, fy, colW);
        fy += 10f;
        Slate.label(f, x0, fy + 9f, "RECORD");
        fy += 22f;
        if (c.recordOf == 0) {
            fy = field(f, x0, fy, "CONTRACTS", "NONE ENDED YET", true);
        } else {
            String done = c.done + " DONE, " + (c.failed == 0 ? "NONE FAILED" : c.failed + " FAILED")
                    + (c.abandoned > 0 ? ", " + c.abandoned + " GIVEN UP" : "");
            fy = field(f, x0, fy, "CONTRACTS", done, false);
        }
        if (c.bounties == 0) fy = field(f, x0, fy, "BOUNTIES", "NONE YET", true);
        else fy = field(f, x0, fy, "BOUNTIES", c.bounties + " FOR " + Glass.thousands(c.bountyPay) + " CR", false);
        if (c.recordFull) Slate.dim(f, x0, fy, "OF THE LAST " + c.recordOf + " CONTRACTS ENDED: THE GAME KEEPS NO MORE");
    }

    /** The right column: each rank given and when, then the next, dim. */
    private void promotions(Filament f, float x0, float fy) {
        Slate.heading(f, x0, fy + 12f, "PROMOTIONS");
        fy += 30f;
        for (int i = 0; i < c.promotions.size; i++) {
            Promotion p = c.promotions.get(i);
            Slate.text(f, x0, fy, Glass.cut(p.rank, RANK_CELLS));
            Slate.text(f, x0 + (RANK_CELLS + 2) * Glass.CW, fy, i == 0 && p.at <= 0 ? "AT THE START" : Fmt.when(p.at));
            fy += STEP;
        }
        if (!c.nextRank.isEmpty() && !Float.isNaN(c.nextAt)) {
            Slate.dim(f, x0, fy, Glass.cut(c.nextRank, RANK_CELLS));
            Slate.dim(f, x0 + (RANK_CELLS + 2) * Glass.CW, fy, "AT " + Math.round(c.nextAt) + " STANDING");
        }
    }

    /** A label and its value; returns the next field's top. */
    private static float field(Filament f, float x0, float fy, String label, String value, boolean dim) {
        Slate.label(f, x0, fy + 9f, label);
        Slate.text(f, x0 + VALUE, fy, value, dim);
        return fy + STEP;
    }

    /** "corvette" as "UP TO A CORVETTE"; any ship with no limit. */
    static String mayFly(String chassis) {
        if (chassis == null || chassis.isEmpty()) return "ANY SHIP";
        String c = chassis.replace('_', ' ').toUpperCase(Locale.ROOT);
        return "UP TO " + ("AEIOU".indexOf(c.charAt(0)) >= 0 ? "AN " : "A ") + c;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }
}
