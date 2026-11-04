package com.ringsofiron.uihud.variants.cassette.context;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.PowerStatusProvider.Fault;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.variants.cassette.YardText;

/**
 * The caution and warning panel's catalogue (spec P2): everything wrong now as fixed-width rows, in the
 * spec's order, from the shared alert registry and the instruments' own readings; and the master alert
 * cue those rows light until the captain acknowledges it.
 *
 * <p>Rows merge by code: two rooms on fire are one FIRE row, "BRIDGE +1". A condition that an instrument
 * on screen shows as its own reading (FAST APPR on the docking pod's closing gauge, PLAN SHORT on the nav
 * tube, the shields on shield control, the captain's wound on the suit unit) is that instrument's: it is
 * not listed, but it still lights the cue. The captain's own suit air is never a row (X3, D1). Missiles
 * inbound are the threat unit's (P3), and the ship's disablement and abandoning are the emergency
 * placard's (X3).
 */
final class Conditions {
    /** Who is on it (P2's handling field). */
    enum Handling {
        /** Needs the captain: YOU. */
        YOU,
        /** The crew deal with it; how many the game can't say yet, so the field stays blank. */
        CREW,
        /** Clears by itself: "back 0:12". */
        BACK
    }

    static final class Row {
        String code = "", detail = "";
        boolean warning;
        Handling handling = Handling.CREW;
        /** Seconds on its countdown, NaN with none; {@link #harm} when it counts down to harm. */
        float timer = Float.NaN;
        boolean harm;
        /** The instrument that shows it on screen now, so the panel leaves it out; null when the panel lists it. */
        String shownBy;
        /** Real seconds the HUD had run when the row came (or came back worse), for the order and the pulse. */
        float since;
        boolean acknowledged;
        /** Raised this frame for the first time, or a new instance of it (a second room). */
        boolean fresh;
        /** How many places it is in: rooms, systems, crew; the detail shows the first and "+n". */
        int places;
        /** Crew working it across its places (the alerts' own counts), -1 when no one can work it. */
        int crew = -1;
        boolean seen;

        /** The handling field as the row prints it, 3 characters: YOU, a dot per crew member to three, "•4" from four. */
        String handlingText() {
            if (handling == Handling.YOU) return "YOU";
            if (handling != Handling.CREW || crew <= 0) return "";
            return crew <= 3 ? "•".repeat(crew) : "•" + Math.min(crew, 99);
        }

        String timerText() {
            if (handling == Handling.BACK) return Float.isNaN(timer) ? "back" : "back " + Kit.timer(timer);
            return harm ? Kit.timer(timer) : "";
        }

        /** Time-critical: a harm timer, so it may flash past its first 2 s (5.3). */
        boolean critical() {
            return warning && harm && !Float.isNaN(timer);
        }
    }

    /** The master alert cue (P2): lit for a new warning (its code) or caution, until acknowledged. */
    static final class Cue {
        boolean lit, warning, flash;
        String code = "";
    }

    /** A caution's cue goes out by itself after this; a warning's waits for the captain. */
    static final float CAUTION_CUE = 5f;
    /** A new warning row pulses for this long, or until acknowledged when it is time-critical. */
    static final float PULSE = 2f;
    /** The panel's rows: five conditions, then a sixth row for the rest. */
    static final int ROWS = 5;
    /** Reactors giving under this share of their rating is POWER LOW; the store under {@link #STORE_LOW} for 3 s too. */
    static final float OUTPUT_LOW = 0.6f, STORE_LOW = 0.25f;

    private final ObjectMap<String, Row> byCode = new ObjectMap<>();
    /** Every condition that holds, in the panel's order, the instruments' own among them. */
    final Array<Row> all = new Array<>();
    /** What the panel lists: {@link #all} less what instruments on screen show. */
    final Array<Row> listed = new Array<>();
    final Cue cue = new Cue();
    private float now, storeLowFor;

    /** What the instruments on screen show this frame, so the panel can leave their conditions out. */
    static final class Shown {
        boolean dockingPod, planLine, shieldControl, suitUnit;
    }

