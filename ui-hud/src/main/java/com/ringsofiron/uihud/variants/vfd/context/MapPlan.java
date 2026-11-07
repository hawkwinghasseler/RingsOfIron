package com.ringsofiron.uihud.variants.vfd.context;

import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrbitPathProvider.OrbitPath;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.variants.vfd.Drive;
import com.ringsofiron.uihud.variants.vfd.Filament;
import com.ringsofiron.uihud.variants.vfd.FilamentActor;
import com.ringsofiron.uihud.variants.vfd.Phosphor;
import com.ringsofiron.uihud.variants.vfd.screens.Glass;

import java.util.Locale;

/**
 * M1's Plan pane, left (functional-context 10.1; mockup cx-map): the orbit now with its apsides and
 * when the ship reaches them; the burns as a table, the selected one in focus; the leg still to come
 * past a crossing, which the game plans there; where the selected burn leads; the plan's delta-v
 * against what the tanks hold, what it leaves, and the arrival; and at the pane's foot the orders that
 * fly it, each with its key and what it is doing.
 */
final class MapPlan {
    static final float W = 320f;
    /** The pane's text, in cells. */
    static final int CELLS = 33;
    /** The burns the table can hold, and its rows' pitch. */
    static final int MAX_BURNS = 40;
    static final float ROW = Glass.ROW;
    /** The orders at the pane's foot: their pitch and how many. */
    static final float ORDER_ROW = 26f;
    static final int ORDERS = 5;
    /** Click ids, from the tab's: a burn's row, an order. */
    static final int BURN = Modes.TAB_IDS, ORDER = Modes.TAB_IDS + MAX_BURNS;
    static final int HELM = 0, AUTOPILOT = 1, WARP = 2, DELETE = 3, CLEAR = 4;

    float x, y, w, h;
    // The orbit now
    String body = "";
    final String[] orbit = new String[3];
    int orbitLines;
    boolean impact;
    // The burns
    int burns, selected = -1, first;
    final String[] burnRows = new String[MAX_BURNS];
    final float[] burnIn = new float[MAX_BURNS];
    String burnsNote = "";
    private int rows = 1;
    private float rowsY;
    // The leg still to come
    final String[] onward = new String[3];
    int onwardLines;
    // The selected burn
    String selName = "", selLeads = "", selNext = "";
    // The plan
    String need = "", have = "", leaves = "", arrival = "";
    float full;
    boolean shortfall, hasHave;
    // The orders
    final String[] orderKey = new String[ORDERS];
    final String[] orderState = new String[ORDERS];
    final boolean[] orderOn = new boolean[ORDERS], orderLit = new boolean[ORDERS];
    static final String[] ORDER_WORD = {"HELM FLIES BURNS", "AUTOPILOT", "WARP TO NEXT BURN", "DELETE BURN", "CLEAR ALL"};
    private final Glass.Cols cols = new Glass.Cols();

    // ------------------------------------------------------------------ reading

    void read(HudData d, OrbitPath p, MapTimeline line) {
        readOrbit(d);
        readBurns(p);
        NavReadout n = d.navReadout;
        readOnward(n, line);
        readSelected(p, line);
        readPlan(d, n);
        readOrders(d);
    }

    private void readOrbit(HudData d) {
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        orbitLines = 0;
        impact = false;
        if (o == null) {
            body = "";
            orbit[orbitLines++] = "NO ORBIT";
            return;
        }
        body = o.bodyName == null ? "" : o.bodyName.toUpperCase(Locale.ROOT);
        boolean closed = !Double.isInfinite(o.period) && !Double.isNaN(o.period);
        if (closed) orbit[orbitLines++] = apsis("AP", o.apoapsis, o.apIn);
        orbit[orbitLines++] = apsis("PE", o.periapsis, o.peIn);
        impact = o.impactWarning;
        if (impact) orbit[orbitLines++] = "ON COURSE TO HIT " + body;
        else orbit[orbitLines++] = closed ? "PERIOD " + Rows.timeText(o.period) : "ESCAPING";
    }

    /** "PE 397 KM       T-8:40": the apsis over the surface, and when the ship reaches it. */
    private String apsis(String which, double meters, float in) {
        cols.clear().left(which + " " + km(meters), 16);
        if (!Float.isNaN(in) && in > 0f) cols.left("T-" + Rows.timeText(in), 12);
        return cols.toString().trim();
    }

