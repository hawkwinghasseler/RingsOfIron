package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.MessageFeed;
import com.ringsofiron.uihud.data.NoticeAlerts;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z3, the incoming rack at top right: each contract held (OBJ) a paper slip of its title with its time
 * left on drums and a state flap (blank, NAV or LATE: the game pays a contract as it ends, so none waits
 * done), the navigation target's slip standing proud with its next step under it; the news (NTF) on a
 * telex strip below, newest at the bottom, important lines in red. The news is the game's info and
 * important notices, and each warning nothing else here shows (9.2's table: a death, a crewman left
 * behind, the boarders this HUD can't raise yet); a warning the band or the crisis placard carries is
 * theirs. A title or a notice too long for its line takes a second (a title breaks before where the job
 * goes, or at a word, its time staying on the first), never a third.
 */
final class Rack extends YardPart {
    static final float W = 300f;
    private static final int CARDS = 4, LINES = 3;
    /** Characters on a slip's line, and on a telex line. */
    static final int SLIP_CHARS = 25, TELEX_CHARS = 36;
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
        boolean armed = f.data.orderState.abandonArmed > 0f || f.data.orderState.towArmed > 0f;
        for (int i = 0; i < all.size && telex.size < LINES; i++) {
            MessageFeed.Message m = all.get(i);
            if (m.secondsLeft <= 0f || !news(m, f.alerts, f.crisis, armed)) continue;
            // In combat, news waits unless it is important
            if (f.combat && !important(m)) continue;
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

    /**
     * Whether a notice is news here: an info or important notice, or a warning nothing this HUD shows
     * in its place this frame ({@link TaskAlerts#carries}). The first press of a guarded order (abandon
     * ship, the tow) asks for the second only while {@code armed}, its guard holding: after that the
     * key asks again.
     */
    static boolean news(MessageFeed.Message m, TaskAlerts alerts, boolean crisis, boolean armed) {
        if (m.level != Level.WARNING) return true;
        if (m.alert.equals(NoticeAlerts.ASKED)) return armed && !crisis;
        return !alerts.carries(m.alert, crisis);
    }

    /** Important news, in red: the game's good news, and a warning that is news unless it is of a loss and nothing more. */
    static boolean important(MessageFeed.Message m) {
        return m.important || m.level == Level.WARNING && !m.minor;
    }

    private float height() {
        float y = 9f;
        ObjectiveFrame of = f.data.objectiveFrame;
        for (int i = 0; i < cards; i++) y += slipHeight(of.objectives.get(i), of);
        if (telex.size > 0) {
            if (cards > 0) y += 2f;
            y += 5f + telexLines() * 16f + 8f + 6f;
        }
        return cards == 0 && telex.size == 0 ? 0f : y + 4f;
    }

    /** A slip's height in the rack: one or two lines of title, and the next step under the target's. */
    private static float slipHeight(Objective o, ObjectiveFrame of) {
        return 26f + (wrap(o.title, SLIP_CHARS, true).length > 1 ? 15f : 0f) + (navLine(o, of) != null ? 19f : 0f);
    }

    private int telexLines() {
        int n = 0;
        for (MessageFeed.Message m : telex) n += wrap(m.label(), TELEX_CHARS, false).length;
        return n;
    }

    /**
     * {@code s} in at most two lines of {@code n} characters: when {@code job}, before where the job
     * goes ("Deliver 40 Food" over "to Shackleton Ice") or before the faction a bounty names ("Bounty:
     * Raider" over "of the Void Raiders"); else at a word, else where it must. Past two it is cut with
     * an ellipsis.
     */
    static String[] wrap(String s, int n, boolean job) {
        if (s.length() <= n) return new String[]{s};
        int at = -1;
        if (job) {
            for (String w : new String[]{" to ", " from ", " of the "}) {
                int k = s.indexOf(w);
                if (k > 0 && k <= n && (at < 0 || k < at)) at = k;
            }
        }
        if (at < 0) at = s.lastIndexOf(' ', n);
        if (at <= 0) at = n;
        return new String[]{s.substring(0, at).trim(), YardText.cut(s.substring(at).trim(), n)};
    }

    @Override
    protected void draw(YardPaint p) {
        p.unit(980f, 0f, W, h, Yard.Finish.YARD, "tr");
        float y = 9f;
        ObjectiveFrame of = f.data.objectiveFrame;
        int n = cards;
        for (int i = 0; i < n; i++) {
            Objective o = of.objectives.get(i);
            String st = o.late ? "LATE" : o.nextIsNav ? "NAV" : "";
            Color ink = st.equals("LATE") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT;
            p.flaps(992f, y + 2f, st, 4, 9f, 16f, 12.5f, ink);
            float raise = o.nextIsNav ? -1.5f : 0f;
            String[] title = wrap(o.title, SLIP_CHARS, true);
            p.paper(1036f, y + raise, 172f, title.length > 1 ? 35f : 20f);
            p.rect(1036f, y + raise, 172f, 2f, PAPER_RED_LINE, 0.55f);
            for (int k = 0; k < title.length; k++) {
                p.typed(YardText.cut(title[k], SLIP_CHARS), 1042f, y + 14.5f + k * 15f + raise, 12.5f, Yard.PAPER_INK);
            }
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
            y += 26f + (title.length > 1 ? 15f : 0f);
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
            float py = y + 5f, ph = telexLines() * 16f + 8f;
            p.paper(990f, py, 280f, ph);
            // The torn foot of the telex strip
            for (int i = 0; i < 28; i++) p.rect(990f + i * 10f + 5f, py + ph - 3f, 5f, 3f, Yard.SHADOW, 0.25f);
            int line = 0;
            for (int i = 0; i < telex.size; i++) {
                MessageFeed.Message m = telex.get(i);
                for (String l : wrap(m.label(), TELEX_CHARS, false)) {
                    p.typed(l, 998f, py + 15f + line++ * 16f, 13f, important(m) ? Yard.PAPER_RED : Yard.PAPER_INK);
                }
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
            float rowH = slipHeight(o, of);
            if (ly >= y && ly < y + rowH) {
                String due = Double.isNaN(o.timeLeft) ? "no deadline" : (o.late ? "late, " : "") + YardText.span(o.timeLeft).toLowerCase(Locale.ROOT) + " left";
                return o.title + (o.giver.isEmpty() ? "" : ", for " + o.giver) + "  (" + due + ")";
            }
            y += rowH;
        }
        // The telex: a notice's whole text
        if (telex.size > 0) {
            if (cards > 0) y += 2f;
            float line = (ly - (y + 5f + 4f)) / 16f;
            int at = 0;
            for (MessageFeed.Message m : telex) {
                int n = wrap(m.label(), TELEX_CHARS, false).length;
                if (line >= at && line < at + n) return m.label();
                at += n;
            }
        }
        return null;
    }
}