    /** Builds this frame's rows from the data, at real time {@code now}. */
    void update(HudData d, ContextFrame f, Shown shown, float now, float delta) {
        this.now = now;
        for (ObjectMap.Entry<String, Row> e : byCode) {
            e.value.seen = false;
            e.value.fresh = false;
            e.value.places = 0;
            e.value.crew = -1;
        }
        AlertRegistry alerts = d.alerts;
        Array<Alert> list = alerts.all();
        for (int i = 0; i < list.size; i++) fromAlert(d, f, list.get(i));
        instruments(d, f, delta);
        // What has cleared goes; a row's order and acknowledgement hold while it does
        all.clear();
        Array<String> gone = null;
        for (ObjectMap.Entry<String, Row> e : byCode) {
            if (!e.value.seen) {
                if (gone == null) gone = new Array<>();
                gone.add(e.key);
            } else all.add(e.value);
        }
        if (gone != null) for (int i = 0; i < gone.size; i++) byCode.remove(gone.get(i));
        // A row the crew could work with nobody on it needs the captain (P2's handling)
        for (int i = 0; i < all.size; i++) {
            Row r = all.get(i);
            if (r.handling == Handling.CREW && r.crew == 0) r.handling = Handling.YOU;
        }
        all.sort(Conditions::order);

        listed.clear();
        for (int i = 0; i < all.size; i++) {
            Row r = all.get(i);
            r.shownBy = shownBy(r, f, shown);
            if (r.shownBy == null) listed.add(r);
        }
        cue(now);
    }

    // --- From the registry ----------------------------------------------------------------------

    private void fromAlert(HudData d, ContextFrame f, Alert a) {
        if (a.level == Level.ADVISORY && !a.id.equals("eva")) return;
        String id = a.id;
        boolean w = a.level == Level.WARNING;
        if (id.equals("missile") || id.equals("disabled") || id.equals("abandon") || id.equals("hostile")) return;
        if (id.startsWith("captain:suit") || id.equals("captain:air") || id.startsWith("empty:")) return;
        if (id.startsWith("fire:")) place(a, "FIRE", true, a.room, Handling.CREW);
        else if (id.startsWith("breach:")) place(a, "BREACH", true, a.room, Handling.CREW);
        else if (id.startsWith("air:")) place(a, w ? "VACUUM" : "PRESS LOW", w, a.room, Handling.CREW);
        else if (id.startsWith("smoke:")) place(a, "SMOKE", false, a.room, Handling.CREW);
        else if (id.equals("hull")) place(a, "HULL DAMAGE", false, "", Handling.CREW);
        else if (id.equals("oxygen")) place(a, w ? "EMERG O2" : "O2 LOW", w, "", w ? Handling.YOU : Handling.CREW);
        else if (id.equals("propellant")) {
            double home = d.flightStatus.deltaVHome;
            String detail = Double.isNaN(home) || home <= 0 ? "" : "home " + YardText.thousands(home);
            place(a, "PROP LOW", false, detail, Handling.YOU);
        } else if (id.equals("captain:bleeding")) {
            Row r = place(a, "CAPT WOUND", true, "bleeding", Handling.CREW);
            harm(r, a.timer);
        } else if (id.startsWith("casualty:")) {
            Row r = place(a, "CREW CRIT", true, id.substring("casualty:".length()), Handling.CREW);
            harm(r, a.timer);
        } else if (id.equals("shield:tripped")) place(a, "SHIELD WEAK", false, "emitter", Handling.BACK);
        else if (id.equals("shield:down")) place(a, "SHIELD DOWN", false, "lowered", Handling.YOU);
        else if (id.equals("helm")) systemDown(a, f, "HELM", Handling.CREW, Float.NaN);
        else if (id.equals("eva")) crewOut(d, a);
        else if (id.startsWith("sys:")) system(d, f, a);
    }

