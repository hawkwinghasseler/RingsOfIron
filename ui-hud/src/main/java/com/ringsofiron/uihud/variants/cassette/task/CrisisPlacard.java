package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
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
 * the reason, so the placard starts at what is being done about it and the choices: wait (the faults
 * and their own timers, on a tube), call a tow, abandon ship (each a guarded switch: its key twice), and
 * the air aboard. Abandoning shrinks it to one strip with the launch key.
 */
final class CrisisPlacard extends YardPart {
    static final float W = 600f, H_FULL = 212f, H_ABANDON = 76f, H_STRANDED = 96f;
    private static final float X = 340f, Y = 112f;
    private static final Color TAG_DIM = Yard.hex("5A4F42");

    private final TaskFrame f;
    private final Array<SystemRow> rows = new Array<>();
    private final Array<String> lines = new Array<>();
    private final Array<Boolean> dim = new Array<>();

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
        float py = Y + 16f, ph = 136f;

        // Wait: what the crew is doing and how long, on a tube, since the words change with the crisis
        p.face(X + 16f, py, 192f, ph, fin, false);
        p.text(Face.LEGEND, 13f, "WAIT", X + 28f, py + 20f, fin.ink);
        p.text(Face.LEGEND, 12f, "LET THE CREW WORK", X + 66f, py + 20f, TAG_DIM);
        float tx = X + 26f, ty = py + 32f, tw = 172f, th = 94f;
        p.tube(tx, ty, tw, th, 3f, 4f, false);
        waitLines(p, d, tw - 12f);
        for (int i = 0; i < lines.size && i < 6; i++) {
            p.tube(lines.get(i), tx + 7f, py + 47f + i * 15f, 13f, dim.get(i) ? Yard.P_DIM : Yard.P_AMBER, false);
        }
        p.tubeOver(tx, ty, tw, th);

        // Call a tow: a guarded switch, what it costs typed on a slip the way a contract's terms are
        tow(p, d, X + 216f, py, 192f, ph, fin);

        // Abandon ship: a guarded switch, the pods and their free seats against the people aboard
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
            p.legend("PODS", ax + 12f, py + 68f, fin.ink);
            p.drums(ax + 57f, py + 54f, String.valueOf(Math.min(9, o.pods)), 11f, 18f, 15f, false);
            p.legend("SEATS", ax + 12f, py + 96f, fin.ink);
            int crew = Math.max(0, o.crewAboard - o.inPods);
            seats(p, ax + 62f, py + 92f, o.freeSeats, crew);
            p.legend("ABOARD", ax + 12f, py + 124f, fin.ink);
            figures(p, ax + 64f, py + 120f, o.crewAboard, 6, o.freeSeats < crew ? Yard.RED : fin.ink);
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

