package com.ringsofiron.uihud.variants.nasapunk.stations;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.coreengine.input.Action;
import com.ringsofiron.uihud.data.AlertRegistry;
import com.ringsofiron.uihud.data.AlertRegistry.Alert;
import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.CaptainStatusProvider.CaptainStatus;
import com.ringsofiron.uihud.providers.ContactProvider;
import com.ringsofiron.uihud.providers.ContactProvider.Contact;
import com.ringsofiron.uihud.providers.DockingApproachProvider.DockingApproach;
import com.ringsofiron.uihud.providers.FlightStatusProvider.FlightStatus;
import com.ringsofiron.uihud.providers.NavigationProvider.NavReadout;
import com.ringsofiron.uihud.providers.NavigationProvider.StationInfo;
import com.ringsofiron.uihud.providers.ObjectiveProvider;
import com.ringsofiron.uihud.providers.ObjectiveProvider.Objective;
import com.ringsofiron.uihud.providers.OrbitInfoProvider.OrbitInfo;
import com.ringsofiron.uihud.providers.OrdersProvider.Autopilot;
import com.ringsofiron.uihud.providers.OrdersProvider.HelmOrder;
import com.ringsofiron.uihud.providers.OrdersProvider.Orders;
import com.ringsofiron.uihud.providers.PowerStatusProvider;
import com.ringsofiron.uihud.providers.PowerStatusProvider.PowerFrame;
import com.ringsofiron.uihud.providers.PowerStatusProvider.SystemRow;
import com.ringsofiron.uihud.providers.ShieldStatusProvider.ShieldStatus;
import com.ringsofiron.uihud.providers.ShipStatusProvider.ShipStatus;
import com.ringsofiron.uihud.providers.StationBoardProvider;
import com.ringsofiron.uihud.providers.TargetStatusProvider.Relation;
import com.ringsofiron.uihud.providers.ThreatProvider;
import com.ringsofiron.uihud.providers.ThreatProvider.MissileTrack;
import com.ringsofiron.uihud.providers.UiContextProvider.UiContext;
import com.ringsofiron.uihud.providers.WeaponStatusProvider;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import com.ringsofiron.uihud.variants.nasapunk.NpColors;
import com.ringsofiron.uihud.variants.nasapunk.NpFormat;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware;
import com.ringsofiron.uihud.variants.nasapunk.NpHardware.BoxState;
import com.ringsofiron.uihud.variants.nasapunk.NpInstruments;
import com.ringsofiron.uihud.variants.nasapunk.NpSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The MFD pages (section 9.2), drawn straight from the frame's shared data while the MFD draws. A
 * page's glass is 272 x 130 inside the bezel: its header, then five rows of 18 above the soft keys.
 * What a page can't hold it leaves to the page that owns it (the cluster keeps the gun chips, the
 * annunciator the counts), and what the shared data lacks (the ship plan, the orbit drawing, the
 * hold) it leaves out.
 */
final class Pages {
    /** SENSORS' range steps, km, as the classic scope's with the 5 km step the spec adds. */
    static final float[] STEPS = {0.5f, 2f, 5f, 10f, 50f, 250f, 1000f};
    /** A step holds a contact with room to spare inside this share of it. */
    private static final float ROOM = 0.8f;
    /** Rows' baselines below the glass's top. */
    private static final float R1 = 35f, PITCH = 18f;

    HudData d;
    Annunciation ann;
    /** The key bound to an action, as a cap shows it. */
    Function<Action, String> key = a -> a.defaultKeyName();

    private final List<NpInstruments.Blip> blips = new ArrayList<>();
    private final List<NpInstruments.Blip> blipPool = new ArrayList<>();
    private final List<Contact> listed = new ArrayList<>();
    private final List<MissileTrack> inbound = new ArrayList<>();
    private final List<SystemRow> rows = new ArrayList<>();
    private final List<Alert> alerts = new ArrayList<>();

    void draw(NpCanvas cv, Mfd m, float gx, float gy, float gw, float gh) {
        if (d == null) return;
        switch (m.page) {
            case NAV: nav(cv, gx, gy, gw); break;
            case PLAN: plan(cv, gx, gy, gw); break;
            case HELM: helm(cv, gx, gy, gw); break;
            case DOCK: dock(cv, gx, gy, gw); break;
            case SENSORS: sensors(cv, m, gx, gy, gw); break;
            case WEAPONS: weapons(cv, gx, gy, gw); break;
            case DEFENSE: defense(cv, gx, gy, gw); break;
            case DAMAGE: damage(cv, gx, gy, gw); break;
            case POWER: power(cv, gx, gy, gw); break;
            case ATMOS: atmos(cv, gx, gy, gw); break;
            case COMMS: comms(cv, gx, gy, gw); break;
            case CREW: crew(cv, gx, gy, gw); break;
            case CONTRACTS: contracts(cv, gx, gy, gw); break;
            default: break;
        }
    }

    private static float row(float gy, int i) {
        return gy + R1 + i * PITCH;
    }

    private static String up(String s) {
        return StDraw.upper(s);
    }

    private static String km(double metres) {
        String[] r = NpFormat.range(metres);
        return r[0] + " " + r[1];
    }

    private static String mps(float v) {
        String[] s = NpFormat.speed(v);
        return (v < -0.05f ? NpFormat.MINUS : "") + s[0] + " " + s[1];
    }

    // ---- NAV and PLAN (Navigation) ---------------------------------------------------------------

    private void nav(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, M = gx + gw / 2f + 4f, R = gx + gw - 8f;
        OrbitInfo o = d.orbitInfo == null ? null : d.orbitInfo.getPlayerOrbit();
        UiContext ctx = d.ctx;
        StDraw.head(cv, gx, gy, gw, "NAV", ctx.docked ? "DOCKED" : o == null ? "" : up(o.bodyName), NpColors.DIM);
        if (o != null && !ctx.docked) {
            boolean bound = !Double.isInfinite(o.period) && !Double.isNaN(o.period) && o.period > 0;
            StDraw.pair(cv, L, row(gy, 0), "ALT", km(o.altitude), NpColors.WHITE);
            StDraw.pair(cv, M, row(gy, 0), "PERIOD", bound ? NpFormat.clock(o.period) : "ESCAPE", NpColors.WHITE);
            StDraw.pair(cv, L, row(gy, 1), "AP", bound ? km(o.apoapsis) : "--", NpColors.WHITE);
            StDraw.pair(cv, M, row(gy, 1), "PE", km(o.periapsis), o.impactWarning ? NpColors.RED : NpColors.WHITE);
            if (o.impactWarning) {
                StDraw.severity(cv, L + 5f, row(gy, 2), Level.WARNING, NpColors.RED);
                StDraw.body(cv, L + 16f, row(gy, 2), "IMPACT TRAJECTORY", NpColors.RED);
            }
        } else if (!ctx.docked) {
            StDraw.lab(cv, L, row(gy, 0), "NO ORBIT TO SHOW");
        }
        NavReadout n = d.navReadout;
        if (!n.present) {
            StDraw.lab(cv, L, row(gy, 3), "NO NAV TARGET");
            StDraw.hint(cv, R - StDraw.hintW(cv, key.apply(Action.NAV_TARGET), "PICK ONE"), row(gy, 3), key.apply(Action.NAV_TARGET), "PICK ONE", NpColors.DIM);
            return;
        }
        float lw = StDraw.lab(cv, L, row(gy, 3), "NAV");
        String range = Double.isNaN(n.distance) ? "" : km(n.distance);
        float rw = StDraw.bodyW(cv, range);
        StDraw.body(cv, L + lw + 6f, row(gy, 3), StDraw.fitName(cv, up(n.name), R - L - lw - rw - 16f, StDraw.B, true, 0.3f), NpColors.MAGENTA);
        StDraw.bodyEnd(cv, R, row(gy, 3), range, NpColors.WHITE);
        float x = L;
        if (!Float.isNaN(n.closing)) x += StDraw.pair(cv, x, row(gy, 4), "CLOSING", mps(n.closing), NpColors.WHITE) + 12f;
        if (!Double.isNaN(n.cpaDistance)) {
            String cpa = km(n.cpaDistance) + (Float.isNaN(n.cpaIn) ? "" : "  " + NpFormat.tMinus(n.cpaIn));
            StDraw.pair(cv, x, row(gy, 4), "CPA", StDraw.fit(cv, cpa, R - x - 30f, StDraw.B, true, 0.3f), NpColors.WHITE);
        }
    }

