package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.ObjectiveProvider.ObjectiveFrame;
import com.ringsofiron.uihud.providers.ObjectiveProvider.State;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * CRS, the crisis placard: a hazard-striped case hung below the visor, the one unit let into the top of
 * the clear zone, since a disabled ship has nothing to steer by. The band already carries the title and
 * the reason, so the placard starts at what is being done about it and the choices: wait (who of the
 * crew is on each fault and the work's own time, and what comes back by itself, on a tube), call a tow,
 * abandon ship (each a guarded switch: its key twice, with its full cost typed on a slip, so the two
 * ways out can be weighed), and the air aboard. Abandoning shrinks it to one strip with the launch key.
 */
final class CrisisPlacard extends YardPart {
    static final float W = 600f, H_FULL = 272f, H_ABANDON = 76f, H_STRANDED = 96f;
    private static final float X = 340f, Y = 112f;
    /** The disabled placard's three faces: wait, the tow and abandon ship, over the air strip. */
    private static final float FACE_H = 196f;
    /** Lines the wait tube holds, and a typed slip's line spacing. */
    private static final int TUBE_LINES = 9;
    private static final float SLIP_LINE = 14f, SLIP_SIZE = 12.5f;
    private static final Color TAG_DIM = Yard.hex("5A4F42");

    private final TaskFrame f;
    private final Array<SystemRow> rows = new Array<>();
    private final Array<String> lines = new Array<>();
    private final Array<Boolean> dim = new Array<>();
    private final Array<String> typed = new Array<>();

    CrisisPlacard(TaskFrame f) {
        super(X, Y);
        this.f = f;
    }

    boolean abandoning() {
        return f.data.ctx.abandoning || f.data.orderState.abandoning;
    }

    float height() {
        return abandoning() ? H_ABANDON : f.stranded ? H_STRANDED : H_FULL;
    }

    /** The orders provider is wired: the tow's terms and the pods' seats are known. */
    private boolean known() {
        return f.data.orders != null;
    }

    @Override
    protected void draw(YardPaint p) {
        float h = height();
        // The hinges it hangs from, then the striped case and its chassis
        p.rrect(X + 60f, Y - 12f, 10f, 16f, 2f, Yard.ALU);
        p.rrect(X + W - 70f, Y - 12f, 10f, 16f, 2f, Yard.ALU);
        p.rrect(X + 2f, Y + 4f, W, h, 5f, Yard.SHADOW, 0.7f);
        p.rrect(X - 1f, Y - 1f, W + 2f, h + 2f, 6f, Yard.BAKELITE);
        p.hazard(X + 1f, Y + 1f, W - 2f, h - 2f);
        p.shade(X + 8f, Y + 8f, W - 16f, h - 16f, 3f, Yard.CHASSIS_TOP, Yard.CHASSIS_BOTTOM);
        if (abandoning()) drawAbandoning(p);
        else if (f.stranded) drawStranded(p);
        else drawDisabled(p);
    }

    private void drawDisabled(YardPaint p) {
        HudData d = f.data;
        Yard.Finish fin = Yard.Finish.YARD;
        float py = Y + 16f, ph = FACE_H;

        // Wait: what the crew is doing and how long, on a tube, since the words change with the crisis
        p.face(X + 16f, py, 192f, ph, fin, false);
        p.text(Face.LEGEND, 13f, "WAIT", X + 28f, py + 20f, fin.ink);
        p.text(Face.LEGEND, 12f, "LET THE CREW WORK", X + 66f, py + 20f, TAG_DIM);
        float tx = X + 26f, ty = py + 32f, tw = 172f, th = ph - 42f;
        p.tube(tx, ty, tw, th, 3f, 4f, false);
        waitLines(p, d, tw - 12f);
        for (int i = 0; i < lines.size && i < TUBE_LINES; i++) {
            p.tube(lines.get(i), tx + 7f, py + 47f + i * 15f, 13f, dim.get(i) ? Yard.P_DIM : Yard.P_AMBER, false);
        }
        p.tubeOver(tx, ty, tw, th);

        // Call a tow: a guarded switch, what it costs typed on a slip the way a contract's terms are
        tow(p, d, X + 216f, py, 192f, ph, fin);

        // Abandon ship: a guarded switch, the pods and their free seats against the people aboard, and
        // what it costs on a slip under them
        float ax = X + 416f;
        Orders o = d.orderState;
        p.face(ax, py, 168f, ph, fin, false);
        p.text(Face.LEGEND, 13f, "ABANDON SHIP", ax + 12f, py + 20f, fin.ink);
        boolean refused = known() && !o.abandonRefusal.isEmpty();
        p.toggle(ax + 140f, py + 34f, false, true, o.abandonArmed > 0f);
        float kw = p.key(ax + 12f, py + 29f, keyName(d, "ABANDON", "P"));
        p.legend(o.abandonArmed > 0f ? "AGAIN" : "TWICE", ax + 17f + kw, py + 41f, fin.ink);
        if (refused) p.barber(ax + 6f, py + 47f, 156f, 4f);
        if (known()) {
            // The pods and their free seats on one row, the people aboard under it
            p.legend("PODS", ax + 12f, py + 68f, fin.ink);
            float dw = p.drums(ax + 50f, py + 54f, String.valueOf(Math.min(9, o.pods)), 11f, 18f, 15f, false);
            int crew = Math.max(0, o.crewAboard - o.inPods);
            seats(p, ax + 64f + dw, py + 64f, o.freeSeats, crew);
            p.legend("ABOARD", ax + 12f, py + 90f, fin.ink);
            figures(p, ax + 64f, py + 86f, o.crewAboard, 6, o.freeSeats < crew ? Yard.RED : fin.ink);
            typed.clear();
            if (refused) wrapInto(p, "Refused: " + o.abandonRefusal, 134f, typed);
            else for (String s : abandonTerms(o, d.objectiveFrame)) wrapInto(p, s, 134f, typed);
            slip(p, ax + 10f, py + 98f, 148f, ph - 102f, typed, refused);
        } else {
            p.legend("THE FIRST ASKS, THE", ax + 12f, py + 72f, fin.dim);
            p.legend("SECOND SENDS THE", ax + 12f, py + 86f, fin.dim);
            p.legend("CREW TO THE PODS", ax + 12f, py + 100f, fin.dim);
            p.legend("ABOARD", ax + 12f, py + 124f, fin.ink);
            figures(p, ax + 64f, py + 120f, d.stations.crewAboard + 1, 6, fin.ink);
        }

        // The air aboard, as the tanks hold it
        float ay = py + ph + 8f;
        p.face(X + 16f, ay, W - 32f, 32f, fin, false);
        ShipStatus s = d.shipStatus;
        boolean emergency = !Double.isNaN(s.emergencyOxygenKg) && !Float.isNaN(s.emergencyOxygen);
        float share = emergency ? s.emergencyOxygen : s.oxygen;
        p.legend(emergency ? "EMERGENCY O2" : "AIR ABOARD", X + 28f, ay + 21f, fin.ink);
        if (!d.hasShipStatus || Float.isNaN(share)) {
            p.blank(X + 130f, ay + 7f, 240f, 18f);
        } else {
            p.level(X + 130f, ay + 11f, 240f, 10f, share, new float[][]{{0f, 0.15f}, {0.15f, 0.3f}}, new Color[]{Yard.RED, Yard.MUSTARD});
            float w = p.drums(X + 390f, ay + 6f, YardText.count(Math.round(share * 100f), 3).trim(), 13f, 19f, 15f, false);
            p.legend(emergency ? "% LEFT, TANKS DRY" : "% IN THE TANKS", X + 396f + w, ay + 21f, fin.ink);
        }
    }

    private void drawAbandoning(YardPaint p) {
        HudData d = f.data;
        Orders o = d.orderState;
        Yard.Finish fin = Yard.Finish.YARD;
        float py = Y + 16f;
        p.face(X + 16f, py, W - 32f, 44f, fin, false);
        p.lamp(X + 36f, py + 22f, 7f, Yard.RED_LAMP, (f.time * 2f) % 1f < 0.7f);
        p.text(Face.LEGEND, 14f, "ABANDONING SHIP", X + 54f, py + 20f, fin.ink);
        // Where the captain is against the nearest pod with a seat free
        String you = !known() ? "CREW TO THE PODS" : o.captainInPod ? "YOU ARE IN A POD"
                : Float.isNaN(o.podDistance) ? "NO FREE SEAT FOR YOU" : "YOU ARE " + Math.round(o.podDistance) + " M FROM A POD";
        p.legend(you, X + 54f, py + 36f, !known() ? fin.dim : o.captainInPod ? fin.ink : Yard.RED);
        float kx = X + 214f;
        if (known() && !Float.isNaN(o.podsLaunchIn)) {
            p.legend("LAUNCH T-", kx, py + 26f, fin.ink);
            kx += 58f + p.drums(kx + 58f, py + 12f, YardText.clock(Math.max(0f, o.podsLaunchIn)), 12f, 19f, 15f, false) + 12f;
        }
        float kw = p.key(kx, py + 14f, keyName(d, "ABANDON", "P"));
        p.legend("NOW", kx + kw + 5f, py + 26f, fin.ink);
        float fx = X + 408f;
        if (known()) {
            p.legend("IN PODS", fx, py + 26f, fin.ink);
            figuresIn(p, fx + 54f, py + 22f, o.inPods, o.crewAboard, 7, fin.ink);
        } else {
            p.legend("ABOARD", fx, py + 26f, fin.ink);
            figures(p, fx + 54f, py + 22f, d.stations.crewAboard + 1, 7, fin.ink);
        }
    }

    /** Stranded: not disabled, but the ship can't get home. The tow alone, its cost, and the delta-v short. */
    private void drawStranded(YardPaint p) {
        HudData d = f.data;
        Yard.Finish fin = Yard.Finish.YARD;
        float py = Y + 16f;
        p.face(X + 16f, py, W - 32f, 64f, fin, false);
        p.text(Face.LEGEND, 13f, "CALL A TOW", X + 28f, py + 20f, fin.ink);
        Orders o = d.orderState;
        towKey(p, d, X + 28f, py + 30f, 98f, fin);
        p.toggle(X + 132f, py + 32f, false, true, o.towArmed > 0f);
        typed.clear();
        if (!known()) typed.add("The sponsor's tug; days pass");
        else if (!o.towRefusal.isEmpty()) wrapInto(p, "Refused: " + o.towRefusal, 206f, typed);
        else for (String s : strandedTerms(o, d.objectiveFrame)) typed.add(s);
        slip(p, X + 160f, py + 6f, 220f, 52f, typed, known() && !o.towRefusal.isEmpty());
        // How far short the propellant leaves the ship
        FlightStatus fs = d.flightStatus;
        double need = !Double.isNaN(fs.deltaVHome) ? fs.deltaVHome : d.navReadout.planDeltaV;
        double shortBy = Double.isNaN(fs.deltaVLeft) || need <= 0 ? Double.NaN : need - fs.deltaVLeft;
        p.legend("ΔV SHORT", X + 392f, py + 26f, fin.ink);
        if (Double.isNaN(shortBy) || shortBy <= 0) p.blank(X + 452f, py + 12f, 60f, 18f);
        else {
            float w = p.drums(X + 452f, py + 11f, YardText.drums(shortBy, 4, 0), 12f, 19f, 15f, true);
            p.legend("M/S", X + 456f + w, py + 26f, fin.ink);
        }
        p.legend("OR LET THE CREW", X + 392f, py + 48f, fin.dim);
        p.legend("FLY HOME WHAT IT CAN", X + 392f, py + 60f, fin.dim);
    }

    /** The tow's face: guarded switch, key, and its full cost on a slip, or why it is refused. */
    private void tow(YardPaint p, HudData d, float wx, float py, float w, float ph, Yard.Finish fin) {
        Orders o = d.orderState;
        p.face(wx, py, w, ph, fin, false);
        p.text(Face.LEGEND, 13f, "CALL A TOW", wx + 12f, py + 20f, fin.ink);
        boolean refused = known() && !o.towRefusal.isEmpty();
        p.toggle(wx + 162f, py + 34f, false, true, o.towArmed > 0f);
        towKey(p, d, wx + 12f, py + 29f, 140f, fin);
        if (refused) p.barber(wx + 6f, py + 47f, w - 12f, 4f);
        typed.clear();
        if (!known()) {
            typed.add("The first press names");
            typed.add("the fee and standing;");
            typed.add("the second calls the");
            typed.add("sponsor's tug. Days pass.");
        } else if (refused) wrapInto(p, "Refused: " + o.towRefusal, 158f, typed);
        else for (String s : towTerms(o, d.objectiveFrame)) wrapInto(p, s, 158f, typed);
        slip(p, wx + 10f, py + 56f, 172f, ph - 62f, typed, refused);
    }

    /**
     * A typed slip of {@code text}, its paper as tall as its lines up to {@code maxH}, in red for a
     * refusal; lines past what fits are left off.
     */
    private static void slip(YardPaint p, float x, float y, float w, float maxH, Array<String> text, boolean red) {
        int fits = Math.max(1, (int) ((maxH - 10f) / SLIP_LINE));
        int n = Math.min(text.size, fits);
        p.paper(x, y, w, Math.min(maxH, 10f + Math.max(2, n) * SLIP_LINE));
        for (int i = 0; i < n; i++) {
            p.typed(text.get(i), x + 7f, y + 15f + i * SLIP_LINE, SLIP_SIZE, red ? Yard.PAPER_RED : Yard.PAPER_INK);
        }
    }

    /** {@code s} broken at its spaces into typed lines no wider than {@code width}, added to {@code out}. */
    private static void wrapInto(YardPaint p, String s, float width, Array<String> out) {
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() > 0 && p.width(Face.PAPER, SLIP_SIZE, line + " " + word) > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
    }

    /**
     * The tow's full cost, a phrase to a line before the slip breaks them (round 17): the fee, the
     * standing and the days on the rails, the contracts those days run out, and where the ship is
     * docked, as it is: "Fee 25,500 cr", "Standing -3, 3 days", "Both contracts run out", "Docked at
     * Highport with its cargo and damage".
     */
    static Array<String> towTerms(Orders o, ObjectiveFrame frame) {
        Array<String> out = new Array<>();
        out.add(String.format(Locale.ROOT, "Fee %,.0f cr", o.towFee));
        out.add(String.format(Locale.ROOT, "Standing -%.0f", o.towStanding) + (o.towDays > 0f ? ", " + days(o.towDays) : ""));
        int held = held(frame);
        if (held > 0 && o.towDays > 0f) out.add(runOut(runOut(frame, o.towDays), held));
        out.add("Docked at " + (o.towStation.isEmpty() ? "the sponsor's station" : o.towStation) + " with its cargo and damage");
        return out;
    }

    /**
     * Abandoning ship's full cost (round 17): the ship and its cargo, every contract held, the
     * standing and the days, and the ship the sponsor gives free: "Ship and cargo lost", "Both
     * contracts fail", "Standing -5, 3 days", "A Patrol Corvette free at Highport, credits kept".
     */
    static Array<String> abandonTerms(Orders o, ObjectiveFrame frame) {
        Array<String> out = new Array<>();
        out.add("Ship and cargo lost");
        int held = held(frame);
        if (held > 0) out.add(held == 1 ? "The contract fails" : held == 2 ? "Both contracts fail" : "All " + held + " contracts fail");
        if (o.abandonStanding > 0f) {
            out.add(String.format(Locale.ROOT, "Standing -%.0f", o.abandonStanding) + (o.towDays > 0f ? ", " + days(o.towDays) : ""));
        }
        String where = o.towStation.isEmpty() ? "the sponsor's station" : o.towStation;
        out.add((o.abandonShip.isEmpty() ? "A ship" : article(o.abandonShip) + " " + o.abandonShip) + " free at " + where + ", credits kept");
        return out;
    }

    /** The tow's cost in three lines for the stranded strip: the fee and standing, the days and the contracts, the berth. */
    static Array<String> strandedTerms(Orders o, ObjectiveFrame frame) {
        Array<String> out = new Array<>();
        out.add(String.format(Locale.ROOT, "Fee %,.0f cr, standing -%.0f", o.towFee, o.towStanding));
        int held = held(frame);
        String days = o.towDays > 0f ? days(o.towDays) : "Days";
        if (held > 0 && o.towDays > 0f) out.add(days + "; " + runOut(runOut(frame, o.towDays), held).toLowerCase(Locale.ROOT));
        else out.add(days + " on the rails");
        out.add("Docked at " + (o.towStation.isEmpty() ? "the sponsor's station" : o.towStation) + " as it is");
        return out;
    }

    /** The contracts held: those under way or on the way back to be paid. */
    static int held(ObjectiveFrame frame) {
        int n = 0;
        for (Objective ob : frame.objectives) if (ob.state == State.ACTIVE || ob.state == State.RETURN) n++;
        return n;
    }

    /** The contracts held whose deadline falls inside {@code days} on the rails, so they fail out of time. */
    static int runOut(ObjectiveFrame frame, float days) {
        int n = 0;
        for (Objective ob : frame.objectives) {
            if (ob.state != State.ACTIVE && ob.state != State.RETURN) continue;
            if (!Double.isNaN(ob.timeLeft) && ob.timeLeft < days * 86_400.0) n++;
        }
        return n;
    }

    /** "Both contracts run out", "The contract runs out", "1 of 3 contracts runs out", "No contract runs out". */
    static String runOut(int n, int held) {
        if (n <= 0) return "No contract runs out";
        if (n == held) return n == 1 ? "The contract runs out" : n == 2 ? "Both contracts run out" : "All " + n + " contracts run out";
        return n + " of " + held + " contracts run" + (n == 1 ? "s" : "") + " out";
    }

    /** "3 days", "1 day", "12 h". */
    static String days(float days) {
        if (days < 1f) return Math.max(1, Math.round(days * 24f)) + " h";
        int n = Math.round(days);
        return n == 1 ? "1 day" : n + " days";
    }

    private static String article(String name) {
        return name.isEmpty() || "AEIOUaeiou".indexOf(name.charAt(0)) < 0 ? "A" : "An";
    }

    /**
     * The tow's key and TWICE (AGAIN once pressed), where the key calls the tug: at the helm and on deck.
     * At the weapons console it does nothing, so the face says where to go instead of naming a dead key.
     */
    private void towKey(YardPaint p, HudData d, float x, float y, float maxW, Yard.Finish fin) {
        if (f.gunnery()) {
            p.legendFit("FROM HELM OR DECK", x, y + 12f, maxW, fin.dim);
            return;
        }
        float kw = p.key(x, y, keyName(d, "CUT", "B"));
        p.legend(d.orderState.towArmed > 0f ? "AGAIN" : "TWICE", x + 5f + kw, y + 12f, fin.ink);
    }

    /** Seat lamps: lit for each free seat, an amber dark lamp for each person who has none. */
    private static void seats(YardPaint p, float x, float y, int free, int crew) {
        int n = Math.min(5, Math.max(free, crew));
        for (int i = 0; i < n; i++) {
            boolean seat = i < free;
            p.lamp(x + i * 16f, y, 5f, seat ? Yard.GREEN_LAMP : Yard.RED_LAMP, seat || i < crew);
        }
    }

    /** The people aboard, those in a pod filled and the rest drawn hollow. */
    private static void figuresIn(YardPaint p, float x, float y, int in, int n, int max, Color c) {
        int shown = Math.min(n, max);
        for (int i = 0; i < shown; i++) {
            float cx = x + i * 16f;
            if (i < in) {
                p.circle(cx, y - 5f, 3f, c);
                p.rrect(cx - 4.5f, y - 1f, 9f, 7f, 3.5f, c);
            } else {
                p.circle(cx, y - 5f, 3f, c, 0.35f);
                p.rrect(cx - 4.5f, y - 1f, 9f, 7f, 3.5f, c, 0.35f);
            }
        }
        if (n > max) p.legend("+" + (n - max), x + shown * 16f - 2f, y + 4.5f, c);
    }

    /** People as figures, up to {@code max}, with the rest as a count. */
    private static void figures(YardPaint p, float x, float y, int n, int max, Color c) {
        int shown = Math.min(n, max);
        for (int i = 0; i < shown; i++) {
            float cx = x + i * 16f;
            p.circle(cx, y - 5f, 3f, c);
            p.rrect(cx - 4.5f, y - 1f, 9f, 7f, 3.5f, c);
        }
        if (n > max) p.legend("+" + (n - max), x + shown * 16f - 2f, y + 4.5f, c);
    }

    /**
     * What the crew is doing about it and how long: each fault that keeps the ship down, who of the
     * crew is on it and the work's own time, and what comes back by itself; else the reason, as
     * nothing clears it. A fault that doesn't fit whole is counted instead.
     */
    private void waitLines(YardPaint p, HudData d, float width) {
        lines.clear();
        dim.clear();
        rows.clear();
        for (SystemRow r : d.powerFrame.faults) {
            if (r.fault == Fault.OFF || r.fault == Fault.NO_POWER) continue;
            rows.add(r);
        }
        // Those that come back by themselves first, soonest first; then the reactor; then the rest
        rows.sort((a, b) -> {
            boolean ta = !Float.isNaN(a.timer), tb = !Float.isNaN(b.timer);
            if (ta != tb) return ta ? -1 : 1;
            if (ta) return Float.compare(a.timer, b.timer);
            boolean ra = "REACTOR".equals(a.system), rb = "REACTOR".equals(b.system);
            if (ra != rb) return ra ? -1 : 1;
            return a.fault.compareTo(b.fault);
        });
        for (int i = 0; i < rows.size; i++) {
            SystemRow r = rows.get(i);
            Alert a = d.alerts.get("sys:" + r.system + ":" + r.tileX + "," + r.tileY);
            int mark = lines.size;
            String[] words = faultWords(r, a);
            wrap(p, words[0], width, false);
            if (!words[1].isEmpty()) wrap(p, words[1], width, true);
            int rest = rows.size - 1 - i;
            if (i > 0 && lines.size + (rest > 0 ? 1 : 0) > TUBE_LINES) {
                lines.truncate(mark);
                dim.truncate(mark);
                int more = rows.size - i;
                add(p, "+" + more + (more == 1 ? " MORE FAULT" : " MORE FAULTS"), width, true);
                break;
            }
        }
        if (rows.size == 0) {
            String[] words = downWords(d.powerFrame, d.ctx.disabledReason);
            wrap(p, words[0], width, false);
            add(p, "", width, true);
            for (String line : words[1].split("\n")) wrap(p, line, width, true);
        }
    }

    /**
     * With no fault the crew can work keeping the ship down, the tube's words for why: a reactor
     * switched off, which the ship's console switches back on; a reactor working again a moment
     * before the game counts the ship as up, its health past the line where it runs while the crew
     * still mend it; or the reason, as nothing clears it.
     */
    static String[] downWords(PowerFrame power, String reason) {
        boolean noPower = "reactor down".equals(reason);
        if (noPower) {
            for (SystemRow r : power.faults) {
                if ("REACTOR".equals(r.system) && r.fault == Fault.OFF) {
                    return new String[]{"REACTOR SWITCHED OFF", "SWITCH IT ON AT THE\nSHIP'S CONSOLE"};
                }
            }
            if (power.outputWatts > 0) return new String[]{"REACTOR BACK ON LINE", "POWER COMING BACK"};
        }
        return new String[]{reason.toUpperCase(Locale.ROOT), "NOTHING CLEARS IT\nBY ITSELF"};
    }

    /**
     * Fault {@code r} in the tube's words: what the crew is doing about it, by name with the work's own
     * time where the game knows them, and what it does by itself (round 17):
     * {"ADA GOING TO THE REACTOR'S SHORT, 5 S TO RESET IT THERE;", "THE BREAKER RECLOSES BY ITSELF IN 38 S"}.
     * The walk there is not known, so on the way the time is the work's alone.
     */
    static String[] faultWords(SystemRow r, Alert a) {
        String sys = r.system.toUpperCase(Locale.ROOT);
        String who = a == null ? "" : a.who.toUpperCase(Locale.ROOT);
        int crew = a == null ? -1 : a.handling;
        String work = a == null || Float.isNaN(a.workLeft) ? "" : YardText.span(a.workLeft);
        String doing, then = "";
        switch (r.fault) {
            case SHORTED:
                if (!who.isEmpty() && a.atWork) doing = who + " RESETTING THE " + sys + "'S SHORT" + (work.isEmpty() ? "" : ", " + work);
                else if (!who.isEmpty()) doing = who + " GOING TO THE " + sys + "'S SHORT" + (work.isEmpty() ? "" : ", " + work + " TO RESET IT THERE");
                else doing = sys + " SHORTED" + nobody(crew);
                if (!Float.isNaN(r.timer)) then = "THE BREAKER RECLOSES BY ITSELF IN " + YardText.span(r.timer);
                break;
            case STUNNED:
                doing = sys + " STUNNED";
                if (!Float.isNaN(r.timer)) then = "IT WEARS OFF IN " + YardText.span(r.timer);
                break;
            default:
                // Damaged or wrecked: the crew repair it where it stands, from nothing if need be
                if (!who.isEmpty() && a.atWork) doing = who + " REPAIRING THE " + sys + (work.isEmpty() ? "" : ", " + work);
                else if (!who.isEmpty()) doing = who + " GOING TO THE " + sys + (work.isEmpty() ? "" : ", " + work + " TO REPAIR IT THERE");
                else doing = sys + (r.fault == Fault.DESTROYED ? " WRECKED" : " DAMAGED") + (crew < 0 ? ", NEEDS REPAIR" : nobody(crew));
        }
        if (!then.isEmpty()) doing += ";";
        return new String[]{doing, then};
    }

    /** After a fault nobody is named on: how many are on it, ", NOBODY ON IT", or nothing where the game can't say. */
    private static String nobody(int crew) {
        if (crew > 0) return crew == 1 ? ", ONE CREW ON IT" : ", " + crew + " CREW ON IT";
        return crew == 0 ? ", NOBODY ON IT" : "";
    }

    private void add(YardPaint p, String s, float width, boolean isDim) {
        lines.add(p.width(Face.TUBE, 13f, s) <= width ? s : YardText.cut(s, Math.max(4, (int) (s.length() * width / p.width(Face.TUBE, 13f, s)))));
        dim.add(isDim);
    }

    private void wrap(YardPaint p, String s, float width, boolean isDim) {
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && p.width(Face.TUBE, 13f, next) > width) {
                add(p, line.toString(), width, isDim);
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) add(p, line.toString(), width, isDim);
    }

    static String keyName(HudData d, String action, String fallback) {
        String k = d.key(action);
        return (k.isEmpty() ? fallback : k).toUpperCase(Locale.ROOT);
    }

    @Override
    public String hover(float px, float py) {
        if (abandoning()) return null;
        float lx = localX(px);
        HudData d = f.data;
        if (f.stranded || (lx >= X + 216f && lx < X + 408f)) {
            String why = known() && !d.orderState.towRefusal.isEmpty() ? ", refused: " + d.orderState.towRefusal : "";
            String where = f.gunnery() ? " at the helm or on deck" : "";
            String fee = known() && d.orderState.towFee > 0 ? ". The fee is taken even if it leaves the account below zero" : "";
            return "Call the sponsor's tug: " + keyName(d, "CUT", "B") + " twice" + where + why + fee + runningOut(d);
        }
        if (lx >= X + 416f) {
            String why = known() && !d.orderState.abandonRefusal.isEmpty() ? ", refused: " + d.orderState.abandonRefusal : "";
            return "Abandon ship: " + keyName(d, "ABANDON", "P") + " twice; once the crew are going, " + keyName(d, "ABANDON", "P")
                    + " launches the pods" + why + ". Anyone not in a pod is lost with the ship";
        }
        return null;
    }

    /** The contracts the tow's days run out, by name, for its hover: ". Runs out: Food to Shackleton Ice". */
    private String runningOut(HudData d) {
        Orders o = d.orderState;
        if (!known() || o.towDays <= 0f) return "";
        StringBuilder names = new StringBuilder();
        for (Objective ob : d.objectiveFrame.objectives) {
            if (ob.state != State.ACTIVE && ob.state != State.RETURN) continue;
            if (Double.isNaN(ob.timeLeft) || ob.timeLeft >= o.towDays * 86_400.0) continue;
            if (names.length() > 0) names.append("; ");
            names.append(ob.title);
        }
        return names.length() == 0 ? "" : ". Runs out: " + names;
    }
}
