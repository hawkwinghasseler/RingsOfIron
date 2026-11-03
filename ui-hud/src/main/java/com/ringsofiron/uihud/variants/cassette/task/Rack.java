package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z3, the incoming rack at top right: each contract held (OBJ) a paper slip with its time left on drums
 * and a state flap (blank, NAV, LATE, DONE), the navigation target's slip standing proud with its next
 * step under it; the news (NTF) on a telex strip below, newest at the bottom, important lines in red.
 */
final class Rack extends YardPart {
    static final float W = 300f;
    private static final int CARDS = 4, LINES = 3;
    private static final Color INK = Yard.Finish.YARD.ink, PAPER_RED_LINE = Yard.hex("B8352B");

    private final TaskFrame f;
    private final Array<MessageFeed.Message> telex = new Array<>();

    Rack(TaskFrame f) {
        super(980f, 0f);
        this.f = f;
    }

    /** The contract slips shown this frame: up to four, fewer when the rack must stop short. */
    private int cards = CARDS;

    /**
     * Reads what the rack shows this frame and returns its height, 0 for nothing, no taller than
     * {@code limit}: news lines go first, then slips from the bottom, so the target plate under it
     * never moves for the rack.
     */
    float measure(float limit) {
        telex.clear();
        Array<MessageFeed.Message> all = f.data.messages.all();
        for (int i = 0; i < all.size && telex.size < LINES; i++) {
            MessageFeed.Message m = all.get(i);
            if (m.secondsLeft <= 0f || m.level == Level.WARNING) continue;
            // In combat, news waits unless it is important
            if (f.combat && !m.important) continue;
            telex.add(m);
        }
        telex.reverse();
        cards = Math.min(CARDS, f.data.objectiveFrame.objectives.size);
        float h = height();
        while (h > limit && telex.size > 0) {
            telex.removeIndex(0);
            h = height();
        }
        while (h > limit && cards > 0) {
            cards--;
            h = height();
        }
        return h;
    }

    private float height() {
        float y = 9f;
        ObjectiveFrame of = f.data.objectiveFrame;
        for (int i = 0; i < cards; i++) {
            y += 26f;
            if (navLine(of.objectives.get(i), of) != null) y += 19f;
        }
        if (telex.size > 0) {
            if (cards > 0) y += 2f;
            y += 5f + telex.size * 16f + 8f + 6f;
        }
        return cards == 0 && telex.size == 0 ? 0f : y + 4f;
    }

    @Override
    protected void draw(YardPaint p) {
        p.unit(980f, 0f, W, h, Yard.Finish.YARD, "tr");
        float y = 9f;
        ObjectiveFrame of = f.data.objectiveFrame;
        int n = cards;
        for (int i = 0; i < n; i++) {
            Objective o = of.objectives.get(i);
            String st = o.state == State.DONE || o.state == State.RETURN ? "DONE" : o.late ? "LATE" : o.state == State.FAILED ? "FAIL" : o.nextIsNav ? "NAV" : "";
            Color ink = st.equals("LATE") || st.equals("FAIL") ? Yard.FLAP_AMBER : st.equals("DONE") ? Yard.FLAP_GREEN : Yard.FLAP_TEXT;
            p.flaps(992f, y + 2f, st, 4, 9f, 16f, 12.5f, ink);
            float raise = o.nextIsNav ? -1.5f : 0f;
            p.paper(1036f, y + raise, 172f, 20f);
            p.rect(1036f, y + raise, 172f, 2f, PAPER_RED_LINE, 0.55f);
            String title = o.kind.isEmpty() ? o.title : o.kind + ": " + o.title;
            p.typed(YardText.cut(title, 25), 1042f, y + 14.5f + raise, 12.5f, Yard.PAPER_INK);
            double left = o.timeLeft;
            if (Double.isNaN(left)) {
                p.drums(1214f, y + 1f, " ", 11f, 18f, 14f, false);
                p.drums(1240f, y + 1f, "  ", 11f, 18f, 14f, false);
            } else {
                long s = (long) Math.max(0.0, left);
                p.drums(1214f, y + 1f, String.valueOf(Math.min(9L, s / 86400)), 11f, 18f, 14f, false);
                p.drums(1240f, y + 1f, String.format(Locale.ROOT, "%02d", (s / 3600) % 24), 11f, 18f, 14f, false);
            }
            p.legend("D", 1228f, y + 15f, INK);
            p.legend("H", 1265f, y + 15f, INK);
            y += 26f;
            String nl = navLine(o, of);
            if (nl != null) {
                p.flaps(1036f, y - 3f, YardText.fit(nl, 28), 28, 8f, 15f, 12.5f, Yard.FLAP_TEXT);
                y += 19f;
            }
        }
        if (telex.size > 0) {
            if (n > 0) y += 2f;
            p.rrect(986f, y, 288f, 6f, 2f, Yard.BAKELITE);
            p.rect(990f, y + 2f, 280f, 2f, Color.BLACK);
            float py = y + 5f, ph = telex.size * 16f + 8f;
            p.paper(990f, py, 280f, ph);
            // The torn foot of the telex strip
            for (int i = 0; i < 28; i++) p.rect(990f + i * 10f + 5f, py + ph - 3f, 5f, 3f, Yard.SHADOW, 0.25f);
            for (int i = 0; i < telex.size; i++) {
                MessageFeed.Message m = telex.get(i);
                p.typed(YardText.cut(m.label(), 36), 998f, py + 15f + i * 16f, 13f, m.important ? Yard.PAPER_RED : Yard.PAPER_INK);
            }
        }
    }

    /** The slip's second line: only for the navigation target's contract, and only with a next step. */
    private static String navLine(Objective o, ObjectiveFrame of) {
        if (!o.nextIsNav) return null;
        if (o.state == State.RETURN && !o.station.isEmpty()) return "RETURN LEG: " + o.station;
        if (of.operationSteps > 0) return "STEP " + of.operationStep + " OF " + of.operationSteps;
        return null;
    }

    @Override
    public String hover(float px, float py) {
        float ly = localY(py);
        ObjectiveFrame of = f.data.objectiveFrame;
        float y = 9f;
        for (int i = 0; i < Math.min(cards, of.objectives.size); i++) {
            Objective o = of.objectives.get(i);
            float rowH = 26f + (navLine(o, of) != null ? 19f : 0f);
            if (ly >= y && ly < y + rowH) {
                String due = Double.isNaN(o.timeLeft) ? "no deadline" : (o.late ? "late, " : "") + YardText.span(o.timeLeft).toLowerCase(Locale.ROOT) + " left";
                return (o.kind.isEmpty() ? "" : o.kind + ": ") + o.title + (o.giver.isEmpty() ? "" : ", for " + o.giver) + "  (" + due + ")";
            }
            y += rowH;
        }
        return null;
    }
}