    private void plan(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        NavReadout n = d.navReadout;
        FlightStatus f = d.flightStatus;
        Orders o = d.orderState;
        String status = n.proposal ? "PROPOSAL" : n.burnsLeft > 0 ? n.burnsLeft + (n.burnsLeft == 1 ? " BURN" : " BURNS") : "NO PLAN";
        StDraw.head(cv, gx, gy, gw, "PLAN", status, n.proposal ? NpColors.MAGENTA : NpColors.DIM);
        if (n.burnsLeft > 0 || n.proposal) {
            float lw = StDraw.lab(cv, L, row(gy, 0), "NEXT");
            NpSymbols.burnMark(cv, L + lw + 12f, row(gy, 0) - 4.5f, 5f, NpColors.MAGENTA, 1.5f, n.burning);
            String name = n.burnNumber > 0 ? "BURN " + n.burnNumber : "BURN";
            float x = L + lw + 24f;
            x += StDraw.body(cv, x, row(gy, 0), name, NpColors.MAGENTA) + 10f;
            String when = n.burning ? "BURNING" : Float.isNaN(n.burnStartIn) ? "" : NpFormat.tMinus(n.burnStartIn);
            StDraw.body(cv, x, row(gy, 0), when, n.burning ? NpColors.GREEN : NpColors.WHITE);
            float dv = n.burning ? n.burnToGo : n.burnDeltaV;
            if (!Float.isNaN(dv)) StDraw.valEnd(cv, R, row(gy, 0), NpFormat.mps(dv), "M/S", StDraw.B, NpColors.WHITE);
            String left = Double.isNaN(f.deltaVLeft) ? "--" : Double.isInfinite(f.deltaVLeft) ? "FREE" : NpFormat.mps(f.deltaVLeft);
            float tx = L + StDraw.pair(cv, L, row(gy, 1), "TOTAL", NpFormat.mps(n.planDeltaV), NpColors.WHITE) + 6f;
            tx += StDraw.pair(cv, tx, row(gy, 1), "OF", left, NpColors.WHITE) + 4f;
            StDraw.lab(cv, tx, row(gy, 1), "M/S");
            if (!Double.isNaN(f.deltaVLeft) && !Double.isInfinite(f.deltaVLeft)) {
                double leaves = f.deltaVLeft - n.planDeltaV;
                StDraw.valEnd(cv, R, row(gy, 1), NpFormat.mps(leaves), "LEFT", StDraw.B, leaves < 0 ? NpColors.RED : NpColors.WHITE);
            }
        } else {
            StDraw.lab(cv, L, row(gy, 0), "NO BURNS PLANNED");
            StDraw.hint(cv, L, row(gy, 1) + 2f, key.apply(Action.PLAN_BURN), "PLAN A BURN", NpColors.DIM);
        }
        float hx = L;
        if (n.proposal) {
            hx += StDraw.hint(cv, hx, row(gy, 2) + 2f, key.apply(Action.ACCEPT_PLAN), "ACCEPT", NpColors.WHITE) + 14f;
            StDraw.hint(cv, hx, row(gy, 2) + 2f, key.apply(Action.DISCARD_PLAN), "DISCARD", NpColors.DIM);
        } else if (n.burnsLeft > 0 && !n.burning) {
            StDraw.hint(cv, hx, row(gy, 2) + 2f, key.apply(Action.WARP_TO_BURN), "WARP TO BURN", NpColors.DIM);
        }
        String ap;
        Color apc = NpColors.WHITE;
        if (o.autopilot == Autopilot.ON) {
            ap = o.autopilotTo.isEmpty() ? "FLYING" : "→ " + up(o.autopilotTo);
            apc = NpColors.GREEN;
        } else if (o.autopilot == Autopilot.HELD) {
            ap = o.autopilotHeld.isEmpty() ? "HELD" : "HELD: " + up(o.autopilotHeld);
            apc = NpColors.AMBER;
        } else {
            ap = "OFF";
            apc = NpColors.DIM;
        }
        float aw = StDraw.lab(cv, L, row(gy, 3) + 2f, "AUTOPILOT");
        StDraw.body(cv, L + aw + 6f, row(gy, 3) + 2f, StDraw.fit(cv, ap, R - L - aw - 12f, StDraw.B, true, 0.3f), apc);
        if (n.burnsLeft > 0) {
            boolean helm = o.helm == HelmOrder.FLY_BURNS;
            String who = f.autopilot ? "THE AUTOPILOT FLIES THEM" : helm ? "THE HELM FLIES THEM" : "YOU FLY THEM";
            StDraw.lab(cv, L, row(gy, 4) + 2f, who);
        }
    }

    // ---- HELM and DOCK (Helm) --------------------------------------------------------------------

    private static final HelmOrder[] ORDERS = {HelmOrder.FLY_BURNS, HelmOrder.MATCH_VELOCITY, HelmOrder.HOLD_POSITION, HelmOrder.APPROACH, HelmOrder.DOCK};
    private static final Action[] ORDER_KEYS = {Action.HELM_FLY_BURNS, Action.HELM_MATCH_VELOCITY, Action.HELM_HOLD, Action.HELM_APPROACH, Action.HELM_DOCK};
    private static final String[] ORDER_WORDS = {"FLY BURNS", "MATCH", "HOLD", "APPROACH", "DOCK"};