    /** A system that is not working: the reactors in a blackout are NO POWER; anything else is SYSTEM DOWN. */
    private void system(HudData d, ContextFrame f, Alert a) {
        String system = a.code;
        SystemRow row = rowFor(d, a);
        Fault fault = row == null ? Fault.NO_POWER : row.fault;
        if (fault == Fault.OFF) return;
        boolean timed = (fault == Fault.SHORTED || fault == Fault.STUNNED) && !Float.isNaN(a.timer);
        if (f.blackout) {
            // In a blackout one row says why nothing has power; the systems it starves are not called out
            if (!"REACTOR".equals(system)) return;
            String why = fault == Fault.SHORTED ? "shorted" : fault == Fault.STUNNED ? "stunned"
                    : fault == Fault.NO_POWER ? "no fuel" : "damaged";
            Row r = place(a, "NO POWER", true, why, timed ? Handling.BACK : Handling.CREW);
            if (timed) recovery(r, a.timer);
            return;
        }
        systemDown(a, f, system, timed ? Handling.BACK : Handling.CREW, timed ? a.timer : Float.NaN);
    }

    private static SystemRow rowFor(HudData d, Alert a) {
        for (int i = 0; i < d.powerFrame.faults.size; i++) {
            SystemRow r = d.powerFrame.faults.get(i);
            if (r.system.equals(a.code) && r.tileX == a.tileX && r.tileY == a.tileY) return r;
        }
        return null;
    }

    /** SYSTEM DOWN: a caution, a warning for the engines or the helm in flight. */
    private void systemDown(Alert a, ContextFrame f, String system, Handling h, float back) {
        boolean flightCritical = !f.berthed() && (system.startsWith("ENGINE") || system.equals("HELM"));
        Row r = place(a, "SYSTEM DOWN", flightCritical, system, h);
        if (h == Handling.BACK) recovery(r, back);
    }

    /** CREW OUT: crew on EVA while thrusting, or with a burn due within 60 s. */
    private void crewOut(HudData d, Alert a) {
        FlightStatus fs = d.flightStatus;
        NavReadout n = d.navReadout;
        float burnIn = Float.isNaN(n.burnStartIn) ? n.burnIn : n.burnStartIn;
        boolean burnDue = !Float.isNaN(burnIn) && burnIn <= 60f;
        if (!fs.mainEngine && !fs.rcs && !burnDue) return;
        Row r = place(a, "CREW OUT", false, "", Handling.YOU);
        if (burnDue) harm(r, burnIn);
    }

    // --- From the instruments -------------------------------------------------------------------

    private void instruments(HudData d, ContextFrame f, float delta) {
        // FAST APPR: closing faster than the safe speed for the distance (a warning at twice it)
        DockingApproach k = d.docking;
        if (f.approach && !Float.isNaN(k.closing) && !Float.isNaN(k.safeSpeed) && k.safeSpeed > 0f && k.closing > k.safeSpeed) {
            boolean danger = k.closing > 2f * k.safeSpeed;
            String over = Math.round(k.closing - k.safeSpeed) + " m/s over";
            Handling h = d.orderState.helm == HelmOrder.DOCK ? Handling.CREW : Handling.YOU;
            Row r = synthetic("FAST APPR", danger, over.length() > 10 ? "over safe" : over, h);
            if (!Float.isNaN(k.range) && k.closing > 0f) harm(r, k.range / k.closing);
        }
        // PLAN SHORT: the plan needs more delta-v than is left
        FlightStatus fs = d.flightStatus;
        NavReadout n = d.navReadout;
        if (n.planDeltaV > 0 && !Double.isNaN(fs.deltaVLeft) && !Double.isInfinite(fs.deltaVLeft) && n.planDeltaV > fs.deltaVLeft) {
            synthetic("PLAN SHORT", false, "need " + YardText.thousands(n.planDeltaV), Handling.YOU);
        }
        // NO HELM: nobody flies while a burn is due within 60 s or the ship turns
        if (d.ctx.helmsman.isEmpty() && !f.berthed() && d.ctx.helmFault.isEmpty() && f.seat != ContextFrame.Seat.NONE) {
            float burnIn = Float.isNaN(n.burnStartIn) ? n.burnIn : n.burnStartIn;
            boolean burnDue = !Float.isNaN(burnIn) && burnIn <= 60f;
            boolean turning = !Float.isNaN(fs.spin) && Math.abs(fs.spin) > 2f;
            if (burnDue || turning) {
                Row r = synthetic("NO HELM", false, "", Handling.YOU);
                if (burnDue) harm(r, burnIn);
            }
        }
        // IMPACT: the orbit meets the surface and no burn is planned to avoid it
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        if (o != null && o.impactWarning && n.burnsLeft == 0 && !f.berthed()) {
            synthetic("IMPACT", true, lower(o.bodyName, 10), Handling.YOU);
        }
        // DOCK STRAIN over 50%
        if (f.berthed() && !Float.isNaN(k.strain) && k.strain > 0.5f) {
            synthetic("DOCK STRAIN", false, Math.round(k.strain * 100f) + "%", Handling.CREW);
        }
        // POWER LOW: the reactors held under 60% of their rating, or the store drained for 3 s
        double rated = d.powerFrame.ratedWatts, out = d.powerFrame.outputWatts;
        boolean store = !f.blackout && d.powerFrame.circuits.size > 0 && Kit.charge(d.powerFrame) < STORE_LOW;
        storeLowFor = store ? storeLowFor + delta : 0f;
        if (!f.blackout && rated > 0 && out > 0 && out < OUTPUT_LOW * rated) synthetic("POWER LOW", false, "damaged", Handling.CREW);
        else if (storeLowFor > 3f) synthetic("POWER LOW", false, "weapons", Handling.CREW);
    }

