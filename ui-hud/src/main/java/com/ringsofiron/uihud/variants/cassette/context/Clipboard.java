package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P5, the objective: the tracked contract's printout on a clipboard on the left rail, typed, with the
 * time left on a bakelite timer clipped to it (paper doesn't change, and the clock does). While a guide
 * runs, the clipboard carries its step instead, with the player's keys. The compact face is two lines,
 * the job and where it goes; under warp the full face adds who gave it, the pay and the other contracts.
 */
final class Clipboard extends YardPart {
    enum Mode {
        COMPACT, FULL, GUIDE
    }

    static final float W = 308f, COMPACT_H = 58f;
    private static final float TEXT = 13f, LEAD = 16f, TIMER_W = 70f;
    private static final int GUIDE_LINES = 5;

    private final ContextFrame f;
    Mode mode = Mode.COMPACT;
    private final List<String> lines = new ArrayList<>();
    private String heading = "";
    private boolean timer;

    Clipboard(ContextFrame f) {
        super(0f, 0f);
        this.f = f;
    }

    /** A contract that is a fight, which the clipboard still shows in combat (footnote 1). */
    static boolean fight(Objective o) {
        return o != null && (o.kind.equalsIgnoreCase("Bounty") || o.kind.equalsIgnoreCase("Escort"));
    }

    /** The tracked contract: the soonest deadline, as the game sorts them. */
    static Objective tracked(HudData d) {
        return d.objectiveFrame.first();
    }

    /** Lays out this frame's words for {@code mode}; returns the height, 0 with nothing to show. */
    float measure(YardPaint p, HudData d, Mode mode) {
        this.mode = mode;
        lines.clear();
        heading = "";
        timer = false;
        if (mode == Mode.GUIDE) {
            if (d.guideText.isEmpty()) return 0f;
            heading = d.guideTitle.isEmpty() ? (d.guideDone ? "GUIDE · DONE" : "GUIDE") : d.guideTitle.toUpperCase(Locale.ROOT);
            lines.addAll(Kit.wrap(d.guideText, paperChars(p, W - 30f), GUIDE_LINES));
            return 14f + 15f + lines.size() * LEAD + 10f;
        }
        Objective o = tracked(d);
        if (o == null) return 0f;
        int chars = paperChars(p, W - 30f - (Double.isNaN(o.timeLeft) ? 0f : TIMER_W + 6f));
        timer = !Double.isNaN(o.timeLeft);
        lines.add(YardText.cut(job(o), chars));
        String where = o.next.isEmpty() ? (o.station.isEmpty() ? "" : "to " + o.station) : "to " + o.next;
        String prog = o.of > 0 ? (int) Math.round(o.progress) + " of " + (int) Math.round(o.of) : "";
        String second = where.isEmpty() ? prog : prog.isEmpty() ? where : where + " · " + prog;
        if (o.state == com.ringsofiron.uihud.providers.ObjectiveProvider.State.RETURN) second = o.station.isEmpty() ? "back to be paid" : "back to " + o.station;
        if (!second.isEmpty()) lines.add(YardText.cut(second, chars));
        if (mode == Mode.FULL) {
            String from = o.giver.isEmpty() ? o.station : o.giver;
            String pay = o.reward > 0 ? YardText.thousands(o.reward) + " cr" : "";
            String third = from.isEmpty() ? pay : pay.isEmpty() ? "from " + from : "from " + from + " · " + pay;
            if (!third.isEmpty()) lines.add(YardText.cut(third, paperChars(p, W - 30f)));
            int more = moreContracts(d.objectiveFrame);
            if (more > 0) lines.add(more == 1 ? "+1 more contract" : "+" + more + " more contracts");
        }
        return Math.max(COMPACT_H, 16f + lines.size() * LEAD + 12f);
    }

    /** The job in a few words: "Deliver: Food to Shackleton Ice". */
    static String job(Objective o) {
        if (o.title.isEmpty()) return o.kind;
        if (o.kind.isEmpty() || o.title.toLowerCase(Locale.ROOT).startsWith(o.kind.toLowerCase(Locale.ROOT))) return o.title;
        return o.kind + ": " + o.title;
    }

    private static int moreContracts(ObjectiveFrame of) {
        return Math.max(0, of.objectives.size - 1);
    }

    private static int paperChars(YardPaint p, float w) {
        float a = (p.width(Face.PAPER, TEXT, "0000000000") - p.width(Face.PAPER, TEXT, "0")) / 9f;
        return a <= 0f ? 0 : (int) Math.floor(w / a);
    }

    @Override
    protected void draw(YardPaint p) {
        HudData d = f.data;
        Kit.slideRail(p, -x, true, 0f, h);
        Kit.housing(p, 0f, 0f, w, h, false);
        float px = 7f, py = 6f, pw = w - 14f, ph = h - 12f;
        p.paper(px, py, pw, ph);
        // The clip across the top, and the sprocket holes of fanfold paper down its edge
        p.rrect(px + pw / 2f - 34f, py - 5f, 68f, 9f, 3f, Kit.KNOB);
        p.rect(px + pw / 2f - 30f, py - 3.5f, 60f, 2f, Color.WHITE, 0.3f);
        for (float hy = py + 8f; hy < py + ph - 4f; hy += 9f) p.circle(px + 6f, hy, 1.4f, Yard.hex("8F8676"));
        float tx = px + 16f, base = py + 20f;
        if (mode == Mode.GUIDE) {
            p.typed(heading, tx, base, 12f, Yard.PAPER_RED);
            base += 15f;
        }
        for (int i = 0; i < lines.size(); i++) {
            p.typed(lines.get(i), tx, base + i * LEAD, TEXT, Yard.PAPER_INK);
        }
        if (timer && mode != Mode.GUIDE) {
            Objective o = tracked(d);
            if (o != null) timerBox(p, px + pw - TIMER_W - 4f, py + 4f, o);
        }
    }

    /** The bakelite timer clipped to the paper: LEFT, and the time on drums ("1.4 D", "3:20"). */
    private void timerBox(YardPaint p, float x, float y, Objective o) {
        float bh = 42f;
        p.rrect(x + 1f, y + 2f, TIMER_W, bh, 4f, Yard.SHADOW, 0.6f);
        p.rrect(x, y, TIMER_W, bh, 4f, Yard.BAKELITE);
        p.rrect(x + TIMER_W / 2f - 10f, y - 4f, 20f, 6f, 2f, Kit.KNOB);
        boolean late = o.timeLeft < 0 || o.late;
        Kit.legendC(p, late ? "LATE" : "LEFT", x + TIMER_W / 2f, y + 13f, late ? Yard.hex("FF8A6A") : Yard.INK_LIGHT);
        String[] t = left(o.timeLeft);
        float dw = YardPaint.drumWidth(t[0], 10f);
        float uw = t[1].isEmpty() ? 0f : 12f;
        float dx = x + (TIMER_W - dw - uw) / 2f;
        p.drums(dx, y + 18f, t[0], 10f, 18f, 15f, false);
        if (!t[1].isEmpty()) p.text(Face.FLAP, 14f, t[1], dx + dw + 5f, y + 32f, Yard.INK_LIGHT);
    }

    /** The time left for the timer's drums and its unit: "1.4" "D", "5" "H", "3:20" "". */
    static String[] left(double s) {
        double a = Math.abs(s);
        if (a >= 86400) return new String[]{String.format(Locale.ROOT, "%.1f", a / 86400), "D"};
        if (a >= 3600) return new String[]{String.valueOf((long) Math.floor(a / 3600)), "H"};
        long t = (long) Math.ceil(a);
        return new String[]{String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60), ""};
    }
}