    private void helm(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        UiContext ctx = d.ctx;
        Orders o = d.orderState;
        String who = ctx.helmsman.isEmpty() ? "NOBODY" : up(ctx.helmsman);
        StDraw.head(cv, gx, gy, gw, "HELM", who, ctx.helmsman.isEmpty() ? NpColors.AMBER : "YOU".equals(who) ? NpColors.CYAN : NpColors.WHITE);
        float lw = StDraw.lab(cv, L, row(gy, 0), "ORDER");
        String order = orderName(o.helm) + (o.helmTarget.isEmpty() || o.helm == HelmOrder.NONE ? "" : " · " + up(o.helmTarget));
        StDraw.body(cv, L + lw + 6f, row(gy, 0), StDraw.fit(cv, order, R - L - lw - 6f, StDraw.B, true, 0.3f), o.helm == HelmOrder.NONE ? NpColors.DIM : NpColors.GREEN);
        if (!ctx.helmFault.isEmpty()) {
            StDraw.severity(cv, L + 5f, row(gy, 1), Level.CAUTION, NpColors.AMBER);
            StDraw.body(cv, L + 16f, row(gy, 1), StDraw.fit(cv, up(ctx.helmFault), R - L - 16f, StDraw.B, true, 0.3f), NpColors.AMBER);
        } else if (!o.helmLine.isEmpty()) {
            StDraw.lab(cv, L, row(gy, 1), StDraw.fit(cv, up(o.helmLine), R - L, StDraw.LB, true, 0.4f));
        }
        // the orders with their keys: running green, refused dim
        float x = L;
        int r = 2;
        for (int i = 0; i < ORDERS.length; i++) {
            String k = key.apply(ORDER_KEYS[i]);
            float w = StDraw.hintW(cv, k, ORDER_WORDS[i]);
            if (x + w > R && x > L) {
                x = L;
                r++;
            }
            boolean on = o.helm == ORDERS[i];
            boolean refused = !on && !o.helmRefusal[ORDERS[i].ordinal()].isEmpty();
            x += StDraw.hint(cv, x, row(gy, r) + 2f, k, ORDER_WORDS[i], on ? NpColors.GREEN : refused ? NpColors.LENS_LEGEND : NpColors.WHITE) + 12f;
        }
        String ak = key.apply(Action.AUTOPILOT), ck = key.apply(Action.HELM_CANCEL);
        float aw = StDraw.hintW(cv, ak, "AUTOPILOT");
        if (x + aw > R) {
            x = L;
            r++;
        }
        boolean apOn = o.autopilot != Autopilot.OFF;
        boolean apRefused = !apOn && !o.autopilotRefusal.isEmpty();
        x += StDraw.hint(cv, x, row(gy, r) + 2f, ak, "AUTOPILOT", apOn ? NpColors.GREEN : apRefused ? NpColors.LENS_LEGEND : NpColors.WHITE) + 12f;
        if (x + StDraw.hintW(cv, ck, "CANCEL") > R) {
            x = L;
            r++;
        }
        StDraw.hint(cv, x, row(gy, r) + 2f, ck, "CANCEL", o.helm == HelmOrder.NONE && !apOn ? NpColors.LENS_LEGEND : NpColors.WHITE);
    }

    static String orderName(HelmOrder h) {
        switch (h) {
            case FLY_BURNS: return "FLY THE BURNS";
            case MATCH_VELOCITY: return "MATCH VELOCITY";
            case HOLD_POSITION: return "HOLD POSITION";
            case APPROACH: return "APPROACH";
            case DOCK: return "DOCK";
            default: return "NONE";
        }
    }

    private void dock(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        DockingApproach k = d.docking;
        UiContext ctx = d.ctx;
        String status;
        Color sc;
        if (ctx.docked) { status = "DOCKED"; sc = NpColors.GREEN; }
        else if (ctx.latched) { status = "LATCHED"; sc = NpColors.GREEN; }
        else if (k.capture == com.ringsofiron.uihud.providers.DockingApproachProvider.Capture.CAPTURED) { status = "CAPTURED"; sc = NpColors.GREEN; }
        else if (k.armed) { status = "ARMED"; sc = NpColors.WHITE; }
        else { status = "NOT ARMED"; sc = NpColors.DIM; }
        StDraw.head(cv, gx, gy, gw, "DOCK", k.present ? status : "NO PORT", k.present ? sc : NpColors.DIM);
        if (!k.present) {
            StDraw.lab(cv, L, row(gy, 0), "THIS HULL HAS NO DOCKING PORT");
            return;
        }
        String own = "PORT " + k.port + " OF " + k.ports + (k.portSize.isEmpty() ? "" : " · " + k.portSize);
        StDraw.body(cv, L, row(gy, 0), own, NpColors.WHITE);
        if (k.collarTorn || k.collarJammed) StDraw.box(cv, R - StDraw.boxW(cv, k.collarTorn ? "TORN" : "JAMMED"), row(gy, 0), k.collarTorn ? "TORN" : "JAMMED", BoxState.CAUTION);
        if (!k.other.isEmpty()) {
            float lw = StDraw.lab(cv, L, row(gy, 1), ctx.docked || ctx.latched ? "AT" : "TO");
            String other = up(k.other) + (k.otherPort > 0 ? " PORT " + k.otherPort : "") + (k.otherPortSize.isEmpty() ? "" : " · " + k.otherPortSize);
            StDraw.body(cv, L + lw + 6f, row(gy, 1), StDraw.fitName(cv, other, R - L - lw - 6f, StDraw.B, true, 0.3f), NpColors.WHITE);
        } else {
            StDraw.lab(cv, L, row(gy, 1), "NO PORT IN RANGE");
        }
        if (ctx.docked || ctx.latched) {
            float x = L;
            if (!Float.isNaN(k.strain)) x += StDraw.pair(cv, x, row(gy, 2), "STRAIN", NpFormat.percent(k.strain), k.strain > 0.6f ? NpColors.AMBER : NpColors.WHITE) + 12f;
            if (k.rigid) StDraw.lab(cv, x, row(gy, 2), "RIGID");
        } else {
            String cl;
            Color cc = NpColors.WHITE;
            switch (k.clearance) {
                case GRANTED: cl = "GRANTED" + (Float.isNaN(k.clearanceLeft) ? "" : "  " + NpFormat.clock(k.clearanceLeft)); cc = NpColors.GREEN; break;
                case DENIED: cl = k.refusal.isEmpty() ? "REFUSED" : "REFUSED: " + up(k.refusal); cc = NpColors.AMBER; break;
                case NO_PORT: cl = "NO FREE PORT"; cc = NpColors.AMBER; break;
                case YIELDS: cl = "STRUCK SHIP: NONE NEEDED"; cc = NpColors.DIM; break;
                default: cl = k.armed ? "ASKED" : "NOT ASKED"; cc = NpColors.DIM; break;
            }
            float lw = StDraw.lab(cv, L, row(gy, 2), "CLEARANCE");
            StDraw.body(cv, L + lw + 6f, row(gy, 2), StDraw.fit(cv, cl, R - L - lw - 6f, StDraw.B, true, 0.3f), cc);
            if (!Float.isNaN(k.range)) {
                float x = L + StDraw.pair(cv, L, row(gy, 3), "RNG", km(k.range), NpColors.WHITE) + 12f;
                if (!Float.isNaN(k.closing)) {
                    boolean fast = !Float.isNaN(k.safeSpeed) && k.closing > k.safeSpeed;
                    StDraw.pair(cv, x, row(gy, 3), "CLOSING", mps(k.closing), fast ? NpColors.AMBER : NpColors.WHITE);
                }
            }
        }
        float x = L;
        if (!ctx.docked && !ctx.latched) {
            if (k.ports > 1) x += StDraw.hint(cv, x, row(gy, 4) + 2f, key.apply(Action.NEXT_PORT), "NEXT PORT", NpColors.DIM) + 12f;
            StDraw.hint(cv, x, row(gy, 4) + 2f, key.apply(Action.ARM_PORT), k.armed ? "DISARM" : "ARM", NpColors.DIM);
        } else {
            StDraw.hint(cv, x, row(gy, 4) + 2f, key.apply(Action.UNDOCK), ctx.latched ? "CAST OFF" : "UNDOCK", NpColors.DIM);
        }
    }