    // --- Rows -----------------------------------------------------------------------------------

    /** The row for {@code code}, raised this frame from {@code a}, with one more place in its detail. */
    private Row place(Alert a, String code, boolean warning, String where, Handling h) {
        Row r = raise(code, warning, h);
        if (a.fresh) refresh(r);
        if (a.handling >= 0) r.crew = Math.max(r.crew, 0) + a.handling;
        if (r.places == 0 || r.detail.isEmpty()) r.detail = where == null ? "" : where;
        r.places++;
        return r;
    }

    private Row synthetic(String code, boolean warning, String detail, Handling h) {
        Row r = raise(code, warning, h);
        r.detail = detail;
        r.places = 1;
        return r;
    }

    private Row raise(String code, boolean warning, Handling h) {
        Row r = byCode.get(code);
        if (r == null) {
            r = new Row();
            r.code = code;
            r.since = now;
            r.fresh = true;
            byCode.put(code, r);
        } else if (!r.seen) {
            r.timer = Float.NaN;
            r.harm = false;
            // Worse than last frame: news again
            if (warning && !r.warning) refresh(r);
        }
        r.warning = r.seen ? r.warning || warning : warning;
        if (!r.seen) r.handling = h;
        else if (h == Handling.YOU) r.handling = Handling.YOU;
        r.seen = true;
        return r;
    }

    private void refresh(Row r) {
        r.since = now;
        r.fresh = true;
        r.acknowledged = false;
    }

    /** A harm countdown: the soonest of the row's places. */
    private static void harm(Row r, float seconds) {
        if (Float.isNaN(seconds)) return;
        if (Float.isNaN(r.timer) || seconds < r.timer) r.timer = seconds;
        r.harm = true;
    }

    private static void recovery(Row r, float seconds) {
        if (Float.isNaN(seconds)) return;
        if (Float.isNaN(r.timer) || seconds > r.timer) r.timer = seconds;
        r.harm = false;
    }

    /** Which instrument on screen shows {@code r} as its own reading, or null. */
    private static String shownBy(Row r, ContextFrame f, Shown s) {
        switch (r.code) {
            case "FAST APPR": return s.dockingPod ? "H3" : null;
            case "PLAN SHORT": return s.planLine ? "H2" : null;
            case "SHIELD DOWN":
            case "SHIELD WEAK": return s.shieldControl ? "F3" : null;
            case "CAPT WOUND": return s.suitUnit || f.deck() ? "D1" : null;
            default: return null;
        }
    }

    /**
     * The panel's order (P2): warnings first; then rows that need the captain, then those the crew are on,
     * then those that clear by themselves; within each, harm timers soonest first, then the newest.
     */
    static int order(Row a, Row b) {
        if (a.warning != b.warning) return a.warning ? -1 : 1;
        if (a.handling != b.handling) return a.handling.ordinal() - b.handling.ordinal();
        boolean at = a.harm && !Float.isNaN(a.timer), bt = b.harm && !Float.isNaN(b.timer);
        if (at != bt) return at ? -1 : 1;
        if (at) return Float.compare(a.timer, b.timer);
        return Float.compare(b.since, a.since);
    }