    /** Stranded: not disabled, but the ship can't get home. The tow alone, and the delta-v short. */
    private void drawStranded(YardPaint p) {
        HudData d = f.data;
        Yard.Finish fin = Yard.Finish.YARD;
        float py = Y + 16f;
        p.face(X + 16f, py, W - 32f, 64f, fin, false);
        p.text(Face.LEGEND, 13f, "CALL A TOW", X + 28f, py + 20f, fin.ink);
        Orders o = d.orderState;
        towKey(p, d, X + 28f, py + 30f, 98f, fin);
        p.toggle(X + 132f, py + 32f, false, true, o.towArmed > 0f);
        p.paper(X + 160f, py + 10f, 210f, 44f);
        p.typed(String.format(Locale.ROOT, "Fee %,.0f cr, standing -%.0f", o.towFee, o.towStanding), X + 167f, py + 26f, 12.5f, Yard.PAPER_INK);
        p.typed("The sponsor's tug; days pass", X + 167f, py + 41f, 12.5f, Yard.PAPER_INK);
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

    /** The tow's face: guarded switch, key, and its terms on a slip, or why it is refused. */
    private void tow(YardPaint p, HudData d, float wx, float py, float w, float ph, Yard.Finish fin) {
        Orders o = d.orderState;
        p.face(wx, py, w, ph, fin, false);
        p.text(Face.LEGEND, 13f, "CALL A TOW", wx + 12f, py + 20f, fin.ink);
        boolean refused = known() && !o.towRefusal.isEmpty();
        p.toggle(wx + 162f, py + 34f, false, true, o.towArmed > 0f);
        towKey(p, d, wx + 12f, py + 29f, 140f, fin);
        if (refused) p.barber(wx + 6f, py + 47f, w - 12f, 4f);
        p.paper(wx + 10f, py + 56f, 172f, 70f);
        String[] slip;
        if (!known()) slip = new String[]{"The first press names", "the fee and standing;", "the second calls the", "sponsor's tug. Days pass."};
        else if (refused) slip = wrapTyped("Refused: " + o.towRefusal, 4);
        else slip = new String[]{String.format(Locale.ROOT, "Fee %,.0f cr", o.towFee), String.format(Locale.ROOT, "Standing -%.0f", o.towStanding),
                "Days pass; the sponsor's", "tug brings you in"};
        for (int i = 0; i < slip.length; i++) p.typed(slip[i], wx + 17f, py + 72f + i * 15f, 12.5f, refused ? Yard.PAPER_RED : Yard.PAPER_INK);
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

    /** A typed line broken into slip lines of about 24 characters. */
    private static String[] wrapTyped(String s, int max) {
        Array<String> out = new Array<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > 24) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        while (out.size > max) out.pop();
        String[] lines = new String[out.size];
        for (int i = 0; i < out.size; i++) lines[i] = out.get(i);
        return lines;
    }

    /** Seat lamps: lit for each free seat, an amber dark lamp for each person who has none. */
    private static void seats(YardPaint p, float x, float y, int free, int crew) {
        int n = Math.min(6, Math.max(free, crew));
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
     * What the crew is doing about it and how long: each fault that keeps the ship down, its own timer
     * or what it needs, the crew on it when the game can say; else the reason, as nothing clears it.
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
        int pairs = Math.min(rows.size, 3);
        if (rows.size > 3) pairs = 2;
        for (int i = 0; i < pairs; i++) {
            SystemRow r = rows.get(i);
            add(p, r.system + " " + TaskAlerts.word(r.fault), width, false);
            Alert a = d.alerts.get("sys:" + r.system + ":" + r.tileX + "," + r.tileY);
            String crew = a != null && a.handling > 0 ? (a.handling == 1 ? "ONE CREW ON IT" : a.handling + " CREW ON IT") : "";
            String then;
            if (!Float.isNaN(r.timer)) then = (r.fault == Fault.SHORTED ? "RECLOSES IN " : "WEARS OFF IN ") + YardText.span(r.timer);
            else if (r.fault == Fault.DESTROYED) then = "NEEDS A YARD";
            else then = crew.isEmpty() ? "NEEDS REPAIR" : "BEING REPAIRED";
            add(p, crew.isEmpty() || then.equals("BEING REPAIRED") ? then : then + ", " + crew, width, true);
        }
        if (rows.size > 3) add(p, "+" + (rows.size - 2) + " MORE FAULTS", width, true);
        if (rows.size == 0) {
            String why = d.ctx.disabledReason.toUpperCase(Locale.ROOT);
            wrap(p, why, width, false);
            add(p, "", width, true);
            add(p, "NOTHING CLEARS IT", width, true);
            add(p, "BY ITSELF", width, true);
        }
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
            return "Call the sponsor's tug: " + keyName(d, "CUT", "B") + " twice" + where + why;
        }
        if (lx >= X + 416f) {
            String why = known() && !d.orderState.abandonRefusal.isEmpty() ? ", refused: " + d.orderState.abandonRefusal : "";
            return "Abandon ship: " + keyName(d, "ABANDON", "P") + " twice; once the crew are going, " + keyName(d, "ABANDON", "P") + " launches the pods" + why;
        }
        return null;
    }
}