    // ---- SENSORS, WEAPONS, DEFENSE (Tactical) ----------------------------------------------------

    /** The step SENSORS opens on: the smallest that holds the lock and every inbound missile, else the nearest hostile, else the nearest contact. */
    int autoStep() {
        ContactProvider.ContactFrame cf = d.contactFrame;
        float need = Float.NaN, hostile = Float.NaN, nearest = Float.NaN;
        for (int i = 0; i < cf.contacts.size; i++) {
            Contact c = cf.contacts.get(i);
            if (Float.isNaN(c.range) || c.kind == ContactProvider.Kind.WAYPOINT) continue;
            if (c.locked || c.kind == ContactProvider.Kind.MISSILE && c.threat) need = Float.isNaN(need) ? c.range : Math.max(need, c.range);
            if (c.kind == ContactProvider.Kind.SHIP && c.relation == Relation.HOSTILE && (Float.isNaN(hostile) || c.range < hostile)) hostile = c.range;
            if (Float.isNaN(nearest) || c.range < nearest) nearest = c.range;
        }
        if (Float.isNaN(need)) need = hostile;
        if (Float.isNaN(need)) need = nearest;
        if (Float.isNaN(need)) return 3;
        for (int i = 0; i < STEPS.length; i++) if (need <= STEPS[i] * 1000f * ROOM) return i;
        return STEPS.length - 1;
    }

    private void sensors(NpCanvas cv, Mfd m, float gx, float gy, float gw) {
        StDraw.head(cv, gx, gy, gw, "SENSORS", "", NpColors.DIM);
        int step = m.rangeSet ? m.rangeStep : autoStep();
        m.rangeStep = step;
        String label = (STEPS[step] < 1f ? NpFormat.tenths(STEPS[step]) : Integer.toString((int) STEPS[step])) + " KM";
        float R = gx + gw - 4f, kw = 18f, lw = cv.width(label, StDraw.LB, true, 0.4f);
        float plus = R - kw, minus = R - kw - 12f - lw - kw;
        StDraw.button(cv, plus, gy + 1.5f, kw, 15f, "+");
        cv.text(R - kw - 6f, gy + 13f, label, StDraw.LB, true, NpColors.DIM, 0.4f, NpCanvas.END);
        StDraw.button(cv, minus, gy + 1.5f, kw, 15f, NpFormat.MINUS);
        m.minusX = minus;
        m.plusX = plus;
        m.buttonsY = gy + 1.5f;

        FlightStatus f = d.flightStatus;
        float hdg = Float.isNaN(f.heading) ? 90f : f.heading;
        float range = STEPS[step] * 1000f;
        blipPool.addAll(blips);
        blips.clear();
        ContactProvider.ContactFrame cf = d.contactFrame;
        for (int i = 0; i < cf.contacts.size; i++) {
            Contact c = cf.contacts.get(i);
            if (c.kind == ContactProvider.Kind.WAYPOINT && !c.nav) continue;
            if (Float.isNaN(c.range) || Float.isNaN(c.bearingDeg)) continue;
            double a = Math.toRadians(hdg + c.bearingDeg);
            float dx = (float) (c.range * Math.cos(a)), dy = (float) (c.range * Math.sin(a));
            NpInstruments.Blip b = blipPool.isEmpty() ? new NpInstruments.Blip() : blipPool.remove(blipPool.size() - 1);
            b.kind = kindOf(c);
            b.x = dx / range;
            b.y = -dy / range;
            b.locked = c.locked;
            b.nav = c.nav;
            b.label = null;
            b.heading = (float) ((90.0 - Math.toDegrees(Math.atan2(-dy, -dx))) % 360.0 + 360.0) % 360f;
            blips.add(b);
        }
        // north up; the bubble's heading is counter-clockwise from +x, the scope's clockwise from north
        NpInstruments.scope(cv, gx + 50f, gy + 66f, 40f, ((90f - hdg) % 360f + 360f) % 360f, blips, null);

        // the contacts by threat: inbound missiles soonest first, the hostiles nearest first, then the rest
        listed.clear();
        inbound.clear();
        ThreatProvider.ThreatFrame tf = d.threatFrame;
        for (int i = 0; i < tf.missiles.size; i++) if (tf.missiles.get(i).side == ThreatProvider.Side.INCOMING) inbound.add(tf.missiles.get(i));
        inbound.sort((a, b) -> Float.compare(nanLast(a.timeToImpact), nanLast(b.timeToImpact)));
        for (int i = 0; i < cf.contacts.size; i++) {
            Contact c = cf.contacts.get(i);
            if (c.kind == ContactProvider.Kind.MISSILE || c.kind == ContactProvider.Kind.WAYPOINT || Float.isNaN(c.range)) continue;
            listed.add(c);
        }
        listed.sort((a, b) -> {
            int ta = a.relation == Relation.HOSTILE ? 0 : 1, tb = b.relation == Relation.HOSTILE ? 0 : 1;
            return ta != tb ? ta - tb : Float.compare(a.range, b.range);
        });
        // the columns: CONTACT, then KM and CLOSING (IMPACT with missiles in) right-aligned, a label's gap apart
        float tx = gx + 96f, kmX = tx + 112f, tR = gx + gw - 4f;
        StDraw.lab(cv, tx, gy + 34f, "CONTACT");
        StDraw.labEnd(cv, kmX, gy + 34f, "KM");
        StDraw.labEnd(cv, tR, gy + 34f, inbound.isEmpty() ? "CLOSING" : "IMPACT");
        int n = 0;
        for (int i = 0; i < inbound.size() && n < 4; i++, n++) {
            MissileTrack mt = inbound.get(i);
            float y = gy + 52f + n * 18f;
            NpSymbols.chevron(cv, tx + 4f, y - 4.5f, 90f, NpColors.RED, 1.5f, 3.5f);
            // a missile by what it is: its shooter is on the scope and in the world
            String km = Float.isNaN(mt.range) ? "--" : NpFormat.tenths(mt.range / 1000f);
            cv.text(tx + 12f, y, "MISSILE", StDraw.LB, true, NpColors.RED, 0.3f, NpCanvas.START);
            cv.text(kmX, y, km, StDraw.B, true, NpColors.WHITE, 0f, NpCanvas.END);
            cv.text(tR, y, Float.isNaN(mt.timeToImpact) ? "--" : NpFormat.clock(mt.timeToImpact), StDraw.B, true, NpColors.RED, 0f, NpCanvas.END);
        }
        for (int i = 0; i < listed.size() && n < 4; i++, n++) {
            Contact c = listed.get(i);
            float y = gy + 52f + n * 18f;
            NpSymbols.Kind k = kindOf(c);
            Color col = NpSymbols.colorOf(k);
            NpSymbols.shape(cv, k, tx + 4f, y - 4.5f, 3.5f, col, 1.2f, c.locked, 0f);
            if (c.locked) cv.stroke(tx - 3f, y - 12f, tR - tx + 6f, 16f, 1.2f, NpColors.CYAN);
            String km = NpFormat.tenths(c.range / 1000f);
            float room = kmX - cv.width(km, StDraw.B, true, 0f) - 4f - (tx + 12f);
            cv.text(tx + 12f, y, StDraw.fitName(cv, up(c.label), room, StDraw.LB, true, 0.3f), StDraw.LB, true, col, 0.3f, NpCanvas.START);
            cv.text(kmX, y, km, StDraw.B, true, NpColors.WHITE, 0f, NpCanvas.END);
            String cl = Float.isNaN(c.closing) ? "" : (c.closing >= 0 ? "+" : NpFormat.MINUS) + NpFormat.tenths(Math.abs(c.closing));
            cv.text(tR, y, cl, StDraw.B, true, NpColors.WHITE, 0f, NpCanvas.END);
        }
        if (n == 0) StDraw.lab(cv, tx, gy + 52f, "NO CONTACTS");
    }