    /** The detail as the row prints it: the first place, cut to 10, with "+n" for the rest. */
    static String detail(Row r) {
        String more = r.places > 1 ? " +" + (r.places - 1) : "";
        String base = r.detail;
        int room = 10 - more.length();
        if (base.length() > room) base = base.substring(0, Math.max(1, room - 1)) + "…";
        return base + more;
    }

    /** The sixth row: "+ 2 more: SMOKE, SHIELD DOWN", as many of the hidden codes as fit 34 characters. */
    static String more(Array<Row> rows, int shown) {
        int hidden = rows.size - shown;
        if (hidden <= 0) return "";
        StringBuilder b = new StringBuilder("+").append(hidden).append(" more: ");
        int listed = 0;
        for (int i = shown; i < rows.size; i++) {
            String c = rows.get(i).code;
            String sep = listed == 0 ? "" : ", ";
            boolean last = i == rows.size - 1;
            int need = sep.length() + c.length() + (last ? 0 : 3);
            if (b.length() + need > 34) {
                b.append(listed == 0 ? "…" : ", …");
                return b.toString();
            }
            b.append(sep).append(c);
            listed++;
        }
        return b.toString();
    }

    // --- The cue --------------------------------------------------------------------------------

    private void cue(float now) {
        Row newestWarning = null, newestCaution = null;
        for (int i = 0; i < all.size; i++) {
            Row r = all.get(i);
            if (r.acknowledged) continue;
            if (r.warning) {
                if (newestWarning == null || r.since > newestWarning.since) newestWarning = r;
            } else if (now - r.since < CAUTION_CUE) {
                if (newestCaution == null || r.since > newestCaution.since) newestCaution = r;
            }
        }
        Cue c = cue;
        c.lit = newestWarning != null || newestCaution != null;
        c.warning = newestWarning != null;
        c.code = newestWarning != null ? newestWarning.code : "";
        c.flash = false;
        if (newestWarning != null) {
            for (int i = 0; i < all.size; i++) {
                Row r = all.get(i);
                if (r.warning && !r.acknowledged && (now - r.since < PULSE || r.critical())) c.flash = true;
            }
        }
    }

    /** Whether row {@code r}'s severity lens flashes now: a new warning for 2 s, a time-critical one until acknowledged. */
    boolean pulsing(Row r) {
        return r.warning && !r.acknowledged && (now - r.since < PULSE || r.critical());
    }

    /** The captain acknowledges: the cue goes out and the pulses stop; the rows stay while they hold. */
    void acknowledge(HudData d) {
        for (int i = 0; i < all.size; i++) all.get(i).acknowledged = true;
        d.alerts.acknowledgeAll();
        cue.lit = false;
        cue.flash = false;
    }

    /** The NO POWER row, while the ship waits for its power to come back by itself (X3 waits on it). */
    Row noPower() {
        return byCode.get("NO POWER");
    }

    /** A row's code in words, for the hover and the master cue's ("BREACH" is "Hull breach"). */
    static String longName(String code) {
        if (code == null) return "";
        switch (code) {
            case "FIRE": return "Fire";
            case "BREACH": return "Hull breach";
            case "VACUUM": return "Vacuum";
            case "PRESS LOW": return "Low pressure";
            case "SMOKE": return "Smoke";
            case "HULL DAMAGE": return "Hull damaged";
            case "EMERG O2": return "Emergency oxygen";
            case "O2 LOW": return "Low oxygen";
            case "PROP LOW": return "Propellant low";
            case "CAPT WOUND": return "Captain wounded";
            case "CREW CRIT": return "Crew critical";
            case "SHIELD WEAK": return "Shield weak";
            case "SHIELD DOWN": return "Shields down";
            case "SYSTEM DOWN": return "System down";
            case "NO POWER": return "No power";
            case "POWER LOW": return "Power low";
            case "CREW OUT": return "Crew outside";
            case "FAST APPR": return "Approach too fast";
            case "PLAN SHORT": return "Plan short of delta-v";
            case "NO HELM": return "Nobody at the helm";
            case "IMPACT": return "Impact course";
            case "DOCK STRAIN": return "Docking strain";
            default: return code.isEmpty() ? "" : code.charAt(0) + code.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Lower case words for a detail field ("Moon"), cut to {@code n}. */
    static String lower(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