    private void readBurns(OrbitPath p) {
        burns = p == null ? 0 : Math.min(MAX_BURNS, p.nodeIn.size);
        selected = p == null ? -1 : p.selectedNode;
        for (int i = 0; i < burns; i++) {
            float pro = 2 * i + 1 < p.nodeDv.size ? p.nodeDv.get(2 * i) : 0f, rad = 2 * i + 1 < p.nodeDv.size ? p.nodeDv.get(2 * i + 1) : 0f;
            burnIn[i] = p.nodeIn.get(i);
            burnRows[i] = cols.clear().left(Integer.toString(i + 1), 2).left(when(burnIn[i]), 9)
                    .right(Glass.thousands(Math.hypot(pro, rad)), 7).right(Glass.thousands(pro), 7).right(Glass.thousands(rad), 6).toString();
        }
        if (burns == 0) burnsNote = "NONE";
        else if (selected >= 0 && selected < burns) burnsNote = (selected + 1) + " OF " + burns;
        else burnsNote = burns + (burns == 1 ? " BURN" : " BURNS");
    }

    /** A burn's time to go, in the table's nine cells: "T-42 S", "T-12:05", "T-3H 05M", "T-2.4 D". */
    static String when(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) return "";
        long s = (long) Math.ceil(seconds - 1e-6);
        if (s < 3600 || s >= 86_400) return "T-" + Rows.timeText(seconds);
        return String.format(Locale.ROOT, "T-%dH %02dM", s / 3600, s % 3600 / 60);
    }

    /** The leg past the plan's last burn, which the game plans at the crossing: the capture there, and its delta-v. */
    private void readOnward(NavReadout n, MapTimeline line) {
        onwardLines = 0;
        if (n.onwardBody == null || n.onwardBody.isEmpty()) return;
        String about = Float.isNaN(n.onwardDeltaV) ? "" : "ABOUT " + Glass.thousands(n.onwardDeltaV) + " M/S";
        MapTimeline.Event capture = line.capture();
        String at = capture == null ? "" : "AT " + capture.when;
        onward[onwardLines++] = Glass.cut("THEN AT " + the(n.onwardBody) + ": CAPTURE,", CELLS);
        if (!about.isEmpty() || !at.isEmpty()) onward[onwardLines++] = Glass.cut((about + " " + at).trim() + ",", CELLS);
        onward[onwardLines++] = "PLANNED AT THE CROSSING";
    }

    /** A body as the lexicon names it after "at": "THE MOON", "THE SUN", else its name. */
    static String the(String body) {
        String u = body.toUpperCase(Locale.ROOT);
        return u.equals("MOON") || u.equals("SUN") ? "THE " + u : u;
    }

    private void readSelected(OrbitPath p, MapTimeline line) {
        selName = "";
        selLeads = "";
        selNext = "";
        if (selected < 0 || selected >= burns) return;
        selName = "BURN " + (selected + 1);
        if (!Double.isNaN(p.selectedPe)) {
            if (p.selectedPe < 0) selLeads = "→ IMPACT";
            else if (Double.isNaN(p.selectedAp)) selLeads = "→ PE " + km(p.selectedPe) + ", ESCAPES";
            else selLeads = "→ PE " + Glass.thousands(p.selectedPe / 1000.0) + " · AP " + km(p.selectedAp);
        }
        MapTimeline.Event next = line.after(burnIn[selected]);
        if (next != null) selNext = Glass.cut(next.name + " " + next.when, CELLS - 7);
    }

    private void readPlan(HudData d, NavReadout n) {
        FlightStatus fs = d.flightStatus;
        double planned = n.planDeltaV;
        // An accepted trip's capture past the crossing counts too: the proposal's total already has it
        if (!n.proposal && !Float.isNaN(n.onwardDeltaV)) planned += n.onwardDeltaV;
        need = Long.toString(Math.round(planned));
        double left = fs.deltaVLeft;
        hasHave = !Double.isNaN(left) && !Double.isInfinite(left);
        have = hasHave ? Long.toString(Math.round(left)) : Double.isInfinite(left) ? "FREE" : "";
        full = hasHave && left > 0 ? (float) Math.min(1.0, planned / left) : 0f;
        shortfall = hasHave && planned > left;
        leaves = !hasHave ? "" : shortfall ? "SHORT BY " + Glass.thousands(planned - left) + " M/S" : "LEAVES " + Glass.thousands(left - planned) + " M/S";
        arrival = Float.isNaN(n.arrivalIn) ? "" : "ARRIVAL T-" + Rows.timeText(n.arrivalIn);
    }

    private void readOrders(HudData d) {
        orderKey[HELM] = cap(d.key("HELM_FLY_BURNS"));
        orderKey[AUTOPILOT] = cap(d.key("AUTOPILOT"));
        orderKey[WARP] = cap(d.key("WARP_TO_BURN"));
        orderKey[DELETE] = cap(d.key("BURN_DELETE"));
        orderKey[CLEAR] = "";
        boolean helm = d.orderState.helm == HelmOrder.FLY_BURNS;
        Autopilot ap = d.orderState.autopilot;
        orderState[HELM] = helm ? "ON" : "OFF";
        orderOn[HELM] = helm;
        orderState[AUTOPILOT] = ap == Autopilot.ON ? "ON" : ap == Autopilot.OFF ? "OFF" : "HELD";
        orderOn[AUTOPILOT] = ap != Autopilot.OFF;
        orderState[WARP] = "";
        orderState[DELETE] = "";
        orderState[CLEAR] = "";
        orderLit[HELM] = orderLit[AUTOPILOT] = true;
        orderLit[WARP] = burns > 0;
        orderLit[DELETE] = selected >= 0 && selected < burns;
        orderLit[CLEAR] = burns > 0;
    }

    /** A key's name as its cap prints it: the game's names for the delete keys are the keyboard's legends. */
    static String cap(String key) {
        if (key == null) return "";
        String u = key.toUpperCase(Locale.ROOT);
        switch (u) {
            case "DELETE":
                // libGDX names Backspace "Delete"
                return "BKSP";
            case "FORWARD DELETE":
                return "DEL";
            case "ESCAPE":
                return "ESC";
            default:
                return u;
        }
    }

    /** Meters over the surface in kilometres with their unit: "397 KM", "378,000 KM". */
    static String km(double meters) {
        return Glass.thousands(meters / 1000.0) + " KM";
    }

    // ------------------------------------------------------------------ laying out

    void place(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        // The table takes the rows the blocks under it leave
        float yy = y + 30f + 18f + 16f * (orbitLines - 1) + 24f + 20f + 16f;
        rowsY = yy;
        float below = 4f + (onwardLines > 0 ? onwardLines * 14f + 10f : 0f) + 40f + 22f + 28f + 16f + 32f;
        float room = ordersTop() - 22f - 12f - below - yy;
        rows = Math.max(1, Math.min(MAX_BURNS, (int) Math.floor(room / ROW)));
        if (burns <= rows) first = 0;
        else if (selected >= 0) {
            if (selected < first) first = selected;
            if (selected >= first + rows) first = selected - rows + 1;
        }
        first = Math.max(0, Math.min(first, Math.max(0, burns - rows)));
    }

    float ordersTop() {
        return y + h - 10f - ORDERS * ORDER_ROW;
    }

    /** Turns the table by {@code amount} rows, when it holds more than it shows. */
    void scroll(int amount) {
        first = Math.max(0, Math.min(first + amount, Math.max(0, burns - rows)));
    }

    // ------------------------------------------------------------------ painting

    void paint(Filament f) {
        Glass.pane(f, x, y, w, h, "PLAN");
        float x0 = x + 10f, x1 = x + w - 10f, yy = y + 30f;
        Glass.label(f, x0, yy + 9.5f, "ORBIT NOW");
        if (!body.isEmpty()) Glass.text(f, x0 + 96f, yy, Glass.cut(body, CELLS - 11));
        for (int i = 0; i < orbitLines; i++) {
            boolean last = i == orbitLines - 1;
            float ly = yy + 18f + 16f * i;
            if (last && impact) Glass.text(f, x0, ly, Glass.cut(orbit[i], CELLS), Drive.NORM, Phosphor.AMBER);
            else if (last) Glass.dim(f, x0, ly, orbit[i]);
            else Glass.text(f, x0, ly, orbit[i]);
        }
        yy += 18f + 16f * (orbitLines - 1) + 24f;
        Glass.head(f, x0, yy, x1, "BURNS");
        Glass.right(f, x1, yy, burnsNote, Drive.DIM);
        yy += 20f;
        Glass.label(f, x0 + Glass.CW, yy + 9.5f, "#");
        Glass.label(f, x0 + 3 * Glass.CW, yy + 9.5f, "WHEN");
        Glass.labelRight(f, x0 + 19 * Glass.CW - Glass.P, yy + 9.5f, "ΔV");
        Glass.labelRight(f, x0 + 26 * Glass.CW - Glass.P, yy + 9.5f, "PRO");
        Glass.labelRight(f, x0 + 32 * Glass.CW - Glass.P, yy + 9.5f, "RAD");
        float ry = rowsY;
        if (burns == 0) {
            Glass.dim(f, x0 + Glass.CW, ry, "CLICK THE PATH TO PLAN ONE");
        }
        for (int i = first; i < Math.min(burns, first + rows); i++) {
            Glass.row(f, x0, ry, burnRows[i], CELLS - 1, i == selected, Drive.NORM);
            ry += ROW;
        }
        yy = rowsY + Math.max(1, Math.min(rows, Math.max(1, burns - first))) * ROW + 4f;
        if (onwardLines > 0) {
            for (int i = 0; i < onwardLines; i++) Glass.dim(f, x0, yy + 14f * i, onward[i]);
            yy += onwardLines * 14f + 10f;
        }
        Glass.head(f, x0, yy, x1, "SELECTED");
        yy += 20f;
        if (selName.isEmpty()) {
            Glass.dim(f, x0, yy, burns == 0 ? "NO BURN" : "NONE: CLICK A BURN");
        } else {
            Glass.text(f, x0, yy, selName, Drive.FULL);
            if (!selLeads.isEmpty()) Glass.text(f, x0 + 62f, yy, Glass.cut(selLeads, CELLS - 7));
            if (!selNext.isEmpty()) Glass.text(f, x0 + 62f, yy + 16f, selNext);
        }
        yy += 40f;
        Glass.head(f, x0, yy, x1, "PLAN");
        yy += 22f;
        float fx = x0 + f.legend(x0, yy + 18f, "ΔV", f.o().size(13f).dim().noGhost()) + 7f;
        fx = Glass.figure(f, fx, yy, 18f, need, null, Math.max(4, need.length()), Drive.NORM);
        if (!have.isEmpty()) {
            fx += 8f;
            fx += f.legend(fx, yy + 18f, "OF", f.o().size(13f).dim().noGhost()) + 7f;
            Glass.figure(f, fx, yy + 2f, 16f, have, hasHave ? "M/S" : null, Math.max(4, have.length()), Drive.NORM);
        }
        yy += 28f;
        f.bar(x0, yy, x1 - x0, 6f, 40, full, f.o().color(shortfall ? Phosphor.AMBER : Phosphor.ZINC).norm().gap(1.2f));
        yy += 16f;
        if (!leaves.isEmpty()) {
            if (shortfall) Glass.text(f, x0, yy, leaves, Drive.NORM, Phosphor.AMBER);
            else Glass.dim(f, x0, yy, leaves);
        }
        if (!arrival.isEmpty()) Glass.text(f, x0, yy + 16f, arrival);
        // The orders, at the pane's foot
        float oy = ordersTop();
        Glass.head(f, x0, oy - 22f, x1, "ORDERS");
        for (int i = 0; i < ORDERS; i++) {
            float ry2 = oy + i * ORDER_ROW;
            // An order with no key of its own (Clear all) has a blank cap, so the words stay in line
            Glass.capWord(f, x0, ry2, capOf(i), ORDER_WORD[i], orderLit[i] ? Drive.NORM : Drive.DIM);
            if (!orderState[i].isEmpty()) Glass.right(f, x1, ry2 + 4f, orderState[i], orderOn[i] ? Drive.FULL : Drive.DIM);
        }
    }

    private final String[][] capArrays = new String[ORDERS][1];

    private String[] capOf(int i) {
        capArrays[i][0] = orderKey[i];
        return capArrays[i];
    }

    // ------------------------------------------------------------------ clicks

    void boxes(FilamentActor a) {
        float x0 = x + 10f, ry = rowsY;
        for (int i = first; i < Math.min(burns, first + rows); i++) {
            a.box(BURN + i, x0, ry - 3f, w - 20f, ROW);
            ry += ROW;
        }
        float oy = ordersTop();
        for (int i = 0; i < ORDERS; i++) a.box(ORDER + i, x0 - 2f, oy + i * ORDER_ROW - 3f, w - 16f, ORDER_ROW - 2f);
    }

    /** Whether the table is under the pointer at {@code px, py} (y down), for the wheel. */
    boolean overBurns(float px, float py) {
        return px >= x && px < x + w && py >= rowsY - 20f && py < rowsY + rows * ROW;
    }
}