    private static float nanLast(float v) {
        return Float.isNaN(v) ? Float.MAX_VALUE : v;
    }

    static NpSymbols.Kind kindOf(Contact c) {
        switch (c.kind) {
            case STATION: return NpSymbols.Kind.STATION;
            case DERELICT: return NpSymbols.Kind.DERELICT;
            case MISSILE: return c.relation == Relation.FRIENDLY && !c.threat ? NpSymbols.Kind.OWN_MISSILE : NpSymbols.Kind.MISSILE;
            case EVA: return NpSymbols.Kind.FRIENDLY;
            default: break;
        }
        if (c.state == ContactProvider.State.WRECKED) return NpSymbols.Kind.DERELICT;
        switch (c.relation) {
            case HOSTILE: return NpSymbols.Kind.HOSTILE;
            case FRIENDLY: return NpSymbols.Kind.FRIENDLY;
            case MISSILE: return NpSymbols.Kind.MISSILE;
            case UNKNOWN: return NpSymbols.Kind.UNKNOWN;
            default: return NpSymbols.Kind.NEUTRAL;
        }
    }

    private void weapons(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        boolean hold = wf.holdFire || d.ctx.holdFire;
        StDraw.head(cv, gx, gy, gw, "WEAPONS", hold ? "HOLD FIRE" : "", NpColors.AMBER);
        if (wf.mounts.size == 0) {
            StDraw.lab(cv, L, row(gy, 0), "NO WEAPONS FITTED");
            return;
        }
        boolean locked = d.targetStatus.present;
        float cWeapon = L + 56f, cState = L + 124f, cHow = R - 40f;
        for (int i = 0; i < wf.mounts.size && i < 5; i++) {
            Mount m = wf.mounts.get(i);
            float y = row(gy, i);
            cv.text(L, y, StDraw.fitName(cv, StDraw.place(m.name), 52f, StDraw.LB, true, 0.3f), StDraw.LB, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            cv.text(cWeapon, y, StDraw.fitHead(cv, StDraw.weaponName(m.weapon), 64f, StDraw.LB, true, 0.3f), StDraw.LB, true, NpColors.DIM, 0.3f, NpCanvas.START);
            cv.text(cState, y, StDraw.fit(cv, Tactics.word(m, locked), cHow - cState - 48f, StDraw.LB, true, 0.3f), StDraw.LB, true, Tactics.stateColor(m, locked), 0.3f, NpCanvas.START);
            String how = Tactics.how(m, hold);
            cv.text(cHow, y, how, StDraw.LB, true, m.controller == WeaponStatusProvider.Controller.PLAYER ? NpColors.CYAN : NpColors.WHITE, 0.3f, NpCanvas.END);
            String last;
            if (m.kind == WeaponStatusProvider.Kind.LAUNCHER && m.ammo >= 0) last = m.ammo + "/" + Math.max(m.ammo, m.magazine);
            else if (locked && m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE && m.working() && !Float.isNaN(m.hitChance)) last = NpFormat.percent(m.hitChance);
            else last = "";
            cv.text(R, y, last, StDraw.B, true, NpColors.CYAN, 0f, NpCanvas.END);
        }
        if (wf.mounts.size > 5) StDraw.labEnd(cv, R, row(gy, 4) + 14f, "+" + (wf.mounts.size - 5) + " MORE");
    }

    private void defense(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        ShieldStatus s = d.shieldStatus;
        boolean emitter = s.present && s.emitters > 0;
        StDraw.head(cv, gx, gy, gw, "DEFENSE", emitter ? s.raised ? "RAISED" : s.loweredForDock ? "LOWERED TO DOCK" : "LOWERED" : "", s.raised ? NpColors.GREEN : NpColors.DIM);
        float cx = gx + 46f, cy = gy + 70f, r = 30f;
        float colX = gx + 100f;
        int line = 0;
        if (emitter) {
            // the ring by sector: bow up, port left; each arc as full as its share of the cloud
            float peak = 0f;
            for (float v : s.sectors) peak = Math.max(peak, v);
            for (int i = 0; i < 4; i++) {
                float share = peak > 0f ? s.sectors[i] / peak : 0f;
                float mid = 90f + 90f * i;
                Color c = !s.raised ? NpColors.DIM : share < 0.25f ? NpColors.AMBER : NpColors.WHITE;
                cv.arc(cx, cy, r, mid - 38f, mid + 38f, 1.2f, NpColors.TRACK, 1f);
                if (s.raised && share > 0f) cv.arc(cx, cy, r, mid - 38f, mid + 38f, 1.2f + 3.5f * share, c, 1f);
            }
            NpSymbols.chevron(cv, cx, cy, 0f, NpColors.CYAN, 1.5f, 5f);
            line += row2(cv, colX, R, gy, line, "CLOUD", NpFormat.percent(s.share()), s.share() < 0.25f && s.raised ? NpColors.AMBER : NpColors.WHITE);
            line += row2(cv, colX, R, gy, line, "TRIM", trimWord(s.trim), NpColors.CYAN);
            String em = s.powerStunned ? "STUNNED" : s.emittersTripped > 0 ? s.emittersTripped + " OF " + s.emitters + " TRIPPED" : s.emittersWorking + " OF " + s.emitters;
            line += row2(cv, colX, R, gy, line, "EMITTERS", em, s.powerStunned || s.emittersTripped > 0 ? NpColors.AMBER : NpColors.WHITE);
        } else {
            cv.ring(cx, cy, r, 1f, NpColors.TRACK);
            cv.text(cx, cy - 3f, "NO SHIELD", StDraw.LB, true, NpColors.DIM, 0.4f, NpCanvas.MIDDLE);
            cv.text(cx, cy + 11f, "EMITTER", StDraw.LB, true, NpColors.DIM, 0.4f, NpCanvas.MIDDLE);
        }
        WeaponStatusProvider.WeaponFrame wf = d.weaponFrame;
        String decoy = wf.decoyCooldown <= 0f ? "READY" : (int) Math.ceil(wf.decoyCooldown) + " S";
        line += row2(cv, colX, R, gy, line, "DECOY", decoy, wf.decoyCooldown <= 0f ? NpColors.WHITE : NpColors.DIM);
        int pd = 0, pdWorking = 0;
        for (int i = 0; i < wf.mounts.size; i++) {
            Mount m = wf.mounts.get(i);
            if (m.kind != WeaponStatusProvider.Kind.POINT_DEFENSE) continue;
            pd++;
            if (m.working()) pdWorking++;
        }
        if (pd > 0) {
            String v = wf.pointDefenseActive ? "ENGAGING" : pdWorking < pd ? pdWorking + " OF " + pd : "AUTO";
            row2(cv, colX, R, gy, line, "PD", v, wf.pointDefenseActive ? NpColors.GREEN : pdWorking < pd ? NpColors.AMBER : NpColors.WHITE);
        }
    }

    /** A label at {@code x} and its value ending at {@code R}, on row {@code i}; returns 1. */
    private static int row2(NpCanvas cv, float x, float R, float gy, int i, String label, String v, Color c) {
        StDraw.lab(cv, x, row(gy, i), label);
        StDraw.bodyEnd(cv, R, row(gy, i), v, c);
        return 1;
    }

    static String trimWord(String trim) {
        return "starboard".equals(trim) ? "STBD" : up(trim);
    }

    // ---- DAMAGE, POWER, ATMOS (Engineering) ------------------------------------------------------

    private void damage(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        ShipStatus s = d.shipStatus;
        alerts.clear();
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.level == Level.ADVISORY) continue;
            if (a.id.startsWith("fire:") || a.id.startsWith("breach:") || a.id.startsWith("smoke:") || a.id.equals("hull")) alerts.add(a);
            else if (a.id.startsWith("sys:")) {
                String w = Annunciation.what(a);
                if (w.equals("destroyed") || w.equals("disabled")) alerts.add(a);
            }
        }
        Level worst = null;
        for (int i = 0; i < alerts.size(); i++) if (worst == null || alerts.get(i).level.ordinal() > worst.ordinal()) worst = alerts.get(i).level;
        StDraw.head(cv, gx, gy, gw, "DAMAGE", alerts.isEmpty() ? "NO DAMAGE" : alerts.size() + (alerts.size() == 1 ? " FAULT" : " FAULTS"), worst == null ? NpColors.DIM : StDraw.color(worst));
        float lw = StDraw.lab(cv, L, row(gy, 0), "HULL");
        float bx = L + lw + 10f, bw = R - bx - 46f;
        cv.fill(bx, row(gy, 0) - 6f, bw, 4f, NpColors.TRACK);
        float hull = MathUtils.clamp(s.structure, 0f, 1f);
        Color hc = hull < 0.55f ? NpColors.RED : hull < 0.75f ? NpColors.AMBER : NpColors.WHITE;
        cv.fill(bx, row(gy, 0) - 6f, bw * hull, 4f, hc);
        cv.line(bx + bw * 0.45f, row(gy, 0) - 10f, bx + bw * 0.45f, row(gy, 0) + 2f, 1.5f, NpColors.RED);
        StDraw.bodyEnd(cv, R, row(gy, 0), NpFormat.percent(hull), hc);
        int line = 1;
        for (int i = 0; i < alerts.size() && line < 5; i++) {
            Alert a = alerts.get(i);
            if (a.id.equals("hull")) continue;
            float y = row(gy, line++);
            Color c = StDraw.color(a.level);
            StDraw.severity(cv, L + 5f, y, a.level, c);
            boolean sys = a.id.startsWith("sys:");
            float ww = StDraw.body(cv, L + 16f, y, up(a.code), c);
            String where = sys ? (Annunciation.what(a).equals("destroyed") ? "WRECKED" : "DISABLED") : up(a.room);
            StDraw.lab(cv, L + 16f + ww + 8f, y, StDraw.fit(cv, where, R - L - ww - 70f, StDraw.LB, true, 0.4f));
            if (!sys && a.count > 1) StDraw.labEnd(cv, R, y, a.count + " TILES");
        }
        if (line == 1) StDraw.lab(cv, L, row(gy, 1), "NO FIRES, BREACHES OR WRECKS");
    }

    private void power(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        PowerFrame p = d.powerFrame;
        boolean blackout = p.ratedWatts > 0 && p.outputWatts <= 0;
        boolean damaged = p.ratedWatts > 0 && p.outputWatts < p.ratedWatts * 0.98;
        StDraw.head(cv, gx, gy, gw, "POWER", blackout ? "BLACKOUT" : damaged ? "REACTOR DAMAGED" : "", blackout ? NpColors.RED : NpColors.AMBER);
        float bx = L + 70f, bw = 72f, vx = bx + bw + 10f;
        StDraw.lab(cv, L, row(gy, 0), "REACTOR");
        cv.fill(bx, row(gy, 0) - 6f, bw, 4f, NpColors.TRACK);
        float out = p.ratedWatts > 0 ? (float) MathUtils.clamp(p.outputWatts / p.ratedWatts, 0, 1) : 0f;
        cv.fill(bx, row(gy, 0) - 6f, bw * out, 4f, blackout ? NpColors.RED : damaged ? NpColors.AMBER : NpColors.WHITE);
        String mw = p.ratedWatts > 0 ? megawatts(p.outputWatts) : "--";
        float w = StDraw.val(cv, vx, row(gy, 0), mw, "", StDraw.B, NpColors.WHITE);
        if (p.ratedWatts > 0) StDraw.lab(cv, vx + w + 5f, row(gy, 0), "OF " + megawatts(p.ratedWatts) + " MW");
        float charge = p.circuits.size > 0 ? p.charge() : d.shipStatus.power;
        StDraw.lab(cv, L, row(gy, 1), "STORE");
        cv.fill(bx, row(gy, 1) - 6f, bw, 4f, NpColors.TRACK);
        if (!Float.isNaN(charge)) cv.fill(bx, row(gy, 1) - 6f, bw * MathUtils.clamp(charge, 0f, 1f), 4f, charge < 0.2f ? NpColors.AMBER : NpColors.WHITE);
        StDraw.body(cv, vx, row(gy, 1), Float.isNaN(charge) ? "--" : NpFormat.percent(charge), NpColors.WHITE);
        // not working: what comes back by itself first, soonest at the top, then what needs work
        rows.clear();
        for (int i = 0; i < p.faults.size; i++) rows.add(p.faults.get(i));
        rows.sort((a, b) -> {
            boolean ta = !Float.isNaN(a.timer), tb = !Float.isNaN(b.timer);
            if (ta != tb) return ta ? -1 : 1;
            if (ta) return Float.compare(a.timer, b.timer);
            return a.fault.ordinal() - b.fault.ordinal();
        });
        if (rows.isEmpty()) {
            StDraw.lab(cv, L, row(gy, 2) + 4f, "EVERY SYSTEM WORKING");
            return;
        }
        StDraw.lab(cv, L, row(gy, 2) + 2f, "NOT WORKING");
        float sX = L + 104f;
        for (int i = 0; i < rows.size() && i < 2; i++) {
            SystemRow r = rows.get(i);
            float y = row(gy, 3 + i) + 2f;
            String name = up(r.system) + (r.room.isEmpty() ? "" : " · " + up(r.room));
            cv.text(L, y, StDraw.fitName(cv, name, sX - L - 6f, StDraw.LB, true, 0.3f), StDraw.LB, true, NpColors.WHITE, 0.3f, NpCanvas.START);
            String st = faultWord(r.fault);
            BoxState box = r.fault == PowerStatusProvider.Fault.DESTROYED ? BoxState.WARNING : r.fault == PowerStatusProvider.Fault.OFF ? BoxState.OFF : BoxState.CAUTION;
            StDraw.box(cv, sX, y, st, box);
            String clear = !Float.isNaN(r.timer) ? "BACK " + NpFormat.clock(r.timer)
                    : r.fault == PowerStatusProvider.Fault.OFF ? "SWITCH ON"
                    : r.fault == PowerStatusProvider.Fault.NO_POWER ? "NO CIRCUIT"
                    : r.fault == PowerStatusProvider.Fault.DESTROYED ? "REPLACE" : "REPAIR " + NpFormat.percent(r.health);
            StDraw.labEnd(cv, R, y, clear);
        }
        if (rows.size() > 2) StDraw.labEnd(cv, R, row(gy, 2) + 2f, "+" + (rows.size() - 2) + " MORE");
    }

    static String faultWord(PowerStatusProvider.Fault f) {
        switch (f) {
            case DESTROYED: return "WRECKED";
            case DISABLED: return "DISABLED";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            default: return "OFF";
        }
    }

    static String megawatts(double watts) {
        return watts <= 0 ? "0" : NpFormat.tenths(watts / 1e6);
    }

    private void atmos(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        ShipStatus s = d.shipStatus;
        CaptainStatus c = d.captainStatus;
        alerts.clear();
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size; i++) {
            Alert a = all.get(i);
            if (a.id.startsWith("air:") || a.id.startsWith("smoke:")) alerts.add(a);
        }
        boolean venting = s.cabinPressure < 0.7f;
        StDraw.head(cv, gx, gy, gw, "ATMOS", alerts.isEmpty() ? "ALL ROOMS BREATHABLE" : alerts.size() + (alerts.size() == 1 ? " ROOM" : " ROOMS"), alerts.isEmpty() ? NpColors.DIM : NpColors.AMBER);
        if (!Double.isNaN(s.emergencyOxygenKg)) {
            row2(cv, L, R, gy, 0, "EMERGENCY O2", Math.round(s.emergencyOxygenKg) + " KG · " + NpFormat.percent(s.emergencyOxygen), NpColors.RED);
        } else {
            row2(cv, L, R, gy, 0, "O2 TANKS", Float.isNaN(s.oxygen) ? "NONE" : NpFormat.percent(s.oxygen), !Float.isNaN(s.oxygen) && s.oxygen < 0.25f ? NpColors.AMBER : NpColors.WHITE);
        }
        row2(cv, L, R, gy, 1, "WORST CABIN", Math.round(s.cabinPressure * 101.3f) + " KPA", venting ? NpColors.AMBER : NpColors.WHITE);
        if (c.present && !Float.isNaN(c.roomPressure)) {
            String here = (c.room.isEmpty() ? "HERE" : up(c.room));
            String air = Math.round(c.roomPressure * 101.3f) + " KPA · O2 " + (Float.isNaN(c.roomOxygen) ? "--" : Math.round(c.roomOxygen * 21f) + "%");
            boolean bad = c.roomPressure < 0.7f || !Float.isNaN(c.roomOxygen) && c.roomOxygen < 0.7f;
            row2(cv, L, R, gy, 2, StDraw.fit(cv, "YOU · " + here, 110f, StDraw.LB, true, 0.4f), air, bad ? NpColors.AMBER : NpColors.WHITE);
        }
        int line = 3;
        for (int i = 0; i < alerts.size() && line < 5; i++) {
            Alert a = alerts.get(i);
            float y = row(gy, line++);
            Color col = StDraw.color(a.level);
            StDraw.severity(cv, L + 5f, y, a.level, col);
            float w = StDraw.body(cv, L + 16f, y, up(a.code), col);
            StDraw.lab(cv, L + 16f + w + 8f, y, StDraw.fit(cv, up(a.room), R - L - w - 30f, StDraw.LB, true, 0.4f));
        }
    }

    // ---- COMMS (Comms) ---------------------------------------------------------------------------

    private void comms(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        UiContext ctx = d.ctx;
        NavReadout n = d.navReadout;
        StationInfo s = ctx.docked ? n.docked : n.station;
        boolean has = s.present || ctx.docked;
        String name = up(ctx.docked && !ctx.stationName.isEmpty() && s.name.isEmpty() ? ctx.stationName : s.name);
        StDraw.head(cv, gx, gy, gw, "COMMS", ctx.docked ? "DOCKED" : has ? "NAV STATION" : "", ctx.docked ? NpColors.GREEN : NpColors.DIM);
        if (!has) {
            StDraw.lab(cv, L, row(gy, 0), "NO STATION IN TOUCH");
            tow(cv, L, R, gy, 2);
            return;
        }
        StDraw.body(cv, L, row(gy, 0), StDraw.fitName(cv, name, R - L, StDraw.B, true, 0.3f), NpColors.WHITE);
        if (!s.owner.isEmpty()) {
            String owner = up(s.owner) + (s.sponsor ? " · YOUR SPONSOR" : "");
            StDraw.lab(cv, L, row(gy, 1), StDraw.fit(cv, owner, R - L, StDraw.LB, true, 0.4f));
        }
        if (!s.standingName.isEmpty()) {
            String st = up(s.standingName) + (Float.isNaN(s.standing) ? "" : " (" + (s.standing >= 0 ? "+" : NpFormat.MINUS) + Math.round(Math.abs(s.standing)) + ")");
            row2(cv, L, R, gy, 2, "STANDING", st, s.standing < -20 ? NpColors.AMBER : NpColors.WHITE);
        }
        if (ctx.docked) {
            float x = L;
            if (s.market) x += StDraw.hint(cv, x, row(gy, 3) + 2f, key.apply(Action.TRADE), "MARKET", NpColors.WHITE) + 12f;
            if (s.yard) x += StDraw.hint(cv, x, row(gy, 3) + 2f, key.apply(Action.YARD), "YARD", NpColors.WHITE) + 12f;
            if (s.contracts) StDraw.hint(cv, x, row(gy, 3) + 2f, key.apply(Action.MISSIONS), "MISSIONS", NpColors.WHITE);
            StDraw.hint(cv, L, row(gy, 4) + 4f, key.apply(Action.UNDOCK), "UNDOCK", NpColors.DIM);
        } else {
            if (!s.refusal.isEmpty()) {
                StDraw.severity(cv, L + 5f, row(gy, 3), Level.CAUTION, NpColors.AMBER);
                StDraw.body(cv, L + 16f, row(gy, 3), StDraw.fit(cv, "WOULD REFUSE: " + up(s.refusal), R - L - 16f, StDraw.B, true, 0.3f), NpColors.AMBER);
            }
            tow(cv, L, R, gy, 4);
        }
    }

    /** The tow, on row {@code i}, while the beacon would answer, or why it wouldn't once the ship needs one. */
    private void tow(NpCanvas cv, float L, float R, float gy, int i) {
        Orders o = d.orderState;
        if (o.towRefusal.isEmpty()) {
            String k = key.apply(Action.CUT);
            float x = L + StDraw.lab(cv, L, row(gy, i) + 2f, "TOW") + 6f;
            x += StDraw.capKey(cv, x, row(gy, i) - 11f, k, 17f) + 2f;
            x += StDraw.capKey(cv, x, row(gy, i) - 11f, k, 17f) + 6f;
            String cost = o.towFee > 0 ? NpFormat.thousands(Math.round(o.towFee)) + " CR · " + Math.round(o.towStanding) + " STANDING" : "CALLS A TOW";
            StDraw.lab(cv, x, row(gy, i) + 2f, StDraw.fit(cv, cost, R - x, StDraw.LB, true, 0.4f));
        } else if (!d.ctx.disabledReason.isEmpty()) {
            StDraw.lab(cv, L, row(gy, i) + 2f, StDraw.fit(cv, "NO TOW: " + up(o.towRefusal), R - L, StDraw.LB, true, 0.4f));
        }
    }

    // ---- CREW and CONTRACTS (Command) ------------------------------------------------------------

    private void crew(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f, M = gx + gw / 2f + 2f;
        StationBoardProvider.StationBoard b = d.stations;
        StDraw.head(cv, gx, gy, gw, "CREW", b.crewAboard + " ABOARD · " + b.crewFree + " FREE", NpColors.DIM);
        // the posts, two to a row
        int cell = 0;
        for (int i = 0; i < b.seats.size && cell < 6; i++, cell++) {
            StationBoardProvider.Seat s = b.seats.get(i);
            float x = cell % 2 == 0 ? L : M, w = (cell % 2 == 0 ? M - 10f : R) - x;
            float y = row(gy, cell / 2);
            float lw = StDraw.lab(cv, x, y, StDraw.fitName(cv, up(s.name), w * 0.55f, StDraw.LB, true, 0.4f));
            String who = s.hand == StationBoardProvider.Hand.PLAYER ? "YOU" : s.hand == StationBoardProvider.Hand.NOBODY ? "--" : up(s.who) + (s.hand == StationBoardProvider.Hand.COMING ? " …" : "");
            Color c = s.hand == StationBoardProvider.Hand.PLAYER ? NpColors.CYAN : s.hand == StationBoardProvider.Hand.NOBODY ? NpColors.DIM : NpColors.WHITE;
            cv.text(x + w, y, StDraw.fitName(cv, who, w - lw - 6f, StDraw.LB, true, 0.3f), StDraw.LB, true, c, 0.3f, NpCanvas.END);
        }
        int line = (cell + 1) / 2;
        Array<Alert> all = d.alerts.all();
        for (int i = 0; i < all.size && line < 5; i++) {
            Alert a = all.get(i);
            if (!a.id.startsWith("casualty:") && !a.id.equals("eva")) continue;
            float y = row(gy, line++);
            Color c = a.level == Level.ADVISORY ? NpColors.WHITE : StDraw.color(a.level);
            if (a.level != Level.ADVISORY) StDraw.severity(cv, L + 5f, y, a.level, c);
            cv.text(L + 16f, y, StDraw.fit(cv, up(a.text), R - L - 60f, StDraw.LB, true, 0.3f), StDraw.LB, true, c, 0.3f, NpCanvas.START);
            if (!Float.isNaN(a.timer)) cv.text(R, y, NpFormat.clock(a.timer), StDraw.B, true, c, 0f, NpCanvas.END);
        }
        Orders o = d.orderState;
        if (line < 5) {
            float x = L;
            if (o.battleStations) x += StDraw.lab(cv, x, row(gy, line), "BATTLE STATIONS") + 12f;
            if (o.suitsAtBattleStations) x += StDraw.lab(cv, x, row(gy, line), "SUITS ON") + 12f;
            if (o.evaHeld) StDraw.lab(cv, x, row(gy, line), "EVA HELD");
        }
    }

    private void contracts(NpCanvas cv, float gx, float gy, float gw) {
        float L = gx + 8f, R = gx + gw - 8f;
        ObjectiveProvider.ObjectiveFrame of = d.objectiveFrame;
        int held = 0;
        for (int i = 0; i < of.objectives.size; i++) if (of.objectives.get(i).state != ObjectiveProvider.State.DONE) held++;
        StDraw.head(cv, gx, gy, gw, "CONTRACTS", held == 0 ? "NONE HELD" : held + " HELD", NpColors.DIM);
        int line = 0, shown = 0;
        for (int i = 0; i < of.objectives.size && line < 4; i++) {
            Objective o = of.objectives.get(i);
            if (o.state == ObjectiveProvider.State.DONE) continue;
            boolean late = o.late || o.state == ObjectiveProvider.State.FAILED || !Double.isNaN(o.timeLeft) && o.timeLeft < 0;
            String left = o.state == ObjectiveProvider.State.FAILED ? "FAILED" : NpFormat.timeLeft(o.timeLeft);
            float lw = left.isEmpty() ? 0f : StDraw.bodyW(cv, left) + 10f;
            StDraw.body(cv, L, row(gy, line), StDraw.fit(cv, up(o.title), R - L - lw, StDraw.B, true, 0.3f), NpColors.WHITE);
            if (!left.isEmpty()) StDraw.bodyEnd(cv, R, row(gy, line), left, late ? NpColors.AMBER : NpColors.WHITE);
            line++;
            String next = o.state == ObjectiveProvider.State.RETURN ? "RETURN TO " + up(o.station) : o.next.isEmpty() ? "" : "→ " + up(o.next);
            if (!next.isEmpty()) StDraw.lab(cv, L + 10f, row(gy, line++), StDraw.fit(cv, next, R - L - 10f, StDraw.LB, true, 0.4f));
            shown++;
        }
        if (held > shown) StDraw.lab(cv, L, row(gy, 4), "+" + (held - shown) + " MORE");
        else if (held == 0) StDraw.hint(cv, L, row(gy, 0) + 4f, key.apply(Action.MISSIONS), "MISSIONS AT A STATION", NpColors.DIM);
    }
}
