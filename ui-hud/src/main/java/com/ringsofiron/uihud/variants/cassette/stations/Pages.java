package com.ringsofiron.uihud.variants.cassette.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.interiorgrid.Compartment;
import com.ringsofiron.interiorgrid.RoomNames;
import com.ringsofiron.interiorgrid.ShipGridComponent;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.minimap.ShipAlerts;
import com.ringsofiron.uihud.minimap.TileShade;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.ContactProvider.Kind;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Capture;
import com.ringsofiron.uihud.providers.DockingApproachProvider.Clearance;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Circuit;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider.Hand;
import com.ringsofiron.uihud.providers.StationBoardProvider.Post;
import com.ringsofiron.uihud.providers.StationBoardProvider.Seat;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardFonts.Face;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardSymbols;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * The thirteen MFD pages (spec 9.2), drawn on an MFD's tube: a reversed title bar with the page's name and
 * its headline, then up to five rows of phosphor text, amber for what reads normally, dim for what is
 * there but quiet, red for what threatens; drawings in green. A page says what its station knows and
 * nothing it doesn't: where the game has no number for a field, the field is left out rather than guessed.
 */
final class Pages {
    /** The rows' size, and how far apart they stand. */
    static final float SIZE = 12.5f, PITCH = 15f;
    /** The SENSORS scope's range steps, km (9.2: 0.5 to 1,000, with the 5 km step where fights are). */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};

    private static final Color AMBER = Yard.P_AMBER, DIM = Yard.P_DIM, RED = Yard.P_RED, GREEN = Yard.P_GREEN, GDIM = Yard.P_GDIM;
    /** What the helm would refuse now: a step under dim, the same phosphor. */
    private static final Color REFUSED = Yard.hex("6E4C1B");
    private static final char K = YardPaint.KEY;

    private final ShipPlan plan = new ShipPlan();
    private final Array<Contact> list = new Array<>();
    private final Array<SystemRow> faults = new Array<>();
    private final Array<Compartment> rooms = new Array<>();
    private final float[] ellipse = new float[2 * 49];

    private YardPaint p;
    private StationsFrame f;
    private HudData d;
    private float tx, ty, tw, th;

    /** Draws {@code page} on the tube at ({@code tx}, {@code ty}), {@code tw} by {@code th}. */
    void draw(YardPaint p, StationsFrame f, Page page, float tx, float ty, float tw, float th) {
        this.p = p;
        this.f = f;
        this.d = f.data;
        this.tx = tx;
        this.ty = ty;
        this.tw = tw;
        this.th = th;
        switch (page) {
            case NAV: nav(); break;
            case PLAN: plan(); break;
            case HELM: helm(); break;
            case DOCK: dock(); break;
            case SENSORS: sensors(); break;
            case WEAPONS: weapons(); break;
            case DEFENSE: defense(); break;
            case DAMAGE: damage(); break;
            case POWER: power(); break;
            case ATMOS: atmos(); break;
            case COMMS: comms(); break;
            case CREW: crew(); break;
            case CONTRACTS: contracts(); break;
            default: break;
        }
    }

    /** The dead page: a console without power shows this, never a stale page (9.1, 5.1 rule 3). */
    static void dead(YardPaint p, float tx, float ty, float tw, float th, String text) {
        p.textC(Face.TUBE, SIZE, text, tx + tw / 2f, ty + th / 2f + 4f, Yard.P_DIM);
    }

    // --- Pieces --------------------------------------------------------------------------------------

    /** The title bar: the page's name on the left, its headline on the right, dark on lit phosphor. */
    private void title(String left, String right, Color c) {
        float x = tx + 3f, y = ty + 14f - SIZE + 1.5f, w = tw - 10f, h = SIZE + 3f;
        p.glowRect(x, y, w, h, 3f, c, 0.25f);
        p.rect(x, y, w, h, c);
        p.text(Face.TUBE_BOLD, SIZE, left, tx + 10f, ty + 14f, Yard.CRT_BLACK);
        if (right != null && !right.isEmpty()) {
            float room = w - 16f - p.width(Face.TUBE_BOLD, SIZE, left) - 12f;
            String r = right;
            while (r.length() > 1 && p.width(Face.TUBE_BOLD, SIZE, r) > room) r = r.substring(0, r.length() - 2) + "…";
            p.textR(Face.TUBE_BOLD, SIZE, r, x + w - 7f, ty + 14f, Yard.CRT_BLACK);
        }
    }

    private void title(String left, String right) {
        title(left, right, AMBER);
    }

    /** Row {@code i}'s baseline, from 1 under the title to 5 at the tube's foot. */
    private float row(int i) {
        return ty + 20f + PITCH * i;
    }

    /** A row of text from {@code x}, cut at the tube's edge. */
    private void line(float x, int i, String s, Color c) {
        Hardware.tubeFit(p, s, x, row(i), SIZE, c, tx + tw - 6f - x);
    }

    private void line(int i, String s, Color c) {
        line(tx + 8f, i, s, c);
    }

    /** A name and its value, the value starting at a fixed column so a page's values line up. */
    private void kv(float x, int i, String key, int col, String value, Color c) {
        line(x, i, pad(key, col) + value, c);
    }

    /**
     * A name and a value that runs on under itself, indented to the value's column, over at most
     * {@code rows} rows; returns the rows it took.
     */
    private int kvWrap(int i, String key, int col, String value, Color c, int rows) {
        float x = tx + 8f;
        int width = Math.max(8, Hardware.chars(p, tx + tw - 6f - x, SIZE) - col);
        java.util.List<String> parts = Hardware.wrap(value, width, rows);
        // The rows under the first start at the value's column, so the tube's bloom stays on the words
        float vx = x + p.width(Face.TUBE, SIZE, pad("", col));
        for (int k = 0; k < parts.size(); k++) {
            if (k == 0) line(x, i, pad(key, col) + parts.get(0), c);
            else line(vx, i + k, parts.get(k), c);
        }
        return Math.max(1, parts.size());
    }

    /** A row with keys in it: segments starting with the key mark are drawn reversed. */
    private void keys(int i, Color c, String... segs) {
        p.tubeLine(tx + 8f, row(i), SIZE, c, segs);
    }

    private static String pad(String s, int n) {
        if (s.length() >= n) return s + " ";
        StringBuilder b = new StringBuilder(n).append(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    private String key(String action, String fallback) {
        return K + Hardware.key(d, action, fallback);
    }

    /** A distance as a tube reads it: "850 m", "2.8 km", "1,204 km". */
    static String dist(double m) {
        if (Double.isNaN(m) || Double.isInfinite(m)) return "--";
        double a = Math.abs(m);
        String sign = m < 0 ? "-" : "";
        if (a < 1000) return sign + Math.round(a) + " m";
        if (a < 100_000) return sign + String.format(Locale.ROOT, "%.1f km", a / 1000);
        return sign + YardText.thousands(a / 1000) + " km";
    }

    /** A speed: "3.9 m/s", "210 m/s". */
    static String mps(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "--";
        return Math.abs(v) < 100 ? String.format(Locale.ROOT, "%.1f m/s", v) : Math.round(v) + " m/s";
    }

    private static String pct(float share) {
        return Float.isNaN(share) ? "--" : Math.round(share * 100f) + "%";
    }

    // --- Navigation ----------------------------------------------------------------------------------

    /** NAV: the body and the orbit about it, on a small drawing, with the numbers beside it. */
    private void nav() {
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        NavReadout n = d.navReadout;
        if (o == null) {
            title("NAV", "NO ORBIT");
            if (n.present) {
                kv(tx + 8f, 1, "TO", 9, YardText.fit(n.name, 18), AMBER);
                kv(tx + 8f, 2, "RANGE", 9, dist(n.distance), AMBER);
                kv(tx + 8f, 3, "CLOSING", 9, mps(n.closing), AMBER);
                if (!Double.isNaN(n.cpaDistance)) kv(tx + 8f, 4, "CPA", 9, dist(n.cpaDistance) + " in " + YardText.clock(n.cpaIn), AMBER);
            } else line(1, "No body holds the ship.", DIM);
            return;
        }
        boolean escape = escape(o);
        title("NAV", YardText.fit(o.bodyName, 14), o.impactWarning ? RED : AMBER);
        orbit(o, escape, tx + 52f, ty + 62f);
        float x = tx + 112f;
        kv(x, 1, "ALT", 7, dist(o.altitude), AMBER);
        kv(x, 2, "AP", 7, escape ? "ESCAPE" : dist(o.apoapsis), AMBER);
        kv(x, 3, "PE", 7, dist(o.periapsis), o.impactWarning ? RED : AMBER);
        kv(x, 4, "PERIOD", 7, escape ? "--" : YardText.clock(o.period), AMBER);
        if (o.impactWarning) line(x, 5, "IMPACT", RED);
        else if (n.present) kv(x, 5, "TO", 7, YardText.fit(n.name, 10), DIM);
    }

    private static boolean escape(OrbitInfo o) {
        return Double.isInfinite(o.period) || Double.isNaN(o.apoapsis) || Double.isInfinite(o.apoapsis) || o.apoapsis < 0;
    }

    /**
     * The orbit drawn round the body: the body's circle, the orbit's ellipse with the body at its focus and
     * its height above the surface to scale between the two (the apoapsis at the outer ring), the apsides
     * ticked, the ship a dot; any of it that runs below the surface in red.
     */
    private void orbit(OrbitInfo o, boolean escape, float cx, float cy) {
        float body = 17f, outer = 33f;
        circle(cx, cy, body, GDIM, 1.2f);
        double top = escape ? Math.max(o.periapsis, 1) * 4 : Math.max(Math.max(o.apoapsis, o.periapsis), 1);
        float ra = escape ? outer * 1.6f : outer;
        float rp = body + (outer - body) * (float) (Math.max(o.periapsis, -top) / top);
        rp = Math.max(4f, rp);
        float a = (ra + rp) / 2f, c = (ra - rp) / 2f, b = (float) Math.sqrt(Math.max(0f, a * a - c * c));
        // Periapsis to the right of the body, apoapsis to the left; the ellipse's centre is c off the focus
        int n = 48;
        for (int i = 0; i < n; i++) {
            float u = MathUtils.PI2 * i / n, v = MathUtils.PI2 * (i + 1) / n;
            float x1 = cx - c + a * MathUtils.cos(u), y1 = cy - b * MathUtils.sin(u);
            float x2 = cx - c + a * MathUtils.cos(v), y2 = cy - b * MathUtils.sin(v);
            if (escape && (x1 < cx - outer || x2 < cx - outer)) continue;
            boolean under = Math.hypot(x1 - cx, y1 - cy) < body || Math.hypot(x2 - cx, y2 - cy) < body;
            p.line(x1, y1, x2, y2, 1.2f, under ? RED : GREEN, 0.95f);
        }
        p.line(cx + rp, cy - 4f, cx + rp, cy + 4f, 1.4f, o.impactWarning ? RED : GREEN);
        if (!escape) p.line(cx - ra, cy - 4f, cx - ra, cy + 4f, 1.4f, GREEN);
        // The ship: its height on the same scale, on the near side of the ellipse
        float rs = body + (outer - body) * (float) (Math.max(o.altitude, 0) / top);
        float e = a > 0f ? c / a : 0f, th0 = MathUtils.PI / 2f;
        if (e > 0.01f) {
            float cos = (a * (1f - e * e) / Math.max(rs, 1f) - 1f) / e;
            th0 = (float) Math.acos(MathUtils.clamp(cos, -1f, 1f));
        }
        float r = a * (1f - e * e) / (1f + e * MathUtils.cos(th0));
        float sx = cx + r * MathUtils.cos(th0), sy = cy - r * MathUtils.sin(th0);
        p.glow(sx, sy, 6f, GREEN, 0.4f);
        p.circle(sx, sy, 2.4f, GREEN);
    }

    private void circle(float cx, float cy, float r, Color c, float w) {
        int n = 48;
        for (int i = 0; i <= n; i++) {
            float u = MathUtils.PI2 * i / n;
            ellipse[2 * i] = cx + r * MathUtils.cos(u);
            ellipse[2 * i + 1] = cy + r * MathUtils.sin(u);
        }
        p.polyline(ellipse, true, w, c, 0.95f);
    }

    /** PLAN: the burns, the plan's delta-v against what the tanks hold, the proposal, the autopilot. */
    private void plan() {
        NavReadout n = d.navReadout;
        Orders o = d.orderState;
        String right = n.proposal ? "PROPOSAL" : n.burnsLeft > 0 ? n.burnsLeft + (n.burnsLeft == 1 ? " BURN" : " BURNS") : "NO PLAN";
        title("PLAN", right);
        if (n.burning) {
            line(1, "BURN " + n.burnNumber + " burning, " + mps(n.burnToGo) + " to go", AMBER);
        } else if (!Float.isNaN(n.burnIn)) {
            line(1, "BURN " + n.burnNumber + (n.burnsPlanned > 1 ? "/" + n.burnsPlanned : "") + " in " + YardText.clock(n.burnIn) + "  " + mps(n.burnDeltaV), AMBER);
        } else line(1, n.proposal ? "A transfer is proposed." : "No burns planned.", DIM);

        double left = d.flightStatus.deltaVLeft, need = n.planDeltaV;
        int r = 3;
        if (need > 0 && !Double.isNaN(left)) {
            boolean short_ = !Double.isInfinite(left) && need > left;
            line(2, "PLAN " + Math.round(need) + " of " + (Double.isInfinite(left) ? "free" : Math.round(left) + " m/s") + " left", short_ ? RED : AMBER);
            double home = d.flightStatus.deltaVHome;
            if (!Double.isNaN(home) && !Double.isInfinite(left)) {
                double after = left - need;
                line(r++, "AFTER " + Math.round(Math.max(0, after)) + " m/s, home " + Math.round(home) + (after < home ? ": SHORT" : ""), after < home ? RED : DIM);
            }
        } else if (!Double.isNaN(left)) {
            line(2, "DELTA-V " + (Double.isInfinite(left) ? "free" : Math.round(left) + " m/s"), DIM);
        }

        if (n.proposal) keys(r++, AMBER, key("ACCEPT_PLAN", "ENTER"), "accept ", key("DISCARD_PLAN", "BKSP"), "discard");
        else if (n.burnsLeft > 0) keys(r++, DIM, key("HELM_FLY_BURNS", "1"), "helm flies ", key("WARP_TO_BURN", "/"), "warp to burn");
        else keys(r++, DIM, key("PLAN_BURN", "N"), "plan ", key("PLAN_TO_TARGET", ";"), "to target");

        int rows = 6 - r;
        switch (o.autopilot) {
            case ON:
                kvWrap(r, "AUTOPILOT", 10, o.autopilotTo.isEmpty() ? "on" : "to " + o.autopilotTo, AMBER, rows);
                break;
            case HELD:
                kvWrap(r, "AUTOPILOT", 10, "held: " + o.autopilotHeld, AMBER, rows);
                break;
            default:
                if (o.autopilotRefusal.isEmpty()) keys(r, DIM, key("AUTOPILOT", "6"), "autopilot");
                else kvWrap(r, "AUTOPILOT", 10, why(o.autopilotRefusal, "The autopilot "), DIM, rows);
                break;
        }
    }

    /** A refusal without the subject the row's name already gives: "The autopilot needs..." to "needs...". */
    static String why(String refusal, String subject) {
        return refusal.regionMatches(true, 0, subject, 0, subject.length()) ? refusal.substring(subject.length()) : refusal;
    }

    // --- Helm --------------------------------------------------------------------------------------

    /** HELM: who has the helm, the order in force and how it is going, and the orders that can be given. */
    private void helm() {
        Orders o = d.orderState;
        String who = d.ctx.helmsman;
        String right = who.isEmpty() ? "EMPTY" : who.equalsIgnoreCase("you") ? "YOU" : YardText.fit(who, 12);
        title("HELM", right, d.ctx.helmFault.isEmpty() ? AMBER : RED);
        String order = !d.ctx.helmOrder.isEmpty() ? d.ctx.helmOrder : o.helm == HelmOrder.NONE ? "No order: by hand." : o.helm.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        kv(tx + 8f, 1, "ORDER", 6, order, AMBER);
        if (!o.helmLine.isEmpty()) line(2, o.helmLine, DIM);
        if (!d.ctx.helmFault.isEmpty()) line(3, d.ctx.helmFault, RED);
        // The orders, keys first; one the helm would refuse now is dim
        float x = tx + 8f;
        x = order(x, 4, HelmOrder.FLY_BURNS, "HELM_FLY_BURNS", "1", "burns ");
        x = order(x, 4, HelmOrder.MATCH_VELOCITY, "HELM_MATCH_VELOCITY", "2", "match ");
        order(x, 4, HelmOrder.HOLD_POSITION, "HELM_HOLD", "3", "hold");
        x = tx + 8f;
        x = order(x, 5, HelmOrder.APPROACH, "HELM_APPROACH", "4", "near ");
        x = order(x, 5, HelmOrder.DOCK, "HELM_DOCK", "5", "dock ");
        x += p.tubeLine(x, row(5), SIZE, DIM, key("AUTOPILOT", "6"), "auto ");
        p.tubeLine(x, row(5), SIZE, DIM, key("HELM_CANCEL", "0"), "off");
    }

    private float order(float x, int i, HelmOrder order, String action, String fallback, String word) {
        boolean refused = !d.orderState.helmRefusal[order.ordinal()].isEmpty();
        boolean standing = d.orderState.helm == order;
        return x + p.tubeLine(x, row(i), SIZE, standing ? AMBER : refused ? REFUSED : DIM, key(action, fallback), word);
    }

    /** DOCK: the ship's own port and the one it lines up with or is latched to, clearance, the collar's strain. */
    private void dock() {
        DockingApproach a = d.docking;
        String right = !a.other.isEmpty() ? YardText.fit(a.other, 14) : d.ctx.docked ? YardText.fit(d.ctx.stationName, 14) : "NO PORT";
        title("DOCK", right);
        if (!a.present) {
            line(1, "No docking port fitted.", DIM);
            return;
        }
        String own = a.port + "/" + a.ports + "  " + a.portSize;
        String state = a.collarTorn ? "COLLAR TORN" : a.collarJammed ? "JAMMED" : a.capture == Capture.DOCKED ? "LATCHED"
                : a.capture == Capture.CAPTURED ? "CAPTURED" : a.armed ? "ARMED" : "SAFE";
        kv(tx + 8f, 1, "OWN PORT", 10, own + "  " + state, a.collarTorn || a.collarJammed ? RED : AMBER);
        if (a.otherPort > 0 || !a.other.isEmpty()) {
            String cl = clearance(a);
            kv(tx + 8f, 2, "TO", 10, (a.otherPort > 0 ? a.otherPort + "  " + a.otherPortSize + "  " : "") + cl,
                    a.clearance == Clearance.DENIED || a.clearance == Clearance.NO_PORT ? RED : AMBER);
        }
        if (!a.refusal.isEmpty()) line(3, a.refusal, RED);
        boolean latched = a.capture == Capture.DOCKED;
        if (latched || a.capture == Capture.CAPTURED) {
            boolean hard = !Float.isNaN(a.strain) && a.strain > 0.6f;
            kv(tx + 8f, 4, "STRAIN", 10, (Float.isNaN(a.strain) ? "--" : pct(a.strain)) + (a.rigid ? "  rigid" : ""), hard ? RED : AMBER);
        }
        if (latched || d.ctx.docked) keys(5, DIM, key("UNDOCK", "U"), "undock");
        else keys(5, DIM, key("NEXT_PORT", "H"), "next port ", key("ARM_PORT", "G"), a.armed ? "disarm" : "arm");
    }

    private static String clearance(DockingApproach a) {
        switch (a.clearance) {
            case GRANTED: return "GRANTED" + (Float.isNaN(a.clearanceLeft) ? "" : " " + YardText.clock(a.clearanceLeft));
            case DENIED: return "DENIED";
            case NO_PORT: return "NO FREE PORT";
            case YIELDS: return "YIELDS";
            default: return a.armed ? "ASKED" : "NOT ASKED";
        }
    }

    // --- Tactical ------------------------------------------------------------------------------------

    /** SENSORS: a radar scope north up, and the contacts by threat with their range and closing. */
    private void sensors() {
        int step = autoStep(d);
        title("SENSORS", label(step));
        float cx = tx + 48f, cy = ty + 62f, r = 38f;
        scope(cx, cy, r, STEPS[step] * 1000f);

        list.clear();
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (!Float.isNaN(c.range) && c.kind != Kind.WAYPOINT) list.add(c);
        }
        list.sort((a, b) -> rank(a) != rank(b) ? rank(a) - rank(b) : Float.compare(a.range, b.range));
        float x = tx + 96f, maxW = tx + tw - 6f - x;
        if (list.size == 0) line(x, 1, "No contacts.", DIM);
        // The name, the range, then a missile's time to reach us or the closing rate: the name gives way first
        int chars = Hardware.chars(p, maxW, LIST_SIZE), nameW = Math.max(4, Math.min(8, chars - 13));
        for (int i = 0; i < Math.min(5, list.size); i++) {
            Contact c = list.get(i);
            String name = YardText.fit(c.label.isEmpty() ? c.kind.name() : c.label, nameW - 1);
            Color col = c.kind == Kind.MISSILE && c.threat ? RED : c.relation == Relation.HOSTILE ? AMBER : c.locked || c.nav ? AMBER : DIM;
            Hardware.tubeFit(p, pad(name, nameW) + pad(dist(c.range), 8) + tail(c), x, row(i + 1), LIST_SIZE, col, maxW);
        }
    }

    /** The contact list's size: a step under the page's, so a name, a range and a time fit beside the scope. */
    private static final float LIST_SIZE = 11.5f;

    /** A contact's last column, four characters at most: "0:13" for a missile coming in, else "+104" closing. */
    static String tail(Contact c) {
        if (c.kind == Kind.MISSILE && c.threat && !Float.isNaN(c.closing) && c.closing > 0f && c.range / c.closing < 600f) return YardText.clock(c.range / c.closing);
        if (Float.isNaN(c.closing) || Math.abs(c.closing) < 0.5f || c.kind == Kind.STATION) return "";
        int v = Math.round(c.closing);
        return Math.abs(v) < 1000 ? String.format(Locale.ROOT, "%+d", v) : String.format(Locale.ROOT, "%+dk", Math.round(c.closing / 1000f));
    }

    private static int rank(Contact c) {
        if (c.kind == Kind.MISSILE && c.threat) return 0;
        if (c.threat) return 1;
        if (c.relation == Relation.HOSTILE && c.kind != Kind.DERELICT) return 2;
        if (c.locked || c.nav) return 3;
        return c.kind == Kind.MISSILE ? 5 : 4;
    }

    private void scope(float cx, float cy, float r, float range) {
        HudData d = this.d;
        circle(cx, cy, r, GDIM, 0.9f);
        circle(cx, cy, r / 2f, GDIM, 0.9f);
        p.line(cx - r, cy, cx + r, cy, 0.7f, GDIM, 0.6f);
        p.line(cx, cy - r, cx, cy + r, 0.7f, GDIM, 0.6f);
        // The sweep: a line going round with a fading wake behind it
        float sweep = (f.time * 120f) % 360f;
        for (int i = 0; i < 18; i++) {
            float a0 = sweep - i * 2.5f, a1 = sweep - (i + 1) * 2.5f;
            p.fillConvex(new float[]{cx, cy, cx + r * MathUtils.cosDeg(a0), cy + r * MathUtils.sinDeg(a0), cx + r * MathUtils.cosDeg(a1), cy + r * MathUtils.sinDeg(a1)},
                    GREEN, 0.2f * (1f - i / 18f));
        }
        p.line(cx, cy, cx + r * MathUtils.cosDeg(sweep), cy + r * MathUtils.sinDeg(sweep), 1.5f, GREEN, 0.9f);
        float hd = d.flightStatus.present && !Float.isNaN(d.flightStatus.heading) ? d.flightStatus.heading : 90f;
        YardSymbols.rel(p, YardSymbols.Rel.OWN, cx, cy, 4f, GREEN, 90f - hd, false, 1.2f, YardSymbols.State.ACTIVE, false);
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg) || c.kind == Kind.WAYPOINT) continue;
            float k = Math.min(c.range / range, 0.97f);
            float abs = hd + c.bearingDeg;
            float px = cx + k * r * MathUtils.cosDeg(abs), py = cy - k * r * MathUtils.sinDeg(abs);
            Color col = c.threat ? RED : c.kind == Kind.STATION || c.kind == Kind.DERELICT ? GDIM : GREEN;
            if (c.kind == Kind.MISSILE) YardSymbols.missile(p, px, py, c.threat ? 180f - abs : -abs, col, 0.7f);
            else if (c.kind == Kind.STATION) YardSymbols.station(p, px, py, 3f, col, 1.2f);
            else if (c.kind == Kind.EVA) p.circle(px, py, 1.6f, GREEN);
            else YardSymbols.rel(p, rel(c), px, py, 3.2f, col, 0f, c.relation == Relation.HOSTILE, 1.2f, state(c), false);
            if (c.locked) YardSymbols.bracket(p, px, py, 8f, GREEN, 1.1f);
            if (c.nav) YardSymbols.navMark(p, px, py, GREEN, 1.1f);
        }
    }

    /** The smallest step that holds the locked target and every inbound missile; else the nearest hostile, else the nearest contact. */
    static int autoStep(HudData d) {
        float need = 0f;
        boolean any = false;
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (Float.isNaN(c.range)) continue;
            if (c.locked || c.kind == Kind.MISSILE && c.threat) {
                need = Math.max(need, c.range);
                any = true;
            }
        }
        if (!any) {
            float hostile = Float.NaN, nearest = Float.NaN;
            for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
                Contact c = d.contactFrame.contacts.get(ci);
                if (Float.isNaN(c.range) || c.kind == Kind.WAYPOINT) continue;
                if (c.relation == Relation.HOSTILE && (Float.isNaN(hostile) || c.range < hostile)) hostile = c.range;
                if (Float.isNaN(nearest) || c.range < nearest) nearest = c.range;
            }
            need = !Float.isNaN(hostile) ? hostile : !Float.isNaN(nearest) ? nearest : 0f;
        }
        for (int i = 0; i < STEPS.length; i++) if (need <= STEPS[i] * 1000f * 0.85f) return i;
        return STEPS.length - 1;
    }

    static String label(int step) {
        float km = STEPS[step];
        return (km < 1f ? String.format(Locale.ROOT, "%.1f", km) : String.valueOf((int) km)) + " KM";
    }

    private static YardSymbols.Rel rel(Contact c) {
        if (c.kind == Kind.DERELICT) return YardSymbols.Rel.INERT;
        switch (c.relation) {
            case HOSTILE: return YardSymbols.Rel.HOSTILE;
            case FRIENDLY: return YardSymbols.Rel.FRIENDLY;
            default: return YardSymbols.Rel.NEUTRAL;
        }
    }

    private static YardSymbols.State state(Contact c) {
        switch (c.state) {
            case DISABLED: return YardSymbols.State.DISABLED;
            case STRUCK: case SILENCED: return YardSymbols.State.STRUCK;
            case WRECKED: return YardSymbols.State.WRECKED;
            default: return YardSymbols.State.ACTIVE;
        }
    }

    /** WEAPONS: each mount by where it sits, its state, how it fires, and its chance on the locked target. */
    private void weapons() {
        WeaponStatusProvider.WeaponFrame w = d.weaponFrame;
        boolean hold = w.holdFire || d.ctx.holdFire;
        title("WEAPONS", hold ? "HOLD FIRE" : w.target.isEmpty() ? w.mounts.size + (w.mounts.size == 1 ? " MOUNT" : " MOUNTS") : YardText.fit(w.target, 12),
                hold ? AMBER : AMBER);
        if (w.mounts.size == 0) {
            line(1, "No weapons fitted.", DIM);
            return;
        }
        float s = 11f, x0 = tx + 8f;
        int rows = Math.min(5, w.mounts.size);
        for (int i = 0; i < rows; i++) {
            Mount m = w.mounts.get(i);
            float y = row(i + 1);
            if (i == 4 && w.mounts.size > 5) {
                p.tube("+" + (w.mounts.size - 4) + " more mounts", x0, y, s, DIM, false);
                break;
            }
            Color c = m.working() ? AMBER : m.state == WeaponStatusProvider.State.DESTROYED || m.state == WeaponStatusProvider.State.EMPTY ? RED : AMBER;
            Hardware.tubeFit(p, m.name, x0, y, s, c, 84f);
            Hardware.tubeFit(p, stateWord(m), x0 + 90f, y, s, c, 66f);
            p.tube(mode(m, hold), x0 + 160f, y, s, DIM, false);
            if (!Float.isNaN(m.hitChance) && m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) {
                p.textR(Face.TUBE, s, Math.round(m.hitChance * 100f) + "%", tx + tw - 7f, y, AMBER);
            }
        }
    }

    /** A mount's state in the gun chips' words (8.3). */
    static String stateWord(Mount m) {
        switch (m.state) {
            case DESTROYED: return "DESTROYED";
            case REFIT: return "REFIT";
            case SHORTED: return m.cooldown > 0f ? "SHORTED " + Math.round(m.cooldown) : "SHORTED";
            case STUNNED: return m.cooldown > 0f ? "STUNNED " + Math.round(m.cooldown) : "STUNNED";
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case RELOADING: return String.format(Locale.ROOT, "RELOAD %.1f", Math.max(0f, m.cooldown));
            case CHARGING: return "CHARGING";
            default:
                if (m.engaging) return m.kind == WeaponStatusProvider.Kind.BEAM ? "BURNING" : "FIRING";
                return "READY";
        }
    }

    /** How a mount fires (3.5): YOURS in the player's hand, PD AUTO, HOLD, AT WILL with a gunner on it, else TRIGGER. */
    static String mode(Mount m, boolean hold) {
        if (m.kind == WeaponStatusProvider.Kind.POINT_DEFENSE || m.controller == Controller.AUTO) return "PD AUTO";
        if (m.controller == Controller.PLAYER) return "YOURS";
        if (hold) return "HOLD";
        if (m.weaponsFree) return "AT WILL";
        return "TRIGGER";
    }

    /** DEFENSE: the shield ring round the hull on a hull with an emitter, then the decoy and point defence. */
    private void defense() {
        ShieldStatus s = d.shieldStatus;
        if (f.emitter) {
            title("DEFENSE", shieldMode(s), s.emittersTripped > 0 || s.powerStunned ? RED : AMBER);
            ring(s, tx + 56f, ty + 61f, 36f);
            float x = tx + 104f;
            kv(x, 1, "MASS", 8, Math.round(s.cloud) + "/" + Math.round(s.capacity) + " kg", AMBER);
            kv(x, 2, "TRIM", 8, s.trim.toUpperCase(Locale.ROOT), AMBER);
            String em = s.emittersTripped > 0 ? s.emittersTripped + " tripped" : "heat " + Math.round(s.hottest * 100f) + "%";
            kv(x, 3, "EMITTER", 8, em, s.emittersTripped > 0 ? RED : AMBER);
            kv(x, 4, "DECOY", 8, decoy(), DIM);
            kv(x, 5, "PD", 8, pd(), DIM);
        } else {
            title("DEFENSE", "NO EMITTER");
            kv(tx + 8f, 1, "DECOY", 9, decoy(), AMBER);
            kv(tx + 8f, 2, "PD", 9, pd(), AMBER);
            kv(tx + 8f, 3, "MISSILES", 9, w().missilesLeft + " left", AMBER);
            int inbound = d.ctx.missilesInbound;
            if (inbound > 0) kv(tx + 8f, 4, "INBOUND", 9, inbound + (Float.isNaN(d.threatFrame.soonestImpact()) ? "" : ", first in " + YardText.clock(d.threatFrame.soonestImpact())), RED);
            keys(5, DIM, key("DECOY", "F"), "decoy");
        }
    }

    private WeaponStatusProvider.WeaponFrame w() {
        return d.weaponFrame;
    }

    static String shieldMode(ShieldStatus s) {
        if (s.emittersTripped > 0 && s.emittersWorking == 0) return "TRIPPED";
        if (s.powerStunned) return "STUNNED";
        if (s.raised) return "RAISED";
        if (s.loweredForDock) return "DOCKING";
        return s.cloud > 0.5f ? "STOWING" : "LOWERED";
    }

    private String decoy() {
        float cd = w().decoyCooldown;
        return cd > 0f ? String.format(Locale.ROOT, "%.1f s", cd) : "ready";
    }

    /** Point defence: AUTO, engaging while it fires, and how many work when one does not. */
    private String pd() {
        int n = 0, up = 0;
        boolean engaging = false;
        Mount down = null;
        for (int mi = 0; mi < w().mounts.size; mi++) {
            Mount m = w().mounts.get(mi);
            if (m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) continue;
            n++;
            if (m.working()) up++;
            else if (down == null) down = m;
            engaging |= m.engaging || m.defending;
        }
        if (n == 0) return "none";
        if (down != null) return "AUTO " + up + " of " + n + ", " + down.name + " " + stateWord(down).toLowerCase(Locale.ROOT);
        return engaging ? "AUTO, engaging" : "AUTO";
    }

    /** The ring of 96 segments round the hull, brighter where the cloud is thicker. */
    private void ring(ShieldStatus s, float cx, float cy, float r) {
        float max = 0f;
        for (float v : s.sectors) max = Math.max(max, v);
        float fill = s.capacity > 0f ? MathUtils.clamp(s.cloud / s.capacity, 0f, 1f) : 0f;
        for (int i = 0; i < 96; i++) {
            float a0 = i * 3.75f - 90f, a1 = a0 + 2.7f;
            // Sectors: bow up, port left, stern down, starboard right; blended between their centres
            float at = (a0 + 90f + 360f) % 360f;
            float k = sector(s, at, max);
            float b = s.raised ? 0.25f + 0.75f * k * fill : 0.12f;
            p.line(cx + r * MathUtils.cosDeg(a0), cy + r * MathUtils.sinDeg(a0), cx + r * MathUtils.cosDeg(a1), cy + r * MathUtils.sinDeg(a1),
                    2.5f + 2f * k, s.emittersTripped > 0 && s.emittersWorking == 0 ? RED : GREEN, b);
        }
        p.polyline(new float[]{cx, cy - 20f, cx + 8f, cy - 8f, cx + 8f, cy + 16f, cx - 8f, cy + 16f, cx - 8f, cy - 8f}, true, 1.2f, GDIM, 0.95f);
    }

    /** The cloud's share at {@code deg} clockwise from the bow, 0 to 1 of the thickest sector. */
    private static float sector(ShieldStatus s, float deg, float max) {
        if (max <= 0f) return 0f;
        // The sectors in clockwise order from the bow: bow, starboard, stern, port
        float[] cw = {s.sectors[0], s.sectors[3], s.sectors[2], s.sectors[1]};
        float u = deg / 90f;
        int i = (int) Math.floor(u) % 4, j = (i + 1) % 4;
        float t = u - (float) Math.floor(u);
        return MathUtils.clamp((cw[i] * (1f - t) + cw[j] * t) / max, 0f, 1f);
    }

    // --- Engineering ---------------------------------------------------------------------------------

    /** DAMAGE: the ship's plan, with what is wrong drawn in its room. */
    private void damage() {
        float hull = d.hasShipStatus ? d.shipStatus.structure : Float.NaN;
        int alerts = 0;
        for (int ai = 0; ai < d.alerts.all().size; ai++) {
            Alert a = d.alerts.all().get(ai);
            if (a.level != Level.ADVISORY && Station.of(a) == Station.ENG) alerts++;
        }
        String right = alerts > 0 ? alerts + (alerts == 1 ? " FAULT" : " FAULTS") + "  HULL " + pct(hull) : "HULL " + pct(hull);
        title("DAMAGE", right, !Float.isNaN(hull) && hull < ShipAlerts.HULL_DAMAGED ? RED : AMBER);
        plan.draw(p, d, tx + 8f, ty + 22f, tw - 16f, th - 26f, f.time);
    }

    /** POWER: output against rating and the store, then what is not working, the soonest back first. */
    private void power() {
        boolean black = f.blackout;
        double out = d.powerFrame.outputWatts, rated = d.powerFrame.ratedWatts;
        title("POWER", black ? "BLACKOUT" : kw(out) + " of " + kw(rated) + " kW", black ? RED : AMBER);
        int r = 1;
        for (int i = 0; i < d.powerFrame.circuits.size && r <= 2; i++, r++) {
            Circuit c = d.powerFrame.circuits.get(i);
            String head = d.powerFrame.circuits.size > 1 ? "C" + (i + 1) + " " : "";
            String gen = c.reactors > 0 ? kw(c.outputWatts) + "/" + kw(c.ratedWatts) + " kW" : "no reactor";
            line(r, head + "RCTR " + gen + "  STORE " + pct(c.charge()), c.reactors > 0 && c.outputWatts <= 0 ? RED : AMBER);
        }
        faults.clear();
        faults.addAll(d.powerFrame.faults);
        // In a blackout the reactor is the cause: its rows lead, and the rest come back with it
        faults.sort(black ? Pages::reactorFirst : Pages::comeBack);
        if (faults.size == 0) {
            line(r, black ? "No reactor running." : "All systems working.", black ? RED : DIM);
            return;
        }
        for (int i = 0; i < faults.size && r <= 5; i++, r++) {
            SystemRow s = faults.get(i);
            if (r == 5 && faults.size - i > 1) {
                line(r, "+" + (faults.size - i) + " more not working", DIM);
                break;
            }
            Color c = s.fault == Fault.DESTROYED || s.fault == Fault.DISABLED ? RED : s.fault == Fault.OFF ? DIM : AMBER;
            line(r, pad(s.system, 10) + pad(faultWord(s.fault), 10) + clears(s, black), c);
        }
    }

    /** A blackout's order: the reactors first, then as {@link #comeBack}. */
    static int reactorFirst(SystemRow a, SystemRow b) {
        boolean ra = a.system.equals("REACTOR"), rb = b.system.equals("REACTOR");
        if (ra != rb) return ra ? -1 : 1;
        return comeBack(a, b);
    }

    private static String kw(double w) {
        return String.format(Locale.ROOT, w < 10_000 ? "%.1f" : "%.0f", w / 1000.0);
    }

    /** The order systems come back in: those that recover by themselves first, soonest at the top; then the rest. */
    static int comeBack(SystemRow a, SystemRow b) {
        boolean ta = timed(a), tb = timed(b);
        if (ta != tb) return ta ? -1 : 1;
        if (ta) return Float.compare(a.timer, b.timer);
        return rest(a.fault) - rest(b.fault);
    }

    private static boolean timed(SystemRow r) {
        return (r.fault == Fault.SHORTED || r.fault == Fault.STUNNED) && !Float.isNaN(r.timer);
    }

    private static int rest(Fault f) {
        switch (f) {
            case NO_POWER: return 0;
            case OFF: return 1;
            case SHORTED: case STUNNED: return 2;
            case DISABLED: return 3;
            default: return 4;
        }
    }

    static String faultWord(Fault f) {
        switch (f) {
            case DESTROYED: return "DESTROYED";
            case DISABLED: return "DISABLED";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            default: return "OFF";
        }
    }

    /** What brings it back: the seconds left on a breaker or a stun, a repair, the switch. */
    /** What brings a system back: its timer, a repair, a switch, or the conduit (the reactor in a blackout). */
    static String clears(SystemRow s, boolean blackout) {
        if (timed(s)) return YardText.clock(s.timer);
        switch (s.fault) {
            case DISABLED: return "repair";
            case OFF: return "switch on";
            case NO_POWER: return blackout ? "reactor" : "conduit";
            default: return "";
        }
    }

    /** ATMOS: the rooms with the worst air first, pressure, oxygen and smoke, then the tanks. */
    private void atmos() {
        ShipGridComponent g = ShipPlan.grid(d);
        RoomNames names = d.roomNames();
        rooms.clear();
        if (g != null) for (int i = 0; i < g.compartments.size; i++) if (g.compartments.get(i).tiles.size > 0) rooms.add(g.compartments.get(i));
        rooms.sort((a, b) -> Float.compare(score(a), score(b)));
        int bad = 0;
        for (Compartment c : rooms) if (score(c) < 1f) bad++;
        title("ATMOS", bad == 0 ? "ALL BREATHABLE" : bad + (bad == 1 ? " ROOM" : " ROOMS") + " BAD", bad > 0 ? RED : AMBER);
        line(1, pad("ROOM", 11) + pad("KPA", 5) + pad("O2", 5) + "SMOKE", DIM);
        for (int i = 0; i < Math.min(3, rooms.size); i++) {
            Compartment c = rooms.get(i);
            String name = names == null || g == null ? "ROOM " + (i + 1) : names.of(g, c);
            float pr = c.pressureRatio();
            boolean pumped = ShipPlan.pumpedDown(d, g, c);
            boolean vac = pr < ShipAlerts.VACUUM_PRESSURE && !pumped, thin = !vac && pr < ShipAlerts.LOW_PRESSURE && !pumped;
            float smoke = c.smokeDensity();
            String sm = smoke > TileShade.THICK_SMOKE ? "THICK" : smoke > TileShade.THICK_SMOKE * 0.3f ? "LIGHT" : "-";
            Color col = vac ? RED : thin || !sm.equals("-") ? AMBER : DIM;
            line(i + 2, pad(YardText.fit(name, 10), 11) + pad(String.valueOf(Math.round(c.pressure())), 5)
                    + pad(Math.round(c.oxygenFraction() * 100f) + "%", 5) + sm, col);
        }
        float tanks = d.hasShipStatus ? d.shipStatus.oxygen : Float.NaN;
        if (!Float.isNaN(tanks)) {
            boolean dry = tanks <= 0.001f;
            String reserve = dry && !Float.isNaN(d.shipStatus.emergencyOxygen) ? ", reserve " + pct(d.shipStatus.emergencyOxygen) : "";
            line(5, "TANKS  O2 " + (dry ? "DRY" : pct(tanks)) + reserve, dry ? RED : tanks < 0.25f ? AMBER : DIM);
        }
    }

    /** How breathable a room is, lowest worst: a pumped-down airlock counts as fine. */
    private float score(Compartment c) {
        ShipGridComponent g = ShipPlan.grid(d);
        if (g != null && ShipPlan.pumpedDown(d, g, c)) return 1.5f;
        float s = Math.min(c.pressureRatio() / ShipAlerts.LOW_PRESSURE, 1.2f);
        if (c.smokeDensity() > TileShade.THICK_SMOKE * 0.3f) s = Math.min(s, 0.9f);
        return s;
    }

    // --- Comms & Command -----------------------------------------------------------------------------

    /** COMMS: clearance and the ports, the beacon, standing with the station's people, what it offers. */
    private void comms() {
        NavReadout n = d.navReadout;
        StationInfo st = d.ctx.docked && n.docked.present ? n.docked : n.station;
        DockingApproach a = d.docking;
        String name = st.present ? st.name : !a.other.isEmpty() ? a.other : "";
        title("COMMS", name.isEmpty() ? "NO STATION" : YardText.fit(name, 14));
        int r = 1;
        if (a.present && (a.clearance != Clearance.NONE || a.armed)) {
            kv(tx + 8f, r++, "CLEARANCE", 10, clearance(a) + (a.otherPort > 0 ? ", port " + a.otherPort : ""),
                    a.clearance == Clearance.DENIED || a.clearance == Clearance.NO_PORT ? RED : AMBER);
        } else if (st.present && !st.refusal.isEmpty()) {
            kv(tx + 8f, r++, "CLEARANCE", 10, "would refuse", RED);
        }
        if (st.present) {
            String standing = st.standingName.isEmpty() ? "" : st.standingName;
            if (!Float.isNaN(st.standing)) standing += (standing.isEmpty() ? "" : " ") + String.format(Locale.ROOT, "(%+d)", Math.round(st.standing));
            if (!standing.isEmpty()) kv(tx + 8f, r++, "STANDING", 10, standing + (st.owner.isEmpty() ? "" : ", " + st.owner), AMBER);
            String services = services(st);
            if (!services.isEmpty() && r <= 4) kv(tx + 8f, r++, "OFFERS", 10, services, DIM);
        }
        Orders o = d.orderState;
        if (r <= 5) {
            if (o.towRefusal.isEmpty()) {
                kv(tx + 8f, r, "BEACON", 10, "tow " + YardText.thousands(o.towFee) + " cr" + (o.towStanding != 0f ? String.format(Locale.ROOT, ", %+d", Math.round(o.towStanding)) : ""), DIM);
            } else kvWrap(r, "BEACON", 10, o.towRefusal, DIM, 6 - r);
        }
    }

    private static String services(StationInfo s) {
        StringBuilder b = new StringBuilder();
        if (s.market) b.append("market ");
        if (s.yard) b.append("yard ");
        if (s.shipyard) b.append("ships ");
        if (s.contracts) b.append("work ");
        if (s.hiring) b.append("crew ");
        if (s.infirmary) b.append("medic ");
        return b.toString().trim();
    }

    /** CREW: who mans each post, the wounded and their timers, who is outside. */
    private void crew() {
        int aboard = d.stations.crewAboard, free = d.stations.crewFree;
        title("CREW", aboard + " ABOARD" + (free > 0 ? ", " + free + " FREE" : ""));
        int r = 1;
        // The wounded first: a bleeding casualty is a warning with a clock
        for (int ai = 0; ai < d.alerts.all().size; ai++) {
            Alert a = d.alerts.all().get(ai);
            if (r > 3) break;
            if (!a.id.startsWith("casualty:") && !a.id.equals("captain:bleeding")) continue;
            String who = a.id.equals("captain:bleeding") ? "YOU" : a.id.substring("casualty:".length());
            line(r++, YardText.fit(who, 10) + " bleeding" + (Float.isNaN(a.timer) ? "" : ", " + YardText.clock(a.timer)), RED);
        }
        StringBuilder posts = new StringBuilder();
        for (int si = 0; si < d.stations.seats.size; si++) {
            Seat s = d.stations.seats.get(si);
            if (s.post == Post.GUN) continue;
            String who = s.hand == Hand.PLAYER ? "you" : s.hand == Hand.CREW ? s.who : s.hand == Hand.COMING ? s.who + "…" : "";
            if (who.isEmpty()) continue;
            if (posts.length() > 0) posts.append("  ");
            posts.append(s.name).append(' ').append(who);
        }
        int guns = 0;
        for (int si = 0; si < d.stations.seats.size; si++) {
            Seat s = d.stations.seats.get(si);
            if (s.post == Post.GUN && s.hand == Hand.CREW) guns++;
        }
        if (guns > 0) posts.append(posts.length() > 0 ? "  " : "").append(guns).append(guns == 1 ? " gunner" : " gunners");
        if (r <= 4) line(r++, posts.length() == 0 ? "Nobody at a post." : posts.toString(), AMBER);
        int outside = 0;
        for (int ci = 0; ci < d.contactFrame.contacts.size; ci++) {
            Contact c = d.contactFrame.contacts.get(ci);
            if (c.kind == Kind.EVA) outside++;
        }
        if (outside > 0 && r <= 4) line(r++, outside + " outside" + (d.orderState.evaHeld ? ", held in" : ""), AMBER);
        Orders o = d.orderState;
        if (r <= 5) line(r, o.battleStations ? "At battle stations" + (o.suitsAtBattleStations ? ", suiting up." : ".") : "Standing down.", o.battleStations ? AMBER : DIM);
    }

    /** CONTRACTS: what is held, its time left, where it goes. */
    private void contracts() {
        Array<Objective> all = d.objectiveFrame.objectives;
        title("CONTRACTS", all.size == 0 ? "NONE HELD" : all.size + " HELD");
        if (all.size == 0) {
            line(1, "No contracts held.", DIM);
            return;
        }
        int r = 1;
        for (int i = 0; i < all.size && r <= 5; i++) {
            Objective o = all.get(i);
            if (r == 5 && all.size - i > 1) {
                line(r, "+" + (all.size - i) + " more", DIM);
                break;
            }
            String left = Double.isNaN(o.timeLeft) ? "" : "  " + YardText.span(o.timeLeft);
            Color c = o.late || o.state == com.ringsofiron.uihud.providers.ObjectiveProvider.State.FAILED ? RED
                    : o.state == com.ringsofiron.uihud.providers.ObjectiveProvider.State.DONE ? DIM : AMBER;
            String body = YardText.cut(o.title, Math.max(8, 30 - left.length()));
            line(r++, body + left, c);
        }
    }
}
